package org.searlelab.msrawjava.model;

import org.searlelab.msrawjava.API;

/**
 * FixedMassTolerance is an m/z (Th) implementation of MassTolerance.
 */
// @Immutable
@API(status = API.Status.STABLE, since = "v26.8.1")
public class FixedMassTolerance extends MassTolerance {
	private final double mzTolerance;

	@API(status = API.Status.STABLE, since = "v26.8.1")
	public FixedMassTolerance(double mzTolerance) {
		super();
		this.mzTolerance=mzTolerance;
	}

	@API(status = API.Status.STABLE, since = "v26.8.1")
	public double getToleranceInMz(double m1, double m2) {
		return mzTolerance;
	}
}
