package org.searlelab.msrawjava.io.tims;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.searlelab.msrawjava.API;
import org.searlelab.msrawjava.algorithms.QuickMedian;
import org.searlelab.msrawjava.model.MassTolerance;
import org.searlelab.msrawjava.model.PeakInterface;
import org.searlelab.msrawjava.model.PeakWithIMS;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.map.hash.TFloatIntHashMap;

/**
 * TIMSPeakPicker implements ion-mobility–aware peak detection for timsTOF data, operating across the mobility dimension
 * after m/z calibration. It processes primitive traces to identify contiguous signal regions, selects apex positions,
 * and aggregates intensities into lightweight Peak tuples (mz, intensity, ims), producing deterministic outputs while
 * minimizing allocation so that downstream spectrum assembly remains fast and GC-friendly.
 */
@API(status = API.Status.STABLE, since = "v26.7.31")
public class TIMSPeakPicker {
	/**
	 * all peaks need to be "on", this will toggle some "off"
	 * 
	 * @param mzSortedPeaks
	 * @param minimumIntensity
	 * @return
	 */
	public static ArrayList<ArrayList<PeakWithIMS>> getIMSChromatograms(ArrayList<PeakWithIMS> mzSortedPeaks, float minimumIntensity) {
		MassTolerance tolerance=new TIMSMassTolerance();
		PeakWithIMS.PeakIMSComparator imsComparator=new PeakWithIMS.PeakIMSComparator();

		ArrayList<PeakWithIMS> intensitySortedPeaks=new ArrayList<>(mzSortedPeaks);

		mzSortedPeaks.sort(null); // sorted on m/z
		intensitySortedPeaks.sort(new PeakWithIMS.PeakIntensityComparator()); // sorted on intensity

		ArrayList<ArrayList<PeakWithIMS>> finalPeaks=new ArrayList<>();
		int lastPeakConsidered=intensitySortedPeaks.size();
		EACHPEAK: while (true) {
			PeakWithIMS targetPeak=null;
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
				int[] indicies=tolerance.getIndices(mzSortedPeaks, targetPeak);
				ArrayList<PeakWithIMS> imsSortedSlice=new ArrayList<>();
				for (int i=0; i<indicies.length; i++) {
					imsSortedSlice.add(mzSortedPeaks.get(indicies[i]));
				}
				Collections.sort(imsSortedSlice, imsComparator);
				finalPeaks.add(imsSortedSlice);

				for (PeakInterface peak : imsSortedSlice) {
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
	 * Greedily squashes an m/z window across all IMS values, without mobility filtering or fitting.
	 * The input is sorted in place by natural order (m/z, IMS, intensity). Eligible points satisfy
	 * {@code p.isAvailable() && p.intensity > 0f}; in particular, NaN intensity is excluded. Apexes are
	 * selected in descending {@link PeakWithIMS.PeakIntensityComparator} order, breaking intensity
	 * ties by descending m/z, then IMS. Each inclusive window is fixed at the original apex m/z plus
	 * or minus {@code tolerance.getToleranceInMz(apex.mz, apex.mz)/2.0}.
	 * <p>
	 * All eligible points in the window are consumed with {@code turnOff()} and their intensities
	 * summed in double precision, then cast once to float. Output m/z and IMS are intensity-weighted
	 * means of only the consumed points satisfying {@code p.intensity >= 0.5f*apex.intensity}, with
	 * the cutoff evaluated in float and all weighted arithmetic in double. The window and cutoff
	 * are never recentered. Repeated references contribute once; distinct equal objects contribute
	 * separately. Initially unavailable and nonpositive points retain their availability.
	 * <p>
	 * Returns at most {@link #MAX_PEAKS} new points in natural order. At the cap, all points not
	 * consumed by the reported groups retain their availability. Sorting the input costs O(n log n),
	 * and sorting u distinct intensity levels costs O(u log u). Exact-intensity buckets use expected
	 * O(n) hashing without rounding intensities. Window scans depend on overlap; scratch space is O(n).
	 *
	 * @param mzSortedPeaks input points, sorted in place and consumed through their original references
	 * @return squashed points sorted by natural order
	 */
	@API(status = API.Status.EXPERIMENTAL, since = "v26.11.25")
	public static ArrayList<PeakWithIMS> squashAcrossIMS(ArrayList<PeakWithIMS> mzSortedPeaks) {
		mzSortedPeaks.sort(null);
		int[] nextMember=new int[mzSortedPeaks.size()];
		// Zero intensity is ineligible; -1 terminates each exact-intensity bucket's member chain.
		TFloatIntHashMap heads=new TFloatIntHashMap(Math.min(mzSortedPeaks.size(), 4096), 0.5f, 0f, -1);
		int count=0;
		for (int i=0; i<mzSortedPeaks.size(); i++) {
			PeakWithIMS p=mzSortedPeaks.get(i);
			if (p.isAvailable()&&p.intensity>0f) {
				// Pushing in natural order makes ties descend by m/z, then IMS, without a second object sort.
				nextMember[i]=heads.put(p.intensity, i);
				count++;
			}
		}
		float[] intensityLevels=heads.keys();
		Arrays.sort(intensityLevels);
		ArrayList<PeakWithIMS> out=new ArrayList<>(Math.min(count, MAX_PEAKS/10));
		int level=intensityLevels.length-1, index=-1;
		while (out.size()<MAX_PEAKS) {
			if (index<0) {
				if (level<0) break;
				index=heads.get(intensityLevels[level--]);
			}
			PeakWithIMS apex=mzSortedPeaks.get(index);
			index=nextMember[index];
			if (!apex.isAvailable()) continue;
			double halfWidth=tolerance.getToleranceInMz(apex.mz, apex.mz)/2.0;
			int start=lowerBoundMz(mzSortedPeaks, apex.mz-halfWidth);
			int end=upperBoundMz(mzSortedPeaks, apex.mz+halfWidth);
			float halfMax=0.5f*apex.intensity;
			double sum=0.0, weight=0.0, weightedMz=0.0, weightedIMS=0.0;
			for (int i=start; i<end; i++) {
				PeakWithIMS p=mzSortedPeaks.get(i);
				if (p.isAvailable()&&p.intensity>0f) {
					double intensity=p.intensity;
					sum+=intensity;
					if (p.intensity>=halfMax) {
						weight+=intensity;
						weightedMz+=intensity*p.mz;
						weightedIMS+=intensity*p.ims;
					}
					p.turnOff();
				}
			}
			if (sum>0.0) out.add(new PeakWithIMS(weightedMz/weight, (float)sum, (float)(weightedIMS/weight)));
		}
		// Exhausting the levels and members, rather than counting consumed list entries, also handles aliases.
		out.sort(null);
		return out;
	}

	/**
	 * all peaks need to be "on", this will toggle some "off"
	 * 
	 * @param mzSortedPeaks
	 * @return
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public static ArrayList<PeakWithIMS> peakPickAcrossIMS(ArrayList<PeakWithIMS> mzSortedPeaks) {
		mzSortedPeaks.sort(null);

		ArrayList<PeakWithIMS> intensitySorted=new ArrayList<>(mzSortedPeaks);
		intensitySorted.sort(new PeakWithIMS.PeakIntensityComparator());
		ArrayList<PeakWithIMS> out=new ArrayList<>(MAX_PEAKS/10);

		// Scratch belongs to this invocation; points are consumed through their original references.
		TDoubleArrayList mzList=null;
		ArrayList<PeakWithIMS> imsPeaks=null;
		GaussianScratch gaussian=null;

		int totalIncluded=0; // count of consumed points

		// Navigate intensity in descending order
		for (int idx=intensitySorted.size()-1; idx>=0; idx--) {
			if (out.size()>=MAX_PEAKS) break;

			PeakWithIMS apex=intensitySorted.get(idx);
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

			if (mzList==null) {
				mzList=new TDoubleArrayList();
				imsPeaks=new ArrayList<>();
			} else {
				mzList.resetQuick();
				imsPeaks.clear();
			}
			for (int i=start; i<end; i++) {
				PeakWithIMS p=mzSortedPeaks.get(i);
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
				if (gaussian==null) gaussian=new GaussianScratch();
				float[] trace=smoothIMSGaussian(imsPeaks, 0.005f, gaussian);
				int maxIndex=0;
				for (int i=1; i<imsPeaks.size(); i++) {
					if (trace[i]>trace[maxIndex]) {
						maxIndex=i;
					}
				}
				ensembleIMS=imsPeaks.get(maxIndex).ims;
			}

			out.add(new PeakWithIMS(ensembleMz, sumIntensity, ensembleIMS));
			totalIncluded+=numIncludable;

			// Early exit when all consumed
			if (totalIncluded>=mzSortedPeaks.size()) break;
		}

		out.sort(null);
		return out;
	}

	private static float[] smoothIMSGaussian(List<PeakWithIMS> peaks, float sigma, GaussianScratch scratch) {
		final int n=peaks.size();
		scratch.ensureCapacity(n);
		final float[] out=scratch.out;
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
		final float[] ims=scratch.ims;
		final float[] y=scratch.y;
		for (int i=0; i<n; i++) {
			PeakWithIMS p=peaks.get(i);
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

	/** Reused only until peakPickAcrossIMS returns; every populated element is overwritten. */
	private static final class GaussianScratch {
		private float[] out;
		private float[] ims;
		private float[] y;

		private void ensureCapacity(int n) {
			if (out==null||out.length<n) {
				out=new float[n];
				ims=new float[n];
				y=new float[n];
			}
		}
	}

	// ---- helpers: m/z lower/upper bounds on a List<Peak> sorted by m/z ----
	private static int lowerBoundMz(ArrayList<PeakWithIMS> a, double keyMz) {
		int lo=0, hi=a.size();
		while (lo<hi) {
			int mid=(lo+hi)>>>1;
			if (a.get(mid).mz<keyMz) lo=mid+1;
			else hi=mid;
		}
		return lo; // first index with mz >= keyMz
	}

	private static int upperBoundMz(ArrayList<PeakWithIMS> a, double keyMz) {
		int lo=0, hi=a.size();
		while (lo<hi) {
			int mid=(lo+hi)>>>1;
			if (a.get(mid).mz<=keyMz) lo=mid+1;
			else hi=mid;
		}
		return lo; // first index with mz > keyMz
	}
}
