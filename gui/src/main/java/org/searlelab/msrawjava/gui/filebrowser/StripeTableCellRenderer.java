package org.searlelab.msrawjava.gui.filebrowser;

import java.awt.Color;
import java.awt.Component;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;

public class StripeTableCellRenderer extends DefaultTableCellRenderer {
	private static final long serialVersionUID=1L;

	public static final StripeTableCellRenderer BASE_RENDERER=new StripeTableCellRenderer();
	public static final IconStripeBorderRenderer ICON_RENDERER=new IconStripeBorderRenderer();
	public static final SizeRendererStripe SIZE_RENDERER=new SizeRendererStripe();

	private final Color altBg=or(UIManager.getColor("Table.alternateRowColor"), new Color(247, 247, 247)); // very light gray
	private final Color grid=or(UIManager.getColor("Table.gridColor"), new Color(220, 220, 220));

	protected StripeTableCellRenderer() {
		setOpaque(true);
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

		super.getTableCellRendererComponent(table, value, false, false, row, column);

		// Compute selection from the table itself (robust across LaFs)
		boolean rowSelected=false;
		int[] selectedRows=table.getSelectedRows();
		for (int i=0; i<selectedRows.length; i++) {
			if (selectedRows[i]==row) {
				rowSelected=true;
				break;
			}
		}

		if (rowSelected) {
			setBackground(table.getSelectionBackground());
			setForeground(table.getSelectionForeground());
		} else {
			setForeground(table.getForeground());
			setBackground((row%2==0)?Color.WHITE:altBg);
		}

		int top=(row==0)?0:1;
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(top, 0, 0, 0, grid),
				BorderFactory.createMatteBorder(0, 2, 0, 2, getBackground())));

		return this;
	}

	private static <T> T or(T a, T b) {
		return (a!=null)?a:b;
	}

	private static class IconStripeBorderRenderer extends StripeTableCellRenderer {
		private static final long serialVersionUID=1L;

		IconStripeBorderRenderer() {
			super();
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

			super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
			setIcon(value instanceof Icon?(Icon)value:null);
			setHorizontalAlignment(CENTER);
			return this;
		}
	}

	private static class SizeRendererStripe extends StripeTableCellRenderer {
		private static final long serialVersionUID=1L;

		SizeRendererStripe() {
			super();
			setHorizontalAlignment(SwingConstants.RIGHT);
		}

		@Override
		public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

			super.getTableCellRendererComponent(tbl, "", isSelected, hasFocus, row, column);
			if (value instanceof Long l&&l>=0L) {
				setText(humanBytes(l));
			} else {
				setText("");
			}
			return this;
		}

		private static String humanBytes(long b) {
			if (b<1024) return b+" B";
			double kb=b/1024.0;
			if (kb<1024) return String.format(Locale.ROOT, "%.1f KB", kb);
			double mb=kb/1024.0;
			if (mb<1024) return String.format(Locale.ROOT, "%.2f MB", mb);
			double gb=mb/1024.0;
			return String.format(Locale.ROOT, "%.2f GB", gb);
		}
	}
}