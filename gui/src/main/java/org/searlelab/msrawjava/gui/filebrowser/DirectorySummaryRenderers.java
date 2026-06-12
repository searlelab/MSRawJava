package org.searlelab.msrawjava.gui.filebrowser;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

import javax.swing.JTable;
import javax.swing.SwingConstants;

final class DirectorySummaryRenderers {
	static final Color COLOR_FILL=new Color(0x5555ED);

	private DirectorySummaryRenderers() {
	}

	/** Renders "X.Y min", respecting stripes/borders via StripeTableCellRenderer. */
	static final class GradientRenderer extends StripeTableCellRenderer {
		private static final long serialVersionUID=1L;

		@Override
		public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
			super.getTableCellRendererComponent(tbl, "", isSelected, hasFocus, row, col);
			if (value instanceof Float) {
				Float f=(Float)value;
				setHorizontalAlignment(SwingConstants.RIGHT);
				setText(String.format(Locale.ROOT, "%.1f min", f));
			} else {
				setText("");
			}
			return this;
		}
	}

	static final class DateTimeRenderer extends StripeTableCellRenderer {
		private static final long serialVersionUID=1L;
		private static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("M/d/yy HH:mm").withZone(ZoneId.systemDefault());

		@Override
		public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
			super.getTableCellRendererComponent(tbl, "", isSelected, hasFocus, row, col);
			if (value instanceof Date) {
				Date d=(Date)value;
				setHorizontalAlignment(SwingConstants.RIGHT);
				setText(formatDateTime(d));
			} else {
				setText("");
			}
			return this;
		}

		static String formatDateTime(Date date) {
			if (date==null) return "";
			return FORMAT.format(date.toInstant());
		}
	}

	/** Sparkline renderer: red area under curve, no labels, striped background. */
	static final class SparkRenderer extends StripeTableCellRenderer {
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

		static void advanceLoadingPhase() {
			loadingPhase=(loadingPhase+1)%3;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			// Keep stripes/border from base class
			super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
			// If spark data not ready -> show placeholder text

			if (value==DirectorySummaryPanel.FAILED) {
				setText("");
				putClientProperty("spark", null);
				return this;
			}

			if (!(value instanceof SparkData)) {
				setHorizontalAlignment(SwingConstants.CENTER);
				setText(getLoadingText());
				putClientProperty("spark", null);
				return this;
			}
			SparkData sd=(SparkData)value;
			if (sd.yNorm==null||sd.yNorm.length==0) {
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
			if (!(o instanceof SparkData)) return;
			SparkData sd=(SparkData)o;
			if (sd.yNorm==null||sd.yNorm.length==0) return;

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
