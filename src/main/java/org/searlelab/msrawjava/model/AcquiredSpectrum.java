package org.searlelab.msrawjava.model;

import java.util.Optional;

/**
 * AcquiredSpectrum defines the common, immutable representation of a spectrum within the library’s data model,
 * capturing core metadata (e.g., identifiers and timing) and primitive arrays for m/z and intensity (and, where
 * applicable, ion-mobility). It serves as the base contract implemented by concrete spectrum types so readers and
 * writers can operate uniformly over MS1 and MS2 content.
 */
public interface AcquiredSpectrum {

	String getSpectrumName();

	int getSpectrumIndex();

	float getScanStartTime();

	int getFraction();

	double getScanWindowLower();

	double getScanWindowUpper();

	double getIsolationWindowLower();

	double getIsolationWindowUpper();

	Float getIonInjectionTime();

	double[] getMassArray();

	float[] getIntensityArray();

	Optional<float[]> getIonMobilityArray();

	float getTIC();

	public double getPrecursorMZ();
}