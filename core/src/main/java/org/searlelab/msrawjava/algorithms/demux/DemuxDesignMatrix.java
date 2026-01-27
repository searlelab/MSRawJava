package org.searlelab.msrawjava.algorithms.demux;

import java.util.ArrayList;
import java.util.Arrays;

import org.ejml.data.DMatrixRMaj;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.algorithms.RangeCounter;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Range;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;

/**
 * Constructs and manages the design matrix for staggered DIA demultiplexing.
 *
 * The design matrix X encodes which demultiplexing sub-windows contribute to each
 * acquired spectrum. For staggered (overlapping) windows, this matrix is nearly
 * tridiagonal:
 *
 * <pre>
 * [1 1 0 0 0 0 0]   Each row = one acquired spectrum position
 * [0 1 1 0 0 0 0]   Each column = one demultiplexing sub-window
 * [0 0 1 1 0 0 0]   Entry = 1 if sub-window contributes to spectrum
 * [0 0 0 1 1 0 0]
 * [0 0 0 0 1 1 0]
 * [0 0 0 0 0 1 1]
 * [0 0 0 0 0 0 1]
 * </pre>
 *
 * This class computes sub-windows from the acquired window boundaries, builds the
 * full design matrix, and provides methods to extract local k×k submatrices for
 * efficient NNLS solving.
 */
public class DemuxDesignMatrix {

	private static final float WINDOW_BOUNDARY_TOLERANCE=0.01f;

	private final DemuxWindow[] subWindows;
	private final double[] subWindowCenters;
	private final DMatrixRMaj fullMatrix;
	private final int numAcquiredPositions; // rows = distinct window positions
	private final int numSubWindows; // columns

	/**
	 * Builds a design matrix from the acquired window definitions.
	 *
	 * @param acquiredWindows
	 *            list of all acquired isolation windows (both normal and staggered)
	 */
	public DemuxDesignMatrix(ArrayList<Range> acquiredWindows) {
		// Sort windows by m/z
		ArrayList<Range> sorted=new ArrayList<>(acquiredWindows);
		sorted.sort(null);

		// Compute sub-windows from boundaries
		ArrayList<RangeCounter> subRanges=computeSubRanges(sorted);
		this.numSubWindows=subRanges.size();
		this.numAcquiredPositions=sorted.size();

		// Create DemuxWindow objects
		this.subWindows=new DemuxWindow[numSubWindows];
		this.subWindowCenters=new double[numSubWindows];
		for (int i=0; i<numSubWindows; i++) {
			subWindows[i]=new DemuxWindow(subRanges.get(i).range, i);
			subWindowCenters[i]=subWindows[i].getCenterMz();
		}

		// Build the design matrix
		this.fullMatrix=buildMatrix(sorted, subRanges);
	}

	/**
	 * Auto-detects window geometry from a cycle of FragmentScans.
	 *
	 * @param cycle
	 *            a list of FragmentScans representing one complete cycle
	 * @return a new DemuxDesignMatrix
	 */
	public static DemuxDesignMatrix fromCycle(ArrayList<FragmentScan> cycle) {
		ArrayList<Range> windows=new ArrayList<>(cycle.size());
		for (FragmentScan scan : cycle) {
			windows.add(scan.getPrecursorRange());
		}
		return new DemuxDesignMatrix(windows);
	}

	/**
	 * Returns all demultiplexing sub-windows.
	 */
	public DemuxWindow[] getSubWindows() {
		return subWindows;
	}

	/**
	 * Returns the number of demultiplexing sub-windows (columns in design matrix).
	 */
	public int getNumSubWindows() {
		return numSubWindows;
	}

	/**
	 * Returns the number of acquired window positions (rows in design matrix).
	 */
	public int getNumAcquiredPositions() {
		return numAcquiredPositions;
	}

	/**
	 * Returns the full design matrix.
	 *
	 * @return m × n matrix where m = acquired positions, n = sub-windows
	 */
	public DMatrixRMaj getFullMatrix() {
		return fullMatrix;
	}

