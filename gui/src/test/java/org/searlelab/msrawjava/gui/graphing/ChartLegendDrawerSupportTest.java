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

	@Test
	void drawerSupport_allowsOnlySingleSelectionAndReplacesHalo() {
		ExtendedChartPanel panel=BasicChartGenerator.getChart("x", "y", LegendMode.DRAWER, sampleTrace("Series One"), sampleTrace("Series Two"));
		ChartLegendDrawerSupport support=panel.getLegendDrawerSupportForTest();
		assertNotNull(support);
		assertEquals(2, support.getLegendRowCountForTest());

		support.selectLegendIndexForTest(0);
		assertEquals(1, support.getSelectedLegendCountForTest());
		int firstHaloCount=support.getSelectedHaloAnnotationCountForTest();
		assertTrue(firstHaloCount>0);

		support.selectLegendIndexForTest(1);
		assertEquals(1, support.getSelectedLegendCountForTest());
		int secondHaloCount=support.getSelectedHaloAnnotationCountForTest();
		assertTrue(secondHaloCount>0);
		assertEquals(firstHaloCount, secondHaloCount);

		support.selectLegendIndexForTest(1);
		assertEquals(0, support.getSelectedLegendCountForTest());
		assertEquals(0, support.getSelectedHaloAnnotationCountForTest());
	}

	@Test
	void drawerSupport_allowsChartTraceSelectionAndToggleClear() {
		ExtendedChartPanel panel=BasicChartGenerator.getChart("x", "y", LegendMode.DRAWER, sampleTrace("Series One"), sampleTrace("Series Two"));
		ChartLegendDrawerSupport support=panel.getLegendDrawerSupportForTest();
		assertNotNull(support);
		assertEquals(2, support.getLegendRowCountForTest());

		support.selectTraceForTest(0, 0);
		assertEquals(1, support.getSelectedLegendCountForTest());
		assertTrue(support.getSelectedHaloAnnotationCountForTest()>0);

		support.selectTraceForTest(0, 0);
		assertEquals(0, support.getSelectedLegendCountForTest());
		assertEquals(0, support.getSelectedHaloAnnotationCountForTest());

		support.selectTraceForTest(1, 0);
		assertEquals(1, support.getSelectedLegendCountForTest());
		assertTrue(support.getSelectedHaloAnnotationCountForTest()>0);
	}

	private static XYTrace sampleTrace(String name) {
		return new XYTrace(new double[] {0.0, 1.0, 2.0}, new double[] {1.0, 2.0, 3.0}, GraphType.line, name);
	}
}
