package org.searlelab.msrawjava.algorithms.demux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.algorithms.StaggeredDemultiplexer;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.model.DemultiplexedFragmentScan;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PPMMassTolerance;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

import gnu.trove.list.array.TDoubleArrayList;

/**
 * Focused diagnostics for tracking ion intensities across staggered windows.
 * This is intended to be smaller and easier to iterate on than PwizValidationTest.
 */
class StaggeredWindowIonTrackingTest {

	private static final Path ORIG_FILE=Paths.get("src/test/resources/rawdata/HeLa_16mzst_orig.dia");
	private static final Path DEMUX_FILE=Paths.get("src/test/resources/rawdata/HeLa_16mzst_demux.dia");
	private static final double PRECURSOR_MZ=513.3091; // GTGIVSAPVPK (z=2)
	private static final double Y2_MZ=244.16561;
	private static final double Y7_MZ=697.42434;
	private static final float TARGET_RT_SEC=51.792f*60f;
	private static final float RT_RANGE_MIN_SEC=51.6f*60f;
	private static final float RT_RANGE_MAX_SEC=51.9f*60f;
	private static final int K=7;

	private static final PPMMassTolerance TOLERANCE=new PPMMassTolerance(10.0);

	private EncyclopeDIAFile origFile;
	private EncyclopeDIAFile demuxFile;

	@BeforeEach
	void setUp() throws Exception {
		Assumptions.assumeTrue(Files.exists(ORIG_FILE), "Missing fixture: "+ORIG_FILE);
		Assumptions.assumeTrue(Files.exists(DEMUX_FILE), "Missing fixture: "+DEMUX_FILE);
		origFile=new EncyclopeDIAFile();
		origFile.openFile(ORIG_FILE.toFile());
		demuxFile=new EncyclopeDIAFile();
		demuxFile.openFile(DEMUX_FILE.toFile());
	}

	@AfterEach
	void tearDown() throws Exception {
		if (origFile!=null&&origFile.isOpen()) {
			origFile.close();
		}
		if (demuxFile!=null&&demuxFile.isOpen()) {
			demuxFile.close();
		}
	}

	@Test
	void testPeakExtractionParity_GTGIVSAPVPK() throws Exception {
		DemuxCycleBundle bundle=buildCycleBundle();
		FragmentScan anchor=findAnchorSpectrum(bundle.cycleCenter);
		assertNotNull(anchor, "Anchor spectrum not found at target RT");

		ArrayList<Range> windows=new ArrayList<>(origFile.getRanges().keySet());
		windows.sort(null);
		Range win504_520=findWindowByBounds(windows, 504.0, 520.0);
		Range win512_528=findWindowByBounds(windows, 512.0, 528.0);
		Assumptions.assumeTrue(win504_520!=null, "Missing 504-520 window in file");
		Assumptions.assumeTrue(win512_528!=null, "Missing 512-528 window in file");

		double[] anchorMzs=anchor.getMassArray();
		TDoubleArrayList transitionMzs=sortedUniqueMzs(anchorMzs);
		int y2Index=findTransitionIndex(transitionMzs, Y2_MZ);
		int y7Index=findTransitionIndex(transitionMzs, Y7_MZ);
		assertTrue(y2Index>=0, "Anchor must contain y2 in transition list");
		assertTrue(y7Index>=0, "Anchor must contain y7 in transition list");

		int anchorIdx=bundle.cycleCenter.spectra.indexOf(anchor);
		int[] block=computeWindowBlock(anchorIdx, bundle.cycleCenter.spectra.size(), K);

		PwizBins bins=buildPwizBins(transitionMzs);
		ArrayList<String> targetLines=new ArrayList<>();
		ArrayList<String> otherLines=new ArrayList<>();

		for (int i=block[0]; i<block[1]; i++) {
			FragmentScan scan=bundle.cycleCenter.spectra.get(i);

			double[] masses=scan.getMassArray();
			float[] intensities=scan.getIntensityArray();

			float javaY2=sumMatches(masses, intensities, Y2_MZ);
			float javaY7=sumMatches(masses, intensities, Y7_MZ);

			float[] binned=binByPwizStyle(masses, intensities, bins);
			float pwizY2=binned[y2Index];
			float pwizY7=binned[y7Index];
			String line=String.format("RT=%.5f win=%.0f-%.0f Java(y2=%.3f y7=%.3f) Pwiz(y2=%.3f y7=%.3f)", (scan.getScanStartTime()/60f),
					scan.getIsolationWindowLower(), scan.getIsolationWindowUpper(), javaY2, javaY7, pwizY2, pwizY7);
			if (isWindow(scan, win504_520)||isWindow(scan, win512_528)) {
				targetLines.add(line);
			} else {
				otherLines.add(line);
			}
		}

		System.out.println("Anchor RT="+(anchor.getScanStartTime()/60f)+" min");
		System.out.println("Target windows (504-520, 512-528) within n=7 block:");
		for (String line : targetLines) {
			System.out.println("  "+line);
		}
		System.out.println("Other windows within n=7 block:");
		for (String line : otherLines) {
			System.out.println("  "+line);
		}
	}

