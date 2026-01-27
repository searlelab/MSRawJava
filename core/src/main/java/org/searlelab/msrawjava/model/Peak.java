package org.searlelab.msrawjava.model;

/**
 * Thin implementation that does not allow for turn on and off
 */
public class Peak implements PeakInterface {
	private final double mz;
	private final float intensity;

	public Peak(double mz, float intensity) {
		super();
		this.mz=mz;
		this.intensity=intensity;
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public void turnOff() {
		throw new UnsupportedOperationException("turnOff not implemented");
	}

	@Override
	public void turnOn() {
		throw new UnsupportedOperationException("turnOn not implemented");
	}

	@Override
	public float getIntensity() {
		return intensity;
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

}
