package org.searlelab.msrawjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PeakTest {

	@Test
	void constructorAndGetters() {
		Peak peak = new Peak(500.25, 1000.5f);
		assertEquals(500.25, peak.getMz(), 1e-9);
		assertEquals(1000.5f, peak.getIntensity(), 1e-6);
	}

	@Test
	void isAvailableAlwaysReturnsTrue() {
		Peak peak = new Peak(100.0, 50.0f);
		assertTrue(peak.isAvailable());
	}

	@Test
	void turnOffThrowsUnsupportedOperationException() {
		Peak peak = new Peak(100.0, 50.0f);
		assertThrows(UnsupportedOperationException.class, () -> peak.turnOff());
	}

	@Test
	void turnOnThrowsUnsupportedOperationException() {
		Peak peak = new Peak(100.0, 50.0f);
		assertThrows(UnsupportedOperationException.class, () -> peak.turnOn());
	}

	@Test
	void compareToWithNullReturnsPositive() {
		Peak peak = new Peak(100.0, 50.0f);
		assertTrue(peak.compareTo(null) > 0);
	}

	@Test
	void compareToOrdersByMzFirst() {
		Peak low = new Peak(100.0, 500.0f);
		Peak high = new Peak(200.0, 100.0f);
		assertTrue(low.compareTo(high) < 0);
		assertTrue(high.compareTo(low) > 0);
	}

	@Test
	void compareToUsesIntensityWhenMzEqual() {
		Peak lowInt = new Peak(100.0, 50.0f);
		Peak highInt = new Peak(100.0, 100.0f);
		assertTrue(lowInt.compareTo(highInt) < 0);
		assertTrue(highInt.compareTo(lowInt) > 0);
	}

	@Test
	void compareToReturnsZeroForEqualPeaks() {
		Peak peak1 = new Peak(100.0, 50.0f);
		Peak peak2 = new Peak(100.0, 50.0f);
		assertEquals(0, peak1.compareTo(peak2));
	}

	@Test
	void compareToWorksWithOtherPeakInterfaceImplementations() {
		Peak peak = new Peak(100.0, 50.0f);
		PeakWithIMS other = new PeakWithIMS(100.0, 50.0f, 0.8f);
		assertEquals(0, peak.compareTo(other));
	}
}
