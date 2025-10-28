package org.searlelab.msrawjava.algorithms;

import java.util.ArrayList;
import java.util.Arrays;

import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.MassTolerance;
import org.searlelab.msrawjava.model.Peak;
import org.searlelab.msrawjava.model.PeakInTime;
import org.searlelab.msrawjava.model.Range;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;

public class StaggeredDemultiplexer {
	private static final float windowBoundaryTolerance=0.01f;

	private final Range[] ranges;
	private final MassTolerance tolerance;

	public StaggeredDemultiplexer(ArrayList<Range> acquiredWindows, MassTolerance tolerance) {
		this.tolerance=tolerance;

		acquiredWindows.sort(null);
		ArrayList<RangeCounter> subRanges=getSubRanges(acquiredWindows);

		ranges=new Range[subRanges.size()];
		for (int i=0; i<ranges.length; i++) {
			ranges[i]=subRanges.get(i).range;
		}
	}

	/**
	 * Each cycle must be in m/z sorted order to match the original acquired windows. No windows are allowed to be
	 * missing!
	 * 
	 * @param cycleM2
	 * @param cycleM1
	 * @param cycleP1
	 * @param cycleP2
	 * @return
	 */
	public ArrayList<FragmentScan> demultiplex(ArrayList<FragmentScan> cycleM2, ArrayList<FragmentScan> cycleM1, ArrayList<FragmentScan> cycleP1,
			ArrayList<FragmentScan> cycleCenter, ArrayList<FragmentScan> cycleP2, int currentScanNumber) {
		assert (cycleCenter.size()==cycleM1.size());
		assert (cycleCenter.size()==cycleM2.size());
		assert (cycleCenter.size()==cycleP1.size());
		assert (cycleCenter.size()==cycleP2.size());

//		System.out.print("m2:"+cycleM2.get(0).getScanStartTime()+"("+cycleM2.size()+")");
//		System.out.print("\tm1:"+cycleM1.get(0).getScanStartTime()+"("+cycleM1.size()+")");
//		System.out.print("\tCENTER:"+cycleCenter.get(0).getScanStartTime()+"("+cycleCenter.size()+")");
//		System.out.print("\tp1:"+cycleP1.get(0).getScanStartTime()+"("+cycleP1.size()+")");
//		System.out.print("\tp2:"+cycleP2.get(0).getScanStartTime()+"("+cycleP2.size()+")"+", [");
//		for (FragmentScan msms : cycleP1) {
//			System.out.print("\t"+(int)msms.getPrecursorRange().getMiddle());
//		}
//		System.out.println("]");

		float rtCenter=0;
		for (FragmentScan fragmentScan : cycleM1) {
			rtCenter+=fragmentScan.getScanStartTime();
		}
		for (FragmentScan fragmentScan : cycleP1) {
			rtCenter+=fragmentScan.getScanStartTime();
		}
		rtCenter=rtCenter/(cycleM1.size()+cycleM2.size());

		ArrayList<ArrayList<HermitePeakIntensityInterpolator>> interpolatablePeaksForEachMSMS=new ArrayList<ArrayList<HermitePeakIntensityInterpolator>>();

		for (int i=0; i<cycleCenter.size(); i++) {
			// for each window
			ArrayList<PeakInTime> m2Peaks=getPeaksInMzOrder(cycleM2.get(i));
			ArrayList<PeakInTime> m1Peaks=getPeaksInMzOrder(cycleM1.get(i));
			ArrayList<PeakInTime> centerPeaks=getPeaksInMzOrder(cycleCenter.get(i));
			ArrayList<PeakInTime> p1Peaks=getPeaksInMzOrder(cycleP1.get(i));
			ArrayList<PeakInTime> p2Peaks=getPeaksInMzOrder(cycleP2.get(i));

			ArrayList<HermitePeakIntensityInterpolator> interpolatedPeaks=new ArrayList<HermitePeakIntensityInterpolator>();

			centerPeaks.sort(PeakInTime.INTENSITY_COMPARATOR);

			for (int j=centerPeaks.size()-1; j>=0; j--) {
				PeakInTime nextBest=centerPeaks.get(j);
				if (!nextBest.isAvailable()||nextBest.intensity<=0f) continue;

				PeakInTime m2=getPeakIntensity(m2Peaks, nextBest, cycleM2.get(i).getScanStartTime());
				PeakInTime m1=getPeakIntensity(m1Peaks, nextBest, cycleM1.get(i).getScanStartTime());
				PeakInTime p1=getPeakIntensity(p1Peaks, nextBest, cycleP1.get(i).getScanStartTime());
				PeakInTime p2=getPeakIntensity(p2Peaks, nextBest, cycleP2.get(i).getScanStartTime());

				HermitePeakIntensityInterpolator interpolator=new HermitePeakIntensityInterpolator(new PeakInTime[] {m2, m1, nextBest, p1, p2}, nextBest.mz);
				interpolatedPeaks.add(interpolator);
			}
			interpolatedPeaks.sort(null); // sort on m/z

			interpolatablePeaksForEachMSMS.add(interpolatedPeaks);
		}

		ArrayList<FragmentScan> demuxMSMS=new ArrayList<FragmentScan>();
		for (int i=0; i<cycleCenter.size(); i++) {
			ArrayList<HermitePeakIntensityInterpolator> left2Peaks=i<2?null:interpolatablePeaksForEachMSMS.get(i-2);
			ArrayList<HermitePeakIntensityInterpolator> left1Peaks=i<1?null:interpolatablePeaksForEachMSMS.get(i-1);
			//ArrayList<HermitePeakIntensityInterpolator> center=interpolatablePeaksForEachMSMS.get(i);
			ArrayList<HermitePeakIntensityInterpolator> right1Peaks=i+1>=interpolatablePeaksForEachMSMS.size()?null:interpolatablePeaksForEachMSMS.get(i+1);
			ArrayList<HermitePeakIntensityInterpolator> right2Peaks=i+2>=interpolatablePeaksForEachMSMS.size()?null:interpolatablePeaksForEachMSMS.get(i+2);

			ArrayList<Peak> leftPeaks=new ArrayList<Peak>();
			ArrayList<Peak> rightPeaks=new ArrayList<Peak>();

			FragmentScan msms=cycleCenter.get(i);
			ArrayList<PeakInTime> centerPeaks=getPeaksInMzOrder(msms);
			for (PeakInTime peak : centerPeaks) {
				float left2Intensity=getInterpolatedPeakIntensity(left2Peaks, peak, msms.getScanStartTime());
				float left1Intensity=getInterpolatedPeakIntensity(left1Peaks, peak, msms.getScanStartTime());
				float right1Intensity=getInterpolatedPeakIntensity(right1Peaks, peak, msms.getScanStartTime());
				float right2Intensity=getInterpolatedPeakIntensity(right2Peaks, peak, msms.getScanStartTime());

				// farthest left window always claims all unclaimed peaks, farthest right window does the same. These windows don't get noise rejection, sadly
				float leftIntensity=Math.max(i==0?1.0f:0.0f, left1Intensity-left2Intensity);
				float rightIntensity=Math.max(i==cycleCenter.size()-1?1.0f:0.0f, right1Intensity-right2Intensity);
				float denom=leftIntensity+rightIntensity;
				if (denom>0) {
					// ignore as noise if we don't observe it above or below
					float percentLeft=leftIntensity/denom;
					float minimumPercentForDemux=0.01f;
					if (percentLeft<minimumPercentForDemux) {
						percentLeft=0.0f;
					} else if (percentLeft>(1.0f-minimumPercentForDemux)) {
						percentLeft=1.0f;
					}
					float percentRight=1.0f-percentLeft;

					if (percentLeft>0.0f) {
						leftPeaks.add(new Peak(peak.mz, percentLeft*peak.intensity));
					}
					if (percentRight>0.0f) {
						rightPeaks.add(new Peak(peak.mz, percentRight*peak.intensity));
					}
				}
			}

			double middleIsolationBoundary=i>0?cycleCenter.get(i-1).getIsolationWindowUpper():cycleCenter.get(i+1).getIsolationWindowLower();

			FragmentScan leftMSMS=msms.rebuild(currentScanNumber++, msms.getScanStartTime(), leftPeaks, msms.getIsolationWindowLower(), middleIsolationBoundary);
			FragmentScan rightMSMS=msms.rebuild(currentScanNumber++, msms.getScanStartTime(), rightPeaks, middleIsolationBoundary, msms.getIsolationWindowUpper());
			
//			System.out.println(msms.getScanStartTime()+"\t"+msms.getIsolationWindowLower()+" to "+msms.getIsolationWindowUpper());
//			if (i>2) {
//				System.out.println(msms);
//				System.out.println(leftMSMS);
//				System.out.println(rightMSMS);
//				System.exit(1);
//			}
			
			demuxMSMS.add(leftMSMS);
			demuxMSMS.add(rightMSMS);
		}

		return demuxMSMS;
	}

