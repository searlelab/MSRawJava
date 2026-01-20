package org.searlelab.msrawjava.algorithms;

import java.util.ArrayList;
import java.util.Arrays;

import org.ejml.data.DMatrixRMaj;
import org.searlelab.msrawjava.algorithms.demux.CubicHermiteInterpolator;
import org.searlelab.msrawjava.algorithms.demux.DemuxConfig;
import org.searlelab.msrawjava.algorithms.demux.DemuxConfig.InterpolationMethod;
import org.searlelab.msrawjava.algorithms.demux.DemuxDesignMatrix;
import org.searlelab.msrawjava.algorithms.demux.DemuxWindow;
import org.searlelab.msrawjava.algorithms.demux.LogQuadraticInterpolator;
import org.searlelab.msrawjava.algorithms.demux.NNLSSolver;
import org.searlelab.msrawjava.algorithms.demux.RetentionTimeInterpolator;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.MassTolerance;
import org.searlelab.msrawjava.model.Peak;
import org.searlelab.msrawjava.model.Range;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TFloatArrayList;

/**
 * Staggered DIA demultiplexer using Non-Negative Least Squares (NNLS).
 *
 * This implementation is a mathematical clone of the pwiz OverlapDemultiplexer,
 * solving the linear system X·a = y where:
 * - X is the design matrix encoding which sub-windows contribute to each spectrum
 * - a is the unknown vector of demultiplexed sub-window intensities
 * - y is the observed intensities from acquired spectra
 *
 * The algorithm uses a k-local approximation (k=7-9) where only nearby spectra
 * are included in the solve, and retention time interpolation aligns all
 * intensities to a common time point before solving.
 */
public class StaggeredDemultiplexer {

	private final MassTolerance tolerance;
	private final DemuxConfig config;
	private final NNLSSolver nnlsSolver;
	private final RetentionTimeInterpolator interpolator;
	private final DemuxDesignMatrix designMatrix;

	/**
	 * Creates a demultiplexer with explicit window definitions.
	 * This constructor pre-initializes the design matrix from the provided windows.
	 *
	 * @param acquiredWindows list of acquired isolation windows
	 * @param tolerance       mass tolerance for peak matching
	 */
	public StaggeredDemultiplexer(ArrayList<Range> acquiredWindows, MassTolerance tolerance) {
		this(acquiredWindows, tolerance, new DemuxConfig());
	}

	/**
	 * Creates a demultiplexer with explicit window definitions and custom configuration.
	 *
	 * @param acquiredWindows list of acquired isolation windows
	 * @param tolerance       mass tolerance for peak matching
	 * @param config          demultiplexing configuration
	 */
	public StaggeredDemultiplexer(ArrayList<Range> acquiredWindows, MassTolerance tolerance, DemuxConfig config) {
		this.tolerance = tolerance;
		this.config = config;
		this.nnlsSolver = new NNLSSolver();
		this.interpolator = config.getInterpolationMethod() == InterpolationMethod.CUBIC_HERMITE
				? new CubicHermiteInterpolator()
				: new LogQuadraticInterpolator();

		// Pre-initialize design matrix
		ArrayList<Range> sorted = new ArrayList<>(acquiredWindows);
		sorted.sort(null);
		this.designMatrix = new DemuxDesignMatrix(sorted);
	}

