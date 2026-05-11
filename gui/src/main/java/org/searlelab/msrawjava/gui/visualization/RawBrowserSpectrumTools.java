package org.searlelab.msrawjava.gui.visualization;

import java.util.ArrayList;

import org.searlelab.msrawjava.io.tims.TIMSPeakPicker;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.PeakWithIMS;

final class RawBrowserSpectrumTools {
	private static final float MINIMUM_MS1_INTENSITY=3.0f;
	private static final float MINIMUM_MS2_INTENSITY=1.0f;

	private RawBrowserSpectrumTools() {
	}

	static AcquiredSpectrum peakPickSpectrumIfIMS(AcquiredSpectrum spectrum, boolean peakPickAcrossIMS) {
		if (spectrum==null) return null;
		if (!peakPickAcrossIMS) return spectrum;
		if (spectrum.getIonMobilityArray().isEmpty()) return spectrum;
		double[] mz=spectrum.getMassArray();
		float[] intensity=spectrum.getIntensityArray();
		float[] ims=spectrum.getIonMobilityArray().get();
		if (mz.length==0||intensity.length==0||ims.length==0) return spectrum;
		float minIntensity=(spectrum.getPrecursorMZ()<0.0)?MINIMUM_MS1_INTENSITY:MINIMUM_MS2_INTENSITY;
		ArrayList<PeakWithIMS> peaks=new ArrayList<>(mz.length);
		for (int i=0; i<mz.length; i++) {
			if (intensity[i]>minIntensity) peaks.add(new PeakWithIMS(mz[i], intensity[i], ims[i]));
		}
		if (peaks.isEmpty()) return spectrum;
		ArrayList<PeakWithIMS> picked=TIMSPeakPicker.peakPickAcrossIMS(peaks);
		if (picked==null||picked.isEmpty()) return spectrum;
		picked.sort(null);
		double[] mzOut=new double[picked.size()];
		float[] intensityOut=new float[picked.size()];
		float[] imsOut=new float[picked.size()];
		for (int i=0; i<picked.size(); i++) {
			PeakWithIMS p=picked.get(i);
			mzOut[i]=p.mz;
			intensityOut[i]=p.intensity;
			imsOut[i]=p.ims;
		}
		return new org.searlelab.msrawjava.model.PrecursorScan(spectrum.getSpectrumName(), spectrum.getSpectrumIndex(), spectrum.getScanStartTime(),
				spectrum.getFraction(), spectrum.getIsolationWindowLower(), spectrum.getIsolationWindowUpper(), spectrum.getIonInjectionTime(), mzOut, intensityOut, imsOut);
	}
}
