package org.searlelab.msrawjava.model;

import java.util.Optional;

public interface PrecursorScanInterface {

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

}