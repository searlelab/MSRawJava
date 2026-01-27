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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.gui.GUIPreferences;
import org.searlelab.msrawjava.io.VendorFiles;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.logging.Logger;

/** Small, streaming table that summarizes raw files in a directory. */
public class DirectorySummaryPanel extends JPanel {
	private static final long serialVersionUID=1L;

	private static final int sparkResolution=128;
	private static final SparkData FAILED=new SparkData(new float[0]);

	private final JTable table;
	private final DirSummaryModel model=new DirSummaryModel();
	// A tiny pool so we don’t thrash the disk; adjust if you want more parallelism.
	private final ExecutorService pool=Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()/2);
	private final Timer loadingTimer;
	private volatile boolean closed=false;
	private boolean applyingSavedLayout=false;
	private boolean pendingColumnSave=false;

	public DirectorySummaryPanel(VendorFiles files) {
		super(new BorderLayout());

		table=new JTable(model);
		TableRowSorter<DirSummaryModel> sorter=new TableRowSorter<>(model);
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

		table.getColumnModel().getColumn(0).setCellRenderer(StripeTableCellRenderer.ROW_NUMBER_RENDERER);
		table.getColumnModel().getColumn(1).setCellRenderer(StripeTableCellRenderer.BASE_RENDERER);
		table.getColumnModel().getColumn(2).setCellRenderer(StripeTableCellRenderer.BASE_RENDERER);
		table.getColumnModel().getColumn(3).setCellRenderer(new DateOnlyRenderer());
		table.getColumnModel().getColumn(4).setCellRenderer(StripeTableCellRenderer.SIZE_RENDERER);
		table.getColumnModel().getColumn(5).setCellRenderer(new GradientRenderer());
		table.getColumnModel().getColumn(6).setCellRenderer(StripeTableCellRenderer.SCI_RENDERER);
		table.getColumnModel().getColumn(7).setCellRenderer(new SparkRenderer());

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

		ArrayList<DirRow> allRows=new ArrayList<DirRow>(brukerRows.size()+thermoRows.size());
		allRows.addAll(brukerRows);
		allRows.addAll(thermoRows);
		Collections.sort(allRows);

		model.addRows(allRows);

		// Stream slow info (gradient + TIC spark) in the background per row, do Bruker first because they are faster
		for (DirRow row : brukerRows) {
			pool.submit(() -> computeSlowBits(row));
		}
		for (DirRow row : thermoRows) {
			pool.submit(() -> computeSlowBits(row));
		}
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
		if (row.vendor==DirRow.Vendor.THERMO) {
			ThermoRawFile raw=new ThermoRawFile();
			try {
				raw.openFile(row.path);
				Pair<float[], float[]> tic=raw.getTICTrace();
				row.totalTIC=raw.getTIC();
				row.gradientMin=raw.getGradientLength()/60f;
				row.spark=SparkData.fromTIC(tic.x, tic.y, sparkResolution);
				safeRowUpdate(row);
			} catch (Throwable ignore) {
				row.spark=FAILED;
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
				safeRowUpdate(row);
			} catch (Throwable ignore) {
				row.spark=FAILED;
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
				case 2 -> row.vendor.label;
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
		enum Vendor {
			THERMO("Thermo"), BRUKER("Bruker");

			final String label;

			Vendor(String s) {
				label=s;
			}
		}

		final Path path;
		final String fileName;
		final Vendor vendor;
		final long sizeBytes;
		final Date lastModified;

		volatile Float gradientMin; // null until computed
		volatile Float totalTIC; // null until computed
		volatile SparkData spark; // null until computed

		private DirRow(Path p, Vendor v, long size, Date lastModified) {
			this.path=p;
			this.fileName=p.getFileName().toString();
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
			return new DirRow(p, Vendor.THERMO, size, modified);
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
						System.err.println("Error getting size of file "+f+": "+e.getMessage());
						return 0L;
					}
				}).sum();
			} catch (IOException e) {
				Logger.errorException(e);
				size=0;
			}
			return new DirRow(p, Vendor.BRUKER, size, modified);
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
		private static final Color RED_FILL=new Color(0xC62828);
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
				g2.setColor(RED_FILL);
				g2.fillPolygon(xs, ys, n+2);
			} finally {
				g2.dispose();
			}
		}
	}
}
