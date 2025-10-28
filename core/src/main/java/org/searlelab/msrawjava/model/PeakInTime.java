package org.searlelab.msrawjava.model;

import java.util.Comparator;

/**
 * Peak is a value object representing a single centroided data point, typically holding an m/z, an intensity, and (when
 * available) an retention time coordinate. It is meant for heavy-weight data analysis, rather than light-weight data
 * transfer.
 */
public class PeakInTime implements PeakInterface {
	public static final PeakIntensityComparator INTENSITY_COMPARATOR=new PeakIntensityComparator();
	public static final PeakRTComparator RT_COMPARATOR=new PeakRTComparator();
	
	public final double mz;
	public final float intensity;
	public final float rtInSec;
	private volatile boolean toggle=true;

	public PeakInTime(double mz, float intensity, float rtInSec) {
		this.mz=mz;
		this.intensity=intensity;
		this.rtInSec=rtInSec;
	}

	@Override
	public String toString() {
		return "mz="+mz+",rt="+rtInSec+",int="+intensity;
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
	public double getMz() {
		return mz;
	}
	
	@Override
	public float getIntensity() {
		return intensity;
	}
	public float getRtInSec() {
		return rtInSec;
	}

	@Override
	public int compareTo(PeakInterface o) {
		if (o==null) return 1;
		int c=Double.compare(mz, o.getMz());
		if (c!=0) return c;
		if (o instanceof PeakInTime) {
			c=Double.compare(rtInSec, ((PeakInTime)o).rtInSec);
			if (c!=0) return c;
		}
		c=Double.compare(intensity, o.getIntensity());
		return c;
	}

	public static final class PeakIntensityComparator implements Comparator<PeakInTime> {
		@Override
		public int compare(PeakInTime a, PeakInTime b) {
			if (a==b) return 0;
			if (a==null) return -1;
			if (b==null) return 1;

			int c=Float.compare(a.intensity, b.intensity);
			if (c!=0) return c;

			c=Double.compare(a.mz, b.mz);
			if (c!=0) return c;

			return Float.compare(a.rtInSec, b.rtInSec);
		}
	}

	public static final class PeakRTComparator implements Comparator<PeakInTime> {
		@Override
		public int compare(PeakInTime a, PeakInTime b) {
			if (a==b) return 0;
			if (a==null) return -1;
			if (b==null) return 1;

			int c=Float.compare(a.rtInSec, b.rtInSec);
			if (c!=0) return c;

			c=Float.compare(a.intensity, b.intensity);
			if (c!=0) return c;

			return Double.compare(a.mz, b.mz);
		}
	}
}
