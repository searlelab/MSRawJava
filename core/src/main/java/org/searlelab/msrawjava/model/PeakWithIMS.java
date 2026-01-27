package org.searlelab.msrawjava.model;

import java.util.Comparator;

/**
 * Peak is a value object representing a single centroided data point, typically holding an m/z, an intensity, and (when
 * available) an ion-mobility coordinate. It is meant for heavy-weight data analysis, rather than light-weight data
 * transfer.
 */
public class PeakWithIMS implements PeakInterface {
	public final double mz;
	public final float intensity;
	public final float ims;
	private volatile boolean toggle=true;

	public PeakWithIMS(double mz, float intensity, float ims) {
		this.mz=mz;
		this.intensity=intensity;
		this.ims=ims;
	}

	@Override
	public String toString() {
		return "mz="+mz+",ims="+ims+",int="+intensity;
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
		return intensity;
	}

	@Override
	public double getMz() {
		return mz;
	}

	public float getIMS() {
		return ims;
	}

	@Override
	public int compareTo(PeakInterface o) {
		if (o==null) return 1;
		int c=Double.compare(mz, o.getMz());
		if (c!=0) return c;
		if (o instanceof PeakWithIMS) {
			c=Double.compare(ims, ((PeakWithIMS)o).ims);
			if (c!=0) return c;
		}
		c=Double.compare(intensity, o.getIntensity());
		return c;
	}

	public static final class PeakIntensityComparator implements Comparator<PeakWithIMS> {
		@Override
		public int compare(PeakWithIMS a, PeakWithIMS b) {
			if (a==b) return 0;
			if (a==null) return -1;
			if (b==null) return 1;

			int c=Float.compare(a.intensity, b.intensity);
			if (c!=0) return c;

			c=Double.compare(a.mz, b.mz);
			if (c!=0) return c;

			return Float.compare(a.ims, b.ims);
		}
	}

	public static final class PeakIMSComparator implements Comparator<PeakWithIMS> {
		@Override
		public int compare(PeakWithIMS a, PeakWithIMS b) {
			if (a==b) return 0;
			if (a==null) return -1;
			if (b==null) return 1;

			int c=Float.compare(a.ims, b.ims);
			if (c!=0) return c;

			c=Float.compare(a.intensity, b.intensity);
			if (c!=0) return c;

			return Double.compare(a.mz, b.mz);
		}
	}
}
