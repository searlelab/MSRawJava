package org.searlelab.msrawjava.algorithms;

import java.util.List;
import java.util.Optional;

import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.MassTolerance;
import org.searlelab.msrawjava.model.PrecursorScan;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TFloatArrayList;

/**
 * Utilities for merging multiple acquired spectra into a single combined spectrum.
 * This mirrors EncyclopeDIA's SpectrumUtils behavior but uses MSRawJava's model types.
 * 
 * FIXME: precursor scans seem like a potential problem here, since this class merges spectra of all types
 */
public final class RawSpectrumMergeUtils {
	private static final int BINNED_MERGE_THRESHOLD = 50;
	private static final double DEFAULT_BIN_WIDTH = 0.1;

	private RawSpectrumMergeUtils() {
	}

	public static AcquiredSpectrum mergeSpectra(List<? extends AcquiredSpectrum> spectra, MassTolerance tolerance) {
		if (spectra==null||spectra.isEmpty()) {
			return new PrecursorScan("Combined", 0, 0.0f, 0, 0.0, Double.MAX_VALUE, null, new double[0], new float[0], null);
		}
		if (spectra.size()>BINNED_MERGE_THRESHOLD) {
			return binnedMergeSpectra(spectra, DEFAULT_BIN_WIDTH);
		}
		return accurateMergeSpectra(spectra, tolerance);
	}

	public static AcquiredSpectrum binnedMergeSpectra(List<? extends AcquiredSpectrum> spectra, double binWidth) {
		double maxMz=0.0;
		for (AcquiredSpectrum spectrum : spectra) {
			double[] mz=spectrum.getMassArray();
			if (mz.length>0&&mz[mz.length-1]>maxMz) {
				maxMz=mz[mz.length-1];
			}
		}

		int binCount=(int)Math.ceil(maxMz/binWidth);
		if (binCount<=0) {
			return new PrecursorScan("Combined", 0, 0.0f, 0, 0.0, Double.MAX_VALUE, null, new double[0], new float[0], null);
		}

		float[] intensityBins=new float[binCount];
		float[] imsBins=new float[binCount];
		boolean anyIMS=false;

		float totalIIT=0.0f;
		float minRT=Float.MAX_VALUE;
		int minFraction=Integer.MAX_VALUE;
		double isolationWindowLower=Double.MAX_VALUE;
		double isolationWindowUpper=-Double.MAX_VALUE;

		for (AcquiredSpectrum spectrum : spectra) {
			minRT=Math.min(minRT, spectrum.getScanStartTime());
			Float iit=spectrum.getIonInjectionTime();
			if (iit!=null&&iit>0) totalIIT+=iit;
			minFraction=Math.min(minFraction, spectrum.getFraction());
			isolationWindowLower=Math.min(isolationWindowLower, spectrum.getIsolationWindowLower());
			isolationWindowUpper=Math.max(isolationWindowUpper, spectrum.getIsolationWindowUpper());

			double[] mz=spectrum.getMassArray();
			float[] intens=spectrum.getIntensityArray();
			Optional<float[]> imsOpt=spectrum.getIonMobilityArray();
			if (imsOpt.isPresent()) anyIMS=true;

			for (int i=0; i<mz.length; i++) {
				int index=(int)Math.round(mz[i]/binWidth);
				if (index<0) index=0;
				if (index>=intensityBins.length) index=intensityBins.length-1;
				float prev=intensityBins[index];
				intensityBins[index]+=intens[i];
				if (imsOpt.isPresent()&&intens[i]>0) {
					float imsVal=imsOpt.get()[i];
					imsBins[index]=(imsBins[index]*prev+imsVal*intens[i])/(prev+intens[i]);
				}
			}
		}

		if (minFraction==Integer.MAX_VALUE) minFraction=0;
		if (isolationWindowLower==Double.MAX_VALUE) isolationWindowLower=0.0;
		if (isolationWindowUpper<0) isolationWindowUpper=Double.MAX_VALUE;

		TDoubleArrayList masses=new TDoubleArrayList();
		TFloatArrayList intensities=new TFloatArrayList();
		TFloatArrayList imsOut=new TFloatArrayList();
		for (int i=0; i<intensityBins.length; i++) {
			if (intensityBins[i]>0.0f) {
				masses.add(i*binWidth);
				intensities.add(intensityBins[i]);
				if (anyIMS) imsOut.add(imsBins[i]);
			}
		}

		float[] imsArray=anyIMS?imsOut.toArray():null;
		return new PrecursorScan("Combined", 0, minRT, minFraction, isolationWindowLower, isolationWindowUpper, totalIIT, masses.toArray(), intensities.toArray(), imsArray);
	}

