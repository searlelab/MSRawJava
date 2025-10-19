package org.searlelab.msrawjava.io.tims;

/**
 * MzCalibrationParams encapsulates the numerical parameters required to convert instrument time-of-flight or index
 * values into calibrated m/z for timsTOF data. It serves as a stable container passed between readers and native
 * layers, often working with MzCalibrationPoly for evaluation, so that spectrum construction can rely on a single,
 * consistent calibration description.
 */
public final class MzCalibrationParams {
	public final double timebaseNsPerSample; // DigitizerTimebase (e.g., 0.2)
	public final double delaySamples; // DigitizerDelay (e.g., 24864)
	public final double T1, T2; // T1, T2 (e.g., 25.656..., 27.344...)
	public final double dC1, dC2; // dC1, dC2
	public final double C0, C1, C2, C3, C4; // C0..C4

	public MzCalibrationParams(double tbNs, double delay, double T1, double T2, double dC1, double dC2, double C0, double C1, double C2, double C3, double C4) {
		this.timebaseNsPerSample=tbNs;
		this.delaySamples=delay;
		this.T1=T1;
		this.T2=T2;
		this.dC1=dC1;
		this.dC2=dC2;
		this.C0=C0;
		this.C1=C1;
		this.C2=C2;
		this.C3=C3;
		this.C4=C4;
	}
}