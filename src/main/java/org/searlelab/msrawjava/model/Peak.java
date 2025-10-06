package org.searlelab.msrawjava.model;

import java.util.Comparator;

public class Peak implements Comparable<Peak> {
	public final double mz;
	public final float intensity;
	public final float ims;
	private boolean toggle=true;

	public Peak(double mz, float intensity, float ims) {
		this.mz=mz;
		this.intensity=intensity;
		this.ims=ims;
	}

	@Override
	public String toString() {
		return "mz="+mz+",ims="+ims+",int="+intensity;
	}

	public boolean isAvailable() {
		return toggle;
	}

	public void turnOff() {
		this.toggle=false;
	}

	@Override
	public int compareTo(Peak o) {
		if (o==null) return 1;
		int c=Double.compare(mz, o.mz);
		if (c!=0) return c;
		c=Double.compare(ims, o.ims);
		if (c!=0) return c;
		c=Double.compare(intensity, o.intensity);
		return c;
	}

	public static final class PeakIntensityComparator implements Comparator<Peak> {
		@Override
		public int compare(Peak a, Peak b) {
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

	public static final class PeakIMSComparator implements Comparator<Peak> {
		@Override
		public int compare(Peak a, Peak b) {
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