	/**
	 * Demultiplexes staggered DIA spectra from 5 complete cycles.
	 * Uses a spectrum-centric approach where each acquired spectrum is the anchor
	 * for demultiplexing the sub-windows it covers.
	 *
	 * <p>This approach matches pwiz OverlapDemultiplexer behavior:
	 * <ul>
	 *   <li>Iterates by acquired spectrum (not by sub-window)</li>
	 *   <li>Each spectrum's actual RT becomes the anchor for its outputs</li>
	 *   <li>Each acquired spectrum covers 2 sub-windows → produces 2 outputs</li>
	 *   <li>Only m/z values from the anchor spectrum appear in output</li>
	 * </ul>
	 *
	 * <p>Edge sub-window handling is controlled by {@link DemuxConfig#isIncludeEdgeSubWindows()}.
	 *
	 * @param cycleM2           cycle at t-2 (earliest)
	 * @param cycleM1           cycle at t-1
	 * @param cycleCenter       cycle at t (the one we're demultiplexing)
	 * @param cycleP1           cycle at t+1
	 * @param cycleP2           cycle at t+2 (latest)
	 * @param currentScanNumber starting scan number for output spectra
	 * @return demultiplexed FragmentScans
	 */
	public ArrayList<FragmentScan> demultiplex(
			ArrayList<FragmentScan> cycleM2,
			ArrayList<FragmentScan> cycleM1,
			ArrayList<FragmentScan> cycleCenter,
			ArrayList<FragmentScan> cycleP1,
			ArrayList<FragmentScan> cycleP2,
			int currentScanNumber) {

		// Validate input
		validateCycles(cycleM2, cycleM1, cycleCenter, cycleP1, cycleP2);

		// Collect all spectra indexed by their window position
		ArrayList<ArrayList<FragmentScan>> allCycles = new ArrayList<>();
		allCycles.add(cycleM2);
		allCycles.add(cycleM1);
		allCycles.add(cycleCenter);
		allCycles.add(cycleP1);
		allCycles.add(cycleP2);

		// Build output: iterate by acquired spectrum in center cycle (spectrum-centric approach)
		ArrayList<FragmentScan> demuxResults = new ArrayList<>();

		for (int specIdx = 0; specIdx < cycleCenter.size(); specIdx++) {
			FragmentScan anchorSpectrum = cycleCenter.get(specIdx);
			float targetRT = anchorSpectrum.getScanStartTime(); // Anchor to actual spectrum RT

			// Find the sub-windows covered by this spectrum
			DemuxWindow[] coveredSubWindows = findCoveredSubWindows(anchorSpectrum);

			for (DemuxWindow subWindow : coveredSubWindows) {
				// Skip edge sub-windows if configured to exclude them
				if (!config.isIncludeEdgeSubWindows() && isEdgeSubWindow(subWindow)) {
					continue;
				}

				// Find spectra that cover this sub-window across all 5 cycles
				ArrayList<SpectrumWithRT> coveringSpectra = findCoveringSpectra(subWindow, allCycles);

				if (coveringSpectra.isEmpty()) {
					// No spectra cover this sub-window - create empty output
					FragmentScan empty = createEmptySubWindowScan(
							subWindow, targetRT, currentScanNumber++, anchorSpectrum);
					demuxResults.add(empty);
					continue;
				}

				// Select k nearest spectra for local NNLS solve
				ArrayList<SpectrumWithRT> selectedSpectra = selectKNearest(coveringSpectra, targetRT, config.getK());

				// Collect m/z values from anchor spectrum ONLY (not from other covering spectra)
				TDoubleArrayList uniqueMzs = collectMzValuesFromAnchor(anchorSpectrum);

				if (uniqueMzs.isEmpty()) {
					FragmentScan empty = createEmptySubWindowScan(
							subWindow, targetRT, currentScanNumber++, anchorSpectrum);
					demuxResults.add(empty);
					continue;
				}

				// For each m/z, build intensity vector and solve NNLS
				TDoubleArrayList demuxMzs = new TDoubleArrayList();
				TFloatArrayList demuxIntensities = new TFloatArrayList();

				for (int mzIdx = 0; mzIdx < uniqueMzs.size(); mzIdx++) {
					double targetMz = uniqueMzs.get(mzIdx);

					// Build intensity vector y with RT interpolation to anchor RT
					double[] intensityVector = buildIntensityVector(selectedSpectra, targetMz, targetRT);

					// Build local design matrix
					DMatrixRMaj localMatrix = buildLocalMatrix(selectedSpectra, subWindow);

					// Solve NNLS
					DMatrixRMaj y = new DMatrixRMaj(intensityVector.length, 1);
					for (int i = 0; i < intensityVector.length; i++) {
						y.set(i, 0, intensityVector[i]);
					}

					DMatrixRMaj solution = nnlsSolver.solve(localMatrix, y);

					// Extract intensity for this sub-window
					int localSubIdx = getLocalSubWindowIndex(selectedSpectra, subWindow);
					double demuxIntensity = localSubIdx >= 0 && localSubIdx < solution.numRows
							? solution.get(localSubIdx, 0)
							: 0.0;

					if (demuxIntensity > 0) {
						demuxMzs.add(targetMz);
						demuxIntensities.add((float) demuxIntensity);
					}
				}

				// Create output FragmentScan for this sub-window with anchor's RT
				FragmentScan outputScan = createSubWindowScan(
						subWindow, targetRT, currentScanNumber++,
						demuxMzs.toArray(), demuxIntensities.toArray(),
						anchorSpectrum);
				demuxResults.add(outputScan);
			}
		}

		return demuxResults;
	}

