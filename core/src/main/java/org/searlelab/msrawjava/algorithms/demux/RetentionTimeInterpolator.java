package org.searlelab.msrawjava.algorithms.demux;

/**
 * Interface for retention time interpolation in staggered DIA demultiplexing.
 *
 * Different scans in the duty cycle are acquired at different retention times.
 * To build a consistent linear system for NNLS solving, we need to interpolate
 * all intensity values to a common reference time (typically the center scan's RT).
 *
 * Implementations must handle:
 * - Interpolation between known data points
 * - Extrapolation at boundaries (usually clamped to nearest value)
 * - Missing data points (treated as zero intensity)
 */
public interface RetentionTimeInterpolator {

	/**
	 * Interpolates intensity at the target retention time.
	 *
	 * @param times
	 *            array of retention times (seconds), must be sorted ascending
	 * @param intensities
	 *            array of intensities corresponding to each time point
	 * @param targetTime
	 *            the retention time at which to interpolate
	 * @return interpolated intensity (always >= 0)
	 */
	float interpolate(float[] times, float[] intensities, float targetTime);

	/**
	 * Interpolates intensity at the target retention time with double precision.
	 *
	 * @param times
	 *            array of retention times (seconds), must be sorted ascending
	 * @param intensities
	 *            array of intensities corresponding to each time point
	 * @param targetTime
	 *            the retention time at which to interpolate
	 * @return interpolated intensity (always >= 0)
	 */
	double interpolate(double[] times, double[] intensities, double targetTime);

	/**
	 * Returns the name of this interpolation method.
	 */
	String getName();
}
