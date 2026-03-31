package org.searlelab.msrawjava.gui.graphing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jfree.chart.axis.NumberAxis;
import org.jfree.data.RangeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BasicChartGeneratorAxisDefaultsTest {

	@BeforeAll
	static void configureHeadless() {
		System.setProperty("java.awt.headless", "true");
	}

	@Test
	void rangeAxis_defaultsToPositiveAutoRangeIncludingZero() {
		ExtendedChartPanel panel=BasicChartGenerator.getChart("m/z", "Intensity", false,
				new XYTrace(new double[] {100.0, 101.0, 102.0}, new double[] {10.0, 50.0, 25.0}, GraphType.line, "Series"));
		NumberAxis rangeAxis=(NumberAxis)panel.getChart().getXYPlot().getRangeAxis();

		assertTrue(rangeAxis.isAutoRange());
		assertTrue(rangeAxis.getAutoRangeIncludesZero());
		assertEquals(RangeType.POSITIVE, rangeAxis.getRangeType());
	}
}