	// ==================== Private Methods ====================

	private void validateCycles(ArrayList<FragmentScan> cycleM2, ArrayList<FragmentScan> cycleM1,
			ArrayList<FragmentScan> cycleCenter, ArrayList<FragmentScan> cycleP1, ArrayList<FragmentScan> cycleP2) {
		if (cycleCenter == null || cycleCenter.isEmpty()) {
			throw new IllegalArgumentException("cycleCenter cannot be null or empty");
		}
		int expectedSize = cycleCenter.size();
		if (cycleM2 == null || cycleM2.size() != expectedSize ||
				cycleM1 == null || cycleM1.size() != expectedSize ||
				cycleP1 == null || cycleP1.size() != expectedSize ||
				cycleP2 == null || cycleP2.size() != expectedSize) {
			throw new IllegalArgumentException("All cycles must have the same size: " + expectedSize);
		}
	}

	/**
	 * Finds the sub-windows that are contained within the given spectrum's isolation window.
	 * In staggered DIA, each acquired spectrum covers exactly 2 sub-windows (except possibly
	 * at the edges where a spectrum might cover only 1).
	 *
	 * @param scan the acquired spectrum
	 * @return array of sub-windows covered by this spectrum (typically 2)
	 */
	private DemuxWindow[] findCoveredSubWindows(FragmentScan scan) {
		DemuxWindow[] allSubWindows = designMatrix.getSubWindows();
		TDoubleArrayList coveredIndices = new TDoubleArrayList();

		double windowLower = scan.getIsolationWindowLower();
		double windowUpper = scan.getIsolationWindowUpper();

		for (int i = 0; i < allSubWindows.length; i++) {
			DemuxWindow subWindow = allSubWindows[i];
			if (subWindow.isContainedBy(windowLower, windowUpper)) {
				coveredIndices.add(i);
			}
		}

		DemuxWindow[] result = new DemuxWindow[coveredIndices.size()];
		for (int i = 0; i < coveredIndices.size(); i++) {
			result[i] = allSubWindows[(int) coveredIndices.get(i)];
		}
		return result;
	}

	/**
	 * Tests if the given sub-window is an edge sub-window (first or last).
	 * Edge sub-windows have only single coverage (one spectrum covers them)
	 * while interior sub-windows have dual coverage.
	 *
	 * @param subWindow the sub-window to test
	 * @return true if this is the first or last sub-window
	 */
	private boolean isEdgeSubWindow(DemuxWindow subWindow) {
		DemuxWindow[] allSubWindows = designMatrix.getSubWindows();
		int index = subWindow.getIndex();
		return index == 0 || index == allSubWindows.length - 1;
	}

	/**
	 * Collects all m/z values from the anchor spectrum only.
	 * This ensures that each demuxed output contains only peaks that were present
	 * in its anchor spectrum, matching pwiz behavior.
	 *
	 * @param anchor the anchor spectrum to collect m/z values from
	 * @return sorted list of unique m/z values
	 */
	private TDoubleArrayList collectMzValuesFromAnchor(FragmentScan anchor) {
		double[] masses = anchor.getMassArray();
		TDoubleArrayList mzs = new TDoubleArrayList(masses.length);

		for (double mz : masses) {
			mzs.add(mz);
		}

		// Sort and remove duplicates (within tolerance)
		mzs.sort();
		TDoubleArrayList unique = new TDoubleArrayList();
		for (int i = 0; i < mzs.size(); i++) {
			if (unique.isEmpty() || tolerance.compareTo(mzs.get(i), unique.get(unique.size() - 1)) != 0) {
				unique.add(mzs.get(i));
			}
		}

		return unique;
	}

	private ArrayList<SpectrumWithRT> findCoveringSpectra(DemuxWindow subWindow,
			ArrayList<ArrayList<FragmentScan>> allCycles) {
		ArrayList<SpectrumWithRT> result = new ArrayList<>();

		for (int cycleIdx = 0; cycleIdx < allCycles.size(); cycleIdx++) {
			ArrayList<FragmentScan> cycle = allCycles.get(cycleIdx);
			for (int scanIdx = 0; scanIdx < cycle.size(); scanIdx++) {
				FragmentScan scan = cycle.get(scanIdx);
				// Check if this spectrum's isolation window covers the sub-window
				if (subWindow.isContainedBy(scan.getIsolationWindowLower(), scan.getIsolationWindowUpper())) {
					result.add(new SpectrumWithRT(scan, cycleIdx, scanIdx));
				}
			}
		}

		return result;
	}

