package org.searlelab.msrawjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrecursorScanTest {

	private PrecursorScan scan;
	private PrecursorScan scanWithNullIms;

	@BeforeEach
	void setUp() {
		double[] masses = {100.0, 200.0, 300.0};
		float[] intensities = {1000.0f, 2000.0f, 500.0f};
		float[] ims = {0.7f, 0.8f, 0.9f};

		scan = new PrecursorScan(
			"precursor1",       // spectrumName
			1,                  // spectrumIndex
			120.5f,             // scanStartTime
			0,                  // fraction
			100.0,              // scanWindowLower
			1000.0,             // scanWindowUpper
			50.0f,              // ionInjectionTime
			masses,
			intensities,
			ims
		);

		scanWithNullIms = new PrecursorScan(
			"precursor2",
			2,
			180.0f,
			1,
			150.0,
			1200.0,
			null,               // null ionInjectionTime
			masses.clone(),
			intensities.clone(),
			null                // null IMS
		);
	}

	@Test
	void constructorAndBasicGetters() {
		assertEquals("precursor1", scan.getSpectrumName());
		assertEquals(1, scan.getSpectrumIndex());
		assertEquals(120.5f, scan.getScanStartTime(), 1e-6);
		assertEquals(0, scan.getFraction());
		assertEquals(100.0, scan.getScanWindowLower(), 1e-9);
		assertEquals(1000.0, scan.getScanWindowUpper(), 1e-9);
		assertEquals(50.0f, scan.getIonInjectionTime(), 1e-6);
	}

	@Test
	void getPrecursorMZReturnsMinusOne() {
		// For MS1 scans, precursor m/z is -1
		assertEquals(-1.0, scan.getPrecursorMZ(), 1e-9);
	}

	@Test
	void getIsolationWindowReturnsGetScanWindow() {
		// For PrecursorScan, isolation window methods delegate to scan window
		assertEquals(scan.getScanWindowLower(), scan.getIsolationWindowLower(), 1e-9);
		assertEquals(scan.getScanWindowUpper(), scan.getIsolationWindowUpper(), 1e-9);
	}

	@Test
	void getMassArrayReturnsCorrectValues() {
		double[] masses = scan.getMassArray();
		assertEquals(3, masses.length);
		assertEquals(100.0, masses[0], 1e-9);
		assertEquals(200.0, masses[1], 1e-9);
		assertEquals(300.0, masses[2], 1e-9);
	}

	@Test
	void getIntensityArrayReturnsCorrectValues() {
		float[] intensities = scan.getIntensityArray();
		assertEquals(3, intensities.length);
		assertEquals(1000.0f, intensities[0], 1e-6);
		assertEquals(2000.0f, intensities[1], 1e-6);
		assertEquals(500.0f, intensities[2], 1e-6);
	}

	@Test
	void getIonMobilityArrayReturnsValuesWhenPresent() {
		Optional<float[]> ims = scan.getIonMobilityArray();
		assertTrue(ims.isPresent());
		assertEquals(3, ims.get().length);
		assertEquals(0.7f, ims.get()[0], 1e-6);
		assertEquals(0.8f, ims.get()[1], 1e-6);
		assertEquals(0.9f, ims.get()[2], 1e-6);
	}

	@Test
	void getIonMobilityArrayReturnsEmptyWhenNull() {
		Optional<float[]> ims = scanWithNullIms.getIonMobilityArray();
		assertFalse(ims.isPresent());
	}

	@Test
	void getTICCalculatesSumManually() {
		float tic = scan.getTIC();
		assertEquals(3500.0f, tic, 1e-6); // 1000 + 2000 + 500
	}

	@Test
	void nullIonInjectionTimeIsHandled() {
		assertNull(scanWithNullIms.getIonInjectionTime());
	}

	@Test
	void getPeaksFiltersAboveMinimumIntensity() {
		ArrayList<PeakWithIMS> peaks = scan.getPeaks(1500.0f);
		assertEquals(1, peaks.size());
		assertEquals(200.0, peaks.get(0).getMz(), 1e-9);
		assertEquals(2000.0f, peaks.get(0).getIntensity(), 1e-6);
		assertEquals(0.8f, peaks.get(0).getIMS(), 1e-6);
	}

	@Test
	void getPeaksReturnsAllWhenMinimumIsZero() {
		ArrayList<PeakWithIMS> peaks = scan.getPeaks(0.0f);
		assertEquals(3, peaks.size());
	}

	@Test
	void getBasePeakReturnsHighestIntensity() {
		PeakInterface basePeak = scan.getBasePeak();
		assertEquals(200.0, basePeak.getMz(), 1e-9);
		assertEquals(2000.0f, basePeak.getIntensity(), 1e-6);
	}

	@Test
	void getBasePeakIncludesIms() {
		PeakInterface basePeak = scan.getBasePeak();
		assertTrue(basePeak instanceof PeakWithIMS);
		assertEquals(0.8f, ((PeakWithIMS) basePeak).getIMS(), 1e-6);
	}

	@Test
	void getBasePeakHandlesNullImsArray() {
		PeakInterface basePeak = scanWithNullIms.getBasePeak();
		assertEquals(200.0, basePeak.getMz(), 1e-9);
		assertEquals(2000.0f, basePeak.getIntensity(), 1e-6);
		// IMS should be 0.0 when array is null
		assertEquals(0.0f, ((PeakWithIMS) basePeak).getIMS(), 1e-6);
	}

	@Test
	void compareToWithNullReturnsPositive() {
		assertTrue(scan.compareTo(null) > 0);
	}

	@Test
	void compareToOrdersByScanStartTimeFirst() {
		double[] masses = {100.0};
		float[] intensities = {100.0f};
		float[] ims = {0.5f};
		PrecursorScan earlier = new PrecursorScan("a", 1, 100.0f, 0, 100.0, 1000.0, null, masses, intensities, ims);
		PrecursorScan later = new PrecursorScan("b", 1, 200.0f, 0, 100.0, 1000.0, null, masses, intensities, ims);
		assertTrue(earlier.compareTo(later) < 0);
		assertTrue(later.compareTo(earlier) > 0);
	}

	@Test
	void compareToUsesSpectrumIndexWhenTimeEqual() {
		double[] masses = {100.0};
		float[] intensities = {100.0f};
		float[] ims = {0.5f};
		PrecursorScan lowIndex = new PrecursorScan("a", 1, 120.0f, 0, 100.0, 1000.0, null, masses, intensities, ims);
		PrecursorScan highIndex = new PrecursorScan("b", 5, 120.0f, 0, 100.0, 1000.0, null, masses, intensities, ims);
		assertTrue(lowIndex.compareTo(highIndex) < 0);
	}

	@Test
	void compareToUsesScanWindowWhenTimeAndIndexEqual() {
		double[] masses = {100.0};
		float[] intensities = {100.0f};
		float[] ims = {0.5f};
		PrecursorScan lowWindow = new PrecursorScan("a", 1, 120.0f, 0, 50.0, 500.0, null, masses, intensities, ims);
		PrecursorScan highWindow = new PrecursorScan("b", 1, 120.0f, 0, 100.0, 1000.0, null, masses, intensities, ims);
		assertTrue(lowWindow.compareTo(highWindow) < 0);
	}

	@Test
	void rebuildCreatesNewScanWithUpdatedPeaks() {
		ArrayList<PeakWithIMS> newPeaks = new ArrayList<>();
		newPeaks.add(new PeakWithIMS(150.0, 500.0f, 0.5f));
		newPeaks.add(new PeakWithIMS(250.0, 750.0f, 0.6f));

		PrecursorScan rebuilt = scan.rebuild(10, newPeaks);

		assertEquals(10, rebuilt.getSpectrumIndex());
		assertEquals(scan.getScanStartTime(), rebuilt.getScanStartTime(), 1e-6);
		assertEquals(2, rebuilt.getMassArray().length);
		// Should be sorted by m/z
		assertEquals(150.0, rebuilt.getMassArray()[0], 1e-9);
		assertEquals(250.0, rebuilt.getMassArray()[1], 1e-9);
	}

	@Test
	void rebuildSortsPeaksByMz() {
		ArrayList<PeakWithIMS> newPeaks = new ArrayList<>();
		newPeaks.add(new PeakWithIMS(300.0, 500.0f, 0.5f));
		newPeaks.add(new PeakWithIMS(100.0, 750.0f, 0.6f));
		newPeaks.add(new PeakWithIMS(200.0, 600.0f, 0.7f));

		PrecursorScan rebuilt = scan.rebuild(10, newPeaks);

		double[] masses = rebuilt.getMassArray();
		assertEquals(100.0, masses[0], 1e-9);
		assertEquals(200.0, masses[1], 1e-9);
		assertEquals(300.0, masses[2], 1e-9);
	}

	@Test
	void rebuildPreservesOriginalMetadata() {
		ArrayList<PeakWithIMS> newPeaks = new ArrayList<>();
		newPeaks.add(new PeakWithIMS(150.0, 500.0f, 0.5f));

		PrecursorScan rebuilt = scan.rebuild(10, newPeaks);

		assertEquals("precursor1", rebuilt.getSpectrumName());
		assertEquals(120.5f, rebuilt.getScanStartTime(), 1e-6);
		assertEquals(0, rebuilt.getFraction());
		assertEquals(100.0, rebuilt.getScanWindowLower(), 1e-9);
		assertEquals(1000.0, rebuilt.getScanWindowUpper(), 1e-9);
		assertEquals(50.0f, rebuilt.getIonInjectionTime(), 1e-6);
	}

	@Test
	void rebuildExtractsImsFromPeaks() {
		ArrayList<PeakWithIMS> newPeaks = new ArrayList<>();
		newPeaks.add(new PeakWithIMS(150.0, 500.0f, 0.55f));
		newPeaks.add(new PeakWithIMS(250.0, 750.0f, 0.65f));

		PrecursorScan rebuilt = scan.rebuild(10, newPeaks);

		Optional<float[]> imsArray = rebuilt.getIonMobilityArray();
		assertTrue(imsArray.isPresent());
		// Order should match sorted m/z order
		assertEquals(0.55f, imsArray.get()[0], 1e-6);
		assertEquals(0.65f, imsArray.get()[1], 1e-6);
	}
}
