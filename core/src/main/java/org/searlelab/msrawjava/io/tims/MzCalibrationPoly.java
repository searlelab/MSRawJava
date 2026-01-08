package org.searlelab.msrawjava.io.tims;

/**
 * MzCalibrationPoly represents the polynomial (and related coefficients) used by the timsTOF path to evaluate
 * calibrated m/z from raw indices/TOF. It provides an immutable, serializable form of the calibration function so
 * readers and native components can perform conversions consistently. 
 */
public final class MzCalibrationPoly implements MzCalibrator {

	private final int digitizerNumSamples;
	//private final double mzLower;
	//private final double mzUpper;
	private final MzCalibrationLinear linear;
	private final MzCalibrationParams params;

	public MzCalibrationPoly(int digitizerNumSamples, double mzLower, double mzUpper, MzCalibrationParams params) {
		this.digitizerNumSamples=digitizerNumSamples;
		//this.mzLower=mzLower;
		//this.mzUpper=mzUpper;
		this.params=params;
		this.linear=new MzCalibrationLinear(digitizerNumSamples, mzLower, mzUpper, params);
	}

	/**
	 * Convert a single TOF index to m/z using reverse-engineered constants by Sebastian Paez (thanks, Sebastian!)
	 * 
	 * Basic ToF theory:
	 * E=(m/2)*v^2=(m/2)*L^2/(t-t0)^2, kinetic energy equation where the time of flight Δt=t-t0 over a distance L
	 * m=2*E*(t-t0)^2/L^2, solve for m
	 * t=t0+L*sqrt(m/2*E), solve for t
	 * E=Eu+E0=Eu+(m/2)*v0^2, E (total energy) is the sum of Eu (energy caused by electric acceleration) 
	 *                        and E0 (initial energy). Here, v0 is the average initial velocity
	 * t=c0*sqrt(m)^0+c1*sqrt(m)^1+c3*sqrt(m)^3, Taylor-series-like approximation
	 * m=k2*(t-t0)^2+k4*(t-t0)^4, invert the approximation
	 * 
	 * Using assumed Bruker constants:
	 * m/z=C1*(t-C0)^2+C2*(t-C0)^4+C3*(t-C0)^6...
	 * 
	 * Notes on assumptions (no promises!):
	 * 1) CX terms behave like kX terms, not cX terms.
	 * 2) C0 behaves like a time zero (t0) in ns.
	 * 3) C1 is in the quadratic time scale, C2 is in the quartic time scale.
	 * 4) dC1 is a minor tweak on C1 in ppm, dC2 is a minor tweak on C2 in ppm.
	 * 5) DigitizerDelay is in nanoseconds.
	 * 6) 1e12 is needed to shift from ns^2 to ms^2 scale.
	 */
	@Override
	public double[] tofToMz(int[] tof, double realT1) {
		// ppm correction factor on C1
		final double cf1=1.0+params.dC1*(params.T1-realT1)/1.0e6;
		final double c1corr=params.C1*cf1;

		// ppm correction factor on C2
		final double cf2=1.0+params.dC2*(params.T1-realT1)/1.0e6;
		final double c2corr=params.C2*cf2;

		final double[] mzs=new double[tof.length];
		for (int i=0; i<tof.length; i++) {
			// time-of-flight in nanoseconds
			final double time_ns=tof[i]*params.timebaseNsPerSample+params.delaySamples;
			final double inner_ns=time_ns-params.C0;
			final double inner2=inner_ns*inner_ns;
			final double inner4=inner2*inner2;
			mzs[i]=(c1corr*inner2)/1.0e12+(c2corr*inner4)/1.0e24;
		}
		return mzs;
	}

	public int[] mzToTof(double[] mz, double realT1) {
		final int[] tof=new int[mz.length];

		// ppm-corrected coefficients
		final double cf1=1.0+params.dC1*(params.T1-realT1)/1.0e6;
		final double c1corr=params.C1*cf1;
		
		// should be params.T2-realT2, but we don't have that here. As a result, let's assume that the temperature differential is the same
		final double cf2=1.0+params.dC2*(params.T1-realT1)/1.0e6;
		final double c2corr=params.C2*cf2;

		// time mapping (delay treated as ns)
		final double tbNs=params.timebaseNsPerSample;
		final double invTbNs=1.0/tbNs;
		final double delayNs=params.delaySamples;
		final double C0ns=params.C0;

		// quadratic in y = inner^2: (a) y^2 + (b) y - m_base = 0
		final double a=c2corr/1.0e24;
		final double b=c1corr/1.0e12;
		final boolean linear=Math.abs(a)<1e-30;

		for (int i=0; i<mz.length; i++) {
			final double m=mz[i]>0.0?mz[i]:0.0;

			// solve for y = inner^2
			final double y;
			if (linear) {
				y=(b>0.0)?(m/b):0.0;
			} else {
				final double disc=Math.max(0.0, b*b+4.0*a*m);
				final double sqrtD=Math.sqrt(disc);
				final double y1=(-b+sqrtD)/(2.0*a);
				final double y2=(-b-sqrtD)/(2.0*a);
				y=(y1>=0.0)?y1:(y2>=0.0?y2:0.0);
			}

			// inner >= 0 (forward flight), then to index
			final double inner=Math.sqrt(Math.max(0.0, y));
			int idx=(int)Math.round(((inner+C0ns)-delayNs)*invTbNs);

			if (idx<0) idx=0;
			if (idx>digitizerNumSamples) idx=digitizerNumSamples;
			tof[i]=idx;
		}
		return tof;
	}

	@Override
	public double[] uncorrectedMzToMz(double[] uncorrectedMz, double realT1) {
		double[] r=tofToMz(linear.mzToTof(uncorrectedMz, realT1), realT1);
		return r;
	}

	@Override
	public double getGlobalT1() {
		return params.T1;
	}

	@Override
	public MzCalibrationLinear getLinear() {
		return linear;
	}
}
