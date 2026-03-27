package org.searlelab.msrawjava.gui.graphing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChartLegendDrawerSupportTest {

	@BeforeAll
	static void configureHeadless() {
		System.setProperty("java.awt.headless", "true");
	}

	@Test
	void drawerSupport_extractsRowsAndFiltersLabels() {
		ExtendedChartPanel panel=BasicChartGenerator.getChart("x", "y", LegendMode.DRAWER, sampleTrace("Series Alpha"));
		ChartLegendDrawerSupport support=panel.getLegendDrawerSupportForTest();
		assertNotNull(support);
		assertTrue(support.isGlyphVisibleForTest());
		assertEquals(1, support.getLegendRowCountForTest());
		assertEquals(1, support.getVisibleLegendRowCountForTest());

		support.setFilterTextForTest("zzz");
		assertEquals(0, support.getVisibleLegendRowCountForTest());

		support.setFilterTextForTest("alpha");
		assertEquals(1, support.getVisibleLegendRowCountForTest());
	}

	@Test
	void drawerSupport_hidesGlyphWhenNoLegendItemsExist() {
		ExtendedChartPanel panel=BasicChartGenerator.getChart("x", "y", LegendMode.DRAWER, new XYTraceInterface[0]);
		ChartLegendDrawerSupport support=panel.getLegendDrawerSupportForTest();
		assertNotNull(support);
		assertEquals(0, support.getLegendRowCountForTest());
		assertFalse(support.isGlyphVisibleForTest());
		assertEquals(0, support.getVisibleLegendRowCountForTest());
	}

	private static XYTrace sampleTrace(String name) {
		return new XYTrace(new double[] {0.0, 1.0, 2.0}, new double[] {1.0, 2.0, 3.0}, GraphType.line, name);
	}
}
