package org.searlelab.msrawjava.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class WindowMenuControllerTest {

	@Test
	void defaultMenuOrder_keepsFileViewWindowHelpOrder() {
		assertEquals(List.of("File", "View", "Window", "Help"), MenuManager.defaultMenuOrder());
	}

	@Test
	void createModel_assignsVisualizationAcceleratorsInOrder() {
		WindowMenuModel model=WindowMenuModel.create(false, -1, List.of("one.raw", "two.raw", "three.raw"));
		assertEquals(1, model.getVisualizationItems().get(0).getAcceleratorDigit());
		assertEquals(2, model.getVisualizationItems().get(1).getAcceleratorDigit());
		assertEquals(3, model.getVisualizationItems().get(2).getAcceleratorDigit());
	}

	@Test
	void createModel_resetsAcceleratorsAfterGapCloses() {
		WindowMenuModel model=WindowMenuModel.create(false, -1, List.of("one.raw", "three.raw"));
		assertEquals("one.raw", model.getVisualizationItems().get(0).getLabel());
		assertEquals(1, model.getVisualizationItems().get(0).getAcceleratorDigit());
		assertEquals("three.raw", model.getVisualizationItems().get(1).getLabel());
		assertEquals(2, model.getVisualizationItems().get(1).getAcceleratorDigit());
	}

	@Test
	void createModel_listsWindowsPastNineWithoutNumericAccelerators() {
		List<String> titles=new ArrayList<>();
		for (int i=1; i<=11; i++) {
			titles.add("window-"+i);
		}
		WindowMenuModel model=WindowMenuModel.create(false, -1, titles);
		assertEquals(9, model.getVisualizationItems().get(8).getAcceleratorDigit());
		assertEquals(null, model.getVisualizationItems().get(9).getAcceleratorDigit());
		assertEquals(null, model.getVisualizationItems().get(10).getAcceleratorDigit());
	}

	@Test
	void wrapIndex_cyclesAcrossBrowserAndVisualizationWindows() {
		assertEquals(3, WindowMenuController.wrapIndex(0, -1, 4));
		assertEquals(0, WindowMenuController.wrapIndex(3, 1, 4));
		assertEquals(2, WindowMenuController.wrapIndex(1, 1, 4));
	}

	@Test
	void createModel_marksActiveEntriesForCurrentContext() {
		WindowMenuModel browserModel=WindowMenuModel.create(true, -1, List.of("one.raw", "two.raw"));
		assertTrue(browserModel.isBrowserActive());

		WindowMenuModel visualizationModel=WindowMenuModel.create(false, 1, List.of("one.raw", "two.raw"));
		assertFalse(visualizationModel.getVisualizationItems().get(0).isActive());
		assertTrue(visualizationModel.getVisualizationItems().get(1).isActive());
	}
}
