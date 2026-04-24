package org.searlelab.msrawjava.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.searlelab.msrawjava.algorithms.MatrixMath;

/**
 * PrecursorScan models an MS1 (precursor) spectrum in the unified data model, carrying calibrated m/z and intensity
 * arrays plus relevant scan metadata such as retention-time context and identifiers.
 */
public class PrecursorScan implements AcquiredSpectrum, Comparable<AcquiredSpectrum> {

	private final String spectrumName;
	private final int spectrumIndex;
	private final float scanStartTime;
	private final int fraction;
	private final double scanWindowLower;
	private final double scanWindowUpper;
	private final float ionInjectionTime;
	private final double[] massArray;
	private final float[] intensityArray;
	private final float[] ionMobilityArray;
	private final float tic;

	public PrecursorScan(String spectrumName, int spectrumIndex, float scanStartTime, int fraction, double scanWindowLower, double scanWindowUpper,
			Float ionInjectionTime, double[] massArray, float[] intensityArray, float[] ionMobilityArray) {
		this(spectrumName, spectrumIndex, scanStartTime, fraction, scanWindowLower, scanWindowUpper, ionInjectionTime, massArray, intensityArray,
				ionMobilityArray, null);
	}

	public PrecursorScan(String spectrumName, int spectrumIndex, float scanStartTime, int fraction, double scanWindowLower, double scanWindowUpper,
			Float ionInjectionTime, double[] massArray, float[] intensityArray, float[] ionMobilityArray, Float tic) {
		this.spectrumName=spectrumName;
		this.spectrumIndex=spectrumIndex;
		this.scanStartTime=scanStartTime;
		this.fraction=fraction;
		this.scanWindowLower=scanWindowLower;
		this.scanWindowUpper=scanWindowUpper;
		this.ionInjectionTime=ionInjectionTime==null?-1f:ionInjectionTime;
		this.massArray=massArray;
		this.intensityArray=intensityArray;
		this.ionMobilityArray=ionMobilityArray;
		this.tic=tic==null?MatrixMath.sum(intensityArray):tic;
	}

	public PrecursorScan shallowClone(int fraction, int spectrumIndex) {
		return new PrecursorScan(spectrumName, spectrumIndex, scanStartTime, fraction, scanWindowLower, scanWindowUpper, ionInjectionTime, massArray,
				intensityArray, ionMobilityArray, tic);
	}

	public PrecursorScan shallowClone(int fraction, int spectrumIndex, Range precursorIsolationWindow) {
		double lowerBound=Math.max(precursorIsolationWindow.getStart(), scanWindowLower);
		double upperBound=Math.min(precursorIsolationWindow.getStop(), scanWindowUpper);
		return new PrecursorScan(spectrumName, spectrumIndex, scanStartTime, fraction, lowerBound, upperBound, ionInjectionTime, massArray, intensityArray,
				ionMobilityArray, tic);
	}

	public float integrate(Range mzRange) {
		int index=Arrays.binarySearch(massArray, mzRange.getStart());
		if (index<0) {
			index=-(index+1);
		}
		float sum=0.0f;
		while (index<massArray.length) {
			if (!mzRange.contains(massArray[index])) {
				break;
			}
			sum+=intensityArray[index];
			index++;
		}
		return sum;
	}

	public PrecursorScan rebuild(int newSpectrumIndex, ArrayList<? extends PeakInterface> peaks) {
		Collections.sort(peaks);
		double[] newMassArray=new double[peaks.size()];
		float[] newIntensityArray=new float[peaks.size()];
		float[] newIonMobilityArray=new float[peaks.size()];
		boolean anyIMS=false;
		for (int i=0; i<peaks.size(); i++) {
			PeakInterface peak=peaks.get(i);
			newMassArray[i]=peak.getMz();
			newIntensityArray[i]=peak.getIntensity();
			if (peak instanceof PeakWithIMS) {
				newIonMobilityArray[i]=((PeakWithIMS)peak).getIMS();
				anyIMS=true;
			}
		}
		if (!anyIMS) {
			newIonMobilityArray=null;
		}
		return new PrecursorScan(spectrumName, newSpectrumIndex, scanStartTime, fraction, scanWindowLower, scanWindowUpper, ionInjectionTime, newMassArray,
				newIntensityArray, newIonMobilityArray);
	}

	public ArrayList<PeakInterface> getPeaks(float minimumIntensity) {
		ArrayList<PeakInterface> peaks=new ArrayList<PeakInterface>();
		boolean hasIMS=ionMobilityArray!=null;
		for (int i=0; i<massArray.length; i++) {
			if (intensityArray[i]>minimumIntensity) {
				if (hasIMS) {
					peaks.add(new PeakWithIMS(massArray[i], intensityArray[i], ionMobilityArray[i]));
				} else {
					peaks.add(new PeakInTime(massArray[i], intensityArray[i], scanStartTime));
				}
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
	public float getIonInjectionTime() {
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
		return tic;
	}

	public PeakInterface getBasePeak() {
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
		return new PeakWithIMS(maxMz, maxIntensity, maxIMS);
	}
}