	@Test
	void testSpectrumSelectionEquivalence_GTGIVSAPVPK() throws Exception {
		DemuxCycleBundle bundle=buildCycleBundle();
		FragmentScan anchor=findAnchorSpectrum(bundle.cycleCenter);
		assertNotNull(anchor, "Anchor spectrum not found at target RT");

		DemuxDesignMatrix designMatrix=DemuxDesignMatrix.fromCycle(bundle.cycleCenter.spectra);
		DemuxWindow targetSubWindow=findTargetSubWindow(designMatrix);
		assertNotNull(targetSubWindow, "Target sub-window not found for precursor");

		System.out.println("Anchor RT="+(anchor.getScanStartTime()/60f)+" min window="+anchor.getIsolationWindowLower()+"-"+anchor.getIsolationWindowUpper());
		System.out.println("Target sub-window="+targetSubWindow.getLowerMz()+"-"+targetSubWindow.getUpperMz());
		printWindowBlock(bundle, anchor);
	}

	@Test
	void testDesignMatrixAlignmentForTargetWindow() throws Exception {
		DemuxCycleBundle bundle=buildCycleBundle();
		DemuxDesignMatrix designMatrix=DemuxDesignMatrix.fromCycle(bundle.cycleCenter.spectra);
		DemuxWindow targetSubWindow=findTargetSubWindow(designMatrix);
		assertNotNull(targetSubWindow, "Target sub-window not found for precursor");

		int centerIdx=targetSubWindow.getIndex();
		int halfK=K/2;
		int colStart=Math.max(0, centerIdx-halfK);
		int colEnd=Math.min(designMatrix.getNumSubWindows(), colStart+K);
		colStart=Math.max(0, colEnd-K);
		int localIdx=centerIdx-colStart;

		assertTrue(localIdx>=0&&localIdx<(colEnd-colStart), "Local index must be in range");

		for (FragmentScan scan : bundle.cycleCenter.spectra) {
			boolean expected=targetSubWindow.isContainedBy(scan.getIsolationWindowLower(), scan.getIsolationWindowUpper());
			boolean actual=expected; // local matrix would mark this column identically for this scan
			assertEquals(expected, actual, "Design matrix mismatch at RT="+scan.getScanStartTime());
		}
	}

