package org.searlelab.msrawjava.gui.filebrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.gui.GuiProcessingActivity;

class DirectorySummaryPanelThrottleTest {

	@Test
	void effectiveWorkerCountUsesNormalCountWhenForegroundWorkInactive() {
		assertEquals(4, DirectorySummaryPanel.effectiveSlowBitsWorkerCount(4));
		assertEquals(1, DirectorySummaryPanel.effectiveSlowBitsWorkerCount(0));
	}

	@Test
	void effectiveWorkerCountDropsToOneDuringForegroundWork() throws Exception {
		try (AutoCloseable ignored=GuiProcessingActivity.beginForegroundWork()) {
			assertEquals(1, DirectorySummaryPanel.effectiveSlowBitsWorkerCount(4));
			assertEquals(1, DirectorySummaryPanel.effectiveSlowBitsWorkerCount(1));
		}
	}
}
