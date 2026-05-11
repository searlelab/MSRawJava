package org.searlelab.msrawjava.gui.filebrowser;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

/** Table model: File | Vendor | Date Modified | Size | Gradient (min) | TIC spark */
final class DirectorySummaryModel extends AbstractTableModel {
	private static final String[] COLS= {"#", "File", "Vendor", "Date Modified", "Size", "Gradient (min)", "Total TIC", "TIC"};
	private static final long serialVersionUID=1L;

	private final CopyOnWriteArrayList<DirectorySummaryRow> rows=new CopyOnWriteArrayList<>();

	void addRows(List<DirectorySummaryRow> rs) {
		final int start=rows.size();
		rows.addAll(rs);
		final int end=rows.size()-1;
		if (end>=start) {
			SwingUtilities.invokeLater(() -> fireTableRowsInserted(start, end));
		}
	}

	DirectorySummaryRow getAt(int modelRow) {
		if (modelRow<0||modelRow>=rows.size()) return null;
		return rows.get(modelRow);
	}

	List<DirectorySummaryRow> snapshotRows() {
		return new ArrayList<>(rows);
	}

	void rowUpdated(DirectorySummaryRow r) {
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
		switch (c) {
			case 0:
			case 1:
			case 2:
				return String.class;
			case 3:
				return Date.class;
			case 4:
				return Long.class; // SIZE_RENDERER will humanize it
			case 5:
				return Float.class; // we format "X.Y min" in renderer
			case 6:
				return Float.class; // total TIC
			case 7:
				return SparkData.class;
			default:
				return Object.class;
		}
	}

	@Override
	public Object getValueAt(int r, int c) {
		DirectorySummaryRow row=rows.get(r);
		switch (c) {
			case 0:
				return null;
			case 1:
				return row.fileName;
			case 2:
				return row.vendor.getVendorName();
			case 3:
				return row.lastModified;
			case 4:
				return row.sizeBytes;
			case 5:
				return row.gradientMin; // may be null
			case 6:
				return row.totalTIC; // may be null
			case 7:
				return row.spark; // may be null
			default:
				return null;
		}
	}
}