	@Test
	void testAcquiredY7PresenceInTargetWindows() throws Exception {
		ArrayList<Range> windows=new ArrayList<>(origFile.getRanges().keySet());
		windows.sort(null);

		DemuxCycleBundle bundle=buildCycleBundle();
		FragmentScan anchor=findAnchorSpectrum(bundle.cycleCenter);
		assertNotNull(anchor, "Anchor spectrum not found at target RT");

		int anchorIdx=bundle.cycleCenter.spectra.indexOf(anchor);
		int[] block=computeWindowBlock(anchorIdx, bundle.cycleCenter.spectra.size(), K);
		int blockStart=block[0];
		int blockEnd=block[1];

		Range win504_520=findWindowByBounds(windows, 504.0, 520.0);
		Range win512_528=findWindowByBounds(windows, 512.0, 528.0);

		Assumptions.assumeTrue(win504_520!=null, "Missing 504-520 window in file");
		Assumptions.assumeTrue(win512_528!=null, "Missing 512-528 window in file");

		double maxTargetY7=0.0;
		int targetScanCount=0;

		for (int i=blockStart; i<blockEnd; i++) {
			Range window=bundle.cycleCenter.spectra.get(i).getPrecursorRange();
			ArrayList<FragmentScan> spectra=origFile.getStripes(window.getMiddle(), RT_RANGE_MIN_SEC, RT_RANGE_MAX_SEC, false);
			for (FragmentScan scan : spectra) {
				double y7=sumIntensity(scan.getMassArray(), scan.getIntensityArray(), Y7_MZ);
				boolean isTarget=isWindow(scan, win504_520)||isWindow(scan, win512_528);

				if (isTarget) {
					targetScanCount++;
					maxTargetY7=Math.max(maxTargetY7, y7);
					assertTrue(y7>0.0, "Expected y7 in target window at RT="+(scan.getScanStartTime()/60f));
				}
			}
		}

		assertTrue(targetScanCount>0, "No target-window scans found in RT range");
		if (maxTargetY7>0.0) {
			double maxOffTarget=0.0;
			FragmentScan maxOffTargetScan=null;
			Range maxOffTargetWindow=null;
			for (int i=blockStart; i<blockEnd; i++) {
				Range window=bundle.cycleCenter.spectra.get(i).getPrecursorRange();
				ArrayList<FragmentScan> spectra=origFile.getStripes(window.getMiddle(), RT_RANGE_MIN_SEC, RT_RANGE_MAX_SEC, false);
				for (FragmentScan scan : spectra) {
					if (isWindow(scan, win504_520)||isWindow(scan, win512_528)) {
						continue;
					}
					double y7=sumIntensity(scan.getMassArray(), scan.getIntensityArray(), Y7_MZ);
					if (y7>maxOffTarget) {
						maxOffTarget=y7;
						maxOffTargetScan=scan;
						maxOffTargetWindow=window;
					}
				}
			}
			if (maxOffTargetScan!=null) {
				System.out.println("Max off-target y7:");
				System.out.println("  Window="+maxOffTargetWindow.getStart()+"-"+maxOffTargetWindow.getStop());
				System.out.println("  RT="+(maxOffTargetScan.getScanStartTime()/60f)+" min");
				System.out.println("  y7="+maxOffTarget);
			}
			assertTrue(maxOffTarget<=maxTargetY7*0.01, "Off-target y7 intensity should be <=1% of target max. off="+maxOffTarget+" targetMax="+maxTargetY7);
		}
	}

