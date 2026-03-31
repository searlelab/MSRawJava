package org.searlelab.msrawjava.gui.filebrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.VendorFile;

class DirectorySummaryPanelVendorFilterTest {

	@Test
	void normalizeSavedVendorFilterDefaultsToAllRawInstrumentFiles() {
		assertEquals("All raw instrument files", DirectorySummaryPanel.normalizeSavedVendorFilter(null));
		assertEquals("All raw instrument files", DirectorySummaryPanel.normalizeSavedVendorFilter(""));
	}

	@Test
	void normalizeSavedVendorFilterPreservesKnownValues() {
		assertEquals("All", DirectorySummaryPanel.normalizeSavedVendorFilter("All"));
		assertEquals("All raw instrument files", DirectorySummaryPanel.normalizeSavedVendorFilter("All raw instrument files"));
		assertEquals("THERMO", DirectorySummaryPanel.normalizeSavedVendorFilter("THERMO"));
	}

	@Test
	void getVendorFilterValueForSelectionMapsExpectedValues() {
		assertEquals("THERMO", DirectorySummaryPanel.getVendorFilterValueForSelection(VendorFile.THERMO));
		assertEquals("All", DirectorySummaryPanel.getVendorFilterValueForSelection("All"));
		assertEquals("All raw instrument files", DirectorySummaryPanel.getVendorFilterValueForSelection(new Object()));
	}

	@Test
	void allRawInstrumentFilesIncludesOnlyBrukerAndThermo() {
		assertTrue(DirectorySummaryPanel.matchesVendorFilterValue(VendorFile.BRUKER, "All raw instrument files"));
		assertTrue(DirectorySummaryPanel.matchesVendorFilterValue(VendorFile.THERMO, "All raw instrument files"));
		assertFalse(DirectorySummaryPanel.matchesVendorFilterValue(VendorFile.ENCYCLOPEDIA, "All raw instrument files"));
		assertFalse(DirectorySummaryPanel.matchesVendorFilterValue(VendorFile.MZML, "All raw instrument files"));
	}

	@Test
	void allIncludesEveryVendor() {
		assertTrue(DirectorySummaryPanel.matchesVendorFilterValue(VendorFile.BRUKER, "All"));
		assertTrue(DirectorySummaryPanel.matchesVendorFilterValue(VendorFile.THERMO, "All"));
		assertTrue(DirectorySummaryPanel.matchesVendorFilterValue(VendorFile.ENCYCLOPEDIA, "All"));
		assertTrue(DirectorySummaryPanel.matchesVendorFilterValue(VendorFile.MZML, "All"));
	}
}