	/**
	 * tolerates null and empty peaklists (will return a zero intensity peak)
	 * 
	 * @param peaks
	 * @param nextBest
	 * @param rtInSec
	 * @return
	 */
	private float getInterpolatedPeakIntensity(ArrayList<HermitePeakIntensityInterpolator> peaks, PeakInTime nextBest, float rtInSec) {
		if (peaks==null) return 0.0f;
		int[] indices=tolerance.getIndices(peaks, nextBest);
		float totalIntensity=0.0f;
		for (int j=0; j<indices.length; j++) {
			HermitePeakIntensityInterpolator peak=peaks.get(indices[j]);
			if (peak.isAvailable()) {
				totalIntensity+=peak.getIntensity(nextBest.getRtInSec());
				peak.turnOff();
			}
		}
		return totalIntensity;
	}

	/**
	 * tolerates empty peaklists (will return a zero intensity peak)
	 * 
	 * @param peaks
	 * @param nextBest
	 * @param rtInSec
	 * @return
	 */
	private PeakInTime getPeakIntensity(ArrayList<PeakInTime> peaks, PeakInTime nextBest, float rtInSec) {
		int[] indices=tolerance.getIndices(peaks, nextBest);
		float totalIntensity=0.0f;
		for (int j=0; j<indices.length; j++) {
			PeakInTime peak=peaks.get(indices[j]);
			if (peak.isAvailable()) {
				totalIntensity+=peak.getIntensity();
				peak.turnOff();
			}
		}
		return new PeakInTime(nextBest.getMz(), totalIntensity, rtInSec);
	}

