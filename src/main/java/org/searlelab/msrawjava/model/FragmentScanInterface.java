package org.searlelab.msrawjava.model;

import java.util.Optional;

public interface FragmentScanInterface {

	String getSpectrumName();

	String getPrecursorName();

	int getSpectrumIndex();

	float getScanStartTime();

	int getFraction();

	Float getIonInjectionTime();

	double getIsolationWindowLower();

	double getIsolationWindowUpper();

	double[] getMassArray();

	float[] getIntensityArray();

	Optional<float[]> getIonMobilityArray();

	byte getCharge();

}