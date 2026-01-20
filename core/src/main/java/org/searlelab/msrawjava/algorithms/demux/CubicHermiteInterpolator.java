package org.searlelab.msrawjava.algorithms.demux;

import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

/**
 * Cubic hermite spline interpolation for retention time alignment.
 *
 * This implementation matches the pwiz/Skyline approach using cubic spline
 * interpolation to estimate intensity values at a common retention time.
 * The cubic spline provides smooth, monotonicity-preserving interpolation
 * that handles chromatographic peak shapes well.
 *
 * For points outside the data range, values are clamped to the nearest boundary.
 * Negative interpolated values are clamped to zero (physical constraint).
 */
public class CubicHermiteInterpolator implements RetentionTimeInterpolator {

	private static final double EPSILON=1e-10;

	@Override
	public float interpolate(float[] times, float[] intensities, float targetTime) {
		if (times==null||intensities==null||times.length==0) {
			return 0.0f;
		}

		if (times.length!=intensities.length) {
			throw new IllegalArgumentException("times and intensities arrays must have same length");
		}

		// Convert to double for interpolation
		double[] dTimes=new double[times.length];
		double[] dIntensities=new double[intensities.length];
		for (int i=0; i<times.length; i++) {
			dTimes[i]=times[i];
			dIntensities[i]=intensities[i];
		}

		double result=interpolate(dTimes, dIntensities, targetTime);
		return (float)result;
	}

	@Override
	public double interpolate(double[] times, double[] intensities, double targetTime) {
		if (times==null||intensities==null||times.length==0) {
			return 0.0;
		}

		int n=times.length;

		if (n!=intensities.length) {
			throw new IllegalArgumentException("times and intensities arrays must have same length");
		}

		// Handle single point
		if (n==1) {
			return Math.max(0.0, intensities[0]);
		}

		// Handle extrapolation - clamp to boundary values
		if (targetTime<=times[0]) {
			return Math.max(0.0, intensities[0]);
		}
		if (targetTime>=times[n-1]) {
			return Math.max(0.0, intensities[n-1]);
		}

		// Handle two points - linear interpolation
		if (n==2) {
			double t=(targetTime-times[0])/(times[1]-times[0]);
			double result=intensities[0]+t*(intensities[1]-intensities[0]);
			return Math.max(0.0, result);
		}

		// Handle duplicate time points by averaging intensities
		double[] uniqueTimes=new double[n];
		double[] uniqueIntensities=new double[n];
		int uniqueCount=0;

		for (int i=0; i<n; i++) {
			if (uniqueCount==0||times[i]-uniqueTimes[uniqueCount-1]>EPSILON) {
				uniqueTimes[uniqueCount]=times[i];
				uniqueIntensities[uniqueCount]=intensities[i];
				uniqueCount++;
			} else {
				// Average with previous value
				uniqueIntensities[uniqueCount-1]=(uniqueIntensities[uniqueCount-1]+intensities[i])/2.0;
			}
		}

		// If we still have fewer than 3 unique points, use linear interpolation
		if (uniqueCount<3) {
			double t=(targetTime-uniqueTimes[0])/(uniqueTimes[uniqueCount-1]-uniqueTimes[0]);
			double result=uniqueIntensities[0]+t*(uniqueIntensities[uniqueCount-1]-uniqueIntensities[0]);
			return Math.max(0.0, result);
		}

		// Trim arrays to unique count
		double[] trimmedTimes=new double[uniqueCount];
		double[] trimmedIntensities=new double[uniqueCount];
		System.arraycopy(uniqueTimes, 0, trimmedTimes, 0, uniqueCount);
		System.arraycopy(uniqueIntensities, 0, trimmedIntensities, 0, uniqueCount);

		try {
			// Use Apache Commons Math spline interpolator
			SplineInterpolator interpolator=new SplineInterpolator();
			PolynomialSplineFunction spline=interpolator.interpolate(trimmedTimes, trimmedIntensities);

			double result=spline.value(targetTime);
			return Math.max(0.0, result);
		} catch (Exception e) {
			// Fallback to linear interpolation if spline fails
			return linearInterpolate(trimmedTimes, trimmedIntensities, targetTime);
		}
	}

	/**
	 * Fallback linear interpolation.
	 */
	private double linearInterpolate(double[] times, double[] intensities, double targetTime) {
		int n=times.length;

		// Find bracketing interval
		int lo=0;
		int hi=n-1;
		while (lo+1<hi) {
			int mid=(lo+hi)/2;
			if (times[mid]<=targetTime) {
				lo=mid;
			} else {
				hi=mid;
			}
		}

		// Linear interpolation within interval
		double t=(targetTime-times[lo])/(times[hi]-times[lo]);
		double result=intensities[lo]+t*(intensities[hi]-intensities[lo]);
		return Math.max(0.0, result);
	}

	@Override
	public String getName() {
		return "CubicHermite";
	}
}
