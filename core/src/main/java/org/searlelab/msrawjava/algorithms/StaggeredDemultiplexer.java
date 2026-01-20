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
import gnu.trove.list.array.TIntArrayList;

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

		// Build output: iterate by acquired spectrum in center cycle (spectrum-centric approach)
		ArrayList<FragmentScan> demuxResults = new ArrayList<>();

		int windowCount = cycleCenter.size();
		int numSubWindows = designMatrix.getNumSubWindows();
		int k = Math.min(config.getK(), Math.min(windowCount, numSubWindows));

		for (int anchorIdx = 0; anchorIdx < windowCount; anchorIdx++) {
			FragmentScan anchorSpectrum = cycleCenter.get(anchorIdx);
			float targetRT = anchorSpectrum.getScanStartTime(); // Anchor to actual spectrum RT

			// Find the sub-windows covered by this spectrum
			DemuxWindow[] coveredSubWindows = findCoveredSubWindows(anchorSpectrum);

			TDoubleArrayList transitions = collectMzValuesFromAnchor(anchorSpectrum);
			if (transitions.isEmpty()) {
				for (DemuxWindow subWindow : coveredSubWindows) {
					if (!config.isIncludeEdgeSubWindows() && isEdgeSubWindow(subWindow)) {
						continue;
					}
					FragmentScan empty = createEmptySubWindowScan(
							subWindow, targetRT, currentScanNumber++, anchorSpectrum);
					demuxResults.add(empty);
				}
				continue;
			}

			for (DemuxWindow subWindow : coveredSubWindows) {
				if (!config.isIncludeEdgeSubWindows() && isEdgeSubWindow(subWindow)) {
					continue;
				}

				ArrayList<Integer> windowIndices = selectWindowIndicesBySubWindow(
						cycleCenter, subWindow.getIndex(), k);
				int blockSize = windowIndices.size();
				if (blockSize == 0) {
					continue;
				}

				int[] colRange = computeColumnRangeForTarget(subWindow.getIndex(), blockSize);
				int colStart = colRange[0];
				int colEnd = colRange[1];
				int colCount = colEnd - colStart;
				if (colCount <= 0) {
					continue;
				}

				DMatrixRMaj design = buildMaskMatrixForWindows(cycleCenter, windowIndices, colStart, colEnd);

				TDoubleArrayList mzOut = new TDoubleArrayList();
				TFloatArrayList intOut = new TFloatArrayList();

				for (int mzIdx = 0; mzIdx < transitions.size(); mzIdx++) {
					double targetMz = transitions.get(mzIdx);

					double[] yValues = new double[blockSize];
					for (int row = 0; row < blockSize; row++) {
						int windowIdx = windowIndices.get(row);
						FragmentScan[] spectra = new FragmentScan[] {
								cycleM2.get(windowIdx),
								cycleM1.get(windowIdx),
								cycleCenter.get(windowIdx),
								cycleP1.get(windowIdx),
								cycleP2.get(windowIdx)
						};
						yValues[row] = interpolateTransitionIntensity(spectra, targetMz, targetRT);
					}

					DMatrixRMaj y = new DMatrixRMaj(blockSize, 1);
					for (int i = 0; i < blockSize; i++) {
						y.set(i, 0, yValues[i]);
					}

					DMatrixRMaj solution = nnlsSolver.solve(design, y);
					int colIdx = subWindow.getIndex() - colStart;
					if (colIdx < 0 || colIdx >= solution.numRows) {
						continue;
					}
					double demuxIntensity = solution.get(colIdx, 0);
					if (demuxIntensity > 0.0) {
						mzOut.add(targetMz);
						intOut.add((float) demuxIntensity);
					}
				}

				FragmentScan outputScan = createSubWindowScan(
						subWindow, targetRT, currentScanNumber++,
						mzOut.toArray(), intOut.toArray(),
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

	private int[] computeColumnRangeForTarget(int targetIndex, int k) {
		int numSubWindows = designMatrix.getNumSubWindows();
		int kLocal = Math.min(k, numSubWindows);
		int halfK = kLocal / 2;
		int colStart = targetIndex - halfK;
		if (colStart < 0) {
			colStart = 0;
		}
		if (colStart + kLocal > numSubWindows) {
			colStart = Math.max(0, numSubWindows - kLocal);
		}
		return new int[] {colStart, colStart + kLocal};
	}

	private ArrayList<Integer> selectWindowIndicesBySubWindow(ArrayList<FragmentScan> cycleCenter, int targetSubWindowIndex, int k) {
		ArrayList<WindowDistance> distances = new ArrayList<>();
		for (int i = 0; i < cycleCenter.size(); i++) {
			double center = centerOfDemuxIndices(cycleCenter.get(i));
			distances.add(new WindowDistance(i, center - targetSubWindowIndex));
		}
		distances.sort((a, b) -> {
			double diff = Math.abs(a.distance) - Math.abs(b.distance);
			if (Math.abs(diff) > 1e-4) {
				return diff < 0 ? -1 : 1;
			}
			return Double.compare(a.distance, b.distance);
		});

		int count = Math.min(k, distances.size());
		ArrayList<WindowDistance> best = new ArrayList<>(distances.subList(0, count));
		best.sort((a, b) -> {
			double diff = a.distance - b.distance;
			if (Math.abs(diff) > 1e-4) {
				return diff < 0 ? -1 : 1;
			}
			return Integer.compare(a.windowIndex, b.windowIndex);
		});

		ArrayList<Integer> indices = new ArrayList<>();
		for (WindowDistance d : best) {
			indices.add(d.windowIndex);
		}
		return indices;
	}

	private double centerOfDemuxIndices(FragmentScan scan) {
		DemuxWindow[] subWindows = designMatrix.getSubWindows();
		double windowLower = scan.getIsolationWindowLower();
		double windowUpper = scan.getIsolationWindowUpper();
		double sum = 0.0;
		int count = 0;
		for (int i = 0; i < subWindows.length; i++) {
			DemuxWindow sw = subWindows[i];
			if (sw.isContainedBy(windowLower, windowUpper)) {
				sum += sw.getIndex();
				count++;
			}
		}
		return count == 0 ? 0.0 : sum / count;
	}

	private DMatrixRMaj buildMaskMatrixForWindows(ArrayList<FragmentScan> cycleCenter,
			ArrayList<Integer> windowIndices, int colStart, int colEnd) {
		int rows = windowIndices.size();
		int cols = colEnd - colStart;
		DMatrixRMaj matrix = new DMatrixRMaj(rows, cols);

		for (int row = 0; row < windowIndices.size(); row++) {
			FragmentScan scan = cycleCenter.get(windowIndices.get(row));
			DemuxWindow[] subWindows = findCoveredSubWindows(scan);
			for (DemuxWindow sw : subWindows) {
				int col = sw.getIndex() - colStart;
				if (col >= 0 && col < cols) {
					matrix.set(row, col, 1.0);
				}
			}
		}
		return matrix;
	}

	private double interpolateTransitionIntensity(FragmentScan[] spectra, double targetMz, float targetRT) {
		int n = spectra.length;
		double[] times = new double[n];
		double[] intensities = new double[n];

		for (int i = 0; i < n; i++) {
			FragmentScan scan = spectra[i];
			times[i] = scan.getScanStartTime();
			intensities[i] = sumIntensity(scan, targetMz);
		}

		// Sort by time for interpolation
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) order[i] = i;
		Arrays.sort(order, (a, b) -> Double.compare(times[a], times[b]));

		double[] sortedTimes = new double[n];
		double[] sortedIntensities = new double[n];
		for (int i = 0; i < n; i++) {
			sortedTimes[i] = times[order[i]];
			sortedIntensities[i] = intensities[order[i]];
		}

		return interpolator.interpolate(sortedTimes, sortedIntensities, targetRT);
	}

	private double sumIntensity(FragmentScan scan, double targetMz) {
		double[] masses = scan.getMassArray();
		float[] intensities = scan.getIntensityArray();
		int[] indices = tolerance.getIndices(masses, targetMz);
		double total = 0.0;
		for (int idx : indices) {
			total += intensities[idx];
		}
		return total;
	}

	private static class WindowDistance {
		private final int windowIndex;
		private final double distance;

		private WindowDistance(int windowIndex, double distance) {
			this.windowIndex = windowIndex;
			this.distance = distance;
		}
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

		TIntArrayList matchIndices = new TIntArrayList();

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
