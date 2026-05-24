package org.searlelab.msrawjava.gui.visualization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.gui.graphing.ExtendedChartPanel;
import org.searlelab.msrawjava.gui.graphing.GraphType;
import org.searlelab.msrawjava.gui.graphing.XYTrace;

class RawBrowserScanRendererTest {

	@BeforeAll
	static void configureHeadless() {
		System.setProperty("java.awt.headless", "true");
	}

	@Test
	void refreshChromatogramChart_installsLegendDrawerWhenSwitchingFromTicToXic() throws Exception {
		RawBrowserScanRenderer renderer=new RawBrowserScanRenderer(action -> {}, minutes -> {}, () -> {});
		RawBrowserXicController controller=new RawBrowserXicController(null, () -> {}, () -> {}, () -> {}, cursor -> {});
		XYTrace ticTrace=new XYTrace(new double[] {0.0, 1.0}, new double[] {10.0, 20.0}, GraphType.line, "TIC");

		renderer.refreshChromatogramChart(ticTrace, 20.0f, controller, false);
		ExtendedChartPanel ticPanel=renderer.getTopChromatogramChart();
		assertNotNull(ticPanel);
		assertFalse(ticPanel.isLegendDrawerEnabled());

		List<RawBrowserXicUtils.XicTarget> targets=RawBrowserXicUtils.parseXicTargets("445.34").precursorTargets();
		XicTraceData traceData=new XicTraceData(targets, XicToleranceOption.DEFAULT, new double[] {0.0, 1.0},
				new double[][] {{2.0, 4.0}}, new double[][] {{445.34, 445.35}}, new double[][] {{0.0, 1.0}}, 4.0f);
		setField(controller, "activeXicTargets", targets);
		setField(controller, "activeXicTraceData", traceData);
		setField(controller, "xicActive", true);

		renderer.refreshChromatogramChart(ticTrace, 20.0f, controller, true);
		ExtendedChartPanel xicPanel=renderer.getTopChromatogramChart();

		assertNotSame(ticPanel, xicPanel);
		assertTrue(xicPanel.isLegendDrawerEnabled());
		assertNotNull(xicPanel.getChart().getLegend());
		assertFalse(xicPanel.getChart().getLegend().isVisible());
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field field=target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