	private ArrayList<PeakInTime> getPeaksInMzOrder(FragmentScan msms) {
		double[] mzs=msms.getMassArray();
		float[] intensities=msms.getIntensityArray();
		ArrayList<PeakInTime> m2Peaks=new ArrayList<PeakInTime>(mzs.length);
		for (int j=0; j<mzs.length; j++) {
			m2Peaks.add(new PeakInTime(mzs[j], intensities[j], msms.getScanStartTime()));
		}
		return m2Peaks;
	}

	public static ArrayList<RangeCounter> getSubRanges(ArrayList<Range> acquiredWindows) {
		TFloatArrayList boundaries=new TFloatArrayList();
		for (Range range : acquiredWindows) {
			boundaries.add(range.getStart());
			boundaries.add(range.getStop());
		}
		boundaries.sort();
		float anchor=boundaries.getQuick(0);

		// subRanges are implicitly sorted
		ArrayList<RangeCounter> subRanges=new ArrayList<RangeCounter>();
		for (int i=1; i<boundaries.size(); i++) {
			float v=boundaries.getQuick(i);
			if (v-anchor>windowBoundaryTolerance) {
				subRanges.add(new RangeCounter(new Range(anchor, v)));
				anchor=v;
			}
		}

		float[] centers=new float[subRanges.size()];
		for (int i=0; i<centers.length; i++) {
			centers[i]=subRanges.get(i).range.getMiddle();
		}

		for (int i=0; i<acquiredWindows.size(); i++) {
			Range range=acquiredWindows.get(i);
			int[] indicies=getIndicies(centers, range);
			for (int j=0; j<indicies.length; j++) {
				subRanges.get(indicies[j]).addRange(range, i);
			}
		}
		return subRanges;
	}

	public static int[] getIndicies(float[] centers, Range target) {
		int value=Arrays.binarySearch(centers, target.getMiddle());
		// exact match (not likely)
		if (value<0) {
			// insertion point
			value=-(value+1);
		}

		TIntArrayList matches=new TIntArrayList();
		// look below
		int index=value;
		while (index>0&&target.contains(centers[index-1])) {
			matches.add(index-1);
			index--;
		}

		// look up
		index=value;
		while (index<centers.length&&target.contains(centers[index])) {
			matches.add(index);
			index++;
		}

		return matches.toArray();
	}
}
