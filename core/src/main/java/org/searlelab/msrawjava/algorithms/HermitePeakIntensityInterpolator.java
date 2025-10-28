package org.searlelab.msrawjava.algorithms;

import java.util.Objects;

import org.searlelab.msrawjava.model.PeakInTime;
import org.searlelab.msrawjava.model.PeakInterface;

public final class HermitePeakIntensityInterpolator implements PeakInterface {
	private static final float DEFAULT_TENSION=0.5f; // tight to avoid overshoot
	private static final float DEFAULT_BIAS=0.0f;

	private final PeakInTime[] knots; // pre-sorted by RT
	private final double mz;
	private final float summedIntensity;
	private volatile boolean toggle=true;

	public HermitePeakIntensityInterpolator(PeakInTime[] sortedPeaks, double mz) {
		this.knots=Objects.requireNonNull(sortedPeaks, "sortedPeaks");
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

		final int i0=i-1;
		final int i1=i;
		final int i2=i+1;
		final int i3=i+2;

		if (i0>=0&&i3<n) {
			return cubicHermiteClamped(knots[i0], knots[i1], knots[i2], knots[i3], rtInSec, DEFAULT_TENSION, DEFAULT_BIAS);
		}

		if (i0<0) {
			return quadraticLagrangeClamped(knots[0], knots[1], knots[2], rtInSec);
		} else {
			return quadraticLagrangeClamped(knots[n-3], knots[n-2], knots[n-1], rtInSec);
		}
	}

	public PeakInTime[] getKnots() {
		return knots;
	}

	@Override
	public String toString() {
		return "HermitePeakIntensityInterpolator[mz="+mz+", knots="+knots.length+"]";
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

	private static float cubicHermiteClamped(PeakInTime p0, PeakInTime p1, PeakInTime p2, PeakInTime p3, float rt, float tension, float bias) {
		final float t1=p1.rtInSec, t2=p2.rtInSec;
		final float y0=p0.intensity, y1=p1.intensity, y2=p2.intensity, y3=p3.intensity;
		final float dt=(t2-t1);
		if (dt==0f) {
			final float leeway=maxAdjDelta(y0, y1, y2, y3);
			return clamp(0.5f*(y1+y2), min4(y0, y1, y2, y3)-leeway, max4(y0, y1, y2, y3)+leeway);
		}
		final float mu=(rt-t1)/dt;

		final float oneMinusT=1f-tension;
		float m0=0.5f*(1f+bias)*oneMinusT*(y1-y0)+0.5f*(1f-bias)*oneMinusT*(y2-y1);
		float m1=0.5f*(1f+bias)*oneMinusT*(y2-y1)+0.5f*(1f-bias)*oneMinusT*(y3-y2);

		final float mu2=mu*mu, mu3=mu2*mu;
		final float a0=2f*mu3-3f*mu2+1f;
		final float a1=mu3-2f*mu2+mu;
		final float a2=mu3-mu2;
		final float a3=-2f*mu3+3f*mu2;

		float y=a0*y1+a1*m0+a2*m1+a3*y2;

		final float leeway=maxAdjDelta(y0, y1, y2, y3);
		return clamp(y, min4(y0, y1, y2, y3)-leeway, max4(y0, y1, y2, y3)+leeway);
	}

	private static float quadraticLagrangeClamped(PeakInTime a, PeakInTime b, PeakInTime c, float rt) {
		final float t0=a.rtInSec, t1=b.rtInSec, t2=c.rtInSec;
		final float y0=a.intensity, y1=b.intensity, y2=c.intensity;

		final float L0d=(t0-t1)*(t0-t2);
		final float L1d=(t1-t0)*(t1-t2);
		final float L2d=(t2-t0)*(t2-t1);

		float L0=(L0d!=0f)?((rt-t1)*(rt-t2))/L0d:0f;
		float L1=(L1d!=0f)?((rt-t0)*(rt-t2))/L1d:0f;
		float L2=(L2d!=0f)?((rt-t0)*(rt-t1))/L2d:0f;

		float y=L0*y0+L1*y1+L2*y2;

		final float ymin=Math.min(y0, Math.min(y1, y2));
		final float ymax=Math.max(y0, Math.max(y1, y2));
		final float leeway=Math.max(Math.abs(y1-y0), Math.abs(y2-y1));
		return clamp(y, ymin-leeway, ymax+leeway);
	}

	private static float min4(float a, float b, float c, float d) {
		float m=(a<b)?a:b;
		if (c<m) m=c;
		if (d<m) m=d;
		return m;
	}

	private static float max4(float a, float b, float c, float d) {
		float m=(a>b)?a:b;
		if (c>m) m=c;
		if (d>m) m=d;
		return m;
	}

	private static float maxAdjDelta(float y0, float y1, float y2, float y3) {
		float d01=Math.abs(y1-y0);
		float d12=Math.abs(y2-y1);
		float d23=Math.abs(y3-y2);
		float m=(d01>d12)?d01:d12;
		if (d23>m) m=d23;
		return m;
	}

	private static float clamp(float v, float lo, float hi) {
		return (v<lo)?lo:(v>hi)?hi:v;
	}
}