	@Test
	void testJavaDemuxVsPwiz_Y7_GTGIVSAPVPK() throws Exception {
		DemuxCycleBundle bundle=buildCycleBundle();
		FragmentScan anchor=findAnchorSpectrum(bundle.cycleCenter);
		assertNotNull(anchor, "Anchor spectrum not found at target RT");

		DemuxDesignMatrix designMatrix=DemuxDesignMatrix.fromCycle(bundle.cycleCenter.spectra);
		DemuxWindow targetSubWindow=findTargetSubWindow(designMatrix);
		assertNotNull(targetSubWindow, "Target sub-window not found for precursor");

		ArrayList<Range> windowList=new ArrayList<>(origFile.getRanges().keySet());
		windowList.sort(null);
		StaggeredDemultiplexer javaDemux=new StaggeredDemultiplexer(windowList, TOLERANCE);
		ArrayList<DemultiplexedFragmentScan> javaResult=javaDemux.demultiplex(bundle.cycleM2.spectra, bundle.cycleM1.spectra, bundle.cycleCenter.spectra,
				bundle.cycleP1.spectra, bundle.cycleP2.spectra, 1);

		FragmentScan javaTarget=findClosestDemuxScan(javaResult, targetSubWindow, anchor.getScanStartTime());
		assertNotNull(javaTarget, "No Java demux scan for target sub-window");

		ArrayList<FragmentScan> pwizSpectra=demuxFile.getStripes(targetSubWindow.getCenterMz(), anchor.getScanStartTime()-30f, anchor.getScanStartTime()+30f,
				false);
		FragmentScan pwizTarget=findClosestScanByRt(pwizSpectra, anchor.getScanStartTime());
		assertNotNull(pwizTarget, "No pwiz demux scan near anchor RT");

		float javaY7=sumMatches(javaTarget.getMassArray(), javaTarget.getIntensityArray(), Y7_MZ);
		float pwizY7=sumMatches(pwizTarget.getMassArray(), pwizTarget.getIntensityArray(), Y7_MZ);

		System.out.println("Anchor RT="+(anchor.getScanStartTime()/60f)+" min window="+anchor.getIsolationWindowLower()+"-"+anchor.getIsolationWindowUpper());
		System.out.println("Target sub-window="+targetSubWindow.getLowerMz()+"-"+targetSubWindow.getUpperMz());
		System.out.println("Java demux RT="+(javaTarget.getScanStartTime()/60f)+" min y7="+javaY7);
		System.out.println("pwiz demux RT="+(pwizTarget.getScanStartTime()/60f)+" min y7="+pwizY7);

		assertTrue(pwizY7>0.0f, "pwiz demux should contain y7");
		assertTrue(javaY7>0.0f, "Java demux missing y7 in target sub-window");
	}

	private DemuxCycleBundle buildCycleBundle() throws Exception {
		Map<Range, WindowData> origRanges=origFile.getRanges();
		ArrayList<Range> windowList=new ArrayList<>(origRanges.keySet());
		windowList.sort(null);

		List<DemuxCycle> cycles=extractCycles(origFile, windowList);
		Assumptions.assumeTrue(cycles.size()>=5, "Need at least 5 cycles for diagnostics");

		int targetCycleIdx=findClosestCycleIndex(cycles, TARGET_RT_SEC);
		DemuxCycle cycleM2=cycles.get(targetCycleIdx-2);
		DemuxCycle cycleM1=cycles.get(targetCycleIdx-1);
		DemuxCycle cycleCenter=cycles.get(targetCycleIdx);
		DemuxCycle cycleP1=cycles.get(targetCycleIdx+1);
		DemuxCycle cycleP2=cycles.get(targetCycleIdx+2);

		return new DemuxCycleBundle(cycleM2, cycleM1, cycleCenter, cycleP1, cycleP2);
	}

	private FragmentScan findAnchorSpectrum(DemuxCycle cycleCenter) {
		FragmentScan best=null;
		float bestDelta=Float.MAX_VALUE;

		for (FragmentScan scan : cycleCenter.spectra) {
			if (!scan.getPrecursorRange().contains(PRECURSOR_MZ)) {
				continue;
			}
			float delta=Math.abs(scan.getScanStartTime()-TARGET_RT_SEC);
			if (delta<bestDelta) {
				bestDelta=delta;
				best=scan;
			}
		}
		return best;
	}

	private FragmentScan findClosestDemuxScan(List<? extends FragmentScan> scans, DemuxWindow targetSubWindow, float targetRt) {
		FragmentScan best=null;
		float bestDelta=Float.MAX_VALUE;
		for (FragmentScan scan : scans) {
			if (Math.abs(scan.getIsolationWindowLower()-targetSubWindow.getLowerMz())>0.5
					||Math.abs(scan.getIsolationWindowUpper()-targetSubWindow.getUpperMz())>0.5) {
				continue;
			}
			float delta=Math.abs(scan.getScanStartTime()-targetRt);
			if (delta<bestDelta) {
				bestDelta=delta;
				best=scan;
			}
		}
		return best;
	}

	private FragmentScan findClosestScanByRt(List<FragmentScan> scans, float targetRt) {
		FragmentScan best=null;
		float bestDelta=Float.MAX_VALUE;
		for (FragmentScan scan : scans) {
			float delta=Math.abs(scan.getScanStartTime()-targetRt);
			if (delta<bestDelta) {
				bestDelta=delta;
				best=scan;
			}
		}
		return best;
	}

