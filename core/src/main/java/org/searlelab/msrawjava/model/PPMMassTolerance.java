package org.searlelab.msrawjava.model;

import org.searlelab.msrawjava.API;

/**
 * PPMMassTolerance is a parts-per-million implementation of MassTolerance.
 */
// @Immutable
@API(status = API.Status.STABLE, since = "v26.7.31")
public class PPMMassTolerance extends MassTolerance {
	private final double tolerancePercent;

	@API(status = API.Status.STABLE, since = "v26.7.31")
	public PPMMassTolerance(double ppmTolerance) {
		super();
		this.tolerancePercent=ppmTolerance/1000000.0; // ppm to percent
	}

	@API(status = API.Status.STABLE, since = "v26.7.31")
	public double getPpmTolerance() {
		return tolerancePercent*1000000.0;
	}

	@API(status = API.Status.STABLE, since = "v26.7.31")
	public double getToleranceInMz(double m1, double m2) {
		return Math.max(Math.abs(m1), Math.abs(m2))*tolerancePercent;
	}
}
