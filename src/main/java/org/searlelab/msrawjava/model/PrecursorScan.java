package org.searlelab.msrawjava.model;

public class PrecursorScan {

	private final String spectrumName;
	private final int spectrumIndex;
	private final float scanStartTime;
	private final int fraction;
	private final double isolationWindowLower;
	private final double isolationWindowUpper;
	private final Float ionInjectionTime;
	private final double[] massArray;
	private final float[] intensityArray;
	private final float[] ionMobilityArray;
	
	public PrecursorScan(String spectrumName, int spectrumIndex, float scanStartTime, int fraction,
			double isolationWindowLower, double isolationWindowUpper, Float ionInjectionTime, double[] massArray,
			float[] intensityArray, float[] ionMobilityArray) {
		this.spectrumName = spectrumName;
		this.spectrumIndex = spectrumIndex;
		this.scanStartTime = scanStartTime;
		this.fraction = fraction;
		this.isolationWindowLower = isolationWindowLower;
		this.isolationWindowUpper = isolationWindowUpper;
		this.ionInjectionTime = ionInjectionTime;
		this.massArray = massArray;
		this.intensityArray = intensityArray;
		this.ionMobilityArray = ionMobilityArray;
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

	public double getIsolationWindowLower() {
		return isolationWindowLower;
	}

	public double getIsolationWindowUpper() {
		return isolationWindowUpper;
	}

	public Float getIonInjectionTime() {
		return ionInjectionTime;
	}

	public double[] getMassArray() {
		return massArray;
	}

	public float[] getIntensityArray() {
		return intensityArray;
	}

	public float[] getIonMobilityArray() {
		return ionMobilityArray;
	}
	
	
}
