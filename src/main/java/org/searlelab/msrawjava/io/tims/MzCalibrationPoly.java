package org.searlelab.msrawjava.io.tims;

import java.util.Arrays;

/**
 * Polynomial time→sqrt(m/z) with piecewise slope bumps at T1/T2.
 * Units: timebase in ns/sample; delay in samples; T1/T2 in microseconds.
 */
public final class MzCalibrationPoly {
	/** Convert a TOF sample index to m/z using the polynomial. */
	public static double tofToMz(int tof, MzCalibrationParams p) {
		// bin-center time in microseconds
		final double t_us=((tof+0.5)+p.delaySamples)*(p.timebaseNsPerSample);
		// piecewise slope bumps at T1/T2 (additive)
		double c1eff=p.C1;
		if (t_us>=p.T1_us) c1eff+=p.dC1;
		if (t_us>=p.T2_us) c1eff+=p.dC2;

		final double r=p.C0+c1eff*t_us+p.C2*t_us*t_us+p.C3*t_us*t_us*t_us+p.C4*t_us*t_us*t_us*t_us;
		return r*r; // r = sqrt(m/z)
	}

	/** Vectorized: compute m/z from TOF array. */
	public static double[] tofToMz(int[] tof, MzCalibrationParams p) {
		final double[] out=new double[tof.length];
		for (int i=0; i<tof.length; i++) {
			out[i]=tofToMz(tof[i], p);
			System.out.println(tof[i]+"\t"+out[i]);
		}
		return out;
	}

	public static double[] tofToMzAnchored(int[] tof, MzCalibrationParams p, double lowerMz, double upperMz, long tMaxSamples) {
		if (tof==null) return new double[0];
		final int n=tof.length;
		final double[] out=new double[n];

		// Anchor times at the ends of the acquisition window:
		final long tMinSamples=0L; // first ADC bin (after delay)
		final long tMaxSamp=(tMaxSamples>0)?tMaxSamples:Arrays.stream(tof).max().orElse(0);

		final double t0=tofToSeconds((int)tMinSamples, p); // seconds
		final double t1=tofToSeconds((int)tMaxSamp, p);

		// Raw √mz at endpoints (vendor-ish units, unscaled)
		final double r0_raw=rawRofTime(t0, p);
		final double r1_raw=rawRofTime(t1, p);

		// Desired √mz endpoints from TDF:
		final double r0=Math.sqrt(lowerMz);
		final double r1=Math.sqrt(upperMz);

		// Affine normalization:  r = α + β * r_raw,  pin endpoints exactly.
		final double denom=(r1_raw-r0_raw);
		final double beta=(denom!=0.0)?(r1-r0)/denom:0.0;
		final double alpha=r0-beta*r0_raw;

		// Now evaluate per peak
		for (int i=0; i<n; i++) {
			final double t_s=tofToSeconds(tof[i], p);
			final double r_raw=rawRofTime(t_s, p);
			final double r_cal=alpha+beta*r_raw; // pinned to bounds; bows in the middle
			out[i]=r_cal*r_cal;
		}
		return out;
	}

	private static double tofToSeconds(int tof, MzCalibrationParams p) {
		final double tb_s=p.timebaseNsPerSample*1e-9; // ns → s
		return ((tof+0.5)+p.delaySamples)*tb_s;
	}

	/**
	 * Vendor-like raw model for r = sqrt(m/z) as a function of time (SECONDS).
	 * Uses slope bumps at T1/T2 (T1,T2 are given in microseconds in the DB).
	 */
	private static double rawRofTime(double t_s, MzCalibrationParams p) {
		final double T1_s=p.T1_us*1e-6;
		final double T2_s=p.T2_us*1e-6;

		double c1eff=p.C1;
		if (t_s>=T1_s) c1eff+=p.dC1;
		if (t_s>=T2_s) c1eff+=p.dC2;

		// Polynomial is in *seconds*; C2..C4 are often zero in practice.
		return p.C0+c1eff*t_s+p.C2*t_s*t_s+p.C3*t_s*t_s*t_s+p.C4*t_s*t_s*t_s*t_s;
	}
}