package org.searlelab.msrawjava.io.tims;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.searlelab.msrawjava.algorithms.QuickMedian;
import org.searlelab.msrawjava.model.MassTolerance;
import org.searlelab.msrawjava.model.Peak;

import gnu.trove.list.array.TDoubleArrayList;

/**
 * TIMSPeakPicker implements ion-mobility–aware peak detection for timsTOF data, operating across the mobility dimension
 * after m/z calibration. It processes primitive traces to identify contiguous signal regions, selects apex positions,
 * and aggregates intensities into lightweight Peak tuples (mz, intensity, ims), producing deterministic outputs while
 * minimizing allocation so that downstream spectrum assembly remains fast and GC-friendly.
 */
public class TIMSPeakPicker {
	/**
	 * all peaks need to be "on", this will toggle some "off"
	 * @param mzSortedPeaks
	 * @param minimumIntensity
	 * @return
	 */
	public static ArrayList<ArrayList<Peak>> getIMSChromatograms(ArrayList<Peak> mzSortedPeaks, float minimumIntensity) {
		MassTolerance tolerance=new TIMSMassTolerance();
		Peak.PeakIMSComparator imsComparator=new Peak.PeakIMSComparator();

		ArrayList<Peak> intensitySortedPeaks=new ArrayList<Peak>(mzSortedPeaks);

		mzSortedPeaks.sort(null); // sorted on m/z
		intensitySortedPeaks.sort(new Peak.PeakIntensityComparator()); // sorted on intensity

		ArrayList<ArrayList<Peak>> finalPeaks=new ArrayList<ArrayList<Peak>>();
		int lastPeakConsidered=intensitySortedPeaks.size();
		EACHPEAK: while (true) {
			Peak targetPeak=null;
			for (int i=lastPeakConsidered-1; i>0; i--) {
				if (intensitySortedPeaks.get(i).isAvailable()) {
					targetPeak=intensitySortedPeaks.get(i);
					lastPeakConsidered=i;
					break;
				}
			}

			if (targetPeak==null) {
				break EACHPEAK;
			} else {
				int[] indicies=tolerance.getIndicies(mzSortedPeaks, targetPeak);
				ArrayList<Peak> imsSortedSlice=new ArrayList<Peak>();
				for (int i=0; i<indicies.length; i++) {
					imsSortedSlice.add(mzSortedPeaks.get(indicies[i]));
				}
				Collections.sort(imsSortedSlice, imsComparator);
				finalPeaks.add(imsSortedSlice);
				
				for (Peak peak : imsSortedSlice) {
					peak.turnOff();
				}
			}
		}
		return finalPeaks;
	}


	public static final MassTolerance tolerance=new TIMSMassTolerance();
	public static final float IM_TOL_PCT=3.0f;
	public static final int MAX_PEAKS=10000;

