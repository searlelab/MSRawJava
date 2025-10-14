package org.searlelab.msrawjava.io.tims;

/**
 * MzCalibrationPoly represents the polynomial (and related coefficients) used by the timsTOF path to evaluate
 * calibrated m/z from raw indices/TOF. It provides an immutable, serializable form of the calibration function so
 * readers and native components can perform conversions consistently. The current logic is based on reverse-engineering
 * provided by Sebastian Paez (thanks Sebastian!)
 */
public final class MzCalibrationPoly {
    /**
     * Convert a single TOF index to m/z using the reverse-engineered parabola designed by Sebastian Paez (thanks Sebastian!):
     *   time_ns = tof * timebase_ns_per_sample + delay_ns
     *   inner   = time_ns - C0_ns
     *   c1corr  = C1 * (1 + dC1 * (T1_us - realT1_us) / 1e6)   // ppm tweak
     *   mz      = c1corr * inner^2 / 1e12                      // ns^2 → ms^2 scale
     */
    public static double[] tofToMz(int[] tof, MzCalibrationParams p, double realT1) {
        // ppm correction factor on C1
    	final double cf = 1.0 + p.dC1 * (p.T1 - realT1) / 1.0e6;
        final double c1corr = p.C1 * cf;
        
        double[] mzs=new double[tof.length];
        for (int i=0; i<tof.length; i++) {
            // time-of-flight in nanoseconds
            final double time_ns = tof[i] * p.timebaseNsPerSample + p.delaySamples;
            final double inner_ns = time_ns - p.C0;
            mzs[i]=c1corr * (inner_ns * inner_ns) / 1.0e12;
		}
        return mzs;
    }
}