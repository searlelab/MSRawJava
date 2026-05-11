package org.searlelab.msrawjava.gui.filebrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.VendorFile;

class DirectorySummaryPanelVendorFilterTest {

	@Test
	void normalizeSavedVendorFilterDefaultsToAllRawInstrumentFiles() {
		assertEquals("All raw instrument files", DirectorySummaryVendorFilter.normalizeSavedVendorFilter(null));
		assertEquals("All raw instrument files", DirectorySummaryVendorFilter.normalizeSavedVendorFilter(""));
	}

	@Test
	void normalizeSavedVendorFilterPreservesKnownValues() {
		assertEquals("All", DirectorySummaryVendorFilter.normalizeSavedVendorFilter("All"));
		assertEquals("All raw instrument files", DirectorySummaryVendorFilter.normalizeSavedVendorFilter("All raw instrument files"));
		assertEquals("THERMO", DirectorySummaryVendorFilter.normalizeSavedVendorFilter("THERMO"));
	}

	@Test
	void getVendorFilterValueForSelectionMapsExpectedValues() {
		assertEquals("THERMO", DirectorySummaryVendorFilter.getVendorFilterValueForSelection(VendorFile.THERMO));
		assertEquals("All", DirectorySummaryVendorFilter.getVendorFilterValueForSelection("All"));
		assertEquals("All raw instrument files", DirectorySummaryVendorFilter.getVendorFilterValueForSelection(new Object()));
	}

	@Test
	void allRawInstrumentFilesIncludesOnlyBrukerAndThermo() {
		assertTrue(DirectorySummaryVendorFilter.matchesVendorFilterValue(VendorFile.BRUKER, "All raw instrument files"));
		assertTrue(DirectorySummaryVendorFilter.matchesVendorFilterValue(VendorFile.THERMO, "All raw instrument files"));
		assertFalse(DirectorySummaryVendorFilter.matchesVendorFilterValue(VendorFile.ENCYCLOPEDIA, "All raw instrument files"));
		assertFalse(DirectorySummaryVendorFilter.matchesVendorFilterValue(VendorFile.MZML, "All raw instrument files"));
	}

	@Test
	void allIncludesEveryVendor() {
		assertTrue(DirectorySummaryVendorFilter.matchesVendorFilterValue(VendorFile.BRUKER, "All"));
		assertTrue(DirectorySummaryVendorFilter.matchesVendorFilterValue(VendorFile.THERMO, "All"));
		assertTrue(DirectorySummaryVendorFilter.matchesVendorFilterValue(VendorFile.ENCYCLOPEDIA, "All"));
		assertTrue(DirectorySummaryVendorFilter.matchesVendorFilterValue(VendorFile.MZML, "All"));
	}

	@Test
	void directorySummaryRowFromRegularFileCapturesSizeAndModifiedTime() throws Exception {
		Path raw=Files.createTempFile("directory-summary-row", ".raw");
		Files.write(raw, new byte[] {1, 2, 3, 4});

		DirectorySummaryRow row=DirectorySummaryRow.fromThermo(raw);

		assertEquals(raw, row.path);
		assertEquals(VendorFile.THERMO, row.vendor);
		assertEquals(4L, row.sizeBytes);
		assertNotNull(row.lastModified);
	}

	@Test
	void sparkDataFromTicNormalizesAndHandlesEmptyInput() {
		SparkData spark=SparkData.fromTIC(new float[] {0f, 1f, 2f}, new float[] {2f, 4f, 8f}, 3);
		assertEquals(3, spark.yNorm.length);
		assertEquals(0.25f, spark.yNorm[0], 0.0001f);
		assertEquals(0.5f, spark.yNorm[1], 0.0001f);
		assertEquals(1.0f, spark.yNorm[2], 0.0001f);

		SparkData empty=SparkData.fromTIC(null, new float[0], 8);
		assertEquals(1, empty.yNorm.length);
		assertEquals(0.0f, empty.yNorm[0], 0.0001f);
	}

	@Test
	void directorySummaryModelExposesExpectedColumns() {
		DirectorySummaryModel model=new DirectorySummaryModel();

		assertEquals(8, model.getColumnCount());
		assertEquals("#", model.getColumnName(0));
		assertEquals("File", model.getColumnName(1));
		assertEquals("Vendor", model.getColumnName(2));
		assertEquals("Date Modified", model.getColumnName(3));
		assertEquals("Size", model.getColumnName(4));
		assertEquals("Gradient (min)", model.getColumnName(5));
		assertEquals("Total TIC", model.getColumnName(6));
		assertEquals("TIC", model.getColumnName(7));
		assertEquals(String.class, model.getColumnClass(1));
		assertEquals(java.util.Date.class, model.getColumnClass(3));
		assertEquals(Long.class, model.getColumnClass(4));
		assertEquals(Float.class, model.getColumnClass(5));
		assertEquals(SparkData.class, model.getColumnClass(7));
	}
}
