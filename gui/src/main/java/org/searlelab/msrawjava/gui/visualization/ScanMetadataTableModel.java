package org.searlelab.msrawjava.gui.visualization;

import javax.swing.table.AbstractTableModel;

import org.searlelab.msrawjava.io.utils.Pair;

final class ScanMetadataTableModel extends AbstractTableModel {
	private static final long serialVersionUID=1L;
	private String[] properties=new String[0];
	private String[] values=new String[0];

	void update(Pair<String[], String[]> metadata) {
		if (metadata==null||metadata.getX()==null||metadata.getY()==null) {
			properties=new String[0];
			values=new String[0];
		} else {
			int n=Math.min(metadata.getX().length, metadata.getY().length);
			properties=new String[n];
			values=new String[n];
			System.arraycopy(metadata.getX(), 0, properties, 0, n);
			System.arraycopy(metadata.getY(), 0, values, 0, n);
		}
		fireTableDataChanged();
	}

	@Override
	public int getRowCount() {
		return properties.length;
	}

	@Override
	public int getColumnCount() {
		return 2;
	}

	@Override
	public String getColumnName(int column) {
		return column==0?"Property":"Value";
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		return columnIndex==0?properties[rowIndex]:values[rowIndex];
	}
}