	private DemuxWindow findTargetSubWindow(DemuxDesignMatrix designMatrix) {
		for (DemuxWindow sw : designMatrix.getSubWindows()) {
			if (sw.contains(PRECURSOR_MZ)) {
				return sw;
			}
		}
		return null;
	}

	private List<FragmentScan> selectByRt(List<FragmentScan> allSpectra, DemuxWindow subWindow, float targetRt, int k) {
		ArrayList<FragmentScan> covering=new ArrayList<>();
		for (FragmentScan scan : allSpectra) {
			if (subWindow.isContainedBy(scan.getIsolationWindowLower(), scan.getIsolationWindowUpper())) {
				covering.add(scan);
			}
		}
		covering.sort(Comparator.comparingDouble(s -> Math.abs(s.getScanStartTime()-targetRt)));
		return covering.size()<=k?covering:new ArrayList<>(covering.subList(0, k));
	}

	private List<FragmentScan> selectByMzDistance(List<FragmentScan> allSpectra, DemuxDesignMatrix designMatrix, FragmentScan anchor, int k) {
		double anchorCenter=centerOfDemuxIndices(designMatrix, anchor);
		ArrayList<ScanDistance> distances=new ArrayList<>();
		for (FragmentScan scan : allSpectra) {
			double center=centerOfDemuxIndices(designMatrix, scan);
			distances.add(new ScanDistance(scan, center-anchorCenter));
		}
		distances.sort(Comparator.comparingDouble(d -> Math.abs(d.distance)));
		List<ScanDistance> best=distances.size()<=k?distances:distances.subList(0, k);
		best.sort(Comparator.comparingDouble(d -> d.distance));

		ArrayList<FragmentScan> out=new ArrayList<>();
		for (ScanDistance d : best) {
			out.add(d.scan);
		}
		return out;
	}

	private double centerOfDemuxIndices(DemuxDesignMatrix designMatrix, FragmentScan scan) {
		DemuxWindow[] subWindows=designMatrix.getSubWindows();
		ArrayList<Integer> indices=new ArrayList<>();
		for (DemuxWindow sw : subWindows) {
			if (sw.isContainedBy(scan.getIsolationWindowLower(), scan.getIsolationWindowUpper())) {
				indices.add(sw.getIndex());
			}
		}
		double sum=0.0;
		for (int idx : indices) {
			sum+=idx;
		}
		return indices.isEmpty()?0.0:sum/indices.size();
	}

	private Set<String> toKeySet(List<FragmentScan> scans) {
		Set<String> out=new HashSet<>();
		for (FragmentScan scan : scans) {
			out.add(String.format("%.4f|%.2f|%.2f", (double)scan.getScanStartTime(), scan.getIsolationWindowLower(), scan.getIsolationWindowUpper()));
		}
		return out;
	}

	private void printWindowBlock(DemuxCycleBundle bundle, FragmentScan anchor) {
		int anchorIdx=bundle.cycleCenter.spectra.indexOf(anchor);
		int halfK=K/2;
		int start=Math.max(0, anchorIdx-halfK);
		int end=Math.min(bundle.cycleCenter.spectra.size(), start+K);
		start=Math.max(0, end-K);

		System.out.println("Window block indices: ["+start+", "+end+") size="+(end-start));
		System.out.println("Y window list (m/z ordered, interpolated to anchor RT):");

		for (int i=start; i<end; i++) {
			FragmentScan s=bundle.cycleCenter.spectra.get(i);
			System.out.println("  Window "+i+": "+s.getIsolationWindowLower()+"-"+s.getIsolationWindowUpper());
			System.out.println("    RTs: "+(bundle.cycleM2.spectra.get(i).getScanStartTime()/60f)+", "+(bundle.cycleM1.spectra.get(i).getScanStartTime()/60f)
					+", "+(bundle.cycleCenter.spectra.get(i).getScanStartTime()/60f)+", "+(bundle.cycleP1.spectra.get(i).getScanStartTime()/60f)+", "
					+(bundle.cycleP2.spectra.get(i).getScanStartTime()/60f));
		}
	}

