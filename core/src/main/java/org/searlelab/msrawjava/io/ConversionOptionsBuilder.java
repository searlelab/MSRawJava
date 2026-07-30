package org.searlelab.msrawjava.io;

import java.util.Objects;
import java.util.Optional;

import org.searlelab.msrawjava.algorithms.demux.DemuxConfig;
import org.searlelab.msrawjava.model.MassTolerance;
import org.searlelab.msrawjava.model.PPMMassTolerance;

/** Builds per-conversion options without accepting batch or CLI concerns. */
public final class ConversionOptionsBuilder {
	private OutputType outputType=OutputType.EncyclopeDIA;
	private float minimumMS1Intensity=3.0f;
	private float minimumMS2Intensity=1.0f;
	private Optional<Boolean> demultiplex=Optional.empty();
	private Optional<Double> precursorMarginSize=Optional.empty();
	private MassTolerance demuxTolerance=new PPMMassTolerance(10.0);
	private DemuxConfig demuxConfig=new DemuxConfig();

	public ConversionOptionsBuilder outputType(OutputType value) { outputType=Objects.requireNonNull(value, "outputType"); return this; }
	public ConversionOptionsBuilder minimumMS1Intensity(float value) { minimumMS1Intensity=value; return this; }
	public ConversionOptionsBuilder minimumMS2Intensity(float value) { minimumMS2Intensity=value; return this; }
	public ConversionOptionsBuilder demultiplex(boolean value) { demultiplex=Optional.of(value); return this; }
	public ConversionOptionsBuilder demultiplex(Optional<Boolean> value) { demultiplex=value==null?Optional.empty():value; return this; }
	public ConversionOptionsBuilder precursorMarginSize(double value) { precursorMarginSize=Optional.of(value); return this; }
	public ConversionOptionsBuilder precursorMarginSize(Optional<Double> value) { precursorMarginSize=value==null?Optional.empty():value; return this; }
	public ConversionOptionsBuilder demuxTolerance(MassTolerance value) { demuxTolerance=Objects.requireNonNull(value, "demuxTolerance"); return this; }
	public ConversionOptionsBuilder demuxConfig(DemuxConfig value) { demuxConfig=Objects.requireNonNull(value, "demuxConfig"); return this; }

	public ConversionOptions build() {
		if (!Float.isFinite(minimumMS1Intensity)||minimumMS1Intensity<0f) throw new IllegalArgumentException("minimumMS1Intensity must be finite and nonnegative");
		if (!Float.isFinite(minimumMS2Intensity)||minimumMS2Intensity<0f) throw new IllegalArgumentException("minimumMS2Intensity must be finite and nonnegative");
		if (precursorMarginSize.isPresent()&&(!Double.isFinite(precursorMarginSize.get())||precursorMarginSize.get()<0d)) {
			throw new IllegalArgumentException("precursorMarginSize must be finite and nonnegative");
		}
		return new ConversionOptions(outputType, minimumMS1Intensity, minimumMS2Intensity, demultiplex, precursorMarginSize, demuxTolerance, demuxConfig);
	}
}
