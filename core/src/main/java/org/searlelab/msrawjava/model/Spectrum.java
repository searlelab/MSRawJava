package org.searlelab.msrawjava.model;

import java.util.Optional;

import org.searlelab.msrawjava.API;

@API(status = API.Status.STABLE, since = "v26.7.31")
public interface Spectrum {
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public String getSpectrumName();
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public float getScanStartTime();
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public double getPrecursorMZ();
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public double[] getMassArray();
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public float[] getIntensityArray();
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public Optional<float[]> getIonMobilityArray();
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public float getTIC();
}