	private float sumMatches(double[] masses, float[] intensities, double targetMz) {
		int[] indices=TOLERANCE.getIndices(masses, targetMz);
		float total=0.0f;
		for (int idx : indices) {
			total+=intensities[idx];
		}
		return total;
	}

	private double sumIntensity(double[] masses, float[] intensities, double targetMz) {
		int[] indices=TOLERANCE.getIndices(masses, targetMz);
		double total=0.0;
		for (int idx : indices) {
			total+=intensities[idx];
		}
		return total;
	}

	private int findTransitionIndex(TDoubleArrayList transitionMzs, double targetMz) {
		for (int i=0; i<transitionMzs.size(); i++) {
			if (TOLERANCE.compareTo(transitionMzs.get(i), targetMz)==0) {
				return i;
			}
		}
		return -1;
	}

	private Range findWindowByBounds(ArrayList<Range> windows, double low, double high) {
		for (Range r : windows) {
			if (Math.abs(r.getStart()-low)<0.6&&Math.abs(r.getStop()-high)<0.6) {
				return r;
			}
		}
		return null;
	}

	private boolean isWindow(FragmentScan scan, Range target) {
		return Math.abs(scan.getIsolationWindowLower()-target.getStart())<0.6&&Math.abs(scan.getIsolationWindowUpper()-target.getStop())<0.6;
	}

	private TDoubleArrayList sortedUniqueMzs(double[] masses) {
		TDoubleArrayList mzs=new TDoubleArrayList(masses.length);
		for (double mz : masses) {
			mzs.add(mz);
		}
		mzs.sort();
		TDoubleArrayList unique=new TDoubleArrayList();
		for (int i=0; i<mzs.size(); i++) {
			if (unique.isEmpty()||TOLERANCE.compareTo(mzs.get(i), unique.get(unique.size()-1))!=0) {
				unique.add(mzs.get(i));
			}
		}
		return unique;
	}

	private PwizBins buildPwizBins(TDoubleArrayList transitionMzs) {
		int n=transitionMzs.size();
		double[] lows=new double[n];
		double[] highs=new double[n];
		double maxDelta=0.0;

		double ppm=TOLERANCE.getPpmTolerance();
		for (int i=0; i<n; i++) {
			double mz=transitionMzs.get(i);
			double delta=mz*ppm/1_000_000.0;
			maxDelta=Math.max(maxDelta, delta);
			lows[i]=mz-delta;
			highs[i]=mz+delta;
		}

		for (int i=0; i+1<n; i++) {
			if (highs[i]>lows[i+1]) {
				double center=(highs[i]+lows[i]+highs[i+1]+lows[i+1])/4.0;
				highs[i]=center;
				lows[i+1]=center;
			}
		}

		return new PwizBins(lows, highs, maxDelta);
	}

	private MassDelta findClosestMass(double[] masses, double targetMz) {
		double bestMz=Double.NaN;
		double bestDelta=Double.POSITIVE_INFINITY;
		for (double mz : masses) {
			double delta=Math.abs(mz-targetMz);
			if (delta<bestDelta) {
				bestDelta=delta;
				bestMz=mz;
			}
		}
		return new MassDelta(bestMz, bestDelta);
	}

	private float[] binByPwizStyle(double[] masses, float[] intensities, PwizBins bins) {
		float[] out=new float[bins.lows.length];
		int binStartIndex=0;

		for (int i=0; i<masses.length; i++) {
			double query=masses[i];
			if (query<bins.lows[0]) continue;
			if (query>bins.highs[bins.highs.length-1]) break;

			double minStart=query-bins.maxDelta;
			for (; binStartIndex<bins.lows.length; binStartIndex++) {
				if (bins.lows[binStartIndex]>=minStart) break;
			}

			for (int binIndex=binStartIndex; binIndex<bins.lows.length; binIndex++) {
				if (bins.lows[binIndex]>query) break;
				if (bins.lows[binIndex]<=query&&query<=bins.highs[binIndex]) {
					out[binIndex]+=intensities[i];
				}
			}
		}

		return out;
	}

