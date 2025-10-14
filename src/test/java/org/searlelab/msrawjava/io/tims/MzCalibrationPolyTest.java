package org.searlelab.msrawjava.io.tims;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
		double[] got=MzCalibrationPoly.tofToMz(tof, p, p.T1);

		assertEquals(expected.length, got.length);
		for (int i=0; i<expected.length; i++) {
			assertEquals(expected[i], got[i], 0.01, "index "+i+" should match");
		}
	}

	@Test
	void tofToMz_appliesPpmAdjustment_whenRealT1Differs() {
		MzCalibrationParams p=params();
		int[] tof=new int[] {1000};
		double[] mzAtT1=MzCalibrationPoly.tofToMz(tof, p, p.T1);
		double[] mzAtT1Minus1=MzCalibrationPoly.tofToMz(tof, p, p.T1-1.0); // realT1 smaller => cf > 1

		assertTrue(mzAtT1Minus1[0]>mzAtT1[0], "ppm tweak should increase mz when realT1 < T1 with positive dC1");
	}

	@Test
	void tofToMz_monotonicIncreasingForPositiveParams() {
		MzCalibrationParams p=params();
		int[] tof=new int[] {0, 1, 2, 3, 4, 5};
		double[] mz=MzCalibrationPoly.tofToMz(tof, p, p.T1);
		for (int i=1; i<mz.length; i++) {
			assertTrue(mz[i]>=mz[i-1], "m/z should be non-decreasing with increasing TOF for chosen params");
		}
	}

	@Test
	void tofToMz_handlesEmptyArray() {
		MzCalibrationParams p=params();
		int[] tof=new int[0];
		double[] mz=MzCalibrationPoly.tofToMz(tof, p, p.T1);
		assertNotNull(mz);
		assertEquals(0, mz.length);
	}

	@Test
	void poly_tofToMz_empty_ok() {
		MzCalibrationParams p=params();
		int[] tof=new int[0];
		double[] mz=MzCalibrationPoly.tofToMz(tof, p, p.T1);
		assertNotNull(mz);
		assertEquals(0, mz.length);
	}

}
