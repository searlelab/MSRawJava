package org.searlelab.msrawjava.gui.filebrowser;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;

import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.gui.GUIPreferences;
import org.searlelab.msrawjava.io.VendorFile;
import org.searlelab.msrawjava.io.VendorFiles;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.mzml.MzmlFile;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.logging.Logger;

/** Small, streaming table that summarizes raw files in a directory. */
public class DirectorySummaryPanel extends JPanel {
	private static final long serialVersionUID=1L;

	private static final int sparkResolution=128;
	private static final Color COLOR_FILL=new Color(0x5555ED);
	private static final Color SPINNER_BG=new Color(0xE0E0E0);
	private static final SparkData FAILED=new SparkData(new float[0]);
	private static final ConcurrentHashMap<Path, SlowBits> SLOW_BITS_CACHE=new ConcurrentHashMap<>();
	private static final String VENDOR_ALL="All";
	private static final AtomicInteger SLOW_BITS_THREAD_ID=new AtomicInteger(1);

	private final JTable table;
	private final DirSummaryModel model=new DirSummaryModel();
	private final TableRowSorter<DirSummaryModel> sorter;
	// Use a wider pool to speed up slow-bit extraction on large directories.
	private final ExecutorService pool=Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()-2), r -> {
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

	public DirectorySummaryPanel(VendorFiles files) {
		super(new BorderLayout());

		table=new JTable(model);
		sorter=new TableRowSorter<>(model);
		sorter.setSortable(0, false); // "#" not sortable
		sorter.setComparator(3, Comparator.nullsLast(Comparator.naturalOrder())); // date modified
		sorter.setComparator(5, Comparator.nullsLast(Float::compareTo)); // gradient
		sorter.setComparator(6, Comparator.nullsLast(Float::compareTo)); // total tic
		sorter.setSortable(7, false); // tic spark 
		List<RowSorter.SortKey> savedSortKeys=GUIPreferences.getDirectorySummarySortKeys();
		ArrayList<RowSorter.SortKey> validSortKeys=new ArrayList<>(savedSortKeys.size());
		for (RowSorter.SortKey key : savedSortKeys) {
			if (key==null) continue;
			int col=key.getColumn();
			if (col>=0&&col<model.getColumnCount()) {
				validSortKeys.add(key);
			}
		}
		if (!validSortKeys.isEmpty()) {
			sorter.setSortKeys(validSortKeys);
		} else {
			sorter.setSortKeys(List.of(new RowSorter.SortKey(3, SortOrder.DESCENDING)));
		}
		sorter.addRowSorterListener(e -> {
			RowSorter<?> src=(RowSorter<?>)e.getSource();
			GUIPreferences.setDirectorySummarySortKeys(src.getSortKeys());
		});

		table.setRowSorter(sorter);

		table.setRowHeight(28);
		table.setFillsViewportHeight(true);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		// Stripe renderers so it blends in
		table.setDefaultRenderer(String.class, StripeTableCellRenderer.BASE_RENDERER);
		table.setDefaultRenderer(Long.class, StripeTableCellRenderer.SIZE_RENDERER);
		table.setDefaultRenderer(Date.class, new DateOnlyRenderer());
		table.setDefaultRenderer(Float.class, new GradientRenderer()); // formats "X.Y min"
		table.setDefaultRenderer(SparkData.class, new SparkRenderer()); // red filled spark
		installTableHeaderTooltips();

		table.getColumnModel().getColumn(0).setCellRenderer(StripeTableCellRenderer.ROW_NUMBER_RENDERER);
		table.getColumnModel().getColumn(1).setCellRenderer(StripeTableCellRenderer.BASE_RENDERER);
		table.getColumnModel().getColumn(2).setCellRenderer(StripeTableCellRenderer.BASE_RENDERER);
		table.getColumnModel().getColumn(3).setCellRenderer(new DateOnlyRenderer());
		table.getColumnModel().getColumn(4).setCellRenderer(StripeTableCellRenderer.SIZE_RENDERER);
		table.getColumnModel().getColumn(5).setCellRenderer(new GradientRenderer());
		table.getColumnModel().getColumn(6).setCellRenderer(StripeTableCellRenderer.SCI_RENDERER);
		table.getColumnModel().getColumn(7).setCellRenderer(new SparkRenderer());

		add(buildSearchBar(), BorderLayout.NORTH);
		JScrollPane sp=new JScrollPane(table);
		add(sp, BorderLayout.CENTER);
		loadingTimer=new Timer(500, e -> {
			SparkRenderer.advanceLoadingPhase();
			table.repaint();
		});
		SwingUtilities.invokeLater(this::applySavedColumnLayout);
		installColumnPreferenceListeners();

		// Seed fast info (file name/vendor/size) synchronously so table appears immediately
		ArrayList<DirRow> brukerRows=new ArrayList<DirRow>();
		for (Path p : files.getBrukerDirs()) {
			brukerRows.add(DirRow.fromBruker(p));
		}
		Collections.sort(brukerRows);
		ArrayList<DirRow> thermoRows=new ArrayList<DirRow>();
		for (Path p : files.getThermoFiles()) {
			thermoRows.add(DirRow.fromThermo(p));
		}
		Collections.sort(thermoRows);
		ArrayList<DirRow> diaRows=new ArrayList<DirRow>();
		for (Path p : files.getDiaFiles()) {
			diaRows.add(DirRow.fromDia(p));
		}
		Collections.sort(diaRows);
		ArrayList<DirRow> mzmlRows=new ArrayList<DirRow>();
		for (Path p : files.getMzmlFiles()) {
			mzmlRows.add(DirRow.fromMzml(p));
		}
		Collections.sort(mzmlRows);

		ArrayList<DirRow> allRows=new ArrayList<DirRow>(brukerRows.size()+thermoRows.size()+diaRows.size()+mzmlRows.size());
		allRows.addAll(brukerRows);
		allRows.addAll(thermoRows);
		allRows.addAll(diaRows);
		allRows.addAll(mzmlRows);
		Collections.sort(allRows);

		model.addRows(allRows);
		initializeSlowBitsProgress(allRows);

		// Stream slow info (gradient + TIC spark) in the background per row, do Bruker first because they are faster
		for (DirRow row : brukerRows) {
			if (!row.isSlowBitsReady()) pool.submit(() -> computeSlowBits(row));
		}
		for (DirRow row : diaRows) {
			if (!row.isSlowBitsReady()) pool.submit(() -> computeSlowBits(row));
		}
		for (DirRow row : mzmlRows) {
			if (!row.isSlowBitsReady()) pool.submit(() -> computeSlowBits(row));
		}
		for (DirRow row : thermoRows) {
			if (!row.isSlowBitsReady()) pool.submit(() -> computeSlowBits(row));
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
		return switch (modelColumn) {
			case 0 -> "The table row number for this file.";
			case 1 -> "The raw file or directory name.";
			case 2 -> "The detected vendor or file format.";
			case 3 -> "The last modified date reported by the file system.";
			case 4 -> "The total file size on disk.";
			case 5 -> "The gradient length in minutes.";
			case 6 -> "The sum of MS1 TIC values across the entire raw file.";
			case 7 -> "A compact trace of total ion current across retention time.";
			default -> null;
		};
	}

	private void updateFilters() {
		String raw=searchField.getText();
		String text=(raw==null)?"":raw.trim();
		String needle=text.isEmpty()?null:text.toLowerCase(Locale.ROOT);
		VendorFile vendorSelection=getSelectedVendor();
		if (needle==null&&vendorSelection==null) {
			sorter.setRowFilter(null);
			return;
		}
		sorter.setRowFilter(new RowFilter<DirSummaryModel, Integer>() {
			@Override
			public boolean include(Entry<? extends DirSummaryModel, ? extends Integer> entry) {
				DirSummaryModel m=entry.getModel();
				DirRow row=m.getAt(entry.getIdentifier());
				if (row==null) return false;
				if (vendorSelection!=null&&row.vendor!=vendorSelection) return false;
				if (needle==null) return true;
				return row.fileNameLower!=null&&row.fileNameLower.contains(needle);
			}
		});
	}

	private void initializeVendorFilter() {
		DefaultComboBoxModel<Object> model=new DefaultComboBoxModel<>();
		model.addElement(VENDOR_ALL);
		for (VendorFile vendor : VendorFile.values()) {
			model.addElement(vendor);
		}
		vendorFilter.setModel(model);
		vendorFilter.setRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID=1L;

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof VendorFile vendor) {
					setText(vendor.getDisplayName());
				}
				return this;
			}
		});
		String saved=GUIPreferences.getDirectorySummaryVendorFilter();
		if (saved!=null&&!saved.isBlank()) {
			if (VENDOR_ALL.equals(saved)) {
				vendorFilter.setSelectedItem(VENDOR_ALL);
			} else {
				try {
					VendorFile vendor=VendorFile.valueOf(saved);
					vendorFilter.setSelectedItem(vendor);
				} catch (IllegalArgumentException ignore) {
					vendorFilter.setSelectedItem(VENDOR_ALL);
				}
			}
		} else {
			vendorFilter.setSelectedItem(VENDOR_ALL);
		}
	}

	private VendorFile getSelectedVendor() {
		Object selection=vendorFilter.getSelectedItem();
		if (selection instanceof VendorFile vendor) return vendor;
		return null;
	}

	private void persistVendorFilter() {
		Object selection=vendorFilter.getSelectedItem();
		if (selection instanceof VendorFile vendor) {
			GUIPreferences.setDirectorySummaryVendorFilter(vendor.name());
		} else {
			GUIPreferences.setDirectorySummaryVendorFilter(VENDOR_ALL);
		}
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

	private void initializeSlowBitsProgress(List<DirRow> rows) {
		int total=rows==null?0:rows.size();
		slowBitsTotal.set(Math.max(0, total));
		slowBitsDone.set(0);

		if (rows!=null) {
			for (DirRow row : rows) {
				if (row==null||row.path==null) continue;
				SlowBits cached=SLOW_BITS_CACHE.get(row.path);
				if (cached!=null) {
					row.applySlowBits(cached);
					markSlowBitsDone(row);
				}
			}
		}
	}

	private void markSlowBitsDone(DirRow row) {
		if (row==null||!row.markSlowBitsReady()) return;
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
			DirRow r=model.getAt(mr);
			if (r!=null) out.add(r.path);
		}
		return out;
	}

	public boolean selectPath(Path target) {
		if (target==null) return false;
		int rows=model.getRowCount();
		for (int mr=0; mr<rows; mr++) {
			DirRow row=model.getAt(mr);
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
		DirRow r=model.getAt(mr);
		return (r==null)?null:r.path;
	}

	public Path getPathAtViewRow(int vr) {
		if (vr<0) return null;
		int mr=table.convertRowIndexToModel(vr);
		DirRow r=model.getAt(mr);
		return (r==null)?null:r.path;
	}

	private void computeSlowBits(DirRow row) {
		if (closed) return;
		// Per-file fault isolation: if anything fails, we just skip updating that row
		SlowBits cached=SLOW_BITS_CACHE.get(row.path);
		if (cached!=null) {
			row.applySlowBits(cached);
			markSlowBitsDone(row);
			safeRowUpdate(row);
			return;
		}
		if (row.vendor==VendorFile.ENCYCLOPEDIA) {
			EncyclopeDIAFile dia=null;
			try {
				dia=new EncyclopeDIAFile();
				dia.openFile(row.path.toFile());
				Pair<float[], float[]> tic=dia.getTICTrace();
				row.totalTIC=dia.getTIC();
				row.gradientMin=dia.getGradientLength()/60f;
				row.spark=SparkData.fromTIC(tic.x, tic.y, sparkResolution);
				SLOW_BITS_CACHE.put(row.path, row.toSlowBits());
				markSlowBitsDone(row);
				safeRowUpdate(row);
			} catch (Throwable ignore) {
				row.spark=FAILED;
				markSlowBitsDone(row);
				safeRowUpdate(row);
			} finally {
				try {
					if (dia!=null) dia.close();
				} catch (Throwable t) {
				}
			}
		} else if (row.vendor==VendorFile.MZML) {
			MzmlFile mzml=new MzmlFile();
			try {
				mzml.openFile(row.path.toFile());
				Pair<float[], float[]> tic=mzml.getTICTrace();
				row.totalTIC=mzml.getTIC();
				row.gradientMin=mzml.getGradientLength()/60f;
				row.spark=SparkData.fromTIC(tic.x, tic.y, sparkResolution);
				SLOW_BITS_CACHE.put(row.path, row.toSlowBits());
				markSlowBitsDone(row);
				safeRowUpdate(row);
			} catch (Throwable ignore) {
				row.spark=FAILED;
				markSlowBitsDone(row);
				safeRowUpdate(row);
			} finally {
				try {
					mzml.close();
				} catch (Throwable t) {
				}
			}
		} else if (row.vendor==VendorFile.THERMO) {
			ThermoRawFile raw=new ThermoRawFile();
			try {
				raw.openFile(row.path);
				Pair<float[], float[]> tic=raw.getTICTrace();
				row.totalTIC=raw.getTIC();
				row.gradientMin=raw.getGradientLength()/60f;
				row.spark=SparkData.fromTIC(tic.x, tic.y, sparkResolution);
				SLOW_BITS_CACHE.put(row.path, row.toSlowBits());
				markSlowBitsDone(row);
				safeRowUpdate(row);
			} catch (Throwable ignore) {
				row.spark=FAILED;
				markSlowBitsDone(row);
				safeRowUpdate(row);
			} finally {
				try {
					raw.close();
				} catch (Throwable t) {
				}
			}
		} else {
			BrukerTIMSFile raw=new BrukerTIMSFile();
			try {
				raw.openFile(row.path);
				Pair<float[], float[]> tic=raw.getTICTrace();
				row.totalTIC=raw.getTIC();
				row.gradientMin=raw.getGradientLength()/60f;
				row.spark=SparkData.fromTIC(tic.x, tic.y, sparkResolution);
				SLOW_BITS_CACHE.put(row.path, row.toSlowBits());
				markSlowBitsDone(row);
				safeRowUpdate(row);
			} catch (Throwable ignore) {
				row.spark=FAILED;
				markSlowBitsDone(row);
				safeRowUpdate(row);
			} finally {
				try {
					raw.close();
				} catch (Throwable t) {
				}
			}
		}
	}

	private void safeRowUpdate(DirRow row) {
		if (closed) return;
		SwingUtilities.invokeLater(() -> model.rowUpdated(row));
	}

	private void applySavedColumnLayout() {
		applyingSavedLayout=true;
		try {
			applyDefaultColumnWidths();
			List<Integer> order=GUIPreferences.getDirectorySummaryColumnOrder();
			if (!order.isEmpty()) {
				applyColumnOrder(order);
			}
			Map<Integer, Integer> widths=GUIPreferences.getDirectorySummaryColumnWidths();
			if (!widths.isEmpty()) {
				applyColumnWidths(widths);
			}
		} finally {
			applyingSavedLayout=false;
		}
	}

	private void applyDefaultColumnWidths() {
		setColumnWidth(0, 50); // #
		setColumnWidth(1, 320); // File
		setColumnWidth(2, 80); // Vendor
		setColumnWidth(3, 110); // Date Modified
		setColumnWidth(4, 100); // Size
		setColumnWidth(5, 110); // Gradient
		setColumnWidth(6, 110); // total tic
		setColumnWidth(7, 220); // TIC
	}

	private void applyColumnOrder(List<Integer> order) {
		int columnCount=table.getColumnModel().getColumnCount();
		int target=0;
		for (Integer modelIndex : order) {
			if (modelIndex==null) continue;
			if (modelIndex<0||modelIndex>=columnCount) continue;
			int current=table.convertColumnIndexToView(modelIndex);
			if (current<0) continue;
			if (current!=target) {
				table.getColumnModel().moveColumn(current, target);
			}
			target++;
		}
	}

	private void applyColumnWidths(Map<Integer, Integer> widths) {
		int columnCount=table.getColumnModel().getColumnCount();
		for (Map.Entry<Integer, Integer> entry : widths.entrySet()) {
			Integer modelIndex=entry.getKey();
			Integer width=entry.getValue();
			if (modelIndex==null||width==null) continue;
			if (modelIndex<0||modelIndex>=columnCount) continue;
			if (width.intValue()<=0) continue;
			setColumnWidth(modelIndex.intValue(), width.intValue());
		}
	}

	private void setColumnWidth(int modelIndex, int width) {
		int viewIndex=table.convertColumnIndexToView(modelIndex);
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
		List<Integer> order=new ArrayList<>(count);
		Map<Integer, Integer> widths=new HashMap<>(count);
		for (int view=0; view<count; view++) {
			TableColumn col=table.getColumnModel().getColumn(view);
			int modelIndex=col.getModelIndex();
			order.add(modelIndex);
			widths.put(modelIndex, Math.max(1, col.getWidth()));
		}
		GUIPreferences.setDirectorySummaryColumnOrder(order);
		GUIPreferences.setDirectorySummaryColumnWidths(widths);
	}

	@Override
	public void addNotify() {
		super.addNotify();
		closed=false;
		if (loadingTimer!=null) loadingTimer.start();
	}

	@Override
	public void removeNotify() {
		if (loadingTimer!=null) loadingTimer.stop();
		super.removeNotify();
		closed=true;
		pool.shutdownNow();
	}

	/** Table model: File | Vendor | Date Modified | Size | Gradient (min) | TIC spark */
	private static final class DirSummaryModel extends AbstractTableModel {
		private static final String[] COLS= {"#", "File", "Vendor", "Date Modified", "Size", "Gradient (min)", "Total TIC", "TIC"};
		private static final long serialVersionUID=1L;

		private final CopyOnWriteArrayList<DirRow> rows=new CopyOnWriteArrayList<>();

		void addRows(List<DirRow> rs) {
			final int start=rows.size();
			rows.addAll(rs);
			final int end=rows.size()-1;
			if (end>=start) {
				SwingUtilities.invokeLater(() -> fireTableRowsInserted(start, end));
			}
		}

		DirRow getAt(int modelRow) {
			if (modelRow<0||modelRow>=rows.size()) return null;
			return rows.get(modelRow);
		}

		void rowUpdated(DirRow r) {
			int idx=rows.indexOf(r);
			if (idx>=0) fireTableRowsUpdated(idx, idx);
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return COLS.length;
		}

		@Override
		public String getColumnName(int c) {
			return COLS[c];
		}

		@Override
		public Class<?> getColumnClass(int c) {
			return switch (c) {
				case 0, 1, 2 -> String.class;
				case 3 -> Date.class;
				case 4 -> Long.class; // SIZE_RENDERER will humanize it
				case 5 -> Float.class; // we format "X.Y min" in renderer
				case 6 -> Float.class; // total TIC
				case 7 -> SparkData.class;
				default -> Object.class;
			};
		}

		@Override
		public Object getValueAt(int r, int c) {
			DirRow row=rows.get(r);
			return switch (c) {
				case 0 -> null;
				case 1 -> row.fileName;
				case 2 -> row.vendor.getVendorName();
				case 3 -> row.lastModified;
				case 4 -> row.sizeBytes;
				case 5 -> row.gradientMin; // may be null
				case 6 -> row.totalTIC; // may be null
				case 7 -> row.spark; // may be null
				default -> null;
			};
		}
	}

	/** Row data for the directory summary. */
	private static final class DirRow implements Comparable<DirRow> {
		final Path path;
		final String fileName;
		final String fileNameLower;
		final VendorFile vendor;
		final long sizeBytes;
		final Date lastModified;

		volatile Float gradientMin; // null until computed
		volatile Float totalTIC; // null until computed
		volatile SparkData spark; // null until computed
		private final AtomicBoolean slowBitsReady=new AtomicBoolean(false);

		private DirRow(Path p, VendorFile v, long size, Date lastModified) {
			this.path=p;
			this.fileName=p.getFileName().toString();
			this.fileNameLower=fileName.toLowerCase(Locale.ROOT);
			this.vendor=v;
			this.sizeBytes=Math.max(0L, size);
			this.lastModified=lastModified;
		}

		@Override
		public int compareTo(DirRow o) {
			if (o==null) return 1;
			int c=String.CASE_INSENSITIVE_ORDER.compare(this.fileName, o.fileName);
			if (c!=0) return c;
			c=this.fileName.compareTo(o.fileName);
			if (c!=0) return c;
			return Long.compare(this.sizeBytes, o.sizeBytes);
		}

		static DirRow fromThermo(Path p) {
			long size=(Files.isRegularFile(p)?p.toFile().length():0L);
			Date modified=null;
			try {
				modified=new Date(Files.getLastModifiedTime(p).toMillis());
			} catch (IOException e) {
				Logger.errorException(e);
			}
			return new DirRow(p, VendorFile.THERMO, size, modified);
		}

		SlowBits toSlowBits() {
			return new SlowBits(gradientMin, totalTIC, spark);
		}

		void applySlowBits(SlowBits bits) {
			if (bits==null) return;
			this.gradientMin=bits.gradientMin;
			this.totalTIC=bits.totalTIC;
			this.spark=bits.spark;
		}

		boolean markSlowBitsReady() {
			return slowBitsReady.compareAndSet(false, true);
		}

		boolean isSlowBitsReady() {
			return slowBitsReady.get();
		}

		static DirRow fromDia(Path p) {
			long size=(Files.isRegularFile(p)?p.toFile().length():0L);
			Date modified=null;
			try {
				modified=new Date(Files.getLastModifiedTime(p).toMillis());
			} catch (IOException e) {
				Logger.errorException(e);
			}
			return new DirRow(p, VendorFile.ENCYCLOPEDIA, size, modified);
		}

		static DirRow fromMzml(Path p) {
			long size=(Files.isRegularFile(p)?p.toFile().length():0L);
			Date modified=null;
			try {
				modified=new Date(Files.getLastModifiedTime(p).toMillis());
			} catch (IOException e) {
				Logger.errorException(e);
			}
			return new DirRow(p, VendorFile.MZML, size, modified);
		}

		static DirRow fromBruker(Path p) {
			long size;
			Date modified=null;
			try {
				modified=new Date(Files.getLastModifiedTime(p).toMillis());
				size=Files.walk(p).filter(Files::isRegularFile).mapToLong(f -> {
					try {
						return Files.size(f);
					} catch (IOException e) {
						Logger.errorLine("Error getting size of file "+f+": "+e.getMessage());
						return 0L;
					}
				}).sum();
			} catch (IOException e) {
				Logger.errorException(e);
				size=0;
			}
			return new DirRow(p, VendorFile.BRUKER, size, modified);
		}
	}

	private static final class SlowBits {
		private final Float gradientMin;
		private final Float totalTIC;
		private final SparkData spark;

		private SlowBits(Float gradientMin, Float totalTIC, SparkData spark) {
			this.gradientMin=gradientMin;
			this.totalTIC=totalTIC;
			this.spark=spark;
		}
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

	/** Compact TIC representation for painting fast. Stores normalized y in [0..1]. */
	private static final class SparkData {
		final float[] yNorm; // 0..1, fixed-size (e.g., 64 points)

		private SparkData(float[] yNorm) {
			this.yNorm=yNorm;
		}

		static SparkData fromTIC(float[] x, float[] y, int maxPts) {
			if (y==null||y.length==0) {
				return new SparkData(new float[] {0.0f});
			}
			int n=Math.min(maxPts, y.length);

			float[] pick=new float[n];
			for (int i=0; i<y.length; i++) {
				int index=(int)Math.floor(n*i/(float)y.length);
				if (y[i]>pick[index]) {
					pick[index]=y[i];
				}
			}
			float max=MatrixMath.max(pick);
			if (max<=0) max=1.0f;

			for (int i=0; i<n; i++) {
				pick[i]=(float)(pick[i]/max);
			}
			return new SparkData(pick);
		}
	}

	/** Renders "X.Y min", respecting stripes/borders via StripeTableCellRenderer. */
	private static final class GradientRenderer extends StripeTableCellRenderer {
		private static final long serialVersionUID=1L;

		@Override
		public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
			super.getTableCellRendererComponent(tbl, "", isSelected, hasFocus, row, col);
			if (value instanceof Float f) {
				setHorizontalAlignment(SwingConstants.RIGHT);
				setText(String.format(Locale.ROOT, "%.1f min", f));
			} else {
				setText("");
			}
			return this;
		}
	}

	private static final class DateOnlyRenderer extends StripeTableCellRenderer {
		private static final long serialVersionUID=1L;
		private final DateFormat format=DateFormat.getDateInstance(DateFormat.SHORT);

		@Override
		public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
			super.getTableCellRendererComponent(tbl, "", isSelected, hasFocus, row, col);
			if (value instanceof Date d) {
				setHorizontalAlignment(SwingConstants.RIGHT);
				setText(format.format(d));
			} else {
				setText("");
			}
			return this;
		}
	}

	/** Sparkline renderer: red area under curve, no labels, striped background. */
	private static final class SparkRenderer extends StripeTableCellRenderer {
		private static final long serialVersionUID=1L;
		private static final int PAD=2;
		private static int loadingPhase=0;

		private static String getLoadingText() {
			int dots=3+loadingPhase;
			StringBuilder sb=new StringBuilder("<html>Reading File");
			for (int i=0; i<dots; i++)
				sb.append('.');
			for (int i=dots; i<5; i++)
				sb.append("&nbsp;");
			sb.append("</html>");
			return sb.toString();
		}

		private static void advanceLoadingPhase() {
			loadingPhase=(loadingPhase+1)%3;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			// Keep stripes/border from base class
			super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
			// If spark data not ready -> show placeholder text

			if (value==FAILED) {
				setText("");
				putClientProperty("spark", null);
				return this;
			}

			if (!(value instanceof SparkData sd)||sd==null||sd.yNorm==null||sd.yNorm.length==0) {
				setHorizontalAlignment(SwingConstants.CENTER);
				setText(getLoadingText());
				putClientProperty("spark", null);
				return this;
			}

			// Spark is ready -> no text, just the area chart
			setText("");

			putClientProperty("spark", value); // hand data to paint()
			return this;
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g); // paints stripe background + border

			Object o=getClientProperty("spark");
			if (!(o instanceof SparkData sd)||sd.yNorm==null||sd.yNorm.length==0) return;

			Graphics2D g2=(Graphics2D)g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Insets ins=getInsets();
				int w=getWidth()-ins.left-ins.right-PAD*2;
				int h=getHeight()-ins.top-ins.bottom-PAD*2;
				int ox=ins.left+PAD;
				int oy=ins.top+PAD;

				if (w<=4||h<=4) return;

				// Build polygon (x from 0..w, y from bottom up)
				int n=sd.yNorm.length;
				int[] xs=new int[n+2];
				int[] ys=new int[n+2];

				// baseline start
				xs[0]=ox;
				ys[0]=oy+h;

				for (int i=0; i<n; i++) {
					float t=(n==1)?0f:(i/(float)(n-1));
					xs[i+1]=ox+Math.min(w, Math.max(0, Math.round(t*w)));
					float yn=sd.yNorm[i];
					int ypix=oy+(int)Math.round((1.0-Math.max(0f, Math.min(1f, yn)))*h);
					ys[i+1]=ypix;
				}

				// baseline end
				xs[n+1]=ox+w;
				ys[n+1]=oy+h;

				g2.setComposite(AlphaComposite.SrcOver.derive(0.85f));
				g2.setColor(COLOR_FILL);
				g2.fillPolygon(xs, ys, n+2);
			} finally {
				g2.dispose();
			}
		}
	}
}
