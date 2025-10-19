package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TIMSMassToleranceTest {
	public static void main(String[] args) {
		TIMSMassTolerance tolerance=new TIMSMassTolerance(true);
		for (int i=95; i<1705; i=i+5) {
			System.out.println(i+"\t"+1000000*tolerance.getToleranceInMz(i, i)/i);
		}
	}

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

	@Test
	void symmetry_m1_m2_equals_m2_m1_defaultCtor() {
		TIMSMassTolerance tol=new TIMSMassTolerance();
		double a=400.123, b=799.876;
		assertEquals(tol.getToleranceInMz(a, b), tol.getToleranceInMz(b, a), 0.0);
	}

	@Test
	void symmetry_m1_m2_equals_m2_m1_altCtor() {
		// exercise the boolean ctor as present in the class
		TIMSMassTolerance tol=new TIMSMassTolerance(true);
		double a=500.0, b=1500.0;
		assertEquals(tol.getToleranceInMz(a, b), tol.getToleranceInMz(b, a), 0.0);
	}

	// ASSUMPTION DOES NOT WORK!
	//    @Test
	//    void signInvariance_sameToleranceWhenSignsFlip() {
	//        TIMSMassTolerance tol = new TIMSMassTolerance();
	//        double a = 600.0, b = 1200.0;
	//        double t1 = tol.getToleranceInMz(a, b);
	//        double t2 = tol.getToleranceInMz(-a, -b);
	//        assertEquals(t1, t2, 0.0);
	//    }

	@Test
	void monotonic_nonDecreasingWithMassScale() {
		TIMSMassTolerance tol=new TIMSMassTolerance();
		double small=tol.getToleranceInMz(400.0, 400.0);
		double large=tol.getToleranceInMz(1200.0, 1200.0);
		assertTrue(large>=small, "tolerance should not decrease as mass scale increases");
	}

	@Test
	void positivity_and_finiteness() {
		TIMSMassTolerance tol=new TIMSMassTolerance();
		double t=tol.getToleranceInMz(700.0, 701.0);
		assertTrue(t>=0.0&&Double.isFinite(t), "tolerance should be finite and non-negative");
	}
}
