package org.searlelab.msrawjava.model;

import java.util.Optional;

import org.searlelab.msrawjava.API;

/**
 * AcquiredSpectrum defines the common, immutable representation of a spectrum within the library’s data model,
 * capturing core metadata (e.g., identifiers and timing) and primitive arrays for m/z and intensity (and, where
 * applicable, ion-mobility). It serves as the base contract implemented by concrete spectrum types so readers and
 * writers can operate uniformly over MS1 and MS2 content.
 */
@API(status = API.Status.STABLE, since = "v26.7.31")
public interface AcquiredSpectrum extends Spectrum {

	/** Human-readable spectrum label (may not be unique). */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	String getSpectrumName();

	/** Stable internal index for this spectrum; not guaranteed to match the vendor index. */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	int getSpectrumIndex();

	/** Scan start time in seconds. */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	float getScanStartTime();

	/** Fraction/run index for multi-file workflows (0 for single-file). */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	int getFraction();

	/** Lower m/z bound of the acquisition scan window. */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	double getScanWindowLower();

	/** Upper m/z bound of the acquisition scan window. */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	double getScanWindowUpper();

	/** Lower m/z bound of the precursor isolation window (for PRM/DIA/DDA). */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	double getIsolationWindowLower();

	/** Upper m/z bound of the precursor isolation window (for PRM/DIA/DDA). */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	double getIsolationWindowUpper();

	/** Ion injection time in seconds, or -1 if unavailable. */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	float getIonInjectionTime();

	/** Calibrated m/z values, index-aligned with intensities. */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	double[] getMassArray();

	/** peak intensities, index-aligned with m/z. */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	float[] getIntensityArray();

	/** Optional per-peak ion-mobility values aligned to m/z and intensity arrays. */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	Optional<float[]> getIonMobilityArray();

	/** Total ion current (sum of non-negative intensities). */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	float getTIC();

	/** Precursor/target m/z for MS2; -1 for MS1. */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public double getPrecursorMZ();
}
