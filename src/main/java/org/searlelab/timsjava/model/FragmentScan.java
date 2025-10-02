package org.searlelab.timsjava.model;

public class FragmentScan {

	private final String spectrumName;
	private final String precursorName;
	private final int spectrumIndex;
	private final float scanStartTime;
	private final int fraction;
	private final Float ionInjectionTime;
	private final double isolationWindowLower;
	private final double isolationWindowUpper;
	private final double[] massArray;
	private final float[] intensityArray;
	private final float[] ionMobilityArray;
	private final byte charge;

	public FragmentScan(String spectrumName, String precursorName, int spectrumIndex, float scanStartTime, int fraction,
			Float ionInjectionTime, double isolationWindowLower, double isolationWindowUpper, double[] massArray,
			float[] intensityArray, float[] ionMobilityArray, byte charge) {
		super();
		this.spectrumName = spectrumName;
		this.precursorName = precursorName;
		this.spectrumIndex = spectrumIndex;
		this.scanStartTime = scanStartTime;
		this.fraction = fraction;
		this.ionInjectionTime = ionInjectionTime;
		this.isolationWindowLower = isolationWindowLower;
		this.isolationWindowUpper = isolationWindowUpper;
		this.massArray = massArray;
		this.intensityArray = intensityArray;
		this.ionMobilityArray = ionMobilityArray;
		this.charge = charge;
	}

	public String getSpectrumName() {
		return spectrumName;
	}

	public String getPrecursorName() {
		return precursorName;
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

	public Float getIonInjectionTime() {
		return ionInjectionTime;
	}

	public double getIsolationWindowLower() {
		return isolationWindowLower;
	}

	public double getIsolationWindowUpper() {
		return isolationWindowUpper;
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

	public byte getCharge() {
		return charge;
	}

}
