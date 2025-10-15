package org.searlelab.msrawjava.io;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;

public class ExportParameters {
	private final ArrayList<File> fileList;
	private final OutputType outType;
	private final Path outputDirPath;
	private final float minimumMS1Intensity;
	private final float minimumMS2Intensity;

	public ExportParameters(ArrayList<File> fileList, OutputType outType, Path outputDirPath, float minimumMS1Intensity, float minimumMS2Intensity) {
		this.fileList=fileList;
		this.outType=outType;
		this.outputDirPath=outputDirPath;
		this.minimumMS1Intensity=minimumMS1Intensity;
		this.minimumMS2Intensity=minimumMS2Intensity;
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
}
