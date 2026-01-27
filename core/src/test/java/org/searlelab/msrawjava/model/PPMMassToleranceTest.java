package org.searlelab.msrawjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PPMMassToleranceTest {

	@Test
	void returnsConfiguredPpm() {
		PPMMassTolerance tol=new PPMMassTolerance(20.0);
		assertEquals(20.0, tol.getPpmTolerance(), 0.0);
	}

	@Test
	void formula_matchesMaxMassTimesPercent() {
		PPMMassTolerance tol=new PPMMassTolerance(10.0); // 10 ppm
		double m1=500.0, m2=800.0;
		double expected=Math.max(Math.abs(m1), Math.abs(m2))*(10.0/1_000_000.0);
		assertEquals(expected, tol.getToleranceInMz(m1, m2), 1e-12);
	}

	@Test
	void symmetry_and_signInvariance() {
		PPMMassTolerance tol=new PPMMassTolerance(15.0);
		double a=300.0, b=900.0;
		assertEquals(tol.getToleranceInMz(a, b), tol.getToleranceInMz(b, a), 0.0);
		assertEquals(tol.getToleranceInMz(a, b), tol.getToleranceInMz(-a, -b), 0.0);
	}

	@Test
	void monotonic_increasingWithMass() {
		PPMMassTolerance tol=new PPMMassTolerance(5.0);
		double small=tol.getToleranceInMz(200.0, 200.0);
		double large=tol.getToleranceInMz(1000.0, 1000.0);
		assertTrue(large>small);
	}

	@Test
	void signInvariance_sameToleranceWhenSignsFlip() {
		PPMMassTolerance tol=new PPMMassTolerance(15.0);
		double a=600.0, b=1200.0;
		double t1=tol.getToleranceInMz(a, b);
		double t2=tol.getToleranceInMz(-a, -b);
		assertEquals(t1, t2, 0.0);
	}
}
