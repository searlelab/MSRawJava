package org.searlelab.msrawjava.model;

/**
 * ScanSummary captures lightweight metadata for a scan without loading spectral arrays.
 * It is intended for fast UI listing and on-demand spectrum retrieval.
 */
public final class ScanSummary {
	private final String spectrumName;
	private final int spectrumIndex;
	private final float scanStartTime;
	private final int fraction;
	private final double precursorMz;
	private final boolean precursor;
	private final Float ionInjectionTime;
	private final double isolationWindowLower;
	private final double isolationWindowUpper;
	private final double scanWindowLower;
	private final double scanWindowUpper;
	private final byte charge;

	public ScanSummary(String spectrumName, int spectrumIndex, float scanStartTime, int fraction, double precursorMz, boolean precursor, Float ionInjectionTime,
			double isolationWindowLower, double isolationWindowUpper, double scanWindowLower, double scanWindowUpper, byte charge) {
		this.spectrumName=spectrumName;
		this.spectrumIndex=spectrumIndex;
		this.scanStartTime=scanStartTime;
		this.fraction=fraction;
		this.precursorMz=precursorMz;
		this.precursor=precursor;
		this.ionInjectionTime=ionInjectionTime;
		this.isolationWindowLower=isolationWindowLower;
		this.isolationWindowUpper=isolationWindowUpper;
		this.scanWindowLower=scanWindowLower;
		this.scanWindowUpper=scanWindowUpper;
		this.charge=charge;
	}

	public String getSpectrumName() {
		return spectrumName;
	}

	public int getSpectrumIndex() {
		return spectrumIndex;
	}

	public float getScanStartTime() {
		return scanStartTime;
	}

	public int getFraction() {
		return fraction;
	}

	public double getPrecursorMz() {
		return precursorMz;
	}

	public boolean isPrecursor() {
		return precursor;
	}

	public Float getIonInjectionTime() {
		return ionInjectionTime;
	}

	public double getIsolationWindowLower() {
		return isolationWindowLower;
	}

	public double getIsolationWindowUpper() {
		return isolationWindowUpper;
	}

	public double getScanWindowLower() {
		return scanWindowLower;
	}

	public double getScanWindowUpper() {
		return scanWindowUpper;
	}

	public byte getCharge() {
		return charge;
	}
}
