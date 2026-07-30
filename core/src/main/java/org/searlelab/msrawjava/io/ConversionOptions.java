package org.searlelab.msrawjava.io;

import java.util.Optional;

import org.searlelab.msrawjava.algorithms.demux.DemuxConfig;
import org.searlelab.msrawjava.model.MassTolerance;

/** Immutable settings that affect one raw-file conversion. */
public final class ConversionOptions {
	private final OutputType outputType;
	private final float minimumMS1Intensity;
	private final float minimumMS2Intensity;
	private final Optional<Boolean> demultiplex;
	private final Optional<Double> precursorMarginSize;
	private final MassTolerance demuxTolerance;
	private final DemuxConfig demuxConfig;

	ConversionOptions(OutputType outputType, float minimumMS1Intensity, float minimumMS2Intensity, Optional<Boolean> demultiplex,
			Optional<Double> precursorMarginSize, MassTolerance demuxTolerance, DemuxConfig demuxConfig) {
		this.outputType=outputType;
		this.minimumMS1Intensity=minimumMS1Intensity;
		this.minimumMS2Intensity=minimumMS2Intensity;
		this.demultiplex=demultiplex;
		this.precursorMarginSize=precursorMarginSize;
		this.demuxTolerance=demuxTolerance;
		this.demuxConfig=demuxConfig;
	}

	public static ConversionOptionsBuilder builder() {
		return new ConversionOptionsBuilder();
	}

	public OutputType getOutputType() { return outputType; }
	public float getMinimumMS1Intensity() { return minimumMS1Intensity; }
	public float getMinimumMS2Intensity() { return minimumMS2Intensity; }
	public Optional<Boolean> getDemultiplex() { return demultiplex; }
	public Optional<Double> getPrecursorMarginSize() { return precursorMarginSize; }
	public MassTolerance getDemuxTolerance() { return demuxTolerance; }
	public DemuxConfig getDemuxConfig() { return demuxConfig; }
}
