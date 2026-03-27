package org.searlelab.msrawjava.gui.graphing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BasicChartLegendModeTest {

	@BeforeAll
	static void configureHeadless() {
		System.setProperty("java.awt.headless", "true");
	}

	@Test
	void booleanLegendTrue_mapsToInlineMode() {
		ExtendedChartPanel panel=BasicChartGenerator.getChart("x", "y", true, sampleTrace("Series A"));
		assertFalse(panel.isLegendDrawerEnabled());
		assertNotNull(panel.getChart().getLegend());
		assertTrue(panel.getChart().getLegend().isVisible());
	}

	@Test
	void booleanLegendFalse_mapsToNoneMode() {
		ExtendedChartPanel panel=BasicChartGenerator.getChart("x", "y", false, sampleTrace("Series A"));
		assertFalse(panel.isLegendDrawerEnabled());
		assertNull(panel.getChart().getLegend());
	}

	@Test
	void drawerMode_enablesDrawerAndHidesInlineLegend() {
		ExtendedChartPanel panel=BasicChartGenerator.getChart("x", "y", LegendMode.DRAWER, sampleTrace("Series A"));
		assertTrue(panel.isLegendDrawerEnabled());
		assertNotNull(panel.getChart().getLegend());
		assertFalse(panel.getChart().getLegend().isVisible());
	}

	private static XYTrace sampleTrace(String name) {
		return new XYTrace(new double[] {0.0, 1.0, 2.0}, new double[] {1.0, 2.0, 3.0}, GraphType.line, name);
	}
}
