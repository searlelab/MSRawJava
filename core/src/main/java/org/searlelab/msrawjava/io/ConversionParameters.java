package org.searlelab.msrawjava.io;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.searlelab.msrawjava.algorithms.demux.DemuxConfig;
import org.searlelab.msrawjava.model.MassTolerance;
import org.searlelab.msrawjava.model.PPMMassTolerance;

/**
 * ConversionParameters captures conversion settings shared by CLI and GUI entry points.
 */
public class ConversionParameters {
	private final ArrayList<File> fileList;
	private final OutputType outType;
	private final Path outputDirPath;
	private final float minimumMS1Intensity;
	private final float minimumMS2Intensity;
	private final Optional<Boolean> demultiplex;
	private final Optional<Double> precursorMarginSize;
	private final MassTolerance demuxTolerance;
	private final DemuxConfig demuxConfig;
	private final Path logFilePath;
	private final boolean batch;
	private final boolean silent;
	private final boolean noAnsi;
	private final boolean discoverDIAFiles;
	private final boolean discoverMzMLFiles;
	private final Path outputFilePathOverride;
	private final Integer processingThreads;

	public ConversionParameters(List<File> fileList, OutputType outType, Path outputDirPath, float minimumMS1Intensity, float minimumMS2Intensity,
			boolean demultiplex, MassTolerance demuxTolerance, DemuxConfig demuxConfig, Path logFilePath, boolean batch, boolean silent, boolean noAnsi,
			boolean discoverDIAFiles, boolean discoverMzMLFiles, Path outputFilePathOverride) {
		this(fileList, outType, outputDirPath, minimumMS1Intensity, minimumMS2Intensity, Optional.of(demultiplex), Optional.empty(), demuxTolerance,
				demuxConfig, logFilePath, batch, silent, noAnsi, discoverDIAFiles, discoverMzMLFiles, outputFilePathOverride, null);
	}

	public ConversionParameters(List<File> fileList, OutputType outType, Path outputDirPath, float minimumMS1Intensity, float minimumMS2Intensity,
			boolean demultiplex, MassTolerance demuxTolerance, DemuxConfig demuxConfig, Path logFilePath, boolean batch, boolean silent, boolean noAnsi,
			boolean discoverDIAFiles, boolean discoverMzMLFiles, Path outputFilePathOverride, Integer processingThreads) {
		this(fileList, outType, outputDirPath, minimumMS1Intensity, minimumMS2Intensity, Optional.of(demultiplex), Optional.empty(), demuxTolerance,
				demuxConfig, logFilePath, batch, silent, noAnsi, discoverDIAFiles, discoverMzMLFiles, outputFilePathOverride, processingThreads);
	}

	public ConversionParameters(List<File> fileList, OutputType outType, Path outputDirPath, float minimumMS1Intensity, float minimumMS2Intensity,
			Optional<Boolean> demultiplex, Optional<Double> precursorMarginSize, MassTolerance demuxTolerance, DemuxConfig demuxConfig, Path logFilePath,
			boolean batch, boolean silent, boolean noAnsi, boolean discoverDIAFiles, boolean discoverMzMLFiles, Path outputFilePathOverride,
			Integer processingThreads) {
		this.fileList=new ArrayList<>(fileList==null?Collections.emptyList():fileList);
		this.outType=outType;
		this.outputDirPath=outputDirPath;
		this.minimumMS1Intensity=minimumMS1Intensity;
		this.minimumMS2Intensity=minimumMS2Intensity;
		this.demultiplex=demultiplex==null?Optional.empty():demultiplex;
		this.precursorMarginSize=precursorMarginSize==null?Optional.empty():precursorMarginSize;
		this.demuxTolerance=demuxTolerance;
		this.demuxConfig=demuxConfig;
		this.logFilePath=logFilePath;
		this.batch=batch;
		this.silent=silent;
		this.noAnsi=noAnsi;
		this.discoverDIAFiles=discoverDIAFiles;
		this.discoverMzMLFiles=discoverMzMLFiles;
		this.outputFilePathOverride=outputFilePathOverride;
		this.processingThreads=processingThreads;
	}

	public ArrayList<File> getFileList() {
		return fileList;
	}

	public OutputType getOutType() {
		return outType;
	}

	public Path getOutputDirPath() {
		return outputDirPath;
	}

	public float getMinimumMS1Intensity() {
		return minimumMS1Intensity;
	}

	public float getMinimumMS2Intensity() {
		return minimumMS2Intensity;
	}

	public Optional<Boolean> getDemultiplex() {
		return demultiplex;
	}

	public Optional<Double> getPrecursorMarginSize() {
		return precursorMarginSize;
	}

	public MassTolerance getDemuxTolerance() {
		return demuxTolerance;
	}

	public DemuxConfig getDemuxConfig() {
		return demuxConfig;
	}

	public Path getLogFilePath() {
		return logFilePath;
	}

	public boolean isBatch() {
		return batch;
	}

	public boolean isSilent() {
		return silent;
	}

	public boolean isNoAnsi() {
		return noAnsi;
	}

	public boolean isDiscoverDIAFiles() {
		return discoverDIAFiles;
	}

	public boolean isDiscoverMzMLFiles() {
		return discoverMzMLFiles;
	}

