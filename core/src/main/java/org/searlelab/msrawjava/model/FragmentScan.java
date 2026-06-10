package org.searlelab.msrawjava.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.algorithms.QuickMedian;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TFloatArrayList;

/**
 * FragmentScan models an MS/MS DIA or DDA spectrum in the unified data model, associating calibrated m/z and intensity
 * arrays with isolation/window information and scan metadata.
 */
public class FragmentScan implements AcquiredSpectrum, Comparable<AcquiredSpectrum> {
	private static final double DEFAULT_PEAK_DEPTH_BIN_SIZE=100.0;
	private static final int DEFAULT_PEAK_DEPTH_NUM_BINS=20;

	private final String spectrumName;
	private final String precursorName;
	private final int spectrumIndex;
	private final double precursorMz;
	private final float scanStartTime;
	private final int fraction;
	private final float ionInjectionTime;
	private final double isolationWindowLower;
	private final double isolationWindowTarget;
	private final double isolationWindowUpper;
	private final double scanWindowLower;
	private final double scanWindowUpper;
	private final double[] massArray;
	private final float[] intensityArray;
	private final float[] ionMobilityArray; // can be nullable
	private final byte charge;
	private final float tic;

	public FragmentScan(String spectrumName, String precursorName, int spectrumIndex, double precursorMz, float scanStartTime, int fraction,
			Float ionInjectionTime, double isolationWindowLower, double isolationWindowUpper, double[] massArray, float[] intensityArray,
			float[] ionMobilityArray, byte charge, double scanWindowLower, double scanWindowUpper) {
		this(spectrumName, precursorName, spectrumIndex, precursorMz, scanStartTime, fraction, ionInjectionTime, isolationWindowLower,
				midpoint(isolationWindowLower, isolationWindowUpper), isolationWindowUpper, massArray, intensityArray, ionMobilityArray, charge,
				scanWindowLower, scanWindowUpper);
	}

	public FragmentScan(String spectrumName, String precursorName, int spectrumIndex, double precursorMz, float scanStartTime, int fraction,
			Float ionInjectionTime, double isolationWindowLower, double isolationWindowTarget, double isolationWindowUpper, double[] massArray,
			float[] intensityArray, float[] ionMobilityArray, byte charge, double scanWindowLower, double scanWindowUpper) {
		super();
		this.spectrumName=spectrumName;
		this.precursorName=precursorName;
		this.spectrumIndex=spectrumIndex;
		this.precursorMz=precursorMz;
		this.scanStartTime=scanStartTime;
		this.fraction=fraction;
		this.ionInjectionTime=ionInjectionTime==null?-1f:ionInjectionTime;
		this.isolationWindowLower=isolationWindowLower;
		this.isolationWindowTarget=Double.isFinite(isolationWindowTarget)?isolationWindowTarget:midpoint(isolationWindowLower, isolationWindowUpper);
		this.isolationWindowUpper=isolationWindowUpper;
		this.massArray=massArray;
		this.intensityArray=intensityArray;
		this.ionMobilityArray=ionMobilityArray;
		this.charge=charge;
		this.scanWindowLower=scanWindowLower;
		this.scanWindowUpper=scanWindowUpper;
		this.tic=MatrixMath.sum(intensityArray);
	}

