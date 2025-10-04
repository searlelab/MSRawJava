package org.searlelab.msrawjava.model;

//@Immutable
public class PPMMassTolerance extends MassTolerance {
	private final double tolerancePercent;

	public PPMMassTolerance(double ppmTolerance) {
		this.tolerancePercent=ppmTolerance/1000000.0; // ppm to percent
	}
	
	public double getPpmTolerance() {
		return tolerancePercent*1000000.0;
	}

	public double getToleranceInMz(double m1, double m2) {
		return Math.max(Math.abs(m1), Math.abs(m2))*tolerancePercent;
	}
}
