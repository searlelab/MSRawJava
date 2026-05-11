package org.searlelab.msrawjava.gui.visualization;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;

import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;

class RawBrowserSettingsTab extends JPanel {
	private static final long serialVersionUID=1L;
	private static final String THERMO_METHOD_COUNT_KEY="thermo.instrument_method.count";

	private final StripeFileInterface stripe;
	private final MetadataTableModel metadataModel=new MetadataTableModel();
	private final JTable metadataTable=createMetadataTable(metadataModel);
	private final JSplitPane settingsSplit=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	private final JTabbedPane instrumentMethodTabs=new JTabbedPane();

	RawBrowserSettingsTab(StripeFileInterface stripe) {
		super(new BorderLayout());
		this.stripe=stripe;
		settingsSplit.setResizeWeight(0.4);
		settingsSplit.setDividerSize(8);
		settingsSplit.setContinuousLayout(true);
		settingsSplit.setOneTouchExpandable(true);
		add(new JScrollPane(metadataTable), BorderLayout.CENTER);
	}

	void applyMetadata(Map<String, String> metadata) {
		metadataModel.update(metadata);
		removeAll();
		add(createSettingsComponent(metadata), BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private Component createSettingsComponent(Map<String, String> metadata) {
		JScrollPane metadataScroll=new JScrollPane(metadataTable);
		metadataScroll.setMinimumSize(new Dimension(240, 120));
		metadataScroll.setToolTipText("File-level metadata from the opened file.");
		if (!shouldShowInstrumentMethodBrowser(metadata)) {
			return metadataScroll;
		}
		populateInstrumentMethodTabs(metadata);
		settingsSplit.setLeftComponent(metadataScroll);
		settingsSplit.setRightComponent(instrumentMethodTabs);
		SwingUtilities.invokeLater(() -> settingsSplit.setDividerLocation(0.4));
		return settingsSplit;
	}

	private void populateInstrumentMethodTabs(Map<String, String> metadata) {
		int selectedIndex=instrumentMethodTabs.getSelectedIndex();
		instrumentMethodTabs.removeAll();
		int count=getInstrumentMethodCount(metadata);
		for (int i=0; i<count; i++) {
			String prefix=instrumentMethodMetadataPrefix(i);
			String name=metadata.get(prefix+".name");
			if (name==null||name.isBlank()) name="Method "+i;
			JTextPane textPane=new JTextPane();
			textPane.setEditable(false);
			textPane.setText(metadata.getOrDefault(prefix+".raw_text", ""));
			textPane.setCaretPosition(0);
			JScrollPane scroll=new JScrollPane(textPane);
			scroll.setMinimumSize(new Dimension(240, 120));
			instrumentMethodTabs.addTab(name, scroll);
		}
		if (instrumentMethodTabs.getTabCount()>0) {
			instrumentMethodTabs.setSelectedIndex(Math.min(Math.max(selectedIndex, 0), instrumentMethodTabs.getTabCount()-1));
		}
	}

	private boolean shouldShowInstrumentMethodBrowser(Map<String, String> metadata) {
		return stripe instanceof ThermoRawFile||hasInstrumentMethods(metadata);
	}

	static String instrumentMethodMetadataPrefix(int index) {
		return "thermo.instrument_method."+index;
	}

	static boolean hasInstrumentMethods(Map<String, String> metadata) {
		return getInstrumentMethodCount(metadata)>0;
	}

	static int getInstrumentMethodCount(Map<String, String> metadata) {
		if (metadata==null) return 0;
		String raw=metadata.get(THERMO_METHOD_COUNT_KEY);
		if (raw==null) return 0;
		try {
			int count=Integer.parseInt(raw.trim());
			return Math.max(0, count);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	static JTable createMetadataTable(MetadataTableModel model) {
		JTable metadataTable=new JTable(model);
		metadataTable.setAutoCreateRowSorter(true);
		metadataTable.setFillsViewportHeight(true);
		metadataTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		metadataTable.setToolTipText("Sortable file-level metadata.");
		TableColumn parameterColumn=metadataTable.getColumnModel().getColumn(0);
		TableColumn valueColumn=metadataTable.getColumnModel().getColumn(1);
		parameterColumn.setPreferredWidth(360);
		valueColumn.setPreferredWidth(240);
		metadataTable.setPreferredScrollableViewportSize(new Dimension(600, 360));
		return metadataTable;
	}

	static final class MetadataTableModel extends AbstractTableModel {
		private static final long serialVersionUID=1L;
		private final ArrayList<Map.Entry<String, String>> rows=new ArrayList<>();

		void update(Map<String, String> metadata) {
			rows.clear();
			if (metadata!=null) {
				rows.addAll(metadata.entrySet());
				rows.sort(Map.Entry.comparingByKey());
			}
			fireTableDataChanged();
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return 2;
		}

		@Override
		public String getColumnName(int column) {
			return column==0?"Parameter":"Value";
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			Map.Entry<String, String> row=rows.get(rowIndex);
			return columnIndex==0?row.getKey():row.getValue();
		}
	}
}
