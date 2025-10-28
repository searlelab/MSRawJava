package org.searlelab.msrawjava.model;

public interface PeakInterface extends Comparable<PeakInterface> {

	String toString();

	boolean isAvailable();

	void turnOff();

	void turnOn();

	float getIntensity();

	double getMz();

	int compareTo(PeakInterface o);

}