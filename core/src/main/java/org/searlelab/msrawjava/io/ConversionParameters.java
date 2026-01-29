package org.searlelab.msrawjava.io;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
	private final boolean demultiplex;
	private final MassTolerance demuxTolerance;
	private final DemuxConfig demuxConfig;
	private final Path logFilePath;
	private final boolean batch;
	private final boolean silent;
	private final boolean noAnsi;
	private final boolean discoverDIAFiles;
	private final Path outputFilePathOverride;

	public ConversionParameters(List<File> fileList, OutputType outType, Path outputDirPath, float minimumMS1Intensity, float minimumMS2Intensity,
			boolean demultiplex, MassTolerance demuxTolerance, DemuxConfig demuxConfig, Path logFilePath, boolean batch, boolean silent, boolean noAnsi,
			boolean discoverDIAFiles, Path outputFilePathOverride) {
		this.fileList=new ArrayList<>(fileList==null?Collections.emptyList():fileList);
		this.outType=outType;
		this.outputDirPath=outputDirPath;
		this.minimumMS1Intensity=minimumMS1Intensity;
		this.minimumMS2Intensity=minimumMS2Intensity;
		this.demultiplex=demultiplex;
		this.demuxTolerance=demuxTolerance;
		this.demuxConfig=demuxConfig;
		this.logFilePath=logFilePath;
		this.batch=batch;
		this.silent=silent;
		this.noAnsi=noAnsi;
		this.discoverDIAFiles=discoverDIAFiles;
		this.outputFilePathOverride=outputFilePathOverride;
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

	public boolean isDemultiplex() {
		return demultiplex;
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

	public Path getOutputFilePathOverride() {
		return outputFilePathOverride;
	}

	@Override
	public String toString() {
		return "ConversionParameters[outType="+outType+", outputDirPath="+outputDirPath+", minMS1="+minimumMS1Intensity+", minMS2="+minimumMS2Intensity
				+", demux="+demultiplex+", demuxTolerance="+demuxTolerance+", demuxConfig="+demuxConfig+", logFilePath="+logFilePath+", batch="+batch
				+", silent="+silent+", noAnsi="+noAnsi+", discoverDIAFiles="+discoverDIAFiles+", outputFilePathOverride="+outputFilePathOverride+"]";
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private ArrayList<File> fileList=new ArrayList<>();
		private OutputType outType=OutputType.EncyclopeDIA;
		private Path outputDirPath=null;
		private float minimumMS1Intensity=3.0f;
		private float minimumMS2Intensity=1.0f;
		private boolean demultiplex=false;
		private MassTolerance demuxTolerance=new PPMMassTolerance(10.0);
		private DemuxConfig demuxConfig=new DemuxConfig();
		private Path logFilePath=null;
		private boolean batch=false;
		private boolean silent=false;
		private boolean noAnsi=false;
		private boolean discoverDIAFiles=false;
		private Path outputFilePathOverride=null;

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
			this.demultiplex=demultiplex;
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

		public Builder outputFilePathOverride(Path outputFilePathOverride) {
			this.outputFilePathOverride=outputFilePathOverride;
			return this;
		}

		public ConversionParameters build() {
			return new ConversionParameters(fileList, outType, outputDirPath, minimumMS1Intensity, minimumMS2Intensity, demultiplex, demuxTolerance,
					demuxConfig, logFilePath, batch, silent, noAnsi, discoverDIAFiles, outputFilePathOverride);
		}
	}
}
