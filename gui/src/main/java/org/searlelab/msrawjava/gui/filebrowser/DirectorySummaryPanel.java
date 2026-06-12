package org.searlelab.msrawjava.gui.filebrowser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableColumn;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;

import org.searlelab.msrawjava.gui.GuiProcessingActivity;
import org.searlelab.msrawjava.gui.GUIPreferences;
import org.searlelab.msrawjava.gui.GUIPreferences.DirectorySummarySortKeySpec;
import org.searlelab.msrawjava.io.StructuredMetadataProvider;
import org.searlelab.msrawjava.io.VendorFile;
import org.searlelab.msrawjava.io.VendorFiles;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.mzml.MzmlFile;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.logging.Logger;

/** Small, streaming table that summarizes raw files in a directory. */
public class DirectorySummaryPanel extends JPanel {
	private static final long serialVersionUID=1L;

	private static final int sparkResolution=128;
	private static final Color COLOR_FILL=DirectorySummaryRenderers.COLOR_FILL;
	private static final Color SPINNER_BG=new Color(0xE0E0E0);
	static final SparkData FAILED=new SparkData(new float[0]);
	private static final ConcurrentHashMap<Path, DirectorySummaryMetrics> SLOW_BITS_CACHE=new ConcurrentHashMap<>();
	private static final java.util.Set<String> EXPECTED_SLOW_BITS_FAILURES_LOGGED=ConcurrentHashMap.newKeySet();
	private static final String VENDOR_ALL=DirectorySummaryVendorFilter.VENDOR_ALL;
	private static final String VENDOR_ALL_RAW_INSTRUMENT_FILES=DirectorySummaryVendorFilter.VENDOR_ALL_RAW_INSTRUMENT_FILES;
	private static final String SLOW_BITS_CANCELLED_BY_USER_SUMMARY=DirectorySummarySlowBitsFailures.SLOW_BITS_CANCELLED_BY_USER_SUMMARY;
	private static final long SLOW_BITS_STALL_THRESHOLD_NANOS=1_000_000_000L;
	private static final long SLOW_BITS_READER_RETRY_NANOS=750_000_000L;
	private static final boolean SLOW_BITS_TIMING_ENABLED=Boolean.getBoolean("msrawjava.gui.slowbits.timing");
	private static final AtomicInteger SLOW_BITS_THREAD_ID=new AtomicInteger(1);

	private final int slowBitsWorkerCount=Math.max(1, Runtime.getRuntime().availableProcessors()-2);
	private final JTable table;
	private final JScrollPane tableScrollPane;
	private final DirectorySummaryModel model=new DirectorySummaryModel();
	private final TableRowSorter<DirectorySummaryModel> sorter;
	// Use a wider pool to speed up slow-bit extraction on large directories.
	private final ExecutorService pool=Executors.newFixedThreadPool(slowBitsWorkerCount, r -> {
		Thread t=new Thread(r, "dir-summary-slow-bits-"+SLOW_BITS_THREAD_ID.getAndIncrement());
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});
	private final Timer loadingTimer;
	private volatile boolean closed=false;
	private boolean applyingSavedLayout=false;
	private boolean pendingColumnSave=false;
	private final JTextField searchField=new JTextField();
	private final JButton clearButton=new JButton("Clear");
	private final JComboBox<Object> vendorFilter=new JComboBox<>();
	private final ProgressSpinner spinner=new ProgressSpinner();
	private final AtomicInteger slowBitsTotal=new AtomicInteger(0);
	private final AtomicInteger slowBitsDone=new AtomicInteger(0);
	private final java.util.Set<DirectorySummaryRow> slowBitsRunning=ConcurrentHashMap.newKeySet();
	private final ConcurrentHashMap<DirectorySummaryRow, SlowBitsLaunchPlanner.Lane> slowBitsRunningLane=new ConcurrentHashMap<>();
	private final ConcurrentHashMap<DirectorySummaryRow, Long> slowBitsRunningStartNanos=new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Path, Long> slowBitsRetryAfterNanos=new ConcurrentHashMap<>();
	private final java.util.Set<Path> slowBitsDeprioritized=ConcurrentHashMap.newKeySet();
	private final java.util.Set<Path> slowBitsStallWarned=ConcurrentHashMap.newKeySet();
	private final java.util.Set<Path> slowBitsReaderNotReadyWarned=ConcurrentHashMap.newKeySet();
	private final AtomicBoolean slowBitsDispatchPending=new AtomicBoolean(false);
	private final Runnable processingActivityListener=this::requestSlowBitsDispatch;