	/**
	 * Finds sub-windows that are covered by an acquired window.
	 *
	 * @param windowLower
	 *            lower m/z bound of acquired window
	 * @param windowUpper
	 *            upper m/z bound of acquired window
	 * @return indices of sub-windows contained within the acquired window
	 */
	public int[] getSubWindowIndices(double windowLower, double windowUpper) {
		TIntArrayList indices=new TIntArrayList();
		for (int i=0; i<numSubWindows; i++) {
			double center=subWindowCenters[i];
			if (center>=windowLower&&center<=windowUpper) {
				indices.add(i);
			}
		}
		return indices.toArray();
	}

	/**
	 * Finds the row index for an acquired window position.
	 *
	 * @param windowCenter
	 *            center m/z of the acquired window
	 * @return row index in the design matrix, or -1 if not found
	 */
	public int getRowIndex(double windowCenter) {
		// This assumes rows are in sorted m/z order
		int idx=Arrays.binarySearch(subWindowCenters, windowCenter);
		if (idx<0) {
			idx=-(idx+1);
		}
		// Find closest match
		if (idx>=numAcquiredPositions) idx=numAcquiredPositions-1;
		return idx;
	}

	/**
	 * Extracts a local k×k submatrix centered on a specific sub-window.
	 *
	 * For efficient NNLS solving, we only use k nearby rows (spectra) and
	 * k columns (sub-windows) around the window of interest.
	 *
	 * @param centerSubWindowIndex
	 *            the sub-window we want to demultiplex
	 * @param k
	 *            the local approximation size (7-9)
	 * @return a k×k submatrix
	 */
	public DMatrixRMaj extractLocalMatrix(int centerSubWindowIndex, int k) {
		// Calculate the column range
		int halfK=k/2;
		int colStart=Math.max(0, centerSubWindowIndex-halfK);
		int colEnd=Math.min(numSubWindows, colStart+k);
		colStart=Math.max(0, colEnd-k); // adjust if we hit the upper bound

		// Find rows that have non-zero entries in these columns
		// (i.e., spectra that cover any of these sub-windows)
		TIntArrayList relevantRows=new TIntArrayList();
		for (int row=0; row<numAcquiredPositions; row++) {
			for (int col=colStart; col<colEnd; col++) {
				if (fullMatrix.get(row, col)>0) {
					relevantRows.add(row);
					break;
				}
			}
		}

		// Select k rows centered around the relevant ones
		int[] rows=selectKRows(relevantRows.toArray(), centerSubWindowIndex, k);

		// Build the k×k submatrix
		DMatrixRMaj local=new DMatrixRMaj(k, k);
		for (int i=0; i<rows.length&&i<k; i++) {
			for (int j=0; j<k&&(colStart+j)<numSubWindows; j++) {
				local.set(i, j, fullMatrix.get(rows[i], colStart+j));
			}
		}

		return local;
	}

	/**
	 * Extracts a local matrix given specific spectrum indices.
	 *
	 * @param spectrumIndices
	 *            indices of spectra to include as rows
	 * @param subWindowStart
	 *            first sub-window column to include
	 * @param numCols
	 *            number of sub-window columns
	 * @return the extracted submatrix
	 */
	public DMatrixRMaj extractLocalMatrix(int[] spectrumIndices, int subWindowStart, int numCols) {
		int numRows=spectrumIndices.length;
		DMatrixRMaj local=new DMatrixRMaj(numRows, numCols);

		for (int i=0; i<numRows; i++) {
			int row=spectrumIndices[i];
			if (row>=0&&row<numAcquiredPositions) {
				for (int j=0; j<numCols; j++) {
					int col=subWindowStart+j;
					if (col>=0&&col<numSubWindows) {
						local.set(i, j, fullMatrix.get(row, col));
					}
				}
			}
		}

		return local;
	}

