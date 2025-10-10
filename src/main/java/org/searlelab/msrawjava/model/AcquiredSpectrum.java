package org.searlelab.msrawjava.model;

import java.util.Optional;

public interface AcquiredSpectrum {

	String getSpectrumName();

	int getSpectrumIndex();

	float getScanStartTime();

	int getFraction();

	double getIsolationWindowLower();

	double getIsolationWindowUpper();

	Float getIonInjectionTime();

	double[] getMassArray();

	float[] getIntensityArray();

	Optional<float[]> getIonMobilityArray();

	float getTIC();

	public double getPrecursorMZ();
}