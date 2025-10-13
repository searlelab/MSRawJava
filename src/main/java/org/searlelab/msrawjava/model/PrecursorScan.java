package org.searlelab.msrawjava.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

public class PrecursorScan implements AcquiredSpectrum, Comparable<AcquiredSpectrum> {

	private final String spectrumName;
	private final int spectrumIndex;
	private final float scanStartTime;
	private final int fraction;
	private final double scanWindowLower;
	private final double scanWindowUpper;
	private final Float ionInjectionTime;
	private final double[] massArray;
	private final float[] intensityArray;
	private final float[] ionMobilityArray;

	public PrecursorScan(String spectrumName, int spectrumIndex, float scanStartTime, int fraction, double scanWindowLower, double scanWindowUpper,
			Float ionInjectionTime, double[] massArray, float[] intensityArray, float[] ionMobilityArray) {
		this.spectrumName=spectrumName;
		this.spectrumIndex=spectrumIndex;
		this.scanStartTime=scanStartTime;
		this.fraction=fraction;
		this.scanWindowLower=scanWindowLower;
		this.scanWindowUpper=scanWindowUpper;
		this.ionInjectionTime=ionInjectionTime;
		this.massArray=massArray;
		this.intensityArray=intensityArray;
		this.ionMobilityArray=ionMobilityArray;
	}

	public PrecursorScan rebuild(int newSpectrumIndex, ArrayList<Peak> peaks) {
		Collections.sort(peaks);
		double[] newMassArray=new double[peaks.size()];
		float[] newIntensityArray=new float[peaks.size()];
		float[] newIonMobilityArray=new float[peaks.size()];
		for (int i=0; i<peaks.size(); i++) {
			Peak peak=peaks.get(i);
			newMassArray[i]=peak.mz;
			newIntensityArray[i]=peak.intensity;
			newIonMobilityArray[i]=peak.ims;
		}
		return new PrecursorScan(spectrumName, newSpectrumIndex, scanStartTime, fraction, scanWindowLower, scanWindowUpper, ionInjectionTime,
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
	public int compareTo(AcquiredSpectrum o) {
		if (o==null) return 1;
		int c=Float.compare(scanStartTime, o.getScanStartTime());
		if (c!=0) return c;
		c=Integer.compare(spectrumIndex, o.getSpectrumIndex());
		if (c!=0) return c;
		c=Double.compare(scanWindowLower, o.getIsolationWindowLower());
		if (c!=0) return c;
		c=Double.compare(scanWindowUpper, o.getIsolationWindowUpper());
		return 0;
	}
	
	@Override
	public double getPrecursorMZ() {
		return -1.0;
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
		return scanWindowLower;
	}

	@Override
	public double getIsolationWindowUpper() {
		return scanWindowUpper;
	}
	
	@Override
	public double getScanWindowLower() {
		return scanWindowLower;
	}
	public double getScanWindowUpper() {
		return scanWindowUpper;
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
	
	public Peak getBasePeak() {
		float maxIntensity=0.0f;
		double maxMz=0.0;
		float maxIMS=0.0f;
		for (int i=0; i<intensityArray.length; i++) {
			if (intensityArray[i]>maxIntensity) {
				maxIntensity=intensityArray[i];
				maxMz=massArray[i];
				if (ionMobilityArray!=null&&ionMobilityArray.length>i) {
					maxIMS=ionMobilityArray[i];
				}
			}
		}
		return new Peak(maxMz, maxIntensity, maxIMS);
	}
}