	/**
	 * Returns a bitmask identifying which rows are included in a submatrix.
	 * Used for QR decomposition caching.
	 *
	 * @param rowIndices
	 *            the row indices
	 * @param totalRows
	 *            total number of possible rows
	 * @return integer bitmask
	 */
	public static int computeRowMask(int[] rowIndices, int totalRows) {
		int mask=0;
		for (int idx : rowIndices) {
			if (idx>=0&&idx<totalRows&&idx<32) {
				mask|=(1<<idx);
			}
		}
		return mask;
	}

	// ---------------- Private helpers ----------------

	private ArrayList<RangeCounter> computeSubRanges(ArrayList<Range> sortedWindows) {
		TFloatArrayList boundaries=new TFloatArrayList();
		for (Range range : sortedWindows) {
			boundaries.add(range.getStart());
			boundaries.add(range.getStop());
		}
		boundaries.sort();

		ArrayList<RangeCounter> subRanges=new ArrayList<>();
		float anchor=boundaries.getQuick(0);

		for (int i=1; i<boundaries.size(); i++) {
			float v=boundaries.getQuick(i);
			if (v-anchor>WINDOW_BOUNDARY_TOLERANCE) {
				subRanges.add(new RangeCounter(new Range(anchor, v)));
				anchor=v;
			}
		}

		// Associate each sub-range with its parent windows
		float[] centers=new float[subRanges.size()];
		for (int i=0; i<centers.length; i++) {
			centers[i]=subRanges.get(i).range.getMiddle();
		}

		for (int i=0; i<sortedWindows.size(); i++) {
			Range range=sortedWindows.get(i);
			int[] indices=findContainedSubRanges(centers, range);
			for (int idx : indices) {
				subRanges.get(idx).addRange(range, i);
			}
		}

		return subRanges;
	}

	private int[] findContainedSubRanges(float[] centers, Range target) {
		TIntArrayList matches=new TIntArrayList();
		for (int i=0; i<centers.length; i++) {
			if (target.contains(centers[i])) {
				matches.add(i);
			}
		}
		return matches.toArray();
	}

	private DMatrixRMaj buildMatrix(ArrayList<Range> sortedWindows, ArrayList<RangeCounter> subRanges) {
		int m=sortedWindows.size();
		int n=subRanges.size();
		DMatrixRMaj matrix=new DMatrixRMaj(m, n);

		for (int row=0; row<m; row++) {
			Range window=sortedWindows.get(row);
			for (int col=0; col<n; col++) {
				// Check if sub-window center is within acquired window
				double subWindowCenter=subRanges.get(col).range.getMiddle();
				if (window.contains(subWindowCenter)) {
					matrix.set(row, col, 1.0);
				}
			}
		}

		return matrix;
	}

	private int[] selectKRows(int[] candidateRows, int centerSubWindowIndex, int k) {
		if (candidateRows.length<=k) {
			// Pad with adjacent rows if needed
			if (candidateRows.length==k) return candidateRows;

			TIntArrayList result=new TIntArrayList(candidateRows);
			int idx=0;
			while (result.size()<k&&idx<numAcquiredPositions) {
				if (!result.contains(idx)) {
					result.add(idx);
				}
				idx++;
			}
			result.sort();
			return Arrays.copyOf(result.toArray(), k);
		}

		// Select k rows closest to the center
		// Simple approach: take k centered rows
		int start=Math.max(0, (candidateRows.length-k)/2);
		return Arrays.copyOfRange(candidateRows, start, start+k);
	}

	@Override
	public String toString() {
		return "DemuxDesignMatrix["+numAcquiredPositions+" × "+numSubWindows+"]";
	}

	/**
	 * Prints the design matrix for debugging.
	 */
	public void printMatrix() {
		Logger.logLine("Design Matrix ("+numAcquiredPositions+" × "+numSubWindows+"):");
		for (int i=0; i<numAcquiredPositions; i++) {
			StringBuilder sb=new StringBuilder();
			sb.append(String.format("Row %2d: [", i));
			for (int j=0; j<numSubWindows; j++) {
				sb.append(fullMatrix.get(i, j)>0?"1 ":"0 ");
			}
			sb.append("]");
			Logger.logLine(sb.toString());
		}
	}
}
