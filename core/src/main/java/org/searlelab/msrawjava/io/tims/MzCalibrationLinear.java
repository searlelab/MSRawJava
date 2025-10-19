package org.searlelab.msrawjava.io.tims;

/**
 * MzCalibrationLinear represents a linear fit to evaluate calibrated m/z from raw indices/TOF. This matches the
 * timsRust code. It provides an immutable, serializable form of the calibration function so readers and native
 * components can perform conversions consistently.
 */
public final class MzCalibrationLinear implements MzCalibrator {

	private final int digitizerNumSamples;
	//private final double mzLower;
	//private final double mzUpper;
	private final MzCalibrationParams params;

	private final double tofIntercept;
	private final double tofSlope;

	public MzCalibrationLinear(int digitizerNumSamples, double mzLower, double mzUpper, MzCalibrationParams params) {
		this.digitizerNumSamples=digitizerNumSamples;
		//this.mzLower=mzLower;
		//this.mzUpper=mzUpper;
		this.params=params;

		tofIntercept=Math.sqrt(mzLower);
		tofSlope=(Math.sqrt(mzUpper)-tofIntercept)/digitizerNumSamples;
	}

	/**
	 * Convert a single TOF index to m/z using a simple linear alignment with the window
	 */
	@Override
	public double[] tofToMz(int[] tof, double realT1) {
		double[] mzs=new double[tof.length];
		for (int i=0; i<tof.length; i++) {
			double sqrtMz=tofIntercept+tofSlope*tof[i];
			mzs[i]=sqrtMz*sqrtMz;
		}
		return mzs;
	}

	@Override
	public int[] mzToTof(double[] mz, double realT1) {
		int[] tof=new int[mz.length];
		final double invSlope=1.0/tofSlope; // one division > many
		for (int i=0; i<mz.length; i++) {
			// Guard against tiny negatives due to noise/rounding
			double sqrtMz=Math.sqrt(mz[i]>0.0?mz[i]:0.0);

			// Continuous inverse index
			double idx=(sqrtMz-tofIntercept)*invSlope;

			// Round to nearest integer sample
			int iIdx=(int)Math.round(idx);

			// Clamp to valid digitizer range
			if (iIdx<0) iIdx=0;
			if (iIdx>digitizerNumSamples) iIdx=digitizerNumSamples;

			tof[i]=iIdx;
		}
		return tof;
	}

	@Override
	public double[] uncorrectedMzToMz(double[] uncorrectedMz, double realT1) {
		// already in linear space!
		return uncorrectedMz;
	}

	@Override
	public double getGlobalT1() {
		return params.T1;
	}
	
	@Override
	public MzCalibrationLinear getLinear() {
		return this;
	}
}