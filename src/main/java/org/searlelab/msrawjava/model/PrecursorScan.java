package org.searlelab.msrawjava.model;

import java.util.ArrayList;
import java.util.Optional;

public class PrecursorScan implements PrecursorScanInterface {

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

	public PrecursorScan(String spectrumName, int spectrumIndex, float scanStartTime, int fraction, double isolationWindowLower, double isolationWindowUpper,
			Float ionInjectionTime, double[] massArray, float[] intensityArray, float[] ionMobilityArray) {
		this.spectrumName=spectrumName;
		this.spectrumIndex=spectrumIndex;
		this.scanStartTime=scanStartTime;
		this.fraction=fraction;
		this.isolationWindowLower=isolationWindowLower;
		this.isolationWindowUpper=isolationWindowUpper;
		this.ionInjectionTime=ionInjectionTime;
		this.massArray=massArray;
		this.intensityArray=intensityArray;
		this.ionMobilityArray=ionMobilityArray;
	}

	public PrecursorScan rebuild(int newSpectrumIndex, ArrayList<Peak> peaks) {
		double[] newMassArray=new double[peaks.size()];
		float[] newIntensityArray=new float[peaks.size()];
		float[] newIonMobilityArray=new float[peaks.size()];
		for (int i=0; i<peaks.size(); i++) {
			Peak peak=peaks.get(i);
			newMassArray[i]=peak.mz;
			newIntensityArray[i]=peak.intensity;
			newIonMobilityArray[i]=peak.ims;
		}
		return new PrecursorScan(spectrumName, newSpectrumIndex, scanStartTime, fraction, isolationWindowLower, isolationWindowUpper, ionInjectionTime,
				newMassArray, newIntensityArray, newIonMobilityArray);
	}

	public ArrayList<Peak> getPeaks(float minimumIntensity) {
		ArrayList<Peak> peaks=new ArrayList<Peak>();
		for (int i=0; i<massArray.length; i++) {
			if (intensityArray[i]>minimumIntensity) {
				peaks.add(new Peak(massArray[i], intensityArray[i], ionMobilityArray[i]));
			}
		}
		return peaks;
	}

	@Override
	public String getSpectrumName() {
		return spectrumName;
	}

	@Override
	public int getSpectrumIndex() {
		return spectrumIndex;
	}

	@Override
	public float getScanStartTime() {
		return scanStartTime;
	}

	@Override
	public int getFraction() {
		return fraction;
	}

	@Override
	public double getIsolationWindowLower() {
		return isolationWindowLower;
	}

	@Override
	public double getIsolationWindowUpper() {
		return isolationWindowUpper;
	}

	@Override
	public Float getIonInjectionTime() {
		return ionInjectionTime;
	}

	@Override
	public double[] getMassArray() {
		return massArray;
	}

	@Override
	public float[] getIntensityArray() {
		return intensityArray;
	}

	@Override
	public Optional<float[]> getIonMobilityArray() {
		return Optional.ofNullable(ionMobilityArray);
	}

	@Override
	public float getTIC() {
		float tic=0.0f;
		for (int i=0; i<intensityArray.length; i++) {
			tic+=intensityArray[i];
		}
		return tic;
	}
}
