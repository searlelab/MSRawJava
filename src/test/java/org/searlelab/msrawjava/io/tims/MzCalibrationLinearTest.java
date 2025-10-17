package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class MzCalibrationLinearTest {
	// Pick a realistic window and digitizer size for the test
	private static final int DIGITIZER_NUM_SAMPLES=397888;
	private static final double MZ_LOWER=100.0;
	private static final double MZ_UPPER=1700.0;
	private static final MzCalibrationParams params=null; // can be null for this test

	@Test
	void roundTripRandomMz_stableWithDoubleRepeat_andQuantizationIsHalfStepOrLess() {
		MzCalibrationLinear cal=new MzCalibrationLinear(DIGITIZER_NUM_SAMPLES, MZ_LOWER, MZ_UPPER, params);
		Random rng=new Random(1337);

		for (int n=0; n<10000; n++) {
			// Draw a random m/z within [lower, upper]
			double mz=MZ_LOWER+rng.nextDouble()*(MZ_UPPER-MZ_LOWER);

			// Round-trip once: mz -> tof -> mz
			int tof1=cal.mzToTof(new double[] {mz}, 0.0)[0];
			double mz1=cal.tofToMz(new int[] {tof1}, 0.0)[0];

			// Round-trip again from mz1: mz1 -> tof -> mz
			int tof2=cal.mzToTof(new double[] {mz1}, 0.0)[0];
			double mz2=cal.tofToMz(new int[] {tof2}, 0.0)[0];

			// Stability across the double repeat
			assertEquals(tof1, tof2, "TOF index changed after second inverse mapping");
			assertEquals(mz1, mz2, 1e-12, "m/z changed after second forward mapping");

			// Quantization sanity: deviation from the original mz must be <= half the local step
			double halfStep=localHalfStep(cal, tof1);
			assertTrue(Math.abs(mz1-mz)<=halfStep+1e-12,
					"Quantization exceeded half-step at index="+tof1+" (|mz1 - mz|="+Math.abs(mz1-mz)+", halfStep="+halfStep+")");
		}
	}

	@Test
	void boundaries_areSafe_andClampProperly() {
		MzCalibrationLinear cal=new MzCalibrationLinear(DIGITIZER_NUM_SAMPLES, MZ_LOWER, MZ_UPPER, params);

		// Exact bounds
		int tofLower=cal.mzToTof(new double[] {MZ_LOWER}, 0.0)[0];
		int tofUpper=cal.mzToTof(new double[] {MZ_UPPER}, 0.0)[0];
		assertEquals(0, tofLower, "Lower bound should map to index 0");
		assertEquals(DIGITIZER_NUM_SAMPLES, tofUpper, "Upper bound should map to max index");

		double mzLowerFwd=cal.tofToMz(new int[] {0}, 0.0)[0];
		double mzUpperFwd=cal.tofToMz(new int[] {DIGITIZER_NUM_SAMPLES}, 0.0)[0];
		assertEquals(MZ_LOWER, mzLowerFwd, 1e-12, "Forward at index 0 must equal mzLower");
		assertEquals(MZ_UPPER, mzUpperFwd, 1e-12, "Forward at max index must equal mzUpper");

		// Slightly out of bounds (should clamp)
		double below=MZ_LOWER-1.0;
		double above=MZ_UPPER+1.0;
		int tofBelow=cal.mzToTof(new double[] {below}, 0.0)[0];
		int tofAbove=cal.mzToTof(new double[] {above}, 0.0)[0];
		assertEquals(0, tofBelow, "Below-lower m/z should clamp to index 0");
		assertEquals(DIGITIZER_NUM_SAMPLES, tofAbove, "Above-upper m/z should clamp to max index");

		// Round-trip from clamped ends remains at the ends
		double mzFromBelow=cal.tofToMz(new int[] {tofBelow}, 0.0)[0];
		double mzFromAbove=cal.tofToMz(new int[] {tofAbove}, 0.0)[0];
		assertEquals(MZ_LOWER, mzFromBelow, 1e-12, "Clamped-lower roundtrip should yield mzLower");
		assertEquals(MZ_UPPER, mzFromAbove, 1e-12, "Clamped-upper roundtrip should yield mzUpper");
	}

	@Test
	void determinism_overMultipleRepeats() {
		MzCalibrationLinear cal=new MzCalibrationLinear(DIGITIZER_NUM_SAMPLES, MZ_LOWER, MZ_UPPER, params);
		double[] mzs=new double[] {100.0, 250.5, 777.7, 1699.999, 1700.0};

		// First pass
		int[] tof1=cal.mzToTof(mzs, 0.0);
		double[] mz1=cal.tofToMz(tof1, 0.0);
		// Second pass
		int[] tof2=cal.mzToTof(mz1, 0.0);
		double[] mz2=cal.tofToMz(tof2, 0.0);

		assertArrayEquals(tof1, tof2, "TOF indices changed between repeats");
		for (int i=0; i<mz1.length; i++) {
			assertEquals(mz1[i], mz2[i], 1e-12, "m/z changed between repeats at "+i);
		}
	}

	/**
	 * Half the local m/z step at a given index, i.e., min(Δmz(i to i+1), Δmz(i to i-1)) / 2,
	 * clamped at boundaries. This is the theoretical maximum quantization error after rounding
	 * to the nearest index in this monotonic mapping.
	 */
	private static double localHalfStep(MzCalibrationLinear cal, int idx) {
		double mzAt=cal.tofToMz(new int[] {idx}, 0.0)[0];

		int iPrev=Math.max(0, idx-1);
		int iNext=Math.min(DIGITIZER_NUM_SAMPLES, idx+1);

		double mzPrev=cal.tofToMz(new int[] {iPrev}, 0.0)[0];
		double mzNext=cal.tofToMz(new int[] {iNext}, 0.0)[0];

		double stepLeft=Math.abs(mzAt-mzPrev);
		double stepRight=Math.abs(mzNext-mzAt);
		double step=Math.min(stepLeft, stepRight);

		// If idx is at a boundary, the "half-step" is defined by the only available neighbor
		return step*0.5;
	}

}