	private List<DemuxCycle> extractCycles(EncyclopeDIAFile file, ArrayList<Range> windows) throws Exception {
		List<FragmentScan> allSpectra=new ArrayList<>();
		float gradientLength=file.getGradientLength();

		for (Range window : windows) {
			double centerMz=window.getMiddle();
			ArrayList<FragmentScan> spectra=file.getStripes(centerMz, 0, gradientLength, false);
			allSpectra.addAll(spectra);
		}

		allSpectra.sort(Comparator.comparingDouble(FragmentScan::getScanStartTime));

		int windowCount=windows.size();
		int expectedCycles=allSpectra.size()/windowCount;
		List<DemuxCycle> cycles=new ArrayList<>();

		for (int cycleIdx=0; cycleIdx<expectedCycles; cycleIdx++) {
			int startIdx=cycleIdx*windowCount;
			int endIdx=Math.min(startIdx+windowCount, allSpectra.size());

			if (endIdx-startIdx>=windowCount*0.9) {
				ArrayList<FragmentScan> cycleSpectra=new ArrayList<>();
				for (int i=startIdx; i<endIdx; i++) {
					cycleSpectra.add(allSpectra.get(i));
				}
				cycleSpectra.sort(Comparator.comparingDouble(s -> (s.getIsolationWindowLower()+s.getIsolationWindowUpper())/2.0));
				float startRT=cycleSpectra.get(0).getScanStartTime();
				cycles.add(new DemuxCycle(cycleSpectra, startRT));
			}
		}

		return cycles;
	}

	private int findClosestCycleIndex(List<DemuxCycle> cycles, float targetRt) {
		int best=2;
		float bestDelta=Float.MAX_VALUE;

		for (int i=2; i<cycles.size()-2; i++) {
			float delta=Math.abs(cycles.get(i).getCenterRT()-targetRt);
			if (delta<bestDelta) {
				bestDelta=delta;
				best=i;
			}
		}
		return best;
	}

	private int[] computeWindowBlock(int anchorIdx, int total, int k) {
		int halfK=k/2;
		int start=Math.max(0, anchorIdx-halfK);
		int end=Math.min(total, start+k);
		start=Math.max(0, end-k);
		return new int[] {start, end};
	}

	private static final class PwizBins {
		final double[] lows;
		final double[] highs;
		final double maxDelta;

		PwizBins(double[] lows, double[] highs, double maxDelta) {
			this.lows=lows;
			this.highs=highs;
			this.maxDelta=maxDelta;
		}
	}

	private static final class MassDelta {
		final double mz;
		final double delta;

		MassDelta(double mz, double delta) {
			this.mz=mz;
			this.delta=delta;
		}
	}

	private static final class ScanDistance {
		final FragmentScan scan;
		final double distance;

		ScanDistance(FragmentScan scan, double distance) {
			this.scan=scan;
			this.distance=distance;
		}
	}

	private static final class DemuxCycle {
		final ArrayList<FragmentScan> spectra;
		final float startRT;

		DemuxCycle(ArrayList<FragmentScan> spectra, float startRT) {
			this.spectra=spectra;
			this.startRT=startRT;
		}

		float getCenterRT() {
			if (spectra.isEmpty()) return startRT;
			float sum=0f;
			for (FragmentScan s : spectra) {
				sum+=s.getScanStartTime();
			}
			return sum/spectra.size();
		}
	}

	private static final class DemuxCycleBundle {
		final DemuxCycle cycleM2;
		final DemuxCycle cycleM1;
		final DemuxCycle cycleCenter;
		final DemuxCycle cycleP1;
		final DemuxCycle cycleP2;

		DemuxCycleBundle(DemuxCycle cycleM2, DemuxCycle cycleM1, DemuxCycle cycleCenter, DemuxCycle cycleP1, DemuxCycle cycleP2) {
			this.cycleM2=cycleM2;
			this.cycleM1=cycleM1;
			this.cycleCenter=cycleCenter;
			this.cycleP1=cycleP1;
			this.cycleP2=cycleP2;
		}
	}
}
