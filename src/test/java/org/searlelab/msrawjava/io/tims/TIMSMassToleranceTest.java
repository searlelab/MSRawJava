package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TIMSMassToleranceTest {

	@Test
	void test() {
		TIMSMassTolerance tolerance=new TIMSMassTolerance();
		assertEquals(0.00208, tolerance.getToleranceInMz(200, 200), 0.0001);
		assertEquals(0.00371, tolerance.getToleranceInMz(400, 400), 0.0001);
		assertEquals(0.00532, tolerance.getToleranceInMz(600, 600), 0.0001);
		assertEquals(0.00693, tolerance.getToleranceInMz(800, 800), 0.0001);
		assertEquals(0.00853, tolerance.getToleranceInMz(1000, 1000), 0.0001);
		assertEquals(0.01031, tolerance.getToleranceInMz(1222, 1222), 0.0001);
		assertEquals(0.01254, tolerance.getToleranceInMz(1500, 1500), 0.0001);
		assertEquals(0.01654, tolerance.getToleranceInMz(2000, 2000), 0.0001);
		assertEquals(0.02454, tolerance.getToleranceInMz(3000, 3000), 0.0001);
	}
}
