package org.searlelab.msrawjava.gui.filebrowser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

/** Table model for directory-level raw file metadata and preview metrics. */
final class DirectorySummaryModel extends AbstractTableModel {
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
		return DirectorySummaryColumn.values().length;
	}

	@Override
	public String getColumnName(int c) {
		DirectorySummaryColumn column=DirectorySummaryColumn.byModelIndex(c);
		return column==null?"":column.label;
	}

	@Override
	public Class<?> getColumnClass(int c) {
		DirectorySummaryColumn column=DirectorySummaryColumn.byModelIndex(c);
		return column==null?Object.class:column.valueClass;
	}

	@Override
	public Object getValueAt(int r, int c) {
		DirectorySummaryRow row=rows.get(r);
		DirectorySummaryColumn column=DirectorySummaryColumn.byModelIndex(c);
		if (column==null) return null;
		switch (column) {
			case ROW_NUMBER:
				return null;
			case FILE:
				return row.fileName;
			case VENDOR:
				return row.vendor.getVendorName();
			case DATE_MODIFIED:
				return row.lastModified;
			case DATE_ACQUIRED:
				return row.acquiredDate;
			case SIZE:
				return row.sizeBytes;
			case GRADIENT_MIN:
				return row.gradientMin; // may be null
			case TOTAL_TIC:
				return row.totalTIC; // may be null
			case TIC_SPARK:
				return row.spark; // may be null
			default:
				return null;
		}
	}
}
