package org.searlelab.msrawjava.gui.graphing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.gui.visualization.StructureChartBuilder;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

import gnu.trove.list.array.TFloatArrayList;

class ChartActionMenuTest {

	@BeforeAll
	static void configureHeadless() {
		System.setProperty("java.awt.headless", "true");
	}

	@Test
	void basicCharts_offerPdfImageAndDataActionsWithoutSvg() {
		ExtendedChartPanel panel=BasicChartGenerator.getChart("m/z", "Intensity", false,
				new XYTrace(new double[] {100.0, 101.0}, new double[] {20.0, 30.0}, GraphType.line, "Trace"));

		assertHasCommonActionsWithoutSvg(panel);
	}

	@Test
	void boxplotCharts_offerPdfImageAndDataActions() {
		ExtendedChartPanel panel=BoxPlotGenerator.getBoxplotChart(null, "Range", "Ion Injection Time",
				Map.of("400 to 500", new TFloatArrayList(new float[] {10.0f, 20.0f})));

		assertHasCommonActionsWithoutSvg(panel);
	}

	@Test
	void globalStructureCharts_offerPdfImageAndDataActions() {
		ExtendedChartPanel panel=StructureChartBuilder.buildGlobalStructureChart(Map.of(new Range(400.0, 500.0), new WindowData(0.5f, 10)));

		assertHasCommonActionsWithoutSvg(panel);
	}

	private static void assertHasCommonActionsWithoutSvg(ExtendedChartPanel panel) {
		JPopupMenu menu=panel.getPopupMenu();
		assertTrue(hasMenuItem(menu, "Save as PDF"));
		assertTrue(hasMenuItem(menu, "Copy as image"));
		assertTrue(hasMenuItem(menu, "Copy data values"));
		assertFalse(hasMenuItem(menu, "Save as SVG"));
	}

	private static boolean hasMenuItem(JPopupMenu menu, String text) {
		for (int i=0; i<menu.getComponentCount(); i++) {
			if (menu.getComponent(i) instanceof JMenuItem) {
				JMenuItem item=(JMenuItem)menu.getComponent(i);
				if (text.equals(item.getText())) return true;
			}
		}
		return false;
	}
}