	/**
	 * all peaks need to be "on", this will toggle some "off"
	 * @param mzSortedPeaks
	 * @return
	 */
	public static ArrayList<Peak> peakPickAcrossIMS(ArrayList<Peak> mzSortedPeaks) {
		mzSortedPeaks.sort(null);

		ArrayList<Peak> intensitySorted=new ArrayList<>(mzSortedPeaks);
		intensitySorted.sort(new Peak.PeakIntensityComparator());
		ArrayList<Peak> out=new ArrayList<>(MAX_PEAKS/10);

		int totalIncluded=0; // count of consumed points

		// Navigate intensity in descending order
		for (int idx=intensitySorted.size()-1; idx>=0; idx--) {
			if (out.size()>=MAX_PEAKS) break;

			Peak apex=intensitySorted.get(idx);
			if (!apex.isAvailable()||apex.intensity<=0f) continue;

			final double mzApex=apex.mz;
			final float imApex=apex.ims;

			// m/z +/- ppm window
			final double daTol=tolerance.getToleranceInMz(mzApex, mzApex)/2.0;
			final double leftMz=mzApex-daTol;
			final double rightMz=mzApex+daTol;

			// IM +/- percent window
			final float absImTol=imApex*(IM_TOL_PCT/100.0f);
			final float leftIm=imApex-absImTol;
			final float rightIm=imApex+absImTol;

			// Tight m/z slice using binary search bounds
			final int start=lowerBoundMz(mzSortedPeaks, leftMz);
			final int end=upperBoundMz(mzSortedPeaks, rightMz);

			float sumIntensity=0f;
			int numIncludable=0;

			TDoubleArrayList mzList=new TDoubleArrayList();
			ArrayList<Peak> imsPeaks=new ArrayList<Peak>();
			for (int i=start; i<end; i++) {
				Peak p=mzSortedPeaks.get(i);
				if (p.isAvailable()&&p.intensity>0f&&p.ims>=leftIm&&p.ims<=rightIm) {
					sumIntensity+=p.intensity;
					mzList.add(p.mz);
					p.turnOff();
					numIncludable++;
					imsPeaks.add(p);
				}
			}
			
			if (numIncludable==0) continue;
			
			double ensembleMz=QuickMedian.median(mzList.toArray());

			float ensembleIMS=imApex;
			if (imsPeaks.size()>1) {
				float[] trace=smoothIMSGaussian(imsPeaks, 0.005f);
				int maxIndex=0;
				for (int i=1; i<trace.length; i++) {
					if (trace[i]>trace[maxIndex]) {
						maxIndex=i;
					}
				}
				ensembleIMS=imsPeaks.get(maxIndex).ims;
			}

			out.add(new Peak(ensembleMz, sumIntensity, ensembleIMS));
			totalIncluded+=numIncludable;

			// Early exit when all consumed
			if (totalIncluded>=mzSortedPeaks.size()) break;
		}

		out.sort(null);
		return out;
	}

	private static float[] smoothIMSGaussian(List<Peak> peaks, float sigma) {
		final int n=peaks.size();
		final float[] out=new float[n];
		if (n==0) return out;
		if (n==1) {
			out[0]=peaks.get(0).intensity;
			return out;
		}
		if (!(sigma>0.0f)) {
			for (int i=0; i<n; i++)
				out[i]=peaks.get(i).intensity;
			return out;
		}

		// Pull to primitive arrays
		final float[] ims=new float[n];
		final float[] y=new float[n];
		for (int i=0; i<n; i++) {
			Peak p=peaks.get(i);
			ims[i]=p.ims;
			y[i]=p.intensity;
		}

		final float invTwoSigma2=1.0f/(2.0f*sigma*sigma);
		final float cutoff=2.0f*sigma;

		int start=0; // inclusive
		int end=0; // exclusive

		for (int i=0; i<n; i++) {
			final float x=ims[i];
			final float lo=x-cutoff;
			final float hi=x+cutoff;

			while (start<n&&ims[start]<lo)
				start++;
			while (end<n&&ims[end]<=hi)
				end++;

			float wsum=0.0f;
			float ysum=0.0f;

			for (int j=start; j<end; j++) {
				final float dx=ims[j]-x;
				final float w=(float)Math.exp(-(dx*dx)*invTwoSigma2);
				wsum+=w;
				ysum+=w*y[j];
			}

			out[i]=(wsum>0.0f)?(ysum/wsum):y[i];
		}
		return out;
	}

	// ---- helpers: m/z lower/upper bounds on a List<Peak> sorted by m/z ----
	private static int lowerBoundMz(ArrayList<Peak> a, double keyMz) {
		int lo=0, hi=a.size();
		while (lo<hi) {
			int mid=(lo+hi)>>>1;
			if (a.get(mid).mz<keyMz) lo=mid+1;
			else hi=mid;
		}
		return lo; // first index with mz >= keyMz
	}

	private static int upperBoundMz(ArrayList<Peak> a, double keyMz) {
		int lo=0, hi=a.size();
		while (lo<hi) {
			int mid=(lo+hi)>>>1;
			if (a.get(mid).mz<=keyMz) lo=mid+1;
			else hi=mid;
		}
		return lo; // first index with mz > keyMz
	}
}