	public DirectorySummaryPanel(VendorFiles files) {
		super(new BorderLayout());

		table=new JTable(model);
		sorter=new TableRowSorter<>(model);
		configureSorter();
		List<RowSorter.SortKey> validSortKeys=toSortKeys(GUIPreferences.getDirectorySummarySortKeySpecs());
		if (!validSortKeys.isEmpty()) {
			sorter.setSortKeys(validSortKeys);
		} else {
			sorter.setSortKeys(List.of(new RowSorter.SortKey(DirectorySummaryColumn.DATE_MODIFIED.modelIndex(), SortOrder.DESCENDING)));
		}
		sorter.addRowSorterListener(e -> {
			RowSorter<?> src=(RowSorter<?>)e.getSource();
			GUIPreferences.setDirectorySummarySortKeySpecs(toSortKeySpecs(src.getSortKeys()));
			requestSlowBitsDispatch();
		});

		table.setRowSorter(sorter);

		table.setRowHeight(28);
		table.setFillsViewportHeight(true);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		// Stripe renderers so it blends in
		table.setDefaultRenderer(String.class, StripeTableCellRenderer.BASE_RENDERER);
		table.setDefaultRenderer(Long.class, StripeTableCellRenderer.SIZE_RENDERER);
		table.setDefaultRenderer(Date.class, new DirectorySummaryRenderers.DateTimeRenderer());
		table.setDefaultRenderer(Float.class, new DirectorySummaryRenderers.GradientRenderer()); // formats "X.Y min"
		table.setDefaultRenderer(SparkData.class, new DirectorySummaryRenderers.SparkRenderer()); // red filled spark
		installTableHeaderTooltips();

		installColumnRenderers();

		add(buildSearchBar(), BorderLayout.NORTH);
		tableScrollPane=new JScrollPane(table);
		tableScrollPane.getViewport().addChangeListener(e -> requestSlowBitsDispatch());
		add(tableScrollPane, BorderLayout.CENTER);
		loadingTimer=new Timer(500, e -> {
			DirectorySummaryRenderers.SparkRenderer.advanceLoadingPhase();
			table.repaint();
			requestSlowBitsDispatch();
		});
		SwingUtilities.invokeLater(this::applySavedColumnLayout);
		installColumnPreferenceListeners();

		// Seed fast info (file name/vendor/size) synchronously so table appears immediately
		ArrayList<DirectorySummaryRow> brukerRows=new ArrayList<DirectorySummaryRow>();
		for (Path p : files.getBrukerDirs()) {
			brukerRows.add(DirectorySummaryRow.fromBruker(p));
		}
		Collections.sort(brukerRows);
		ArrayList<DirectorySummaryRow> thermoRows=new ArrayList<DirectorySummaryRow>();
		for (Path p : files.getThermoFiles()) {
			thermoRows.add(DirectorySummaryRow.fromThermo(p));
		}
		Collections.sort(thermoRows);
		ArrayList<DirectorySummaryRow> diaRows=new ArrayList<DirectorySummaryRow>();
		for (Path p : files.getDiaFiles()) {
			diaRows.add(DirectorySummaryRow.fromDia(p));
		}
		Collections.sort(diaRows);
		ArrayList<DirectorySummaryRow> mzmlRows=new ArrayList<DirectorySummaryRow>();
		for (Path p : files.getMzmlFiles()) {
			mzmlRows.add(DirectorySummaryRow.fromMzml(p));
		}
		Collections.sort(mzmlRows);

		ArrayList<DirectorySummaryRow> allRows=new ArrayList<DirectorySummaryRow>(brukerRows.size()+thermoRows.size()+diaRows.size()+mzmlRows.size());
		allRows.addAll(brukerRows);
		allRows.addAll(thermoRows);
		allRows.addAll(diaRows);
		allRows.addAll(mzmlRows);
		Collections.sort(allRows);

		model.addRows(allRows);
		initializeSlowBitsProgress(allRows);
		requestSlowBitsDispatch();
	}

	private void configureSorter() {
		for (DirectorySummaryColumn column : DirectorySummaryColumn.values()) {
			sorter.setSortable(column.modelIndex(), column.sortable);
			if (column.valueClass==Date.class) {
				sorter.setComparator(column.modelIndex(), Comparator.nullsLast(Comparator.<Date>naturalOrder()));
			} else if (column.valueClass==Float.class) {
				sorter.setComparator(column.modelIndex(), Comparator.nullsLast(Float::compareTo));
			}
		}
	}

	private static List<RowSorter.SortKey> toSortKeys(List<DirectorySummarySortKeySpec> specs) {
		if (specs==null||specs.isEmpty()) return List.of();
		ArrayList<RowSorter.SortKey> keys=new ArrayList<>(specs.size());
		for (DirectorySummarySortKeySpec spec : specs) {
			if (spec==null||spec.getSortOrder()==null) continue;
			DirectorySummaryColumn column=DirectorySummaryColumn.byKey(spec.getColumnKey());
			if (column==null||!column.sortable) continue;
			keys.add(new RowSorter.SortKey(column.modelIndex(), spec.getSortOrder()));
		}
		return keys;
	}

	private static List<DirectorySummarySortKeySpec> toSortKeySpecs(List<? extends RowSorter.SortKey> keys) {
		if (keys==null||keys.isEmpty()) return List.of();
		ArrayList<DirectorySummarySortKeySpec> specs=new ArrayList<>(keys.size());
		for (RowSorter.SortKey key : keys) {
			if (key==null||key.getSortOrder()==null) continue;
			DirectorySummaryColumn column=DirectorySummaryColumn.byModelIndex(key.getColumn());
			if (column!=null&&column.sortable) {
				specs.add(new DirectorySummarySortKeySpec(column.key, key.getSortOrder()));
			}
		}
		return specs;
	}

