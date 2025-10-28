package org.searlelab.msrawjava.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.algorithms.QuickMedian;

/**
 * FragmentScan models an MS/MS DIA or DDA spectrum in the unified data model, associating calibrated m/z and intensity
 * arrays with isolation/window information and scan metadata.
 */
public class FragmentScan implements AcquiredSpectrum, Comparable<AcquiredSpectrum> {

	private final String spectrumName;
	private final String precursorName;
	private final int spectrumIndex;
	private final double precursorMz;
	private final float scanStartTime;
	private final int fraction;
	private final Float ionInjectionTime;
	private final double isolationWindowLower;
	private final double isolationWindowUpper;
	private final double scanWindowLower;
	private final double scanWindowUpper;
	private final double[] massArray;
	private final float[] intensityArray;
	private final float[] ionMobilityArray; // can be nullable
	private final byte charge;

	public FragmentScan(String spectrumName, String precursorName, int spectrumIndex, double precursorMz, float scanStartTime, int fraction, Float ionInjectionTime,
			double isolationWindowLower, double isolationWindowUpper, double[] massArray, float[] intensityArray, float[] ionMobilityArray, byte charge, double 
			scanWindowLower, double scanWindowUpper) {
		super();
		this.spectrumName=spectrumName;
		this.precursorName=precursorName;
		this.spectrumIndex=spectrumIndex;
		this.precursorMz=precursorMz;
		this.scanStartTime=scanStartTime;
		this.fraction=fraction;
		this.ionInjectionTime=ionInjectionTime;
		this.isolationWindowLower=isolationWindowLower;
		this.isolationWindowUpper=isolationWindowUpper;
		this.massArray=massArray;
		this.intensityArray=intensityArray;
		this.ionMobilityArray=ionMobilityArray;
		this.charge=charge;
		this.scanWindowLower=scanWindowLower;
		this.scanWindowUpper=scanWindowUpper;
	}
	
	@Override
	public String toString() {
		StringBuilder sb=new StringBuilder(isolationWindowLower+" to "+isolationWindowUpper+" (z="+charge+")\n");
		for (int i=0; i<intensityArray.length; i++) {
			if (ionMobilityArray==null) {
				sb.append(massArray[i]+"\t"+intensityArray[i]+"\n");
			} else {
				sb.append(massArray[i]+"\t"+intensityArray[i]+"\t"+ionMobilityArray[i]+"\n");
			}
		}
		
		return sb.toString();
	}

	public FragmentScan rebuild(int newSpectrumIndex, ArrayList<? extends PeakInterface> peaks) {
		return rebuild(newSpectrumIndex, scanStartTime, peaks);
	}

	public FragmentScan rebuild(int newSpectrumIndex, float rtInsec, ArrayList<? extends PeakInterface> peaks) {
		return rebuild(newSpectrumIndex, scanStartTime, peaks, isolationWindowLower, isolationWindowUpper);
	}
	
	public FragmentScan rebuild(int newSpectrumIndex, float rtInsec, ArrayList<? extends PeakInterface> peaks, double isolationWindowLower, double isolationWindowUpper) {
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
		
		return new FragmentScan(spectrumName, precursorName, newSpectrumIndex, precursorMz, rtInsec, fraction, ionInjectionTime, isolationWindowLower,
				isolationWindowUpper, newMassArray, newIntensityArray, newIonMobilityArray, charge, scanWindowLower, scanWindowUpper);
	}
	
	public Range getPrecursorRange() {
		return new Range(isolationWindowLower, isolationWindowUpper);
	}

	public ArrayList<PeakWithIMS> getPeaks(float minimumIntensity) {
		ArrayList<PeakWithIMS> peaks=new ArrayList<PeakWithIMS>();
		for (int i=0; i<massArray.length; i++) {
			if (intensityArray[i]>minimumIntensity) {
				peaks.add(new PeakWithIMS(massArray[i], intensityArray[i], ionMobilityArray[i]));
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
		c=Double.compare(isolationWindowLower, o.getIsolationWindowLower());
		if (c!=0) return c;
		c=Double.compare(isolationWindowUpper, o.getIsolationWindowUpper());
		return 0;
	}

	@Override
	public float getTIC() {
		return MatrixMath.sum(intensityArray);
	}

	@Override
	public double getPrecursorMZ() {
		return precursorMz;
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
	public Float getIonInjectionTime() {
		return ionInjectionTime;
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
	public double getScanWindowLower() {
		return scanWindowLower;
	}
	@Override
	public double getScanWindowUpper() {
		return scanWindowUpper;
	}

	public byte getCharge() {
		return charge;
	}

	public String getPrecursorName() {
		return precursorName;
	}
	
	public Optional<Float> getMedianIonMobility() {
		if (ionMobilityArray==null) {
			return Optional.empty();
		} else {
			return Optional.of(QuickMedian.median(ionMobilityArray));
		}
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
