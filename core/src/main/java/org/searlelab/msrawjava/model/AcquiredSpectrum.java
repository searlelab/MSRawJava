package org.searlelab.msrawjava.model;

import java.util.Optional;

/**
 * AcquiredSpectrum defines the common, immutable representation of a spectrum within the library’s data model,
 * capturing core metadata (e.g., identifiers and timing) and primitive arrays for m/z and intensity (and, where
 * applicable, ion-mobility). It serves as the base contract implemented by concrete spectrum types so readers and
 * writers can operate uniformly over MS1 and MS2 content.
 */
public interface AcquiredSpectrum {

	/** Human-readable spectrum label (may not be unique). */
	String getSpectrumName();

	/** Stable internal index for this spectrum; not guaranteed to match the vendor index. */
	int getSpectrumIndex();

	/** Scan start time in seconds. */
	float getScanStartTime();

	/** Fraction/run index for multi-file workflows (0 for single-file). */
	int getFraction();

	/** Lower m/z bound of the acquisition scan window. */
	double getScanWindowLower();

	/** Upper m/z bound of the acquisition scan window. */
	double getScanWindowUpper();

	/** Lower m/z bound of the precursor isolation window (for PRM/DIA/DDA). */
	double getIsolationWindowLower();

	/** Upper m/z bound of the precursor isolation window (for PRM/DIA/DDA). */
	double getIsolationWindowUpper();

	/** Ion injection time in seconds, or null if unavailable. */
	Float getIonInjectionTime();

	/** Calibrated m/z values, index-aligned with intensities. */
	double[] getMassArray();

	/** peak intensities, index-aligned with m/z. */
	float[] getIntensityArray();

	/** Optional per-peak ion-mobility values aligned to m/z and intensity arrays. */
	Optional<float[]> getIonMobilityArray();

	/** Total ion current (sum of non-negative intensities). */
	float getTIC();

	/** Precursor m/z for MS2; -1 for MS1 (DIA uses the isolation-window center). */
	public double getPrecursorMZ();
}