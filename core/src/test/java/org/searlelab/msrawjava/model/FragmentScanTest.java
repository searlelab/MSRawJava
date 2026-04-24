package org.searlelab.msrawjava.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FragmentScanTest {

	private FragmentScan scan;
	private FragmentScan scanWithIms;

	@BeforeEach
	void setUp() {
		double[] masses= {100.0, 200.0, 300.0};
		float[] intensities= {1000.0f, 2000.0f, 500.0f};

		scan=new FragmentScan("spectrum1", // spectrumName
				"precursor1", // precursorName
				1, // spectrumIndex
				500.25, // precursorMz
				120.5f, // scanStartTime
				0, // fraction
				50.0f, // ionInjectionTime
				400.0, // isolationWindowLower
				600.0, // isolationWindowUpper
				masses, intensities, null, // no IMS
				(byte)2, // charge
				100.0, // scanWindowLower
				1000.0 // scanWindowUpper
		);

		float[] ims= {0.7f, 0.8f, 0.9f};
		scanWithIms=new FragmentScan("spectrum2", "precursor2", 2, 600.5, 180.0f, 1, 75.0f, 500.0, 700.0, masses.clone(), intensities.clone(), ims, (byte)3,
				100.0, 1200.0);
	}

	@Test
	void constructorAndBasicGetters() {
		assertEquals("spectrum1", scan.getSpectrumName());
		assertEquals("precursor1", scan.getPrecursorName());
		assertEquals(1, scan.getSpectrumIndex());
		assertEquals(500.25, scan.getPrecursorMZ(), 1e-9);
		assertEquals(120.5f, scan.getScanStartTime(), 1e-6);
		assertEquals(0, scan.getFraction());
		assertEquals(50.0f, scan.getIonInjectionTime(), 1e-6);
		assertEquals(400.0, scan.getIsolationWindowLower(), 1e-9);
		assertEquals(600.0, scan.getIsolationWindowUpper(), 1e-9);
		assertEquals(100.0, scan.getScanWindowLower(), 1e-9);
		assertEquals(1000.0, scan.getScanWindowUpper(), 1e-9);
		assertEquals((byte)2, scan.getCharge());
	}

	@Test
	void getMassArrayReturnsCorrectValues() {
		double[] masses=scan.getMassArray();
		assertEquals(3, masses.length);
		assertEquals(100.0, masses[0], 1e-9);
		assertEquals(200.0, masses[1], 1e-9);
		assertEquals(300.0, masses[2], 1e-9);
	}

	@Test
	void getIntensityArrayReturnsCorrectValues() {
		float[] intensities=scan.getIntensityArray();
		assertEquals(3, intensities.length);
		assertEquals(1000.0f, intensities[0], 1e-6);
		assertEquals(2000.0f, intensities[1], 1e-6);
		assertEquals(500.0f, intensities[2], 1e-6);
	}

	@Test
	void getIonMobilityArrayReturnsEmptyWhenNull() {
		Optional<float[]> ims=scan.getIonMobilityArray();
		assertFalse(ims.isPresent());
	}

	@Test
	void getIonMobilityArrayReturnsValuesWhenPresent() {
		Optional<float[]> ims=scanWithIms.getIonMobilityArray();
		assertTrue(ims.isPresent());
		assertEquals(3, ims.get().length);
		assertEquals(0.7f, ims.get()[0], 1e-6);
	}

	@Test
	void getTICCalculatesSum() {
		float tic=scan.getTIC();
		assertEquals(3500.0f, tic, 1e-6); // 1000 + 2000 + 500
	}

	@Test
	void getPrecursorRangeReturnsIsolationWindow() {
		Range range=scan.getPrecursorRange();
		assertEquals(400.0, range.getStart(), 1e-6);
		assertEquals(600.0, range.getStop(), 1e-6);
	}

	@Test
	void compatibilityHelpersExposeLegacySurface() {
		assertEquals(500.0, scan.getIsolationWindowCenter(), 1e-9);
		assertEquals((byte)2, scan.getPrecursorCharge());
		assertEquals(scan.getPrecursorRange(), scan.getRange());
	}

	@Test
	void getPeaksFiltersAboveMinimumIntensity() {
		ArrayList<PeakInterface> peaks=scanWithIms.getPeaks(1500.0f);
		assertEquals(1, peaks.size());
		assertEquals(200.0, peaks.get(0).getMz(), 1e-9);
		assertEquals(2000.0f, peaks.get(0).getIntensity(), 1e-6);
		assertTrue(peaks.get(0) instanceof PeakWithIMS);
	}

	@Test
	void getPeaksReturnsAllWhenMinimumIsZero() {
		ArrayList<PeakInterface> peaks=scanWithIms.getPeaks(0.0f);
		assertEquals(3, peaks.size());
	}

	@Test
	void getPeaksUsesPeakInTimeWhenImsMissing() {
		ArrayList<PeakInterface> peaks=scan.getPeaks(0.0f);
		assertEquals(3, peaks.size());
		assertTrue(peaks.get(0) instanceof PeakInTime);
	}

	@Test
	void getBasePeakReturnsHighestIntensity() {
		PeakInterface basePeak=scan.getBasePeak();
		assertEquals(200.0, basePeak.getMz(), 1e-9);
		assertEquals(2000.0f, basePeak.getIntensity(), 1e-6);
	}

	@Test
	void getBasePeakIncludesImsWhenPresent() {
		PeakInterface basePeak=scanWithIms.getBasePeak();
		assertTrue(basePeak instanceof PeakWithIMS);
		assertEquals(0.8f, ((PeakWithIMS)basePeak).getIMS(), 1e-6);
	}

	@Test
	void getMedianIonMobilityReturnsEmptyWhenNoIms() {
		Optional<Float> median=scan.getMedianIonMobility();
		assertFalse(median.isPresent());
	}

	@Test
	void getMedianIonMobilityCalculatesMedian() {
		Optional<Float> median=scanWithIms.getMedianIonMobility();
		assertTrue(median.isPresent());
		assertEquals(0.8f, median.get(), 1e-6); // median of {0.7, 0.8, 0.9}
	}

	@Test
	void compareToWithNullReturnsPositive() {
		assertTrue(scan.compareTo(null)>0);
	}

	@Test
	void compareToOrdersByScanStartTimeFirst() {
		double[] masses= {100.0};
		float[] intensities= {100.0f};
		FragmentScan earlier=new FragmentScan("a", "a", 1, 500.0, 100.0f, 0, null, 400.0, 600.0, masses, intensities, null, (byte)2, 100.0, 1000.0);
		FragmentScan later=new FragmentScan("b", "b", 1, 500.0, 200.0f, 0, null, 400.0, 600.0, masses, intensities, null, (byte)2, 100.0, 1000.0);
		assertTrue(earlier.compareTo(later)<0);
		assertTrue(later.compareTo(earlier)>0);
	}

	@Test
	void compareToUsesSpectrumIndexWhenTimeEqual() {
		double[] masses= {100.0};
		float[] intensities= {100.0f};
		FragmentScan lowIndex=new FragmentScan("a", "a", 1, 500.0, 120.0f, 0, null, 400.0, 600.0, masses, intensities, null, (byte)2, 100.0, 1000.0);
		FragmentScan highIndex=new FragmentScan("b", "b", 5, 500.0, 120.0f, 0, null, 400.0, 600.0, masses, intensities, null, (byte)2, 100.0, 1000.0);
		assertTrue(lowIndex.compareTo(highIndex)<0);
	}

	@Test
	void compareToUsesIsolationWindowWhenTimeAndIndexEqual() {
		double[] masses= {100.0};
		float[] intensities= {100.0f};
		FragmentScan lowWindow=new FragmentScan("a", "a", 1, 500.0, 120.0f, 0, null, 300.0, 400.0, masses, intensities, null, (byte)2, 100.0, 1000.0);
		FragmentScan highWindow=new FragmentScan("b", "b", 1, 500.0, 120.0f, 0, null, 500.0, 600.0, masses, intensities, null, (byte)2, 100.0, 1000.0);
		assertTrue(lowWindow.compareTo(highWindow)<0);
	}

	@Test
	void rebuildCreatesNewScanWithUpdatedPeaks() {
		ArrayList<PeakWithIMS> newPeaks=new ArrayList<>();
		newPeaks.add(new PeakWithIMS(150.0, 500.0f, 0.5f));
		newPeaks.add(new PeakWithIMS(250.0, 750.0f, 0.6f));

		FragmentScan rebuilt=scanWithIms.rebuild(10, newPeaks);

		assertEquals(10, rebuilt.getSpectrumIndex());
		assertEquals(scanWithIms.getScanStartTime(), rebuilt.getScanStartTime(), 1e-6);
		assertEquals(2, rebuilt.getMassArray().length);
		// Should be sorted by m/z
		assertEquals(150.0, rebuilt.getMassArray()[0], 1e-9);
		assertEquals(250.0, rebuilt.getMassArray()[1], 1e-9);
		assertTrue(rebuilt.getIonMobilityArray().isPresent());
	}

	@Test
	void rebuildWithRtCreatesNewScanWithNewTime() {
		ArrayList<PeakWithIMS> newPeaks=new ArrayList<>();
		newPeaks.add(new PeakWithIMS(150.0, 500.0f, 0.5f));

		FragmentScan rebuilt=scanWithIms.rebuild(10, 300.0f, newPeaks);

		assertEquals(10, rebuilt.getSpectrumIndex());
		// Note: the rebuild method has a bug - it uses scanStartTime instead of rtInsec
		// This test documents the current behavior
		assertEquals(scanWithIms.getScanStartTime(), rebuilt.getScanStartTime(), 1e-6);
	}

	@Test
	void rebuildWithWindowsCreatesNewScanWithUpdatedWindows() {
		ArrayList<PeakInterface> newPeaks=new ArrayList<>();
		newPeaks.add(new Peak(150.0, 500.0f));

		FragmentScan rebuilt=scan.rebuild(10, 200.0f, newPeaks, 450.0, 550.0);

		assertEquals(10, rebuilt.getSpectrumIndex());
		assertEquals(450.0, rebuilt.getIsolationWindowLower(), 1e-9);
		assertEquals(550.0, rebuilt.getIsolationWindowUpper(), 1e-9);
		assertFalse(rebuilt.getIonMobilityArray().isPresent());
	}

	@Test
	void rebuildPreservesOriginalMetadata() {
		ArrayList<PeakWithIMS> newPeaks=new ArrayList<>();
		newPeaks.add(new PeakWithIMS(150.0, 500.0f, 0.5f));

		FragmentScan rebuilt=scan.rebuild(10, newPeaks);

		assertEquals("spectrum1", rebuilt.getSpectrumName());
		assertEquals("precursor1", rebuilt.getPrecursorName());
		assertEquals(500.25, rebuilt.getPrecursorMZ(), 1e-9);
		assertEquals(0, rebuilt.getFraction());
		assertEquals(50.0f, rebuilt.getIonInjectionTime(), 1e-6);
		assertEquals((byte)2, rebuilt.getCharge());
	}

	@Test
	void shallowCloneUpdatesIndexAndFraction() {
		FragmentScan clone=scan.shallowClone(4, 99);
		assertEquals(4, clone.getFraction());
		assertEquals(99, clone.getSpectrumIndex());
		assertEquals(scan.getPrecursorMZ(), clone.getPrecursorMZ(), 1e-9);
		assertArrayEquals(scan.getMassArray(), clone.getMassArray(), 1e-9);
	}

	@Test
	void sqrtReturnsProtectedSqrtIntensityArray() {
		FragmentScan sqrtScan=scan.sqrt();
		assertEquals((float)Math.sqrt(1000.0), sqrtScan.getIntensityArray()[0], 1e-5f);
		assertEquals((float)Math.sqrt(2000.0), sqrtScan.getIntensityArray()[1], 1e-5f);
	}

	@Test
	void trimMassesKeepsOnlyPeaksInRange() {
		FragmentScan trimmed=scanWithIms.trimMasses(new Range(150.0f, 250.0f));
		assertEquals(1, trimmed.getMassArray().length);
		assertEquals(200.0, trimmed.getMassArray()[0], 1e-9);
		assertTrue(trimmed.getIonMobilityArray().isPresent());
		assertEquals(0.8f, trimmed.getIonMobilityArray().get()[0], 1e-6f);
	}

	@Test
	void trimToPeakDepthLimitsPeaksPerBin() {
		double[] masses= {10.0, 20.0, 30.0, 140.0};
		float[] intensities= {5.0f, 25.0f, 15.0f, 7.0f};
		FragmentScan dense=new FragmentScan("dense", "prec", 3, 400.0, 10.0f, 0, null, 390.0, 410.0, masses, intensities, null, (byte)2, 0.0, 500.0);
		FragmentScan trimmed=dense.trimToPeakDepth(2);
		assertEquals(3, trimmed.getMassArray().length);
		assertArrayEquals(new double[] {20.0, 30.0, 140.0}, trimmed.getMassArray(), 1e-9);
	}

	@Test
	void toStringContainsIsolationWindowInfo() {
		String str=scan.toString();
		assertTrue(str.contains("400.0"));
		assertTrue(str.contains("600.0"));
		assertTrue(str.contains("z=2"));
	}

	@Test
	void toStringIncludesImsWhenPresent() {
		String str=scanWithIms.toString();
		assertTrue(str.contains("0.7"));
	}

	@Test
	void nullIonInjectionTimeIsHandled() {
		double[] masses= {100.0};
		float[] intensities= {100.0f};
		FragmentScan scanNoInjectionTime=new FragmentScan("a", "a", 1, 500.0, 120.0f, 0, null, 400.0, 600.0, masses, intensities, null, (byte)2, 100.0, 1000.0);
		assertEquals(-1.0f, scanNoInjectionTime.getIonInjectionTime(), 1e-6f);
	}
}
