package org.searlelab.msrawjava.io.tims;

import org.searlelab.msrawjava.model.MassTolerance;

public class TIMSMassTolerance extends MassTolerance {
	private final double ccoverN;
	private final double systemSquared;
	

	public TIMSMassTolerance() {
		this(false);
	}
	public TIMSMassTolerance(boolean lowIonStats) {
		double resolution=50000.0;
		double resolutionAtMass=1222.0;
		double N=lowIonStats?1.0:10.0; // assume 10 ions go into each centroid
		double systemPPM=8.0; // noise floor, this is "ok" for timstof
		
		double gauss=2.0*Math.sqrt(2.0*Math.log(2.0));
		double c=(1000000.0*Math.sqrt(resolutionAtMass))/(gauss*resolution);
		
		ccoverN=c*c/N;
		systemSquared=systemPPM*systemPPM;
	}
	
	@Override
	public double getToleranceInMz(double m1, double m2) {
		double m=(m1+m2)/2.0;
		
		// delta mass of centroids at FWHM assuming 10 ions
		return (m/1000000.0)*Math.sqrt(ccoverN/m+systemSquared);
	}
}
