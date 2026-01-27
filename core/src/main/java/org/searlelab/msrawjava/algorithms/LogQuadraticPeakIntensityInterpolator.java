package org.searlelab.msrawjava.algorithms;

import java.util.Objects;

import org.searlelab.msrawjava.model.PeakInTime;
import org.searlelab.msrawjava.model.PeakInterface;

public final class LogQuadraticPeakIntensityInterpolator implements PeakInterface {

	private final PeakInTime[] knots; // pre-sorted by RT
	private final double mz;
	private final float summedIntensity;
	private final float averageXIncrement;
	private volatile boolean toggle=true;

	public LogQuadraticPeakIntensityInterpolator(PeakInTime[] sortedPeaks, double mz) {
		this.knots=Objects.requireNonNull(sortedPeaks, "sortedPeaks").clone();
		// log all the knots
		for (int i=0; i<knots.length; i++) {
			knots[i]=new PeakInTime(knots[i].mz, (float)Math.log(knots[i].intensity+Math.E), knots[i].rtInSec);
		}
		averageXIncrement=(knots[knots.length-1].rtInSec-knots[0].rtInSec)/knots.length;
		if (sortedPeaks.length<1) throw new IllegalArgumentException("At least 1 knot required");
		this.mz=mz;

		float s=0f;
		for (PeakInTime p : sortedPeaks) {
			s+=p.intensity;
		}
		this.summedIntensity=s;
	}

	/** Return intensity at the given retention time. */
	public float getIntensity(float rtInSec) {
		final int n=knots.length;

		if (n==1) return knots[0].intensity;
		if (rtInSec<=knots[0].rtInSec) return knots[0].intensity;
		if (rtInSec>=knots[n-1].rtInSec) return knots[n-1].intensity;

		// binary search for insertion point
		int lo=0, hi=n-1;
		while (lo+1<hi) {
			int mid=lo+(hi-lo)/2;
			if (knots[mid].rtInSec<=rtInSec) {
				lo=mid;
			} else {
				hi=mid;
			}
		}
		int i=lo;

		float y0=i>0?knots[i-1].intensity:0.0f;
		float y1=i>=0?knots[i].intensity:0.0f;
		float y2=i<knots.length-1?knots[i+1].intensity:0.0f;
		float y3=i<knots.length-2?knots[i+2].intensity:0.0f;

		float x0=i>0?knots[i-1].rtInSec:knots[i].rtInSec-averageXIncrement;
		float x1=i>=0?knots[i].rtInSec:0.0f;
		float x2=i<knots.length-1?knots[i+1].rtInSec:knots[i].rtInSec+averageXIncrement;
		float x3=i<knots.length-2?knots[i+2].rtInSec:knots[i].rtInSec+2.0f*averageXIncrement;

		if (y1==0.0f&&y2==0.0f) {
			return 0.0f;
		}

		//float logIntensity=Math.max(0.0f, quadraticFit(knots[i0], knots[i1], knots[i2], knots[i3], rtInSec));
		float logIntensity=Math.max(0.0f, (float)quadraticFit(x0, x1, x2, x3, y0, y1, y2, y3, rtInSec));
		return (float)(Math.exp(logIntensity)-Math.E);
	}

	public PeakInTime[] getKnots() {
		return knots;
	}

	@Override
	public String toString() {
		return "LogQuadraticPeakIntensityInterpolator[mz="+mz+", knots="+knots.length+"]";
	}

	@Override
	public boolean isAvailable() {
		return toggle;
	}

	@Override
	public void turnOff() {
		this.toggle=false;
	}

	@Override
	public void turnOn() {
		this.toggle=true;
	}

	@Override
	public float getIntensity() {
		return summedIntensity;
	}

	@Override
	public double getMz() {
		return mz;
	}

	@Override
	public int compareTo(PeakInterface o) {
		if (o==null) return 1;
		int c=Double.compare(getMz(), o.getMz());
		if (c!=0) return c;
		c=Double.compare(getIntensity(), o.getIntensity());
		return c;
	}

	private static double quadraticFit(double x0, double x1, double x2, double x3, // x values surrounding xi
			double y0, double y1, double y2, double y3, // corresponding y values
			double xi) {

		// If inner times coincide, fall back to their average
		double epsilon=1e-6;
		double deltaX12=x2-x1;
		if (Math.abs(deltaX12)<epsilon) return 0.5*(y1+y2);

		// Calculate a line through inner points
		double slope=(y2-y1)/deltaX12;
		double b=y1-slope*x1;

		// Check to see if just effectively a straight line within epsilon
		double linearFitAtX0=slope*x0+b;
		double linearFitAtX3=slope*x3+b;
		double linearFitAtXi=slope*xi+b;
		if (Math.abs(linearFitAtX0-y0)<=epsilon&&Math.abs(linearFitAtX3-y3)<=epsilon) {
			// If so, just return the line
			return linearFitAtXi;
		}

		// Calculate the parabolic shape relative to xi
		double parabolaXi=(xi-x1)*(xi-x2);

		// Calculate the parabolic shape at the outer points as left and right guides
		double parabolaX0=(x0-x1)*(x0-x2);
		double parabolaX3=(x3-x1)*(x3-x2);

		// Linear weights that defer to higher outer values
		double weight0=Math.max(y0, epsilon);
		double weight3=Math.max(y3, epsilon);

		// Closed-form k from weighted least squares using the two outer points
		double numerator=weight0*parabolaX0*(y0-linearFitAtX0)+weight3*parabolaX3*(y3-linearFitAtX3);
		double denominator=weight0*parabolaX0*parabolaX0+weight3*parabolaX3*parabolaX3;

		// Final curvature parameter chosen by the weighted fit
		double k=(Math.abs(denominator)>epsilon)?(numerator/denominator):0.0;

		//System.out.println(xi+"\t"+k+"\t"+(slope-k*(x1+x2))+"\t"+(b+k*x1*x2));

		// Quadratic value at rtInSec
		return linearFitAtXi+k*parabolaXi;
	}
}