	public Path getOutputFilePathOverride() {
		return outputFilePathOverride;
	}

	public Integer getProcessingThreads() {
		return processingThreads;
	}

	@Override
	public String toString() {
		return "ConversionParameters[outType="+outType+", outputDirPath="+outputDirPath+", minMS1="+minimumMS1Intensity+", minMS2="+minimumMS2Intensity
				+", demux="+demultiplex+", precursorMarginSize="+precursorMarginSize+", demuxTolerance="+demuxTolerance+", demuxConfig="+demuxConfig
				+", logFilePath="+logFilePath+", batch="+batch+", silent="+silent+", noAnsi="+noAnsi+", discoverDIAFiles="+discoverDIAFiles
				+", discoverMzMLFiles="+discoverMzMLFiles+", outputFilePathOverride="+outputFilePathOverride+", processingThreads="+processingThreads+"]";
	}

	/** @deprecated Use {@link ConversionOptions#builder()} for library conversion settings. */
	@Deprecated
	public static Builder builder() {
		return new Builder();
	}

	/** @deprecated Use the top-level {@link ConversionOptionsBuilder}. */
	@Deprecated
	public static class Builder {
		private ArrayList<File> fileList=new ArrayList<>();
		private OutputType outType=OutputType.EncyclopeDIA;
		private Path outputDirPath=null;
		private float minimumMS1Intensity=3.0f;
		private float minimumMS2Intensity=1.0f;
		private Optional<Boolean> demultiplex=Optional.empty();
		private Optional<Double> precursorMarginSize=Optional.empty();
		private MassTolerance demuxTolerance=new PPMMassTolerance(10.0);
		private DemuxConfig demuxConfig=new DemuxConfig();
		private Path logFilePath=null;
		private boolean batch=false;
		private boolean silent=false;
		private boolean noAnsi=false;
		private boolean discoverDIAFiles=false;
		private boolean discoverMzMLFiles=false;
		private Path outputFilePathOverride=null;
		private Integer processingThreads=null;

		public Builder fileList(List<File> files) {
			this.fileList=new ArrayList<>(files);
			return this;
		}

		public Builder addFile(File file) {
			this.fileList.add(file);
			return this;
		}

		public Builder outType(OutputType outType) {
			this.outType=outType;
			return this;
		}

		public Builder outputDirPath(Path outputDirPath) {
			this.outputDirPath=outputDirPath;
			return this;
		}

		public Builder minimumMS1Intensity(float minimumMS1Intensity) {
			this.minimumMS1Intensity=minimumMS1Intensity;
			return this;
		}

		public Builder minimumMS2Intensity(float minimumMS2Intensity) {
			this.minimumMS2Intensity=minimumMS2Intensity;
			return this;
		}

		public Builder demultiplex(boolean demultiplex) {
			this.demultiplex=Optional.of(demultiplex);
			return this;
		}

		public Builder demultiplex(Boolean demultiplex) {
			this.demultiplex=demultiplex==null?Optional.empty():Optional.of(demultiplex);
			return this;
		}

		public Builder demultiplex(Optional<Boolean> demultiplex) {
			this.demultiplex=demultiplex==null?Optional.empty():demultiplex;
			return this;
		}

		public Builder precursorMarginSize(double precursorMarginSize) {
			this.precursorMarginSize=Optional.of(precursorMarginSize);
			return this;
		}

		public Builder precursorMarginSize(Optional<Double> precursorMarginSize) {
			this.precursorMarginSize=precursorMarginSize==null?Optional.empty():precursorMarginSize;
			return this;
		}

		public Builder demuxTolerance(MassTolerance demuxTolerance) {
			this.demuxTolerance=demuxTolerance;
			return this;
		}

		public Builder demuxConfig(DemuxConfig demuxConfig) {
			this.demuxConfig=demuxConfig;
			return this;
		}

		public Builder logFilePath(Path logFilePath) {
			this.logFilePath=logFilePath;
			return this;
		}

		public Builder batch(boolean batch) {
			this.batch=batch;
			return this;
		}

		public Builder silent(boolean silent) {
			this.silent=silent;
			return this;
		}

		public Builder noAnsi(boolean noAnsi) {
			this.noAnsi=noAnsi;
			return this;
		}

		public Builder discoverDIAFiles(boolean discoverDIAFiles) {
			this.discoverDIAFiles=discoverDIAFiles;
			return this;
		}

		public Builder discoverMzMLFiles(boolean discoverMzMLFiles) {
			this.discoverMzMLFiles=discoverMzMLFiles;
			return this;
		}

		public Builder outputFilePathOverride(Path outputFilePathOverride) {
			this.outputFilePathOverride=outputFilePathOverride;
			return this;
		}

		public Builder processingThreads(Integer processingThreads) {
			this.processingThreads=processingThreads;
			return this;
		}

		public ConversionParameters build() {
			return new ConversionParameters(fileList, outType, outputDirPath, minimumMS1Intensity, minimumMS2Intensity, demultiplex, precursorMarginSize,
					demuxTolerance, demuxConfig, logFilePath, batch, silent, noAnsi, discoverDIAFiles, discoverMzMLFiles, outputFilePathOverride,
					processingThreads);
		}
	}
}