	private static double midpoint(double lower, double upper) {
		return (lower+upper)/2.0;
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

	public FragmentScan renumber(int newSpectrumIndex) {
		return new FragmentScan(spectrumName, precursorName, newSpectrumIndex, precursorMz, scanStartTime, fraction, ionInjectionTime, isolationWindowLower,
				isolationWindowTarget, isolationWindowUpper, massArray, intensityArray, ionMobilityArray, charge, scanWindowLower, scanWindowUpper);
	}

	public FragmentScan shallowClone(int fraction, int spectrumIndex) {
		return new FragmentScan(spectrumName, precursorName, spectrumIndex, precursorMz, scanStartTime, fraction, ionInjectionTime, isolationWindowLower,
				isolationWindowTarget, isolationWindowUpper, massArray, intensityArray, ionMobilityArray, charge, scanWindowLower, scanWindowUpper);
	}

	public FragmentScan withIsolationWindow(double isolationWindowLower, double isolationWindowUpper) {
		return new FragmentScan(spectrumName, precursorName, spectrumIndex, precursorMz, scanStartTime, fraction, ionInjectionTime, isolationWindowLower,
				isolationWindowTarget, isolationWindowUpper, massArray, intensityArray, ionMobilityArray, charge, scanWindowLower, scanWindowUpper);
	}

	public FragmentScan trimIsolationWindow(double margin) {
		if (margin<=0.0) return this;
		double lower=isolationWindowLower+margin;
		double upper=isolationWindowUpper-margin;
		if (lower>upper) {
			double center=(isolationWindowLower+isolationWindowUpper)/2.0;
			lower=center;
			upper=center;
		}
		return withIsolationWindow(lower, upper);
	}

	public FragmentScan sqrt() {
		float[] sqrtIntensityArray=new float[intensityArray.length];
		for (int i=0; i<intensityArray.length; i++) {
			if (intensityArray[i]>0f) {
				sqrtIntensityArray[i]=(float)Math.sqrt(intensityArray[i]);
			}
		}
		return new FragmentScan(spectrumName, precursorName, spectrumIndex, precursorMz, scanStartTime, fraction, ionInjectionTime, isolationWindowLower,
				isolationWindowTarget, isolationWindowUpper, massArray, sqrtIntensityArray, ionMobilityArray, charge, scanWindowLower, scanWindowUpper);
	}

	public FragmentScan trimToPeakDepth(int depth) {
		if (depth<=0||massArray.length==0) {
			return new FragmentScan(spectrumName, precursorName, spectrumIndex, precursorMz, scanStartTime, fraction, ionInjectionTime, isolationWindowLower,
					isolationWindowTarget, isolationWindowUpper, new double[0], new float[0], ionMobilityArray==null?null:new float[0], charge,
					scanWindowLower, scanWindowUpper);
		}

		@SuppressWarnings("unchecked")
		ArrayList<Integer>[] bins=new ArrayList[DEFAULT_PEAK_DEPTH_NUM_BINS];
		for (int i=0; i<bins.length; i++) {
			bins[i]=new ArrayList<Integer>();
		}
		for (int i=0; i<massArray.length; i++) {
			bins[getPeakDepthIndex(massArray[i])].add(i);
		}

		ArrayList<Integer> selected=new ArrayList<Integer>();
		for (ArrayList<Integer> bin : bins) {
			bin.sort((a, b) -> Float.compare(intensityArray[b], intensityArray[a]));
			int keep=Math.min(depth, bin.size());
			for (int i=0; i<keep; i++) {
				selected.add(bin.get(i));
			}
		}
		Collections.sort(selected);

		double[] trimmedMasses=new double[selected.size()];
		float[] trimmedIntensities=new float[selected.size()];
		float[] trimmedIms=ionMobilityArray==null?null:new float[selected.size()];
		for (int i=0; i<selected.size(); i++) {
			int idx=selected.get(i);
			trimmedMasses[i]=massArray[idx];
			trimmedIntensities[i]=intensityArray[idx];
			if (trimmedIms!=null) {
				trimmedIms[i]=ionMobilityArray[idx];
			}
		}

		return new FragmentScan(spectrumName, precursorName, spectrumIndex, precursorMz, scanStartTime, fraction, ionInjectionTime, isolationWindowLower,
				isolationWindowTarget, isolationWindowUpper, trimmedMasses, trimmedIntensities, trimmedIms, charge, scanWindowLower, scanWindowUpper);
	}

	public FragmentScan trimMasses(Range r) {
		TFloatArrayList ints=new TFloatArrayList();
		TDoubleArrayList masses=new TDoubleArrayList();
		TFloatArrayList mobilities=new TFloatArrayList();
		for (int i=0; i<massArray.length; i++) {
			if (r.contains(massArray[i])) {
				ints.add(intensityArray[i]);
				masses.add(massArray[i]);
				if (ionMobilityArray!=null) {
					mobilities.add(ionMobilityArray[i]);
				}
			}
		}
		float[] mobilitiesArray=ionMobilityArray==null?null:mobilities.toArray();
		return new FragmentScan(spectrumName, precursorName, spectrumIndex, precursorMz, scanStartTime, fraction, ionInjectionTime, isolationWindowLower,
				isolationWindowTarget, isolationWindowUpper, masses.toArray(), ints.toArray(), mobilitiesArray, charge, scanWindowLower, scanWindowUpper);
	}

	public FragmentScan rebuild(int newSpectrumIndex, ArrayList<? extends PeakInterface> peaks) {
		return rebuild(newSpectrumIndex, scanStartTime, peaks);
	}

	public FragmentScan rebuild(int newSpectrumIndex, float rtInsec, ArrayList<? extends PeakInterface> peaks) {
		return rebuild(newSpectrumIndex, scanStartTime, peaks, isolationWindowLower, isolationWindowUpper);
	}

	public FragmentScan rebuild(int newSpectrumIndex, float rtInsec, ArrayList<? extends PeakInterface> peaks, double isolationWindowLower,
			double isolationWindowUpper) {
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
				isolationWindowTarget, isolationWindowUpper, newMassArray, newIntensityArray, newIonMobilityArray, charge, scanWindowLower, scanWindowUpper);
	}

	public Range getPrecursorRange() {
		return new Range(isolationWindowLower, isolationWindowUpper);
	}

	public Range getRange() {
		return getPrecursorRange();
	}

	public double getIsolationWindowCenter() {
		return midpoint(isolationWindowLower, isolationWindowUpper);
	}

	public double getIsolationWindowTarget() {
		return isolationWindowTarget;
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
		c=Double.compare(isolationWindowLower, o.getIsolationWindowLower());
		if (c!=0) return c;
		c=Double.compare(isolationWindowUpper, o.getIsolationWindowUpper());
		return 0;
	}

	@Override
	public float getTIC() {
		return tic;
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
	public float getIonInjectionTime() {
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

	public byte getPrecursorCharge() {
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

	private static int getPeakDepthIndex(double mz) {
		int index=(int)(mz/DEFAULT_PEAK_DEPTH_BIN_SIZE);
		if (index<0) return 0;
		if (index>=DEFAULT_PEAK_DEPTH_NUM_BINS) return DEFAULT_PEAK_DEPTH_NUM_BINS-1;
		return index;
	}

}