	private void installColumnRenderers() {
		for (DirectorySummaryColumn column : DirectorySummaryColumn.values()) {
			TableColumn tableColumn=table.getColumnModel().getColumn(column.modelIndex());
			switch (column) {
				case ROW_NUMBER:
					tableColumn.setCellRenderer(StripeTableCellRenderer.ROW_NUMBER_RENDERER);
					break;
				case FILE:
				case VENDOR:
					tableColumn.setCellRenderer(StripeTableCellRenderer.BASE_RENDERER);
					break;
				case DATE_MODIFIED:
				case DATE_ACQUIRED:
					tableColumn.setCellRenderer(new DirectorySummaryRenderers.DateTimeRenderer());
					break;
				case SIZE:
					tableColumn.setCellRenderer(StripeTableCellRenderer.SIZE_RENDERER);
					break;
				case GRADIENT_MIN:
					tableColumn.setCellRenderer(new DirectorySummaryRenderers.GradientRenderer());
					break;
				case TOTAL_TIC:
					tableColumn.setCellRenderer(StripeTableCellRenderer.SCI_RENDERER);
					break;
				case TIC_SPARK:
					tableColumn.setCellRenderer(new DirectorySummaryRenderers.SparkRenderer());
					break;
			}
		}
	}

	private JPanel buildSearchBar() {
		JPanel searchBar=new JPanel();
		searchBar.setLayout(new BoxLayout(searchBar, BoxLayout.X_AXIS));
		spinner.setToolTipText("Shows progress while file metrics are being read for this directory.");
		searchBar.add(Box.createHorizontalStrut(6));
		searchBar.add(spinner);
		searchBar.add(Box.createHorizontalStrut(10));
		searchBar.add(makeSeparator());
		searchBar.add(Box.createHorizontalStrut(10));
		searchBar.add(new JLabel("Search:"));
		searchBar.add(Box.createHorizontalStrut(6));
		searchField.setToolTipText("Filter files in this table by file name.");
		searchBar.add(searchField);
		searchBar.add(Box.createHorizontalStrut(6));
		clearButton.setToolTipText("Clear the search text and show all files again.");
		searchBar.add(clearButton);
		searchBar.add(Box.createHorizontalStrut(10));
		searchBar.add(makeSeparator());
		searchBar.add(Box.createHorizontalStrut(10));
		searchBar.add(new JLabel("Vendor:"));
		searchBar.add(Box.createHorizontalStrut(6));
		initializeVendorFilter();
		vendorFilter.setToolTipText("Filter for specific raw file vendors or formats.");
		searchBar.add(vendorFilter);
		searchBar.add(Box.createHorizontalStrut(6));
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				updateFilters();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				updateFilters();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				updateFilters();
			}
		});
		clearButton.addActionListener(e -> {
			searchField.setText("");
			searchField.requestFocusInWindow();
		});
		searchField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSearch");
		searchField.getActionMap().put("clearSearch", new AbstractAction() {
			private static final long serialVersionUID=1L;

			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				clearButton.doClick();
			}
		});
		vendorFilter.addActionListener(e -> {
			persistVendorFilter();
			updateFilters();
		});
		updateFilters();
		return searchBar;
	}

	private void installTableHeaderTooltips() {
		JTableHeader header=new JTableHeader(table.getColumnModel()) {
			private static final long serialVersionUID=1L;

			@Override
			public String getToolTipText(MouseEvent event) {
				int viewColumn=columnAtPoint(event.getPoint());
				if (viewColumn<0) return null;
				int modelColumn=table.convertColumnIndexToModel(viewColumn);
				return getHeaderTooltip(modelColumn);
			}
		};
		header.setToolTipText("Hover a column header to see what it means.");
		table.setTableHeader(header);
	}

	private String getHeaderTooltip(int modelColumn) {
		DirectorySummaryColumn column=DirectorySummaryColumn.byModelIndex(modelColumn);
		return column==null?null:column.tooltip;
	}

	private void updateFilters() {
		String raw=searchField.getText();
		String text=(raw==null)?"":raw.trim();
		String needle=text.isEmpty()?null:text.toLowerCase(Locale.ROOT);
		String vendorFilterValue=DirectorySummaryVendorFilter.getVendorFilterValueForSelection(vendorFilter.getSelectedItem());
		VendorFile specificVendorFilter=DirectorySummaryVendorFilter.parseSpecificVendorFilter(vendorFilterValue);
		if (needle==null&&VENDOR_ALL.equals(vendorFilterValue)) {
			sorter.setRowFilter(null);
			requestSlowBitsDispatch();
			return;
		}
		sorter.setRowFilter(new RowFilter<DirectorySummaryModel, Integer>() {
			@Override
			public boolean include(Entry<? extends DirectorySummaryModel, ? extends Integer> entry) {
				DirectorySummaryModel m=entry.getModel();
				DirectorySummaryRow row=m.getAt(entry.getIdentifier());
				if (row==null) return false;
				if (!DirectorySummaryVendorFilter.matchesVendorFilterValue(row.vendor, vendorFilterValue, specificVendorFilter)) return false;
				if (needle==null) return true;
				return row.fileNameLower!=null&&row.fileNameLower.contains(needle);
			}
		});
		requestSlowBitsDispatch();
	}

	private void initializeVendorFilter() {
		DefaultComboBoxModel<Object> model=new DefaultComboBoxModel<>();
		model.addElement(VENDOR_ALL);
		model.addElement(VENDOR_ALL_RAW_INSTRUMENT_FILES);
		for (VendorFile vendor : VendorFile.values()) {
			model.addElement(vendor);
		}
		vendorFilter.setModel(model);
		vendorFilter.setRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID=1L;

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof VendorFile) {
					VendorFile vendor=(VendorFile)value;
					setText(vendor.getDisplayName());
				}
				return this;
			}
		});
		String saved=GUIPreferences.getDirectorySummaryVendorFilter();
		String vendorFilterValue=DirectorySummaryVendorFilter.normalizeSavedVendorFilter(saved);
		VendorFile specificVendorFilter=DirectorySummaryVendorFilter.parseSpecificVendorFilter(vendorFilterValue);
		if (specificVendorFilter!=null) {
			vendorFilter.setSelectedItem(specificVendorFilter);
		} else {
			vendorFilter.setSelectedItem(vendorFilterValue);
		}
	}

	private void persistVendorFilter() {
		GUIPreferences.setDirectorySummaryVendorFilter(DirectorySummaryVendorFilter.getVendorFilterValueForSelection(vendorFilter.getSelectedItem()));
	}

	private static JSeparator makeSeparator() {
		JSeparator sep=new JSeparator(SwingConstants.VERTICAL);
		Color line=UIManager.getColor("MenuBar.separatorColor");
		if (line==null) line=UIManager.getColor("Separator.foreground");
		if (line==null) line=new Color(0xD0D0D0);
		sep.setForeground(line);
		sep.setBackground(line);
		sep.setMaximumSize(new Dimension(2, 18));
		sep.setPreferredSize(new Dimension(2, 18));
		return sep;
	}

	private void initializeSlowBitsProgress(List<DirectorySummaryRow> rows) {
		int total=rows==null?0:rows.size();
		slowBitsTotal.set(Math.max(0, total));
		slowBitsDone.set(0);

		if (rows!=null) {
			for (DirectorySummaryRow row : rows) {
				if (row==null||row.path==null) continue;
				DirectorySummaryMetrics cached=SLOW_BITS_CACHE.get(row.path);
				if (cached!=null) {
					row.applyMetrics(cached);
					markSlowBitsDone(row);
				}
			}
		}
	}

	private void markSlowBitsDone(DirectorySummaryRow row) {
		if (row==null||!row.markSlowBitsReady()) return;
		if (row.path!=null) {
			slowBitsDeprioritized.remove(row.path);
			slowBitsRetryAfterNanos.remove(row.path);
			slowBitsReaderNotReadyWarned.remove(row.path);
		}
		slowBitsDone.incrementAndGet();
		SwingUtilities.invokeLater(spinner::repaint);
	}

	public JTable getTable() {
		return table;
	}

	public List<Path> getSelectedPaths() {
		int[] view=table.getSelectedRows();
		List<Path> out=new ArrayList<>(view.length);
		for (int vr : view) {
			int mr=table.convertRowIndexToModel(vr);
			DirectorySummaryRow r=model.getAt(mr);
			if (r!=null) out.add(r.path);
		}
		return out;
	}

	public boolean selectPath(Path target) {
		if (target==null) return false;
		int rows=model.getRowCount();
		for (int mr=0; mr<rows; mr++) {
			DirectorySummaryRow row=model.getAt(mr);
			if (row==null||row.path==null) continue;
			if (row.path.equals(target)) {
				int vr=table.convertRowIndexToView(mr);
				if (vr>=0) {
					table.getSelectionModel().setSelectionInterval(vr, vr);
					Rectangle rect=table.getCellRect(vr, 0, true);
					table.scrollRectToVisible(rect);
					return true;
				}
			}
		}
		return false;
	}

	public Path getFirstSelectedPath() {
		int row=table.getSelectedRow();
		if (row<0) return null;
		int mr=table.convertRowIndexToModel(row);
		DirectorySummaryRow r=model.getAt(mr);
		return (r==null)?null:r.path;
	}

	public Path getPathAtViewRow(int vr) {
		if (vr<0) return null;
		int mr=table.convertRowIndexToModel(vr);
		DirectorySummaryRow r=model.getAt(mr);
		return (r==null)?null:r.path;
	}

	private void requestSlowBitsDispatch() {
		if (closed) return;
		if (!slowBitsDispatchPending.compareAndSet(false, true)) return;
		SwingUtilities.invokeLater(() -> {
			slowBitsDispatchPending.set(false);
			dispatchSlowBitsNow();
		});
	}

	private void dispatchSlowBitsNow() {
		if (closed) return;
		if (!SwingUtilities.isEventDispatchThread()) {
			requestSlowBitsDispatch();
			return;
		}
		List<DirectorySummaryRow> rows=model.snapshotRows();
		if (rows.isEmpty()) return;

		long nowNanos=System.nanoTime();
		int[] visibleRange=currentVisibleViewRange();
		int firstVisible=visibleRange[0];
		int lastVisible=visibleRange[1];

		ArrayList<SlowBitsLaunchPlanner.RowState> states=new ArrayList<>(rows.size());
		HashMap<Integer, DirectorySummaryRow> rowsByModelIndex=new HashMap<>(rows.size());
		for (int modelIndex=0; modelIndex<rows.size(); modelIndex++) {
			DirectorySummaryRow row=rows.get(modelIndex);
			if (row==null) continue;
			rowsByModelIndex.put(Integer.valueOf(modelIndex), row);
			int viewIndex=safeConvertRowIndexToView(table, modelIndex);
			boolean hidden=(viewIndex<0);
			boolean inViewport=!hidden&&firstVisible>=0&&lastVisible>=0&&viewIndex>=firstVisible&&viewIndex<=lastVisible;
			int distanceFromViewport=hidden?Integer.MAX_VALUE:distanceFromViewport(viewIndex, firstVisible, lastVisible);
			boolean running=slowBitsRunning.contains(row);
			SlowBitsLaunchPlanner.Lane runningLane=running?slowBitsRunningLane.get(row):null;
			Long startNanos=slowBitsRunningStartNanos.get(row);
			long runningNanos=(running&&startNanos!=null)?Math.max(0L, nowNanos-startNanos.longValue()):0L;
			boolean deprioritized=row.path!=null&&slowBitsDeprioritized.contains(row.path);
			long retryAfterNanos=(row.path==null)?0L:slowBitsRetryAfterNanos.getOrDefault(row.path, 0L).longValue();
			boolean launchEligible=!row.isSlowBitsReady()&&nowNanos>=retryAfterNanos;
			states.add(new SlowBitsLaunchPlanner.RowState(modelIndex, row.vendor, hidden, inViewport, row.isSlowBitsReady(), running, runningLane,
					distanceFromViewport, runningNanos, deprioritized, launchEligible));
		}

		SlowBitsLaunchPlanner.Plan plan=SlowBitsLaunchPlanner.plan(states, effectiveSlowBitsWorkerCount(slowBitsWorkerCount),
				SLOW_BITS_STALL_THRESHOLD_NANOS);
		for (Integer stalledModelIndex : plan.stalledVisibleModelRows()) {
			DirectorySummaryRow stalledRow=rowsByModelIndex.get(stalledModelIndex);
			if (stalledRow==null||stalledRow.path==null) continue;
			slowBitsDeprioritized.add(stalledRow.path);
			if (slowBitsStallWarned.add(stalledRow.path)) {
				Logger.logLine("Slow bits still loading after 1s for visible row: "+stalledRow.path);
			}
		}

		for (SlowBitsLaunchPlanner.Launch launch : plan.launches()) {
			DirectorySummaryRow row=rowsByModelIndex.get(Integer.valueOf(launch.modelIndex()));
			if (row==null||row.isSlowBitsReady()) continue;
			if (!isReaderReadyForSlowBits(row.vendor)) {
				deferSlowBitsForReaderNotReady(row);
				continue;
			}
			if (!slowBitsRunning.add(row)) continue;
			slowBitsRunningLane.put(row, launch.lane());
			slowBitsRunningStartNanos.put(row, Long.valueOf(System.nanoTime()));
			if (row.path!=null) slowBitsRetryAfterNanos.remove(row.path);
			try {
				pool.submit(() -> {
					try {
						computeSlowBits(row);
					} finally {
						slowBitsRunning.remove(row);
						slowBitsRunningLane.remove(row);
						slowBitsRunningStartNanos.remove(row);
						requestSlowBitsDispatch();
					}
				});
			} catch (RejectedExecutionException ignore) {
				slowBitsRunning.remove(row);
				slowBitsRunningLane.remove(row);
				slowBitsRunningStartNanos.remove(row);
			}
		}
	}

	private int[] currentVisibleViewRange() {
		int rowCount=table.getRowCount();
		if (rowCount<=0) return new int[] {-1, -1};
		Rectangle vr=tableScrollPane.getViewport().getViewRect();
		int first=table.rowAtPoint(new Point(0, vr.y));
		int last=table.rowAtPoint(new Point(0, vr.y+Math.max(0, vr.height-1)));
		if (first<0) first=0;
		if (last<0) last=rowCount-1;
		if (first>last) {
			int tmp=first;
			first=last;
			last=tmp;
		}
		return new int[] {first, last};
	}

	private static int distanceFromViewport(int viewIndex, int firstVisible, int lastVisible) {
		if (viewIndex<0) return Integer.MAX_VALUE;
		if (firstVisible<0||lastVisible<0) return 0;
		if (viewIndex<firstVisible) return firstVisible-viewIndex;
		if (viewIndex>lastVisible) return viewIndex-lastVisible;
		return 0;
	}

	static int safeConvertRowIndexToView(JTable table, int modelIndex) {
		if (table==null) return -1;
		if (modelIndex<0) return -1;
		try {
			return table.convertRowIndexToView(modelIndex);
		} catch (IndexOutOfBoundsException ignore) {
			return -1;
		} catch (IllegalArgumentException ignore) {
			return -1;
		}
	}

	static int effectiveSlowBitsWorkerCount(int normalWorkerCount) {
		return GuiProcessingActivity.isForegroundWorkActive()?1:Math.max(1, normalWorkerCount);
	}

	private boolean isReaderReadyForSlowBits(VendorFile vendor) {
		if (vendor==VendorFile.THERMO) {
			if (ThermoServerPool.isReady()) return true;
			ThermoServerPool.startAsync();
			return false;
		}
		return true;
	}

	private void deferSlowBitsForReaderNotReady(DirectorySummaryRow row) {
		if (row==null||row.path==null) return;
		slowBitsDeprioritized.add(row.path);
		slowBitsRetryAfterNanos.put(row.path, Long.valueOf(System.nanoTime()+SLOW_BITS_READER_RETRY_NANOS));
		if (slowBitsReaderNotReadyWarned.add(row.path)) {
			Logger.logLine("Deferring slow bits until reader is ready: "+row.path);
		}
	}

	private void computeSlowBits(DirectorySummaryRow row) {
		SlowBitsTiming timing=SlowBitsTiming.start(row);
		try {
			if (closed) {
				timing.status="closed";
				return;
			}
			// Per-file fault isolation: if anything fails, we just skip updating that row
			DirectorySummaryMetrics cached=SLOW_BITS_CACHE.get(row.path);
			if (cached!=null) {
				timing.cacheHit=true;
				timing.status="cache";
				row.applyMetrics(cached);
				markSlowBitsDone(row);
				safeRowUpdate(row);
				return;
			}
			if (row.vendor==VendorFile.ENCYCLOPEDIA) {
				EncyclopeDIAFile dia=null;
				try {
					dia=new EncyclopeDIAFile();
					long start=System.nanoTime();
					dia.openFile(row.path.toFile());
					timing.openNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					Pair<float[], float[]> tic=dia.getTICTrace();
					timing.ticTraceNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					row.acquiredDate=parseAcquiredDate(dia.getMetadata().get(EncyclopeDIAFile.RUN_START_TIME)).orElse(null);
					timing.acquiredDateNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					row.totalTIC=dia.getTIC();
					row.gradientMin=dia.getGradientLength()/60f;
					timing.runSummaryNanos+=System.nanoTime()-start;
					row.spark=SparkData.fromTIC(tic.x, tic.y, sparkResolution);
					SLOW_BITS_CACHE.put(row.path, row.toMetrics());
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} catch (Throwable ignore) {
					timing.status=failureStatus(ignore);
					logSlowBitsFailure(row, ignore);
					row.spark=FAILED;
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} finally {
					long start=System.nanoTime();
					try {
						if (dia!=null) dia.close();
					} catch (Throwable t) {
						logSlowBitsFailure(row, t);
					} finally {
						timing.closeNanos+=System.nanoTime()-start;
					}
				}
			} else if (row.vendor==VendorFile.MZML) {
				MzmlFile mzml=new MzmlFile();
				try {
					long start=System.nanoTime();
					mzml.openFile(row.path.toFile());
					timing.openNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					Pair<float[], float[]> tic=mzml.getTICTrace();
					timing.ticTraceNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					row.acquiredDate=getStructuredRunStartTime(mzml).orElse(null);
					timing.acquiredDateNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					row.totalTIC=mzml.getTIC();
					row.gradientMin=mzml.getGradientLength()/60f;
					timing.runSummaryNanos+=System.nanoTime()-start;
					row.spark=SparkData.fromTIC(tic.x, tic.y, sparkResolution);
					SLOW_BITS_CACHE.put(row.path, row.toMetrics());
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} catch (Throwable ignore) {
					timing.status=failureStatus(ignore);
					logSlowBitsFailure(row, ignore);
					row.spark=FAILED;
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} finally {
					long start=System.nanoTime();
					try {
						mzml.close();
					} catch (Throwable t) {
						logSlowBitsFailure(row, t);
					} finally {
						timing.closeNanos+=System.nanoTime()-start;
					}
				}
			} else if (row.vendor==VendorFile.THERMO) {
				ThermoRawFile raw=new ThermoRawFile();
				boolean skipClose=false;
				try {
					long start=System.nanoTime();
					raw.openFile(row.path);
					timing.openNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					Pair<float[], float[]> tic=raw.getTICTrace();
					timing.ticTraceNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					ThermoRawFile.RunSummary summary=raw.getRunSummary();
					timing.runSummaryNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					Optional<Date> acquired=raw.getRunStartTimeIfKnown();
					row.acquiredDate=acquired.orElse(null);
					timing.acquisitionDateSource=acquired.isPresent()?"open_reply":"missing";
					timing.acquiredDateNanos+=System.nanoTime()-start;
					row.totalTIC=(float)summary.totalIonCurrent;
					row.gradientMin=(float)(summary.gradientLengthSeconds/60.0);
					row.spark=SparkData.fromTIC(tic.x, tic.y, sparkResolution);
					SLOW_BITS_CACHE.put(row.path, row.toMetrics());
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} catch (Throwable ignore) {
					timing.status=failureStatus(ignore);
					timing.acquisitionDateSource="error";
					if (DirectorySummarySlowBitsFailures.isThermoReaderUnavailable(ignore)) {
						if (DirectorySummarySlowBitsFailures.shouldSkipThermoRetryOnClose(closed)) {
							skipClose=true;
							return;
						}
						ThermoServerPool.startAsync();
						deferSlowBitsForReaderNotReady(row);
						safeRowUpdate(row);
						return;
					}
					logSlowBitsFailure(row, ignore);
					row.spark=FAILED;
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} finally {
					long start=System.nanoTime();
					try {
						if (!skipClose) raw.close();
					} catch (Throwable t) {
						logSlowBitsFailure(row, t);
					} finally {
						timing.closeNanos+=System.nanoTime()-start;
					}
				}
			} else {
				BrukerTIMSFile raw=new BrukerTIMSFile();
				try {
					long start=System.nanoTime();
					raw.openFile(row.path);
					timing.openNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					Pair<float[], float[]> tic=raw.getTICTrace();
					timing.ticTraceNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					row.acquiredDate=getStructuredRunStartTime(raw).orElse(null);
					timing.acquiredDateNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					row.totalTIC=raw.getTIC();
					row.gradientMin=raw.getGradientLength()/60f;
					timing.runSummaryNanos+=System.nanoTime()-start;
					row.spark=SparkData.fromTIC(tic.x, tic.y, sparkResolution);
					SLOW_BITS_CACHE.put(row.path, row.toMetrics());
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} catch (Throwable ignore) {
					timing.status=failureStatus(ignore);
					logSlowBitsFailure(row, ignore);
					row.spark=FAILED;
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} finally {
					long start=System.nanoTime();
					try {
						raw.close();
					} catch (Throwable t) {
						logSlowBitsFailure(row, t);
					} finally {
						timing.closeNanos+=System.nanoTime()-start;
					}
				}
			}
		} finally {
			timing.log(row);
		}
	}

	private static String failureStatus(Throwable failure) {
		if (failure==null) return "error";
		return "error:"+failure.getClass().getSimpleName();
	}

	private static class SlowBitsTiming {
		private final long totalStartNanos;
		private boolean cacheHit=false;
		private long openNanos=0L;
		private long ticTraceNanos=0L;
		private long runSummaryNanos=0L;
		private long acquiredDateNanos=0L;
		private long closeNanos=0L;
		private String status="ok";
		private String acquisitionDateSource="n/a";

		private SlowBitsTiming() {
			totalStartNanos=System.nanoTime();
		}

		static SlowBitsTiming start(DirectorySummaryRow row) {
			SlowBitsTiming timing=new SlowBitsTiming();
			if (row!=null&&row.vendor==VendorFile.THERMO) {
				timing.acquisitionDateSource="unknown";
			}
			return timing;
		}

		void log(DirectorySummaryRow row) {
			if (!SLOW_BITS_TIMING_ENABLED) return;
			long totalNanos=System.nanoTime()-totalStartNanos;
			Logger.logLine("slowbits-timing\tvendor="+safeVendor(row)+"\tpath="+safePath(row)+"\tthread="+Thread.currentThread().getName()+"\tstatus="
					+status+"\tcache_hit="+cacheHit+"\ttotal_ms="+millis(totalNanos)+"\topen_ms="+millis(openNanos)+"\ttic_trace_ms="
					+millis(ticTraceNanos)+"\trun_summary_ms="+millis(runSummaryNanos)+"\tacquired_date_ms="+millis(acquiredDateNanos)
					+"\tclose_ms="+millis(closeNanos)+"\tacquisition_date_source="+acquisitionDateSource);
		}

		private static String safeVendor(DirectorySummaryRow row) {
			return row==null||row.vendor==null?"<unknown>":row.vendor.name();
		}

		private static String safePath(DirectorySummaryRow row) {
			return row==null||row.path==null?"<unknown>":row.path.toString();
		}

		private static String millis(long nanos) {
			return String.format(Locale.ROOT, "%.3f", nanos/1_000_000.0);
		}
	}

	static Optional<Date> parseAcquiredDate(String raw) {
		if (raw==null||raw.isBlank()) return Optional.empty();
		String value=raw.trim();
		try {
			return Optional.of(Date.from(Instant.parse(value)));
		} catch (DateTimeParseException ignored) {
		}
		try {
			return Optional.of(Date.from(OffsetDateTime.parse(value).toInstant()));
		} catch (DateTimeParseException ignored) {
		}
		try {
			return Optional.of(Date.from(LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.systemDefault())
					.toInstant()));
		} catch (DateTimeParseException ignored) {
		}
		return Optional.empty();
	}

	private static Optional<Date> getStructuredRunStartTime(StructuredMetadataProvider provider) {
		if (provider==null) return Optional.empty();
		try {
			return provider.getRunStartTime();
		} catch (Exception e) {
			Logger.errorException(e);
			return Optional.empty();
		}
	}

	private void logSlowBitsFailure(DirectorySummaryRow row, Throwable failure) {
		if (failure==null) return;
		String file=(row!=null&&row.path!=null)?row.path.toString():"<unknown>";
		String summary=DirectorySummarySlowBitsFailures.expectedSlowBitsFailureSummary(failure);
		if (summary==null) {
			Logger.errorLine("Unclassified slow-bits failure for "+file+": "+String.valueOf(failure));
			Logger.errorException(failure);
			return;
		}
		String dedupeKey=summary+"|"+file;
		if (EXPECTED_SLOW_BITS_FAILURES_LOGGED.add(dedupeKey)) {
			if (SLOW_BITS_CANCELLED_BY_USER_SUMMARY.equals(summary)) {
				Logger.logLine("Previous request cancelled by user for "+file);
			} else {
				Logger.logLine("Preview unavailable for "+file+": "+summary);
			}
		}
	}

	private void safeRowUpdate(DirectorySummaryRow row) {
		if (closed) return;
		SwingUtilities.invokeLater(() -> model.rowUpdated(row));
	}

	private void applySavedColumnLayout() {
		applyingSavedLayout=true;
		try {
			applyDefaultColumnWidths();
			List<String> order=GUIPreferences.getDirectorySummaryColumnOrderKeys();
			if (!order.isEmpty()) {
				applyColumnOrder(order);
			}
			Map<String, Integer> widths=GUIPreferences.getDirectorySummaryColumnWidthKeys();
			if (!widths.isEmpty()) {
				applyColumnWidths(widths);
			}
		} finally {
			applyingSavedLayout=false;
		}
		saveColumnPreferences();
		GUIPreferences.setDirectorySummarySortKeySpecs(toSortKeySpecs(sorter.getSortKeys()));
	}

	private void applyDefaultColumnWidths() {
		for (DirectorySummaryColumn column : DirectorySummaryColumn.values()) {
			setColumnWidth(column, column.defaultWidth);
		}
	}

	private void applyColumnOrder(List<String> order) {
		int columnCount=table.getColumnModel().getColumnCount();
		int target=0;
		java.util.HashSet<DirectorySummaryColumn> applied=new java.util.HashSet<>();
		for (String columnKey : order) {
			DirectorySummaryColumn column=DirectorySummaryColumn.byKey(columnKey);
			if (column==null||!applied.add(column)) continue;
			int current=table.convertColumnIndexToView(column.modelIndex());
			if (current<0) continue;
			if (current!=target) {
				table.getColumnModel().moveColumn(current, target);
			}
			target++;
		}
		for (DirectorySummaryColumn column : DirectorySummaryColumn.values()) {
			if (!applied.add(column)) continue;
			int current=table.convertColumnIndexToView(column.modelIndex());
			if (current<0||current>=columnCount) continue;
			if (current!=target) {
				table.getColumnModel().moveColumn(current, target);
			}
			target++;
		}
	}

	private void applyColumnWidths(Map<String, Integer> widths) {
		for (Map.Entry<String, Integer> entry : widths.entrySet()) {
			DirectorySummaryColumn column=DirectorySummaryColumn.byKey(entry.getKey());
			Integer width=entry.getValue();
			if (column==null||width==null) continue;
			if (width.intValue()<=0) continue;
			setColumnWidth(column, width.intValue());
		}
	}

	private void setColumnWidth(DirectorySummaryColumn column, int width) {
		if (column==null) return;
		int viewIndex=table.convertColumnIndexToView(column.modelIndex());
		if (viewIndex<0) return;
		TableColumn col=table.getColumnModel().getColumn(viewIndex);
		col.setPreferredWidth(width);
		col.setWidth(width);
	}

	private void installColumnPreferenceListeners() {
		table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
			@Override
			public void columnAdded(TableColumnModelEvent e) {
				scheduleColumnSave();
			}

			@Override
			public void columnRemoved(TableColumnModelEvent e) {
				scheduleColumnSave();
			}

			@Override
			public void columnMoved(TableColumnModelEvent e) {
				scheduleColumnSave();
			}

			@Override
			public void columnMarginChanged(ChangeEvent e) {
				scheduleColumnSave();
			}

			@Override
			public void columnSelectionChanged(ListSelectionEvent e) {
			}
		});
	}

	private void scheduleColumnSave() {
		if (applyingSavedLayout) return;
		if (pendingColumnSave) return;
		pendingColumnSave=true;
		SwingUtilities.invokeLater(() -> {
			pendingColumnSave=false;
			saveColumnPreferences();
		});
	}

	private void saveColumnPreferences() {
		if (applyingSavedLayout) return;
		int count=table.getColumnModel().getColumnCount();
		List<String> order=new ArrayList<>(count);
		Map<String, Integer> widths=new LinkedHashMap<>(count);
		for (int view=0; view<count; view++) {
			TableColumn col=table.getColumnModel().getColumn(view);
			DirectorySummaryColumn column=DirectorySummaryColumn.byModelIndex(col.getModelIndex());
			if (column==null) continue;
			order.add(column.key);
			widths.put(column.key, Math.max(1, col.getWidth()));
		}
		GUIPreferences.setDirectorySummaryColumnOrderKeys(order);
		GUIPreferences.setDirectorySummaryColumnWidthKeys(widths);
	}

	@Override
	public void addNotify() {
		super.addNotify();
		closed=false;
		GuiProcessingActivity.addListener(processingActivityListener);
		if (loadingTimer!=null) loadingTimer.start();
		requestSlowBitsDispatch();
	}

	@Override
	public void removeNotify() {
		closed=true;
		GuiProcessingActivity.removeListener(processingActivityListener);
		if (loadingTimer!=null) loadingTimer.stop();
		super.removeNotify();
		slowBitsRunning.clear();
		slowBitsRunningLane.clear();
		slowBitsRunningStartNanos.clear();
		slowBitsRetryAfterNanos.clear();
		slowBitsDeprioritized.clear();
		slowBitsReaderNotReadyWarned.clear();
		DirectorySummarySlowBitsFailures.shutdownSlowBitsPool(pool);
	}

	private final class ProgressSpinner extends JComponent {
		private static final long serialVersionUID=1L;
		private static final int SIZE=16;
		private static final int STROKE=3;

		private ProgressSpinner() {
			setPreferredSize(new Dimension(SIZE, SIZE));
			setMinimumSize(new Dimension(SIZE, SIZE));
			setMaximumSize(new Dimension(SIZE, SIZE));
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2=(Graphics2D)g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int w=getWidth();
				int h=getHeight();
				int d=Math.min(w, h)-2;
				int x=(w-d)/2;
				int y=(h-d)/2;

				g2.setColor(SPINNER_BG);
				g2.setStroke(new java.awt.BasicStroke(STROKE, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
				g2.drawOval(x, y, d, d);

				float total=slowBitsTotal.get();
				float done=slowBitsDone.get();
				float pct=(total<=0f)?1f:Math.max(0f, Math.min(1f, done/total));
				int arc=(int)Math.round(360.0*pct);
				if (arc>0) {
					g2.setColor(COLOR_FILL);
					g2.drawArc(x, y, d, d, 90, -arc);
				}
			} finally {
				g2.dispose();
			}
		}
	}

}
