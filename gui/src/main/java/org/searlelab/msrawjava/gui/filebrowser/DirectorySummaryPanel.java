package org.searlelab.msrawjava.gui.filebrowser;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import org.searlelab.msrawjava.io.VendorFiles;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.io.utils.Pair;

/** Small, streaming table that summarizes raw files in a directory. */
public class DirectorySummaryPanel extends JPanel {
	private static final long serialVersionUID=1L;
	
	private final JTable table;
	private final DirSummaryModel model=new DirSummaryModel();
	// A tiny pool so we don’t thrash the disk; adjust if you want more parallelism.
	private final ExecutorService pool=Executors.newFixedThreadPool(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()/2)));
	private volatile boolean closed=false;

	public DirectorySummaryPanel(VendorFiles files) {
		super(new BorderLayout());

		table=new JTable(model);
		table.setRowHeight(28);
		table.setFillsViewportHeight(true);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		// Stripe renderers so it blends in
		table.setDefaultRenderer(String.class, org.searlelab.msrawjava.gui.filebrowser.StripeTableCellRenderer.BASE_RENDERER);
		table.setDefaultRenderer(Long.class, org.searlelab.msrawjava.gui.filebrowser.StripeTableCellRenderer.SIZE_RENDERER);
		table.setDefaultRenderer(Float.class, new GradientRenderer()); // formats "X.Y min"
		table.setDefaultRenderer(SparkData.class, new SparkRenderer()); // red filled spark

		// Column widths (tweak as you like)
		JScrollPane sp=new JScrollPane(table);
		add(sp, BorderLayout.CENTER);
		SwingUtilities.invokeLater(() -> {
			if (table.getColumnModel().getColumnCount()>=5) {
				table.getColumnModel().getColumn(0).setPreferredWidth(320); // File
				table.getColumnModel().getColumn(1).setPreferredWidth(90); // Vendor
				table.getColumnModel().getColumn(2).setPreferredWidth(110); // Size
				table.getColumnModel().getColumn(3).setPreferredWidth(110); // Gradient
				table.getColumnModel().getColumn(4).setPreferredWidth(220); // TIC
			}
		});

		// Seed fast info (file name/vendor/size) synchronously so table appears immediately
		for (Path p : files.getThermoFiles())
			model.addRow(DirRow.fromThermo(p));
		for (Path p : files.getBrukerDirs())
			model.addRow(DirRow.fromBruker(p));

		// Stream slow info (gradient + TIC spark) in the background per row
		for (DirRow row : model.rows) {
			pool.submit(() -> computeSlowBits(row));
		}
	}
	
	public JTable getTable() { return table; }

	public List<Path> getSelectedPaths() {
	    int[] view = table.getSelectedRows();
	    List<Path> out = new ArrayList<>(view.length);
	    for (int vr : view) {
	        int mr = table.convertRowIndexToModel(vr);
	        DirRow r = model.getAt(mr);
	        if (r != null) out.add(r.path);
	    }
	    return out;
	}

	public Path getFirstSelectedPath() {
	    int row = table.getSelectedRow();
	    if (row < 0) return null;
	    int mr = table.convertRowIndexToModel(row);
	    DirRow r = model.getAt(mr);
	    return (r == null) ? null : r.path;
	}
	
	public Path getPathAtViewRow(int vr) {
	    if (vr < 0) return null;
	    int mr = table.convertRowIndexToModel(vr);
	    DirRow r = model.getAt(mr);
	    return (r == null) ? null : r.path;
	}

	private void computeSlowBits(DirRow row) {
		if (closed) return;
		// Per-file fault isolation: if anything fails, we just skip updating that row
		if (row.vendor==DirRow.Vendor.THERMO) {
			ThermoRawFile raw=new ThermoRawFile();
			try {
				raw.openFile(row.path);
				Pair<float[], float[]> tic=raw.getTICTrace();
				float gradMin=(float)(raw.getGradientLength()/60.0);
				SparkData spark=SparkData.fromTIC(tic.x, tic.y, 64);
				row.gradientMin=gradMin;
				row.spark=spark;
				safeRowUpdate(row);
			} catch (Throwable ignore) {
				// ignore this file; leave row with fast info only
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
				float gradMin=(float)(raw.getGradientLength()/60.0);
				SparkData spark=SparkData.fromTIC(tic.x, tic.y, 64);
				row.gradientMin=gradMin;
				row.spark=spark;
				safeRowUpdate(row);
			} catch (Throwable ignore) {
				// ignore this file; leave row with fast info only
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

	@Override
	public void addNotify() {
		super.addNotify();
		closed=false;
	}

	@Override
	public void removeNotify() {
		super.removeNotify();
		closed=true;
		pool.shutdownNow();
	}

	/** Table model: File | Vendor | Size | Gradient (min) | TIC spark */
	private static final class DirSummaryModel extends AbstractTableModel {
		private static final String[] COLS= {"File", "Vendor", "Size", "Gradient (min)", "TIC"};
		private static final long serialVersionUID=1L;

		private final CopyOnWriteArrayList<DirRow> rows=new CopyOnWriteArrayList<>();

		void addRow(DirRow r) {
			rows.add(r);
			int i=rows.size()-1;
			SwingUtilities.invokeLater(() -> fireTableRowsInserted(i, i));
		}
		
		DirRow getAt(int modelRow) {
		    if (modelRow < 0 || modelRow >= rows.size()) return null;
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
				case 0, 1 -> String.class;
				case 2 -> Long.class; // SIZE_RENDERER will humanize it
				case 3 -> Float.class; // we format "X.Y min" in renderer
				case 4 -> SparkData.class;
				default -> Object.class;
			};
		}

		@Override
		public Object getValueAt(int r, int c) {
			DirRow row=rows.get(r);
			return switch (c) {
				case 0 -> row.fileName;
				case 1 -> row.vendor.label;
				case 2 -> row.sizeBytes;
				case 3 -> row.gradientMin; // may be null -> renderer shows ""
				case 4 -> row.spark; // may be null -> renderer shows empty spark
				default -> null;
			};
		}
	}

	/** Row data for the directory summary. */
	private static final class DirRow {
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

		volatile Float gradientMin; // null until computed
		volatile SparkData spark; // null until computed

		private DirRow(Path p, Vendor v, long size) {
			this.path=p;
			this.fileName=p.getFileName().toString();
			this.vendor=v;
			this.sizeBytes=Math.max(0L, size);
		}

		static DirRow fromThermo(Path p) {
			long size=(Files.isRegularFile(p)?p.toFile().length():0L);
			return new DirRow(p, Vendor.THERMO, size);
		}

		static DirRow fromBruker(Path p) {
			// Bruker .d is a directory; Directory.length() is usually 0. We’ll leave it 0 for speed.
			// (If you want actual directory size, compute asynchronously with Files.walk & sum.)
			long size=p.toFile().length();
			return new DirRow(p, Vendor.BRUKER, size);
		}
	}

	/** Compact TIC representation for painting fast. Stores normalized y in [0..1]. */
	private static final class SparkData {
		final float[] yNorm; // 0..1, fixed-size (e.g., 64 points)

		private SparkData(float[] yNorm) {
			this.yNorm=yNorm;
		}

		static SparkData fromTIC(float[] x, float[] y, int maxPts) {
			if (y==null||y.length==0) return null;
			// Downsample by picking evenly spaced indices
			int n=Math.min(maxPts, y.length);
			float[] pick=new float[n];
			double max=0.0;
			for (int i=0; i<n; i++) {
				int idx=(int)Math.floor((long)i*(y.length-1)/(double)(n-1));
				float v=y[idx];
				pick[i]=v;
				if (v>max) max=v;
			}
			if (max<=0) max=1.0;
			for (int i=0; i<n; i++)
				pick[i]=(float)(pick[i]/max);
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
				setText(String.format(Locale.ROOT, "%.2f min", f));
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

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			// Keep stripes/border from base class
			super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
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
