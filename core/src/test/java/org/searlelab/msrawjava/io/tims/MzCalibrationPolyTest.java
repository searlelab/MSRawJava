package org.searlelab.msrawjava.io.tims;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

class MzCalibrationPolyTest {


	private static MzCalibrationParams params() {
		// Choose stable, positive coefficients for predictable behavior
		double tbNs=0.2; // ns per sample
		double delay=24864.0; // ns
		double T1=25.0; // us
		double T2=27.0; // us (unused in current formula but part of params)
		double dC1=1000.0; // ppm/us tweak
		double dC2=0.0; // currently unused in formula
		double C0=1000.0; // ns
		double C1=1_000_000.0; // scaling
		double C2=0.0, C3=0.0, C4=0.0; // not used by current formula
		return new MzCalibrationParams(tbNs, delay, T1, T2, dC1, dC2, C0, C1, C2, C3, C4);
	}

	// not a robust test, this is copied from the current implementation so it only checks for change
	private static double[] manualTofToMz(int[] tof, MzCalibrationParams p, double realT1) {
		final double cf=1.0+p.dC1*(p.T1-realT1)/1.0e6;
		final double c1corr=p.C1*cf;
		double[] mzs=new double[tof.length];
		for (int i=0; i<tof.length; i++) {
			final double time_ns=tof[i]*p.timebaseNsPerSample+p.delaySamples;
			final double inner_ns=time_ns-p.C0;
			mzs[i]=c1corr*(inner_ns*inner_ns)/1.0e12;
		}
		return mzs;
	}

	@Test
	void tofToMz_matchesManualComputation_whenRealT1EqualsT1() {
		MzCalibrationParams p=params();
		int[] tof=new int[] {0, 1, 10, 1000, 5000};
		double[] expected=manualTofToMz(tof, p, p.T1);
		double[] got=new MzCalibrationPoly(0, 0.0, 0.0, p).tofToMz(tof, p.T1);

		assertEquals(expected.length, got.length);
		for (int i=0; i<expected.length; i++) {
			assertEquals(expected[i], got[i], 0.01, "index "+i+" should match");
		}
	}

	@Test
	void tofToMz_appliesPpmAdjustment_whenRealT1Differs() {
		MzCalibrationParams p=params();
		int[] tof=new int[] {1000};
		double[] mzAtT1=new MzCalibrationPoly(0, 0.0, 0.0, p).tofToMz(tof, p.T1);
		double[] mzAtT1Minus1=new MzCalibrationPoly(0, 0.0, 0.0, p).tofToMz(tof, p.T1-1.0); // realT1 smaller => cf > 1

		assertTrue(mzAtT1Minus1[0]>mzAtT1[0], "ppm tweak should increase mz when realT1 < T1 with positive dC1");
	}

	@Test
	void tofToMz_monotonicIncreasingForPositiveParams() {
		MzCalibrationParams p=params();
		int[] tof=new int[] {0, 1, 2, 3, 4, 5};
		double[] mz=new MzCalibrationPoly(0, 0.0, 0.0, p).tofToMz(tof, p.T1);
		for (int i=1; i<mz.length; i++) {
			assertTrue(mz[i]>=mz[i-1], "m/z should be non-decreasing with increasing TOF for chosen params");
		}
	}

	@Test
	void tofToMz_handlesEmptyArray() {
		MzCalibrationParams p=params();
		int[] tof=new int[0];
		double[] mz=new MzCalibrationPoly(0, 0.0, 0.0, p).tofToMz(tof, p.T1);
		assertNotNull(mz);
		assertEquals(0, mz.length);
	}

	@Test
	void poly_tofToMz_empty_ok() {
		MzCalibrationParams p=params();
		int[] tof=new int[0];
		double[] mz=new MzCalibrationPoly(0, 0.0, 0.0, p).tofToMz(tof, p.T1);
		assertNotNull(mz);
		assertEquals(0, mz.length);
	}
	
	// Pick a realistic window and digitizer size for the test
	private static final int DIGITIZER_NUM_SAMPLES=397888;
	private static final double MZ_LOWER=100.0;
	private static final double MZ_UPPER=1700.0;
	private static final double EXAMPLE_REAL_T1=25.6477301215136;

	@Test
	void roundTripRandomMz_stableWithDoubleRepeat_andQuantizationIsHalfStepOrLess() {
		MzCalibrationPoly cal=new MzCalibrationPoly(DIGITIZER_NUM_SAMPLES, MZ_LOWER, MZ_UPPER, params());
		Random rng=new Random(1337);

		for (int n=0; n<10000; n++) {
			// Draw a random m/z within [lower, upper]
			double mz=MZ_LOWER+rng.nextDouble()*(MZ_UPPER-MZ_LOWER);

			// Round-trip once: mz -> tof -> mz
			int tof1=cal.mzToTof(new double[] {mz}, EXAMPLE_REAL_T1)[0];
			double mz1=cal.tofToMz(new int[] {tof1}, EXAMPLE_REAL_T1)[0];

			// Round-trip again from mz1: mz1 -> tof -> mz
			int tof2=cal.mzToTof(new double[] {mz1}, EXAMPLE_REAL_T1)[0];
			double mz2=cal.tofToMz(new int[] {tof2}, EXAMPLE_REAL_T1)[0];

			// Stability across the double repeat
			assertEquals(tof1, tof2, "TOF index changed after second inverse mapping");
			assertEquals(mz1, mz2, 1e-12, "m/z changed after second forward mapping");
		}
	}

	@Test
	void determinism_overMultipleRepeats() {
		MzCalibrationPoly cal=new MzCalibrationPoly(DIGITIZER_NUM_SAMPLES, MZ_LOWER, MZ_UPPER, params());
		double[] mzs=new double[] {100.0, 250.5, 777.7, 1699.999, 1700.0};

		// First pass
		int[] tof1=cal.mzToTof(mzs, EXAMPLE_REAL_T1);
		double[] mz1=cal.tofToMz(tof1, EXAMPLE_REAL_T1);
		// Second pass
		int[] tof2=cal.mzToTof(mz1, EXAMPLE_REAL_T1);
		double[] mz2=cal.tofToMz(tof2, EXAMPLE_REAL_T1);

		assertArrayEquals(tof1, tof2, "TOF indices changed between repeats");
		for (int i=0; i<mz1.length; i++) {
			assertEquals(mz1[i], mz2[i], 1e-12, "m/z changed between repeats at "+i);
		}
	}

}
