package org.searlelab.msrawjava.gui.filebrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

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

		assertEquals(9, model.getColumnCount());
		assertEquals("#", model.getColumnName(DirectorySummaryColumn.ROW_NUMBER.modelIndex()));
		assertEquals("File", model.getColumnName(DirectorySummaryColumn.FILE.modelIndex()));
		assertEquals("Vendor", model.getColumnName(DirectorySummaryColumn.VENDOR.modelIndex()));
		assertEquals("Date Modified", model.getColumnName(DirectorySummaryColumn.DATE_MODIFIED.modelIndex()));
		assertEquals("Date Acquired", model.getColumnName(DirectorySummaryColumn.DATE_ACQUIRED.modelIndex()));
		assertEquals("Size", model.getColumnName(DirectorySummaryColumn.SIZE.modelIndex()));
		assertEquals("Gradient (min)", model.getColumnName(DirectorySummaryColumn.GRADIENT_MIN.modelIndex()));
		assertEquals("Total TIC", model.getColumnName(DirectorySummaryColumn.TOTAL_TIC.modelIndex()));
		assertEquals("TIC", model.getColumnName(DirectorySummaryColumn.TIC_SPARK.modelIndex()));
		assertEquals(String.class, model.getColumnClass(DirectorySummaryColumn.FILE.modelIndex()));
		assertEquals(java.util.Date.class, model.getColumnClass(DirectorySummaryColumn.DATE_MODIFIED.modelIndex()));
		assertEquals(java.util.Date.class, model.getColumnClass(DirectorySummaryColumn.DATE_ACQUIRED.modelIndex()));
		assertEquals(Long.class, model.getColumnClass(DirectorySummaryColumn.SIZE.modelIndex()));
		assertEquals(Float.class, model.getColumnClass(DirectorySummaryColumn.GRADIENT_MIN.modelIndex()));
		assertEquals(SparkData.class, model.getColumnClass(DirectorySummaryColumn.TIC_SPARK.modelIndex()));
	}

	@Test
	void directorySummaryRowMetricsIncludeAcquiredDate() throws Exception {
		Path raw=Files.createTempFile("directory-summary-row", ".raw");
		DirectorySummaryRow row=DirectorySummaryRow.fromThermo(raw);
		Date acquired=Date.from(Instant.parse("2024-01-02T03:04:05Z"));
		row.gradientMin=12.5f;
		row.totalTIC=100.0f;
		row.acquiredDate=acquired;
		row.spark=SparkData.fromTIC(new float[] {1f}, new float[] {2f}, 1);

		DirectorySummaryRow copy=DirectorySummaryRow.fromThermo(raw);
		copy.applyMetrics(row.toMetrics());

		assertEquals(acquired, copy.acquiredDate);
		assertEquals(12.5f, copy.gradientMin, 0.001f);
		assertEquals(100.0f, copy.totalTIC, 0.001f);
		assertNotNull(copy.spark);
	}

	@Test
	void parseAcquiredDate_handlesIsoAndBlankValues() {
		assertEquals(Date.from(Instant.parse("2024-01-02T03:04:05Z")), DirectorySummaryPanel.parseAcquiredDate("2024-01-02T03:04:05Z").orElseThrow());
		assertEquals(Date.from(Instant.parse("2023-07-11T20:28:01.167Z")),
				DirectorySummaryPanel.parseAcquiredDate("2023-07-11T12:28:01.167-08:00").orElseThrow());
		assertTrue(DirectorySummaryPanel.parseAcquiredDate("").isEmpty());
	}

	@Test
	void dateTimeRendererIncludesLocalHourAndMinuteWithoutSeconds() {
		Date value=Date.from(Instant.parse("2024-01-02T03:04:05Z"));
		String expected=DateTimeFormatter.ofPattern("M/d/yy HH:mm").withZone(ZoneId.systemDefault()).format(value.toInstant());

		assertEquals(expected, DirectorySummaryRenderers.DateTimeRenderer.formatDateTime(value));
		assertFalse(DirectorySummaryRenderers.DateTimeRenderer.formatDateTime(value).matches(".*:\\d{2}:\\d{2}.*"));
	}
}
