package org.searlelab.msrawjava.model;

import java.util.Optional;

public interface Spectrum {
	public String getSpectrumName();
	public float getScanStartTime();
	public double getPrecursorMZ();
	public double[] getMassArray();
	public float[] getIntensityArray();
	public Optional<float[]> getIonMobilityArray();
	public float getTIC();
}
