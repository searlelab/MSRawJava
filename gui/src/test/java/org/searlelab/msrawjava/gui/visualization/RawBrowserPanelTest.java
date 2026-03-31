package org.searlelab.msrawjava.gui.visualization;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
