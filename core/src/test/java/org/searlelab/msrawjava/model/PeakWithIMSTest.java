package org.searlelab.msrawjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class PeakWithIMSTest {

	@Test
	void constructorAndGetters() {
		PeakWithIMS peak = new PeakWithIMS(500.25, 1000.5f, 0.85f);
		assertEquals(500.25, peak.getMz(), 1e-9);
		assertEquals(1000.5f, peak.getIntensity(), 1e-6);
		assertEquals(0.85f, peak.getIMS(), 1e-6);
	}

	@Test
	void publicFieldsAccessible() {
		PeakWithIMS peak = new PeakWithIMS(500.25, 1000.5f, 0.85f);
		assertEquals(500.25, peak.mz, 1e-9);
		assertEquals(1000.5f, peak.intensity, 1e-6);
		assertEquals(0.85f, peak.ims, 1e-6);
	}

	@Test
	void toggleDefaultsToAvailable() {
		PeakWithIMS peak = new PeakWithIMS(100.0, 50.0f, 0.5f);
		assertTrue(peak.isAvailable());
	}

	@Test
	void turnOffMakesPeakUnavailable() {
		PeakWithIMS peak = new PeakWithIMS(100.0, 50.0f, 0.5f);
		peak.turnOff();
		assertFalse(peak.isAvailable());
	}

	@Test
	void turnOnRestoresAvailability() {
		PeakWithIMS peak = new PeakWithIMS(100.0, 50.0f, 0.5f);
		peak.turnOff();
		peak.turnOn();
		assertTrue(peak.isAvailable());
	}

	@Test
	void toStringContainsMzImsAndIntensity() {
		PeakWithIMS peak = new PeakWithIMS(500.25, 1000.5f, 0.85f);
		String str = peak.toString();
		assertTrue(str.contains("mz=500.25"));
		assertTrue(str.contains("ims=0.85"));
		assertTrue(str.contains("int=1000.5"));
	}

	@Test
	void compareToWithNullReturnsPositive() {
		PeakWithIMS peak = new PeakWithIMS(100.0, 50.0f, 0.5f);
		assertTrue(peak.compareTo(null) > 0);
	}

	@Test
	void compareToOrdersByMzFirst() {
		PeakWithIMS low = new PeakWithIMS(100.0, 500.0f, 0.5f);
		PeakWithIMS high = new PeakWithIMS(200.0, 100.0f, 0.5f);
		assertTrue(low.compareTo(high) < 0);
		assertTrue(high.compareTo(low) > 0);
	}

	@Test
	void compareToUsesImsWhenMzEqual() {
		PeakWithIMS lowIms = new PeakWithIMS(100.0, 50.0f, 0.3f);
		PeakWithIMS highIms = new PeakWithIMS(100.0, 50.0f, 0.8f);
		assertTrue(lowIms.compareTo(highIms) < 0);
		assertTrue(highIms.compareTo(lowIms) > 0);
	}

	@Test
	void compareToUsesIntensityWhenMzAndImsEqual() {
		PeakWithIMS lowInt = new PeakWithIMS(100.0, 50.0f, 0.5f);
		PeakWithIMS highInt = new PeakWithIMS(100.0, 100.0f, 0.5f);
		assertTrue(lowInt.compareTo(highInt) < 0);
		assertTrue(highInt.compareTo(lowInt) > 0);
	}

	@Test
	void compareToReturnsZeroForEqualPeaks() {
		PeakWithIMS peak1 = new PeakWithIMS(100.0, 50.0f, 0.5f);
		PeakWithIMS peak2 = new PeakWithIMS(100.0, 50.0f, 0.5f);
		assertEquals(0, peak1.compareTo(peak2));
	}

	@Test
	void compareToWithPlainPeakIgnoresIms() {
		PeakWithIMS withIms = new PeakWithIMS(100.0, 50.0f, 0.5f);
		Peak plain = new Peak(100.0, 50.0f);
		assertEquals(0, withIms.compareTo(plain));
	}

	// PeakIntensityComparator tests
	@Test
	void intensityComparatorOrdersByIntensityFirst() {
		PeakWithIMS.PeakIntensityComparator cmp = new PeakWithIMS.PeakIntensityComparator();
		PeakWithIMS lowInt = new PeakWithIMS(200.0, 50.0f, 0.5f);
		PeakWithIMS highInt = new PeakWithIMS(100.0, 100.0f, 0.5f);
		assertTrue(cmp.compare(lowInt, highInt) < 0);
		assertTrue(cmp.compare(highInt, lowInt) > 0);
	}

	@Test
	void intensityComparatorUsesMzWhenIntensityEqual() {
		PeakWithIMS.PeakIntensityComparator cmp = new PeakWithIMS.PeakIntensityComparator();
		PeakWithIMS lowMz = new PeakWithIMS(100.0, 50.0f, 0.5f);
		PeakWithIMS highMz = new PeakWithIMS(200.0, 50.0f, 0.5f);
		assertTrue(cmp.compare(lowMz, highMz) < 0);
	}

	@Test
	void intensityComparatorUsesImsAsLastTiebreaker() {
		PeakWithIMS.PeakIntensityComparator cmp = new PeakWithIMS.PeakIntensityComparator();
		PeakWithIMS lowIms = new PeakWithIMS(100.0, 50.0f, 0.3f);
		PeakWithIMS highIms = new PeakWithIMS(100.0, 50.0f, 0.8f);
		assertTrue(cmp.compare(lowIms, highIms) < 0);
	}

	@Test
	void intensityComparatorHandlesSameInstance() {
		PeakWithIMS.PeakIntensityComparator cmp = new PeakWithIMS.PeakIntensityComparator();
		PeakWithIMS peak = new PeakWithIMS(100.0, 50.0f, 0.5f);
		assertEquals(0, cmp.compare(peak, peak));
	}

	@Test
	void intensityComparatorHandlesNulls() {
		PeakWithIMS.PeakIntensityComparator cmp = new PeakWithIMS.PeakIntensityComparator();
		PeakWithIMS peak = new PeakWithIMS(100.0, 50.0f, 0.5f);
		assertTrue(cmp.compare(null, peak) < 0);
		assertTrue(cmp.compare(peak, null) > 0);
	}

	// PeakIMSComparator tests
	@Test
	void imsComparatorOrdersByImsFirst() {
		PeakWithIMS.PeakIMSComparator cmp = new PeakWithIMS.PeakIMSComparator();
		PeakWithIMS lowIms = new PeakWithIMS(200.0, 100.0f, 0.3f);
		PeakWithIMS highIms = new PeakWithIMS(100.0, 50.0f, 0.8f);
		assertTrue(cmp.compare(lowIms, highIms) < 0);
		assertTrue(cmp.compare(highIms, lowIms) > 0);
	}

	@Test
	void imsComparatorUsesIntensityWhenImsEqual() {
		PeakWithIMS.PeakIMSComparator cmp = new PeakWithIMS.PeakIMSComparator();
		PeakWithIMS lowInt = new PeakWithIMS(100.0, 50.0f, 0.5f);
		PeakWithIMS highInt = new PeakWithIMS(100.0, 100.0f, 0.5f);
		assertTrue(cmp.compare(lowInt, highInt) < 0);
	}

	@Test
	void imsComparatorUsesMzAsLastTiebreaker() {
		PeakWithIMS.PeakIMSComparator cmp = new PeakWithIMS.PeakIMSComparator();
		PeakWithIMS lowMz = new PeakWithIMS(100.0, 50.0f, 0.5f);
		PeakWithIMS highMz = new PeakWithIMS(200.0, 50.0f, 0.5f);
		assertTrue(cmp.compare(lowMz, highMz) < 0);
	}

	@Test
	void imsComparatorHandlesSameInstance() {
		PeakWithIMS.PeakIMSComparator cmp = new PeakWithIMS.PeakIMSComparator();
		PeakWithIMS peak = new PeakWithIMS(100.0, 50.0f, 0.5f);
		assertEquals(0, cmp.compare(peak, peak));
	}

	@Test
	void imsComparatorHandlesNulls() {
		PeakWithIMS.PeakIMSComparator cmp = new PeakWithIMS.PeakIMSComparator();
		PeakWithIMS peak = new PeakWithIMS(100.0, 50.0f, 0.5f);
		assertTrue(cmp.compare(null, peak) < 0);
		assertTrue(cmp.compare(peak, null) > 0);
	}

	@Test
	void sortingWithComparators() {
		List<PeakWithIMS> peaks = new ArrayList<>();
		peaks.add(new PeakWithIMS(300.0, 100.0f, 0.5f));
		peaks.add(new PeakWithIMS(100.0, 50.0f, 0.8f));
		peaks.add(new PeakWithIMS(200.0, 200.0f, 0.3f));

		// Sort by intensity
		List<PeakWithIMS> byIntensity = new ArrayList<>(peaks);
		Collections.sort(byIntensity, new PeakWithIMS.PeakIntensityComparator());
		assertEquals(50.0f, byIntensity.get(0).intensity, 1e-6);
		assertEquals(100.0f, byIntensity.get(1).intensity, 1e-6);
		assertEquals(200.0f, byIntensity.get(2).intensity, 1e-6);

		// Sort by IMS
		List<PeakWithIMS> byIms = new ArrayList<>(peaks);
		Collections.sort(byIms, new PeakWithIMS.PeakIMSComparator());
		assertEquals(0.3f, byIms.get(0).ims, 1e-6);
		assertEquals(0.5f, byIms.get(1).ims, 1e-6);
		assertEquals(0.8f, byIms.get(2).ims, 1e-6);
	}
}
