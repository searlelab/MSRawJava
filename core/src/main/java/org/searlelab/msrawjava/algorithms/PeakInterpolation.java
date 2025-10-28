package org.searlelab.msrawjava.algorithms;

import org.searlelab.msrawjava.model.PeakInTime;

public final class PeakInterpolation {

	/**
	 * Hermite 4-point cubic with tension/bias controls (Kochanek–Bartels).
	 * Interpolates on the segment [p1, p2] using neighbors p0 and p3 for tangents.
	 * Tight defaults to avoid overshoot; final value is clamped with small leeway
	 * equal to the largest adjacent intensity delta.
	 *
	 * Assumes time order: p0.rt < p1.rt <= rtInSec <= p2.rt < p3.rt
	 */
	public static float interpolateHermiteIntensity(PeakInTime p0, PeakInTime p1, PeakInTime p2, PeakInTime p3, float rtInSec) {

		// unpack (float math for speed)
		final float t0=p0.rtInSec, t1=p1.rtInSec, t2=p2.rtInSec, t3=p3.rtInSec;
		final float y0=p0.intensity, y1=p1.intensity, y2=p2.intensity, y3=p3.intensity;

		final float dt=(t2-t1);
		if (dt==0f) {
			// degenerate: return midpoint
			return (y1+y2)/2.0f;
		}
		final float mu=(rtInSec-t1)/dt;

		// --- Kochanek–Bartels tangents (Paul Bourke’s form) ---
		// Tension: 1 high (tight), 0 normal; Bias: 0 even; positive bias favors incoming segment.
		// Choose fairly tight defaults to reduce overshoot:
		final float tension=0.5f; // tighter than Catmull-Rom
		final float bias=0.0f;

		final float oneMinusT=(1f-tension);
		// m0 at y1, m1 at y2 (scaled by 1/2 * (1±bias) * (1−tension))
		float m0=0.5f*(1f+bias)*oneMinusT*(y1-y0);
		m0+=0.5f*(1f-bias)*oneMinusT*(y2-y1);
		float m1=0.5f*(1f+bias)*oneMinusT*(y2-y1);
		m1+=0.5f*(1f-bias)*oneMinusT*(y3-y2);

		// Hermite basis (mu in [0,1])
		final float mu2=mu*mu;
		final float mu3=mu2*mu;
		final float a0=2f*mu3-3f*mu2+1f;
		final float a1=mu3-2f*mu2+mu;
		final float a2=mu3-mu2;
		final float a3=-2f*mu3+3f*mu2;

		// interpolate
		float y=a0*y1+a1*m0+a2*m1+a3*y2;

		// small overshoot clamp: allow up to the largest adjacent step
		final float leeway=maxAdjDelta(y0, y1, y2, y3);
		final float lo=min4(y0, y1, y2, y3)-leeway;
		final float hi=max4(y0, y1, y2, y3)+leeway;
		return clamp(y, lo, hi);
	}

	/**
	 * @param m2
	 *            point at time-2 (earliest)
	 * @param m1
	 *            point at time-1 (before target)
	 * @param p1
	 *            point at time+1 (after target)
	 * @param p2
	 *            point at time+2 (latest)
	 * @param rtInSec
	 *            target retention time (guaranteed: m2.rt < m1.rt <= rtInSec <= p1.rt < p2.rt)
	 */
	public static float interpolateIntensity(PeakInTime m2, PeakInTime m1, PeakInTime p1, PeakInTime p2, float rtInSec) {

		// unpack (guaranteed time order: m2 < m1 < rt < p1 < p2)
		final float t0=m2.rtInSec, t1=m1.rtInSec, t2=p1.rtInSec, t3=p2.rtInSec;
		final float y0=m2.intensity, y1=m1.intensity, y2=p1.intensity, y3=p2.intensity;
		final float t=rtInSec;

		// range + leeway (allow overshoot up to max adjacent delta)
		final float ymin=min4(y0, y1, y2, y3);
		final float ymax=max4(y0, y1, y2, y3);
		final float leeway=maxAdjDelta(y0, y1, y2, y3);
		final float lo=ymin-leeway;
		final float hi=ymax+leeway;

		// choose 3 points for log-quadratic: {m1, p1} plus the nearer of {m2, p2}
		final float dL=Math.abs(t-t0);
		final float dR=Math.abs(t-t3);
		float T1, Y1, T2, Y2, T3, Y3;
		if (dL<dR) {
			T1=t0;
			Y1=y0;
			T2=t1;
			Y2=y1;
			T3=t2;
			Y3=y2;
		} else {
			T1=t1;
			Y1=y1;
			T2=t2;
			Y2=y2;
			T3=t3;
			Y3=y3;
		}

		// log-parabola path (Gaussian core): fit ln(y) = a*t^2 + b*t + c
		if (Y1>0f&&Y2>0f&&Y3>0f&&T1!=T2&&T1!=T3&&T2!=T3) {
			final float L1=(float)Math.log(Y1);
			final float L2=(float)Math.log(Y2);
			final float L3=(float)Math.log(Y3);

			final float dt12=T2-T1, dt23=T3-T2, dt13=T3-T1;
			final float s12=(L2-L1)/dt12;
			final float s23=(L3-L2)/dt23;
			final float a2=(s23-s12)/dt13;
			final float b2=s12-a2*(T1+T2);
			final float c2=L2-a2*T2*T2-b2*T2;

			final float Lhat=a2*t*t+b2*t+c2;
			if (Float.isFinite(Lhat)) {
				float yhat=(float)Math.exp(Lhat);
				if (yhat>0f&&Float.isFinite(yhat)) {
					return clamp(yhat, lo, hi);
				}
			}
		}

		// Hermite fallback on [m1, p1]
		final float dt=(t2-t1);
		if (dt==0f) return clamp(safeMid(y1, y2, y0, y3), lo, hi);

		final float tension=0.35f; // damp overshoot
		final float oneMinusT=1f-tension;

		final float mT1=oneMinusT*secant(y0, y1, y2, t0, t1, t2)*dt; // tangent at m1
		final float mT2=oneMinusT*secant(y1, y2, y3, t1, t2, t3)*dt; // tangent at p1

		final float u=(t-t1)/dt;
		final float u2=u*u, u3=u2*u;
		final float h00=2f*u3-3f*u2+1f;
		final float h10=u3-2f*u2+u;
		final float h01=-2f*u3+3f*u2;
		final float h11=u3-u2;

		float y=h00*y1+h10*mT1+h01*y2+h11*mT2;
		return clamp(y, lo, hi);
	}

	private static float secant(float yA, float yB, float yC, float tA, float tB, float tC) {
		final float dAB=(tB!=tA)?(yB-yA)/(tB-tA):0f;
		final float dBC=(tC!=tB)?(yC-yB)/(tC-tB):0f;
		return 0.5f*(dAB+dBC);
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
		lo=Math.max(0.0f, lo);
		return (v<lo)?lo:(v>hi)?hi:v;
	}

	private static float safeMid(float y1, float y2, float y0, float y3) {
		final float lo=Math.min(y1, y2);
		final float hi=Math.max(y1, y2);
		float mid=0.5f*(y1+y2);
		if (mid<lo) mid=lo;
		else if (mid>hi) mid=hi;
		final float gmin=Math.min(Math.min(y0, y1), Math.min(y2, y3));
		final float gmax=Math.max(Math.max(y0, y1), Math.max(y2, y3));
		if (mid<gmin) mid=gmin;
		else if (mid>gmax) mid=gmax;
		return mid;
	}
}