	private ArrayList<SpectrumWithRT> selectKNearest(ArrayList<SpectrumWithRT> spectra, float targetRT, int k) {
		if (spectra.size() <= k) {
			return new ArrayList<>(spectra);
		}

		// Sort by distance to target RT
		spectra.sort((a, b) -> {
			float distA = Math.abs(a.scan.getScanStartTime() - targetRT);
			float distB = Math.abs(b.scan.getScanStartTime() - targetRT);
			return Float.compare(distA, distB);
		});

		return new ArrayList<>(spectra.subList(0, k));
	}

	private double[] buildIntensityVector(ArrayList<SpectrumWithRT> spectra, double targetMz, float targetRT) {
		double[] result = new double[spectra.size()];

		for (int i = 0; i < spectra.size(); i++) {
			FragmentScan scan = spectra.get(i).scan;

			// Find matching peaks within tolerance
			double[] masses = scan.getMassArray();
			float[] intensities = scan.getIntensityArray();
			int[] indices = tolerance.getIndices(masses, targetMz);

			if (indices.length == 0) {
				result[i] = 0.0; // Missing peak treated as zero
			} else {
				// Sum intensities of matching peaks
				double totalIntensity = 0;
				for (int idx : indices) {
					totalIntensity += intensities[idx];
				}
				result[i] = totalIntensity;
			}
		}

		// Interpolate to target RT
		if (spectra.size() >= 2) {
			float[] times = new float[spectra.size()];
			float[] intensities = new float[spectra.size()];
			for (int i = 0; i < spectra.size(); i++) {
				times[i] = spectra.get(i).scan.getScanStartTime();
				intensities[i] = (float) result[i];
			}

			// Sort by time for interpolation
			Integer[] sortedIndices = new Integer[spectra.size()];
			for (int i = 0; i < sortedIndices.length; i++) sortedIndices[i] = i;
			Arrays.sort(sortedIndices, (a, b) -> Float.compare(times[a], times[b]));

			float[] sortedTimes = new float[spectra.size()];
			float[] sortedIntensities = new float[spectra.size()];
			for (int i = 0; i < spectra.size(); i++) {
				sortedTimes[i] = times[sortedIndices[i]];
				sortedIntensities[i] = intensities[sortedIndices[i]];
			}

			// Interpolate each position to target RT
			float interpolatedValue = interpolator.interpolate(sortedTimes, sortedIntensities, targetRT);

			// Use the interpolated value as a weight factor
			// Apply to each spectrum based on its RT distance from target
			for (int i = 0; i < spectra.size(); i++) {
				float rt = spectra.get(i).scan.getScanStartTime();
				float weight = 1.0f / (1.0f + (float) Math.pow(5.0 * Math.abs(rt - targetRT) / spectra.size(), 2));
				result[i] *= weight;
			}
		}

		return result;
	}

	private DMatrixRMaj buildLocalMatrix(ArrayList<SpectrumWithRT> spectra, DemuxWindow centerSubWindow) {
		// Build a local design matrix for the selected spectra
		// Each row corresponds to a spectrum, each column to a sub-window

		DemuxWindow[] allSubWindows = designMatrix.getSubWindows();
		int centerIdx = centerSubWindow.getIndex();

		// Determine column range (sub-windows around the center)
		int k = config.getK();
		int halfK = k / 2;
		int colStart = Math.max(0, centerIdx - halfK);
		int colEnd = Math.min(allSubWindows.length, colStart + k);
		colStart = Math.max(0, colEnd - k);
		int numCols = colEnd - colStart;

		DMatrixRMaj matrix = new DMatrixRMaj(spectra.size(), numCols);

		for (int row = 0; row < spectra.size(); row++) {
			FragmentScan scan = spectra.get(row).scan;
			double windowLower = scan.getIsolationWindowLower();
			double windowUpper = scan.getIsolationWindowUpper();

			for (int col = 0; col < numCols; col++) {
				DemuxWindow subWindow = allSubWindows[colStart + col];
				// Set 1 if sub-window is contained within the spectrum's isolation window
				if (subWindow.isContainedBy(windowLower, windowUpper)) {
					matrix.set(row, col, 1.0);
				}
			}
		}

		return matrix;
	}

