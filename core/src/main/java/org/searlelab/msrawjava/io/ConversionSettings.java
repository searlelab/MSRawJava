package org.searlelab.msrawjava.io;

import java.nio.file.Path;
import java.util.Optional;

import org.searlelab.msrawjava.algorithms.demux.DemuxConfig;
import org.searlelab.msrawjava.model.MassTolerance;

/** Package-private writer settings shared by the new facade and low-level writer implementation. */
final class ConversionSettings {
	private final OutputType outputType;
	private final float minimumMS1Intensity;
	private final float minimumMS2Intensity;
	private final Optional<Boolean> demultiplex;
	private final Optional<Double> precursorMarginSize;
	private final MassTolerance demuxTolerance;
	private final DemuxConfig demuxConfig;
	private final Path outputFilePathOverride;

	private ConversionSettings(OutputType outputType, float minimumMS1Intensity, float minimumMS2Intensity, Optional<Boolean> demultiplex,
			Optional<Double> precursorMarginSize, MassTolerance demuxTolerance, DemuxConfig demuxConfig, Path outputFilePathOverride) {
		this.outputType=outputType;
		this.minimumMS1Intensity=minimumMS1Intensity;
		this.minimumMS2Intensity=minimumMS2Intensity;
		this.demultiplex=demultiplex==null?Optional.empty():demultiplex;
		this.precursorMarginSize=precursorMarginSize==null?Optional.empty():precursorMarginSize;
		this.demuxTolerance=demuxTolerance;
		this.demuxConfig=demuxConfig;
		this.outputFilePathOverride=outputFilePathOverride;
	}

	static ConversionSettings fromOptions(ConversionOptions options, boolean demultiplex, Path outputFilePathOverride) {
		if (options==null) throw new IllegalArgumentException("options must not be null");
		return new ConversionSettings(options.getOutputType(), options.getMinimumMS1Intensity(), options.getMinimumMS2Intensity(), Optional.of(demultiplex),
				options.getPrecursorMarginSize(), options.getDemuxTolerance(), options.getDemuxConfig(), outputFilePathOverride);
	}

	static ConversionSettings fromLegacy(ConversionParameters params) {
		if (params==null) throw new IllegalArgumentException("params must not be null");
		return new ConversionSettings(params.getOutType(), params.getMinimumMS1Intensity(), params.getMinimumMS2Intensity(), params.getDemultiplex(),
				params.getPrecursorMarginSize(), params.getDemuxTolerance(), params.getDemuxConfig(), params.getOutputFilePathOverride());
	}

	OutputType getOutType() { return outputType; }
	float getMinimumMS1Intensity() { return minimumMS1Intensity; }
	float getMinimumMS2Intensity() { return minimumMS2Intensity; }
	Optional<Boolean> getDemultiplex() { return demultiplex; }
	Optional<Double> getPrecursorMarginSize() { return precursorMarginSize; }
	MassTolerance getDemuxTolerance() { return demuxTolerance; }
	DemuxConfig getDemuxConfig() { return demuxConfig; }
	Path getOutputFilePathOverride() { return outputFilePathOverride; }
}
