package org.searlelab.msrawjava.gui.graphing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;

import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChartStyleTransferTest {

	@BeforeAll
	static void configureHeadless() {
		System.setProperty("java.awt.headless", "true");
	}

	@Test
	void apply_preservesManualAxisStateAndStyling() {
		ExtendedChartPanel source=BasicChartGenerator.getChart("m/z", "Intensity", false, sampleTrace("Source"));
		XYPlot sourcePlot=source.getChart().getXYPlot();
		NumberAxis sourceDomain=(NumberAxis)sourcePlot.getDomainAxis();
		sourceDomain.setLabel("Custom m/z");
		sourceDomain.setLabelFont(new Font("Dialog", Font.BOLD, 17));
		sourceDomain.setTickLabelPaint(Color.MAGENTA);
		sourceDomain.setAutoRange(false);
		sourceDomain.setRange(410.0, 460.0);
		sourceDomain.setAutoTickUnitSelection(false);
		sourceDomain.setTickUnit(new org.jfree.chart.axis.NumberTickUnit(12.5), false, false);
		sourcePlot.setBackgroundPaint(Color.YELLOW);
		source.getChart().setBackgroundPaint(Color.CYAN);

		ExtendedChartPanel target=BasicChartGenerator.getChart("m/z", "Intensity", false, sampleTrace("Target"));
		ChartStyleTransfer.apply(source, target);

		XYPlot targetPlot=target.getChart().getXYPlot();
		NumberAxis targetDomain=(NumberAxis)targetPlot.getDomainAxis();
		assertEquals("Custom m/z", targetDomain.getLabel());
		assertEquals(sourceDomain.getLabelFont(), targetDomain.getLabelFont());
		assertEquals(Color.MAGENTA, targetDomain.getTickLabelPaint());
		assertFalse(targetDomain.isAutoRange());
		assertEquals(410.0, targetDomain.getLowerBound(), 1e-6);
		assertEquals(460.0, targetDomain.getUpperBound(), 1e-6);
		assertFalse(targetDomain.isAutoTickUnitSelection());
		assertEquals(sourceDomain.getTickUnit().getSize(), targetDomain.getTickUnit().getSize(), 1e-6);
		assertEquals(Color.YELLOW, targetPlot.getBackgroundPaint());
		assertEquals(Color.CYAN, target.getChart().getBackgroundPaint());
	}

	@Test
	void apply_preservesAutoRangeStateForUnfrozenAxes() {
		ExtendedChartPanel source=BasicChartGenerator.getChart("Time", "Intensity", false, sampleTrace("Source"));
		XYPlot sourcePlot=source.getChart().getXYPlot();
		NumberAxis sourceDomain=(NumberAxis)sourcePlot.getDomainAxis();
		sourceDomain.setAutoRange(true);

		ExtendedChartPanel target=BasicChartGenerator.getChart("Time", "Intensity", false, sampleTrace("Target"));
		XYPlot targetPlot=target.getChart().getXYPlot();
		((NumberAxis)targetPlot.getDomainAxis()).setAutoRange(false);

		ChartStyleTransfer.apply(source, target);

		assertTrue(((NumberAxis)targetPlot.getDomainAxis()).isAutoRange());
	}

	private static XYTrace sampleTrace(String name) {
		return new XYTrace(new double[] {400.0, 425.0, 450.0}, new double[] {25.0, 100.0, 75.0}, GraphType.line, name);
	}
}
