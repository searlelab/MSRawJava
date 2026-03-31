package org.searlelab.msrawjava.gui.visualization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.event.KeyEvent;

import javax.swing.InputMap;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.Test;

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
}
