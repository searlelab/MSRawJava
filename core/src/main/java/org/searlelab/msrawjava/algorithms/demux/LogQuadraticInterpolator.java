package org.searlelab.msrawjava.algorithms.demux;

/**
 * Log-quadratic interpolation for retention time alignment.
 *
 * This implementation adapts the original MSRawJava approach that uses
 * log-space quadratic fitting to handle chromatographic peak shapes.
 * Working in log-space helps preserve non-negativity and handles the
 * exponential nature of intensity changes.
 *
 * The algorithm:
 * 1. Transform intensities to log-space: log(intensity + e)
 * 2. Fit a quadratic through the surrounding points
 * 3. Evaluate at target time and transform back: exp(result) - e
 *
 * For points outside the data range, values are clamped to the nearest boundary.
 */
public class LogQuadraticInterpolator implements RetentionTimeInterpolator {

	private static final double EPSILON=1e-6;

	@Override
	public float interpolate(float[] times, float[] intensities, float targetTime) {
		if (times==null||intensities==null||times.length==0) {
			return 0.0f;
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

		// Transform to log-space
		double[] logIntensities=new double[n];
		for (int i=0; i<n; i++) {
			logIntensities[i]=Math.log(intensities[i]+Math.E);
		}

		// Find bracketing interval using binary search
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

		// Handle two points - linear interpolation in log-space
		if (n==2) {
			double t=(targetTime-times[0])/(times[1]-times[0]);
			double logResult=logIntensities[0]+t*(logIntensities[1]-logIntensities[0]);
			return Math.max(0.0, Math.exp(logResult)-Math.E);
		}

		// Get 4 points for quadratic fit (or fewer at boundaries)
		int i0=Math.max(0, lo-1);
		int i1=lo;
		int i2=hi;
		int i3=Math.min(n-1, hi+1);

		// Check if we have valid points for both pairs
		if (logIntensities[i1]==0.0&&logIntensities[i2]==0.0) {
			return 0.0;
		}

		// Quadratic fit using the 4 points
		double logResult=quadraticFit(times[i0], times[i1], times[i2], times[i3], logIntensities[i0], logIntensities[i1], logIntensities[i2],
				logIntensities[i3], targetTime);

		// Transform back from log-space and clamp to non-negative
		double result=Math.exp(Math.max(0.0, logResult))-Math.E;
		return Math.max(0.0, result);
	}

	/**
	 * Fits a quadratic through 4 points and evaluates at xi.
	 *
	 * Uses weighted least squares with the outer points as guides for curvature,
	 * while the inner points define the baseline linear trend.
	 */
	private double quadraticFit(double x0, double x1, double x2, double x3, double y0, double y1, double y2, double y3, double xi) {

		// If inner times coincide, fall back to their average
		double deltaX12=x2-x1;
		if (Math.abs(deltaX12)<EPSILON) {
			return 0.5*(y1+y2);
		}

		// Calculate a line through inner points
		double slope=(y2-y1)/deltaX12;
		double b=y1-slope*x1;

		// Check to see if effectively a straight line
		double linearFitAtX0=slope*x0+b;
		double linearFitAtX3=slope*x3+b;
		double linearFitAtXi=slope*xi+b;

		if (Math.abs(linearFitAtX0-y0)<=EPSILON&&Math.abs(linearFitAtX3-y3)<=EPSILON) {
			return linearFitAtXi;
		}

		// Calculate parabolic shapes
		double parabolaXi=(xi-x1)*(xi-x2);
		double parabolaX0=(x0-x1)*(x0-x2);
		double parabolaX3=(x3-x1)*(x3-x2);

		// Linear weights that defer to higher outer values
		double weight0=Math.max(y0, EPSILON);
		double weight3=Math.max(y3, EPSILON);

		// Closed-form k from weighted least squares using the two outer points
		double numerator=weight0*parabolaX0*(y0-linearFitAtX0)+weight3*parabolaX3*(y3-linearFitAtX3);
		double denominator=weight0*parabolaX0*parabolaX0+weight3*parabolaX3*parabolaX3;

		// Final curvature parameter
		double k=(Math.abs(denominator)>EPSILON)?(numerator/denominator):0.0;

		// Quadratic value at xi
		return linearFitAtXi+k*parabolaXi;
	}

	@Override
	public String getName() {
		return "LogQuadratic";
	}
}