	private int getLocalSubWindowIndex(ArrayList<SpectrumWithRT> spectra, DemuxWindow centerSubWindow) {
		int centerIdx = centerSubWindow.getIndex();
		int k = config.getK();
		int halfK = k / 2;
		int colStart = Math.max(0, centerIdx - halfK);
		int colEnd = Math.min(designMatrix.getNumSubWindows(), colStart + k);
		colStart = Math.max(0, colEnd - k);

		return centerIdx - colStart;
	}

	private FragmentScan createEmptySubWindowScan(DemuxWindow subWindow, float targetRT, int scanNumber,
			FragmentScan template) {
		return template.rebuild(
				scanNumber,
				targetRT,
				new ArrayList<>(),
				subWindow.getLowerMz(),
				subWindow.getUpperMz());
	}

	private FragmentScan createSubWindowScan(DemuxWindow subWindow, float targetRT, int scanNumber,
			double[] mzs, float[] intensities, FragmentScan template) {
		ArrayList<Peak> peaks = new ArrayList<>(mzs.length);
		for (int i = 0; i < mzs.length; i++) {
			peaks.add(new Peak(mzs[i], intensities[i]));
		}

		return template.rebuild(
				scanNumber,
				targetRT,
				peaks,
				subWindow.getLowerMz(),
				subWindow.getUpperMz());
	}

	// ==================== Helper Classes ====================

	/**
	 * Associates a FragmentScan with its cycle and position information.
	 */
	private static class SpectrumWithRT {
		final FragmentScan scan;
		final int cycleIndex;
		final int scanIndex;

		SpectrumWithRT(FragmentScan scan, int cycleIndex, int scanIndex) {
			this.scan = scan;
			this.cycleIndex = cycleIndex;
			this.scanIndex = scanIndex;
		}
	}

	// ==================== Legacy Static Methods ====================
	// (Preserved for backward compatibility with existing code)

	/**
	 * Computes sub-ranges from acquired window boundaries.
	 * @deprecated Use DemuxDesignMatrix instead
	 */
	@Deprecated
	public static ArrayList<RangeCounter> getSubRanges(ArrayList<Range> acquiredWindows) {
		TFloatArrayList boundaries = new TFloatArrayList();
		for (Range range : acquiredWindows) {
			boundaries.add(range.getStart());
			boundaries.add(range.getStop());
		}
		boundaries.sort();

		float windowBoundaryTolerance = 0.01f;
		ArrayList<RangeCounter> subRanges = new ArrayList<>();
		float anchor = boundaries.getQuick(0);

		for (int i = 1; i < boundaries.size(); i++) {
			float v = boundaries.getQuick(i);
			if (v - anchor > windowBoundaryTolerance) {
				subRanges.add(new RangeCounter(new Range(anchor, v)));
				anchor = v;
			}
		}

		float[] centers = new float[subRanges.size()];
		for (int i = 0; i < centers.length; i++) {
			centers[i] = subRanges.get(i).range.getMiddle();
		}

		for (int i = 0; i < acquiredWindows.size(); i++) {
			Range range = acquiredWindows.get(i);
			int[] indices = getIndicies(centers, range);
			for (int idx : indices) {
				subRanges.get(idx).addRange(range, i);
			}
		}

		return subRanges;
	}

	/**
	 * Finds sub-range indices that fall within a target range.
	 * @deprecated Use DemuxDesignMatrix instead
	 */
	@Deprecated
	public static int[] getIndicies(float[] centers, Range target) {
		int value = Arrays.binarySearch(centers, target.getMiddle());
		if (value < 0) {
			value = -(value + 1);
		}

		TFloatArrayList matches = new TFloatArrayList();
		gnu.trove.list.array.TIntArrayList matchIndices = new gnu.trove.list.array.TIntArrayList();

		int index = value;
		while (index > 0 && target.contains(centers[index - 1])) {
			matchIndices.add(index - 1);
			index--;
		}

		index = value;
		while (index < centers.length && target.contains(centers[index])) {
			matchIndices.add(index);
			index++;
		}

		return matchIndices.toArray();
	}
}
