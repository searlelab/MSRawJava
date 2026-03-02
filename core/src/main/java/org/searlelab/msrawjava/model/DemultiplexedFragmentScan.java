package org.searlelab.msrawjava.model;

/**
 * DemultiplexedFragmentScan extends FragmentScan with explicit demultiplexing metadata,
 * allowing spectrum names to be built from stable values instead of string parsing.
 */
public class DemultiplexedFragmentScan extends FragmentScan {
	private final int originalSpectrumIndex;
	private final int demuxCode;

	public DemultiplexedFragmentScan(FragmentScan source, int originalSpectrumIndex, int demuxCode) {
		this(source.getSpectrumName(), source.getPrecursorName(), source.getSpectrumIndex(), source.getPrecursorMZ(), source.getScanStartTime(),
				source.getFraction(), source.getIonInjectionTime(), source.getIsolationWindowLower(), source.getIsolationWindowUpper(), source.getMassArray(),
				source.getIntensityArray(), source.getIonMobilityArray().orElse(null), source.getCharge(), source.getScanWindowLower(),
				source.getScanWindowUpper(), originalSpectrumIndex, demuxCode);
	}

	public DemultiplexedFragmentScan(String spectrumName, String precursorName, int spectrumIndex, double precursorMz, float scanStartTime, int fraction,
			Float ionInjectionTime, double isolationWindowLower, double isolationWindowUpper, double[] massArray, float[] intensityArray,
			float[] ionMobilityArray, byte charge, double scanWindowLower, double scanWindowUpper, int originalSpectrumIndex, int demuxCode) {
		super(spectrumName, precursorName, spectrumIndex, precursorMz, scanStartTime, fraction, ionInjectionTime, isolationWindowLower, isolationWindowUpper,
				massArray, intensityArray, ionMobilityArray, charge, scanWindowLower, scanWindowUpper);
		if (demuxCode!=0&&demuxCode!=1) {
			throw new IllegalArgumentException("demuxCode must be 0 or 1, but was "+demuxCode);
		}
		this.originalSpectrumIndex=originalSpectrumIndex;
		this.demuxCode=demuxCode;
	}

	public int getOriginalSpectrumIndex() {
		return originalSpectrumIndex;
	}

	public int getDemuxCode() {
		return demuxCode;
	}

	@Override
	public String getSpectrumName() {
		return buildCanonicalSpectrumName(originalSpectrumIndex, demuxCode, getSpectrumIndex());
	}

	@Override
	public DemultiplexedFragmentScan renumber(int newSpectrumIndex) {
		return new DemultiplexedFragmentScan(super.getSpectrumName(), getPrecursorName(), newSpectrumIndex, getPrecursorMZ(), getScanStartTime(),
				getFraction(), getIonInjectionTime(), getIsolationWindowLower(), getIsolationWindowUpper(), getMassArray(), getIntensityArray(),
				getIonMobilityArray().orElse(null), getCharge(), getScanWindowLower(), getScanWindowUpper(), originalSpectrumIndex, demuxCode);
	}

	public static String buildCanonicalSpectrumName(int originalSpectrumIndex, int demuxCode, int spectrumIndex) {
		return "originalScan="+originalSpectrumIndex+" demux="+demuxCode+" scan="+spectrumIndex;
	}
}