	public static AcquiredSpectrum accurateMergeSpectra(List<? extends AcquiredSpectrum> spectra, MassTolerance tolerance) {
		TDoubleArrayList masses=new TDoubleArrayList();
		TFloatArrayList intensities=new TFloatArrayList();
		TFloatArrayList ims=new TFloatArrayList();

		float totalIIT=0.0f;
		float averageRT=0.0f;
		int minFraction=Integer.MAX_VALUE;
		double isolationWindowLower=Double.MAX_VALUE;
		double isolationWindowUpper=-Double.MAX_VALUE;
		boolean anyIMS=false;

		for (AcquiredSpectrum spectrum : spectra) {
			averageRT+=spectrum.getScanStartTime();
			Float iit=spectrum.getIonInjectionTime();
			if (iit!=null&&iit>0) totalIIT+=iit;
			minFraction=Math.min(minFraction, spectrum.getFraction());
			isolationWindowLower=Math.min(isolationWindowLower, spectrum.getIsolationWindowLower());
			isolationWindowUpper=Math.max(isolationWindowUpper, spectrum.getIsolationWindowUpper());

			double[] mz=spectrum.getMassArray();
			float[] intens=spectrum.getIntensityArray();
			Optional<float[]> imsOpt=spectrum.getIonMobilityArray();
			if (imsOpt.isPresent()) anyIMS=true;

			for (int i=0; i<mz.length; i++) {
				int index=getIndex(masses, mz[i], tolerance);
				if (index<0) {
					int insertionPoint=-(index+1);
					masses.insert(insertionPoint, mz[i]);
					intensities.insert(insertionPoint, intens[i]);
					if (imsOpt.isPresent()) {
						ims.insert(insertionPoint, imsOpt.get()[i]);
					}
				} else {
					float prev=intensities.getQuick(index);
					intensities.setQuick(index, prev+intens[i]);
					if (imsOpt.isPresent()) {
						float newIMS=(ims.getQuick(index)*prev+imsOpt.get()[i]*intens[i])/(prev+intens[i]);
						ims.setQuick(index, newIMS);
					}
				}
			}
		}

		if (!spectra.isEmpty()) {
			averageRT/=spectra.size();
		}
		if (minFraction==Integer.MAX_VALUE) minFraction=0;
		if (isolationWindowLower==Double.MAX_VALUE) isolationWindowLower=0.0;
		if (isolationWindowUpper<0) isolationWindowUpper=Double.MAX_VALUE;

		float[] imsArray=anyIMS?ims.toArray():null;
		return new PrecursorScan("Combined", 0, averageRT, minFraction, isolationWindowLower, isolationWindowUpper, totalIIT, masses.toArray(), intensities.toArray(), imsArray);
	}

	private static int getIndex(TDoubleArrayList peaks, double target, MassTolerance tolerance) {
		if (peaks.isEmpty()) return -1;
		int value=peaks.binarySearch(target);
		if (value>=0) return value;

		int insertionPoint=-(value+1);
		if (insertionPoint>0) {
			if (tolerance.compareTo(peaks.get(insertionPoint-1), target)==0) {
				return insertionPoint-1;
			}
		}
		if (insertionPoint<peaks.size()) {
			if (tolerance.compareTo(peaks.get(insertionPoint), target)==0) {
				return insertionPoint;
			}
		}
		return value;
	}
}
