package org.searlelab.msrawjava.model;

import org.searlelab.msrawjava.API;

@API(status = API.Status.STABLE, since = "v26.7.31")
public interface PeakInterface extends Comparable<PeakInterface> {

	@API(status = API.Status.STABLE, since = "v26.7.31")
	String toString();

	@API(status = API.Status.STABLE, since = "v26.7.31")
	boolean isAvailable();

	@API(status = API.Status.STABLE, since = "v26.7.31")
	void turnOff();

	@API(status = API.Status.STABLE, since = "v26.7.31")
	void turnOn();

	@API(status = API.Status.STABLE, since = "v26.7.31")
	float getIntensity();

	@API(status = API.Status.STABLE, since = "v26.7.31")
	double getMz();

	@API(status = API.Status.STABLE, since = "v26.7.31")
	int compareTo(PeakInterface o);

}
