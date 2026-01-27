package org.searlelab.msrawjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class PeakInTimeTest {

	@Test
	void constructorAndGetters() {
		PeakInTime peak=new PeakInTime(500.25, 1000.5f, 120.5f);
		assertEquals(500.25, peak.getMz(), 1e-9);
		assertEquals(1000.5f, peak.getIntensity(), 1e-6);
		assertEquals(120.5f, peak.getRtInSec(), 1e-6);
	}

	@Test
	void publicFieldsAccessible() {
		PeakInTime peak=new PeakInTime(500.25, 1000.5f, 120.5f);
		assertEquals(500.25, peak.mz, 1e-9);
		assertEquals(1000.5f, peak.intensity, 1e-6);
		assertEquals(120.5f, peak.rtInSec, 1e-6);
	}

	@Test
	void staticComparatorsExist() {
		assertNotNull(PeakInTime.INTENSITY_COMPARATOR);
		assertNotNull(PeakInTime.RT_COMPARATOR);
	}

	@Test
	void toggleDefaultsToAvailable() {
		PeakInTime peak=new PeakInTime(100.0, 50.0f, 60.0f);
		assertTrue(peak.isAvailable());
	}

	@Test
	void turnOffMakesPeakUnavailable() {
		PeakInTime peak=new PeakInTime(100.0, 50.0f, 60.0f);
		peak.turnOff();
		assertFalse(peak.isAvailable());
	}

	@Test
	void turnOnRestoresAvailability() {
		PeakInTime peak=new PeakInTime(100.0, 50.0f, 60.0f);
		peak.turnOff();
		peak.turnOn();
		assertTrue(peak.isAvailable());
	}

	@Test
	void toStringContainsMzRtAndIntensity() {
		PeakInTime peak=new PeakInTime(500.25, 1000.5f, 120.5f);
		String str=peak.toString();
		assertTrue(str.contains("mz=500.25"));
		assertTrue(str.contains("rt=120.5"));
		assertTrue(str.contains("int=1000.5"));
	}

	@Test
	void compareToWithNullReturnsPositive() {
		PeakInTime peak=new PeakInTime(100.0, 50.0f, 60.0f);
		assertTrue(peak.compareTo(null)>0);
	}

	@Test
	void compareToOrdersByMzFirst() {
		PeakInTime low=new PeakInTime(100.0, 500.0f, 60.0f);
		PeakInTime high=new PeakInTime(200.0, 100.0f, 60.0f);
		assertTrue(low.compareTo(high)<0);
		assertTrue(high.compareTo(low)>0);
	}

	@Test
	void compareToUsesRtWhenMzEqual() {
		PeakInTime lowRt=new PeakInTime(100.0, 50.0f, 30.0f);
		PeakInTime highRt=new PeakInTime(100.0, 50.0f, 90.0f);
		assertTrue(lowRt.compareTo(highRt)<0);
		assertTrue(highRt.compareTo(lowRt)>0);
	}

	@Test
	void compareToUsesIntensityWhenMzAndRtEqual() {
		PeakInTime lowInt=new PeakInTime(100.0, 50.0f, 60.0f);
		PeakInTime highInt=new PeakInTime(100.0, 100.0f, 60.0f);
		assertTrue(lowInt.compareTo(highInt)<0);
		assertTrue(highInt.compareTo(lowInt)>0);
	}

	@Test
	void compareToReturnsZeroForEqualPeaks() {
		PeakInTime peak1=new PeakInTime(100.0, 50.0f, 60.0f);
		PeakInTime peak2=new PeakInTime(100.0, 50.0f, 60.0f);
		assertEquals(0, peak1.compareTo(peak2));
	}

	@Test
	void compareToWithPlainPeakIgnoresRt() {
		PeakInTime withRt=new PeakInTime(100.0, 50.0f, 60.0f);
		Peak plain=new Peak(100.0, 50.0f);
		assertEquals(0, withRt.compareTo(plain));
	}

	// PeakIntensityComparator tests
	@Test
	void intensityComparatorOrdersByIntensityFirst() {
		PeakInTime.PeakIntensityComparator cmp=new PeakInTime.PeakIntensityComparator();
		PeakInTime lowInt=new PeakInTime(200.0, 50.0f, 60.0f);
		PeakInTime highInt=new PeakInTime(100.0, 100.0f, 60.0f);
		assertTrue(cmp.compare(lowInt, highInt)<0);
		assertTrue(cmp.compare(highInt, lowInt)>0);
	}

	@Test
	void intensityComparatorUsesMzWhenIntensityEqual() {
		PeakInTime.PeakIntensityComparator cmp=new PeakInTime.PeakIntensityComparator();
		PeakInTime lowMz=new PeakInTime(100.0, 50.0f, 60.0f);
		PeakInTime highMz=new PeakInTime(200.0, 50.0f, 60.0f);
		assertTrue(cmp.compare(lowMz, highMz)<0);
	}

	@Test
	void intensityComparatorUsesRtAsLastTiebreaker() {
		PeakInTime.PeakIntensityComparator cmp=new PeakInTime.PeakIntensityComparator();
		PeakInTime lowRt=new PeakInTime(100.0, 50.0f, 30.0f);
		PeakInTime highRt=new PeakInTime(100.0, 50.0f, 90.0f);
		assertTrue(cmp.compare(lowRt, highRt)<0);
	}

	@Test
	void intensityComparatorHandlesSameInstance() {
		PeakInTime.PeakIntensityComparator cmp=new PeakInTime.PeakIntensityComparator();
		PeakInTime peak=new PeakInTime(100.0, 50.0f, 60.0f);
		assertEquals(0, cmp.compare(peak, peak));
	}

	@Test
	void intensityComparatorHandlesNulls() {
		PeakInTime.PeakIntensityComparator cmp=new PeakInTime.PeakIntensityComparator();
		PeakInTime peak=new PeakInTime(100.0, 50.0f, 60.0f);
		assertTrue(cmp.compare(null, peak)<0);
		assertTrue(cmp.compare(peak, null)>0);
	}

	// PeakRTComparator tests
	@Test
	void rtComparatorOrdersByRtFirst() {
		PeakInTime.PeakRTComparator cmp=new PeakInTime.PeakRTComparator();
		PeakInTime lowRt=new PeakInTime(200.0, 100.0f, 30.0f);
		PeakInTime highRt=new PeakInTime(100.0, 50.0f, 90.0f);
		assertTrue(cmp.compare(lowRt, highRt)<0);
		assertTrue(cmp.compare(highRt, lowRt)>0);
	}

	@Test
	void rtComparatorUsesIntensityWhenRtEqual() {
		PeakInTime.PeakRTComparator cmp=new PeakInTime.PeakRTComparator();
		PeakInTime lowInt=new PeakInTime(100.0, 50.0f, 60.0f);
		PeakInTime highInt=new PeakInTime(100.0, 100.0f, 60.0f);
		assertTrue(cmp.compare(lowInt, highInt)<0);
	}

	@Test
	void rtComparatorUsesMzAsLastTiebreaker() {
		PeakInTime.PeakRTComparator cmp=new PeakInTime.PeakRTComparator();
		PeakInTime lowMz=new PeakInTime(100.0, 50.0f, 60.0f);
		PeakInTime highMz=new PeakInTime(200.0, 50.0f, 60.0f);
		assertTrue(cmp.compare(lowMz, highMz)<0);
	}

	@Test
	void rtComparatorHandlesSameInstance() {
		PeakInTime.PeakRTComparator cmp=new PeakInTime.PeakRTComparator();
		PeakInTime peak=new PeakInTime(100.0, 50.0f, 60.0f);
		assertEquals(0, cmp.compare(peak, peak));
	}

	@Test
	void rtComparatorHandlesNulls() {
		PeakInTime.PeakRTComparator cmp=new PeakInTime.PeakRTComparator();
		PeakInTime peak=new PeakInTime(100.0, 50.0f, 60.0f);
		assertTrue(cmp.compare(null, peak)<0);
		assertTrue(cmp.compare(peak, null)>0);
	}

	@Test
	void sortingWithComparators() {
		List<PeakInTime> peaks=new ArrayList<>();
		peaks.add(new PeakInTime(300.0, 100.0f, 60.0f));
		peaks.add(new PeakInTime(100.0, 50.0f, 90.0f));
		peaks.add(new PeakInTime(200.0, 200.0f, 30.0f));

		// Sort by intensity using static comparator
		List<PeakInTime> byIntensity=new ArrayList<>(peaks);
		Collections.sort(byIntensity, PeakInTime.INTENSITY_COMPARATOR);
		assertEquals(50.0f, byIntensity.get(0).intensity, 1e-6);
		assertEquals(100.0f, byIntensity.get(1).intensity, 1e-6);
		assertEquals(200.0f, byIntensity.get(2).intensity, 1e-6);

		// Sort by RT using static comparator
		List<PeakInTime> byRt=new ArrayList<>(peaks);
		Collections.sort(byRt, PeakInTime.RT_COMPARATOR);
		assertEquals(30.0f, byRt.get(0).rtInSec, 1e-6);
		assertEquals(60.0f, byRt.get(1).rtInSec, 1e-6);
		assertEquals(90.0f, byRt.get(2).rtInSec, 1e-6);
	}
}
