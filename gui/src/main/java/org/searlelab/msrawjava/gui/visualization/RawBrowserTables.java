package org.searlelab.msrawjava.gui.visualization;

import java.awt.Dimension;

import javax.swing.JTable;
import javax.swing.table.TableColumn;

final class RawBrowserTables {
	private RawBrowserTables() {
	}

	static JTable createScanMetadataTable(ScanMetadataTableModel model) {
		JTable metadataTable=new JTable(model);
		metadataTable.setAutoCreateRowSorter(true);
		metadataTable.setFillsViewportHeight(true);
		metadataTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		metadataTable.setToolTipText("Sortable per-scan vendor metadata.");
		TableColumn propertyColumn=metadataTable.getColumnModel().getColumn(0);
		TableColumn valueColumn=metadataTable.getColumnModel().getColumn(1);
		propertyColumn.setPreferredWidth(143);
		valueColumn.setPreferredWidth(77);
		metadataTable.setPreferredScrollableViewportSize(new Dimension(220, 160));
		return metadataTable;
	}
}
