package org.searlelab.msrawjava.algorithms.demux;

import org.searlelab.msrawjava.model.Range;

/**
 * Represents a demultiplexing sub-window within the staggered DIA scheme.
 *
 * In staggered DIA, each acquired isolation window covers multiple sub-windows.
 * These sub-windows represent the overlap regions where signals can be uniquely
 * assigned after demultiplexing. For example, with 20 Th windows and 10 Th offset:
 *
 * <pre>
 * Acquired Window 1: |------- 400-420 Th -------|
 * Acquired Window 2:          |------- 410-430 Th -------|
 *
 * Sub-windows:       |-- 400-410 --|-- 410-420 --|-- 420-430 --|
 *                    (sub-window 0) (sub-window 1) (sub-window 2)
 * </pre>
 *
 * Each sub-window tracks its bounds and index in the design matrix.
 */
public class DemuxWindow implements Comparable<DemuxWindow> {

	private final Range range;
	private final int index;

	/**
	 * Creates a new demultiplexing sub-window.
	 *
	 * @param range the m/z bounds of this sub-window
	 * @param index the column index in the design matrix
	 */
	public DemuxWindow(Range range, int index) {
		this.range = range;
		this.index = index;
	}

	/**
	 * Creates a new demultiplexing sub-window.
	 *
	 * @param lowerMz the lower m/z bound
	 * @param upperMz the upper m/z bound
	 * @param index   the column index in the design matrix
	 */
	public DemuxWindow(double lowerMz, double upperMz, int index) {
		this(new Range((float) lowerMz, (float) upperMz), index);
	}

	/**
	 * Returns the m/z range of this sub-window.
	 */
	public Range getRange() {
		return range;
	}

	/**
	 * Returns the lower m/z bound.
	 */
	public double getLowerMz() {
		return range.getStart();
	}

	/**
	 * Returns the upper m/z bound.
	 */
	public double getUpperMz() {
		return range.getStop();
	}

	/**
	 * Returns the center m/z of this sub-window.
	 */
	public double getCenterMz() {
		return range.getMiddle();
	}

	/**
	 * Returns the width of this sub-window in m/z.
	 */
	public double getWidth() {
		return range.getRange();
	}

	/**
	 * Returns the column index in the design matrix.
	 */
	public int getIndex() {
		return index;
	}

	/**
	 * Tests if a given m/z value falls within this sub-window.
	 *
	 * @param mz the m/z value to test
	 * @return true if mz is within [lowerMz, upperMz]
	 */
	public boolean contains(double mz) {
		return range.contains(mz);
	}

	/**
	 * Tests if a given range overlaps with this sub-window.
	 *
	 * @param other the range to test
	 * @return true if there is any overlap
	 */
	public boolean overlaps(Range other) {
		return range.getStart() < other.getStop() && range.getStop() > other.getStart();
	}

	/**
	 * Tolerance for window boundary comparisons in Th.
	 * Floating-point precision issues between window definitions and spectrum
	 * isolation bounds can cause mismatches of ~0.01 Th. A 0.1 Th tolerance
	 * is safe since sub-windows are typically 4-8 Th wide.
	 */
	private static final double BOUNDARY_TOLERANCE = 0.1;

	/**
	 * Tests if an acquired window (defined by its bounds) contains this sub-window.
	 * Uses a small tolerance to handle floating-point precision differences
	 * between window definitions and spectrum isolation bounds.
	 *
	 * @param windowLower the lower bound of the acquired window
	 * @param windowUpper the upper bound of the acquired window
	 * @return true if this sub-window is entirely within the acquired window (with tolerance)
	 */
	public boolean isContainedBy(double windowLower, double windowUpper) {
		// Use tolerance to handle floating-point precision issues
		// Sub-window must be within acquired window, allowing small boundary differences
		return range.getStart() >= windowLower - BOUNDARY_TOLERANCE &&
				range.getStop() <= windowUpper + BOUNDARY_TOLERANCE;
	}

	@Override
	public int compareTo(DemuxWindow other) {
		int c = Double.compare(this.getCenterMz(), other.getCenterMz());
		if (c != 0) return c;
		return Integer.compare(this.index, other.index);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof DemuxWindow)) return false;
		DemuxWindow other = (DemuxWindow) obj;
		return this.index == other.index &&
				Double.compare(this.range.getStart(), other.range.getStart()) == 0 &&
				Double.compare(this.range.getStop(), other.range.getStop()) == 0;
	}

	@Override
	public int hashCode() {
		int result = index;
		result = 31 * result + Float.floatToIntBits(range.getStart());
		result = 31 * result + Float.floatToIntBits(range.getStop());
		return result;
	}

	@Override
	public String toString() {
		return "DemuxWindow[" + index + ": " +
				String.format("%.2f", range.getStart()) + "-" +
				String.format("%.2f", range.getStop()) + " Th]";
	}
}
