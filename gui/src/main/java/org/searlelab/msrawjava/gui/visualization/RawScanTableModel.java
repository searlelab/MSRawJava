package org.searlelab.msrawjava.gui.visualization;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import org.searlelab.msrawjava.model.ScanSummary;

public class RawScanTableModel extends AbstractTableModel {
	private static final long serialVersionUID=1L;

	private static final String[] COLUMNS=new String[] {"#", "Spectrum Name", "Scan Start Time (min)", "Precursor m/z"};
	private final ArrayList<ScanSummary> entries=new ArrayList<>();

	public void updateEntries(List<? extends ScanSummary> newEntries) {
		entries.clear();
		if (newEntries!=null) entries.addAll(newEntries);
		fireTableDataChanged();
	}

	public ScanSummary getSelectedRow(int rowIndex) {
		return entries.get(rowIndex);
	}

	@Override
	public int getRowCount() {
		return entries.size();
	}

	@Override
	public int getColumnCount() {
		return COLUMNS.length;
	}

	@Override
	public String getColumnName(int column) {
		return COLUMNS[column];
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		switch (columnIndex) {
			case 0: return Integer.class;
			case 1: return String.class;
			case 2: return Float.class;
			case 3: return Double.class;
			default: return Object.class;
		}
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		ScanSummary entry=getSelectedRow(rowIndex);
		switch (columnIndex) {
			case 0: return rowIndex+1;
			case 1: return entry.getSpectrumName();
			case 2: return entry.getScanStartTime()/60f;
			case 3: return entry.getPrecursorMz();
			default: return null;
		}
	}
}
