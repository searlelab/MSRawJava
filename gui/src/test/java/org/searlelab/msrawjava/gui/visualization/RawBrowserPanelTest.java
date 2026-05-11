package org.searlelab.msrawjava.gui.visualization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.InputMap;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.RowSorter;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.utils.Pair;

class RawBrowserPanelTest {

	@Test
	void findNearestValueIndex_returnsClosestFiniteValue() {
		double[] values=new double[] {1.0, 2.5, 4.1, 7.2};
		assertEquals(2, RawBrowserPanel.findNearestValueIndex(3.9, values));
		assertEquals(0, RawBrowserPanel.findNearestValueIndex(1.2, values));
		assertEquals(3, RawBrowserPanel.findNearestValueIndex(9.0, values));
	}

	@Test
	void findNearestValueIndex_ignoresNonFiniteValues() {
		double[] values=new double[] {Double.NaN, Double.NEGATIVE_INFINITY, 5.0, Double.POSITIVE_INFINITY};
		assertEquals(2, RawBrowserPanel.findNearestValueIndex(4.8, values));
		assertEquals(-1, RawBrowserPanel.findNearestValueIndex(4.8, new double[] {Double.NaN}));
		assertEquals(-1, RawBrowserPanel.findNearestValueIndex(Double.NaN, values));
	}

	@Test
	void installHorizontalRowNavigation_mapsLeftRightToRowActions() {
		InputMap inputMap=new InputMap();
		RawBrowserPanel.mapHorizontalNavigationToRows(inputMap);
		assertEquals("selectPreviousRow", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)));
		assertEquals("selectNextRow", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)));
		assertEquals("selectPreviousRowExtendSelection", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.SHIFT_DOWN_MASK)));
		assertEquals("selectNextRowExtendSelection", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.SHIFT_DOWN_MASK)));
	}

	@Test
	void mapChartNavigationToRows_mapsArrowKeysToChartRowActions() {
		InputMap inputMap=new InputMap();
		RawBrowserPanel.mapChartNavigationToRows(inputMap);
		assertEquals("rawBrowser.chartSelectPreviousRow", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)));
		assertEquals("rawBrowser.chartSelectNextRow", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)));
		assertEquals("rawBrowser.chartSelectPreviousRow", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)));
		assertEquals("rawBrowser.chartSelectNextRow", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)));
		assertEquals("rawBrowser.chartSelectPreviousRowExtend", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.SHIFT_DOWN_MASK)));
		assertEquals("rawBrowser.chartSelectNextRowExtend", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.SHIFT_DOWN_MASK)));
	}

	@Test
	void findFirstMatchingIndex_returnsIndexOfMs1StyleEntry() {
		assertEquals(1, RawBrowserPanel.findFirstMatchingIndex(List.of("All spectra", "MS1", "MS2 500.0 to 520.0 m/z"), "MS1"::equals));
		assertEquals(-1, RawBrowserPanel.findFirstMatchingIndex(List.of("All spectra", "MS2"), "MS1"::equals));
	}

	@Test
	void scanMetadataTableModel_usesPropertyAndValueColumnsInVendorOrder() {
		RawBrowserPanel.ScanMetadataTableModel model=new RawBrowserPanel.ScanMetadataTableModel();
		model.update(new Pair<>(new String[] {"Source: Source Type", "Source: Set Capillary"}, new String[] {"11", "1600 V"}));

		assertEquals("Property", model.getColumnName(0));
		assertEquals("Value", model.getColumnName(1));
		assertEquals(2, model.getRowCount());
		assertEquals("Source: Source Type", model.getValueAt(0, 0));
		assertEquals("1600 V", model.getValueAt(1, 1));
	}

	@Test
	void scanMetadataTable_isSortableAndCompactByDefault() {
		RawBrowserPanel.ScanMetadataTableModel model=new RawBrowserPanel.ScanMetadataTableModel();
		model.update(new Pair<>(new String[] {"B", "A"}, new String[] {"2", "1"}));
		JTable table=RawBrowserPanel.createScanMetadataTable(model);

		assertEquals(220, table.getPreferredScrollableViewportSize().width);
		assertEquals(143, table.getColumnModel().getColumn(0).getPreferredWidth());
		assertEquals(77, table.getColumnModel().getColumn(1).getPreferredWidth());
		assertTrue(table.getRowSorter()!=null);

		@SuppressWarnings("unchecked")
		RowSorter<?> sorter=table.getRowSorter();
		sorter.toggleSortOrder(0);
		assertEquals("A", table.getValueAt(0, 0));
	}

	@Test
	void scanMetadataTableModel_emptyForNullOrMismatchedMetadata() {
		RawBrowserPanel.ScanMetadataTableModel model=new RawBrowserPanel.ScanMetadataTableModel();
		model.update(null);
		assertEquals(0, model.getRowCount());

		model.update(new Pair<>(new String[] {"Only property"}, new String[0]));
		assertEquals(0, model.getRowCount());
	}
}
