package org.searlelab.msrawjava;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.searlelab.msrawjava.algorithms.demux.DemuxConfig;
import org.searlelab.msrawjava.algorithms.demux.DemuxConfig.InterpolationMethod;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConverters;
import org.searlelab.msrawjava.io.VendorFileFinder;
import org.searlelab.msrawjava.io.VendorFiles;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.logging.ConsoleStatus;
import org.searlelab.msrawjava.logging.FileLogRecorder;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.logging.LoggingProgressIndicator;
import org.searlelab.msrawjava.model.PPMMassTolerance;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Main is the command-line entry point for MSRawJava. It parses options, discovers vendor inputs via VendorFileFinder,
 * and selects the appropriate reader (e.g., BrukerTIMSFile or ThermoRawFile). The class coordinates batch
 * orchestration, logging, and deterministic serialization for reproducible runs.
 */
public class Main {
	/** Main CLI entry point for raw file conversion. */
	public static void main(String[] args) throws Exception {
		CommandLine cmd=new CommandLine(new CliArguments());
		cmd.setCaseInsensitiveEnumValuesAllowed(true);
		int exitCode=cmd.execute(args);
		if (exitCode!=0) {
			System.exit(exitCode);
		}
	}

	/** Discovers vendor files and writes outputs using the selected format. */
	public static void convertKnownFiles(ConversionParameters params) throws Exception {
		ProcessingThreadPool pool=ProcessingThreadPool.createDefault();
		VendorFiles files=new VendorFiles();
		LoggingProgressIndicator indicator=null;
		for (File f : params.getFileList()) {
			if (f.exists()&&f.canRead()) {
				VendorFileFinder.findAndAddRawAndD(f.toPath(), files);
			}
		}

		if (files.getThermoFiles().size()>0) {
			Logger.logLine("Found "+files.getThermoFiles().size()+" total Thermo Raw files");
			try {
				Logger.logLine("Setting up Thermo .raw reader...");
				ThermoServerPool.port();

				for (Path path : files.getThermoFiles()) {
					Logger.logLine("Processing .raw "+path);

					Path outputPath=params.getOutputDirPath()==null?path.getParent():params.getOutputDirPath();
					Logger.logLine("Writing "+params.getOutType()+" file to "+outputPath.toString());

					ThermoRawFile rawFile=new ThermoRawFile();
					rawFile.openFile(path);

					indicator=createIndicator(params);
					try {
						if (params.isDemultiplex()) {
							RawFileConverters.writeDemux(pool, rawFile, outputPath, params, indicator);
						} else {
							RawFileConverters.writeStandard(pool, rawFile, outputPath, params, indicator);
						}
					} finally {
						indicator.close();
					}
					Logger.logLine("Finished writing "+params.getOutType()+" file");
				}

			} finally {
				ThermoServerPool.shutdown();
			}
		}

		if (files.getBrukerDirs().size()>0) {
			Logger.logLine("Found "+files.getBrukerDirs().size()+" total timsTOF files");
			for (Path path : files.getBrukerDirs()) {
				Logger.logLine("Processing .d "+path);

				Path outputPath=params.getOutputDirPath()==null?path.getParent():params.getOutputDirPath();
				Logger.logLine("Writing "+params.getOutType()+" file to "+outputPath.toString());

				if (params.isDemultiplex()) {
					Logger.errorLine("Sorry, staggered demultiplexing is not available for timsTOF files. Processing without demultiplexing.");
				}
				indicator=createIndicator(params);
				try {
					RawFileConverters.writeTims(pool, path, outputPath, params, indicator);
				} finally {
					indicator.close();
				}
				Logger.logLine("Finished writing "+params.getOutType()+" file");
			}
		}
		pool.close();
	}

	private static LoggingProgressIndicator createIndicator(ConversionParameters params) {
		boolean useAnsi=System.console()!=null&&!params.isNoAnsi();
		if (params.isSilent()) {
			return new LoggingProgressIndicator(LoggingProgressIndicator.Mode.SILENT, useAnsi);
		}
		if (params.isBatch()) {
			return new LoggingProgressIndicator(LoggingProgressIndicator.Mode.BATCH, useAnsi);
		}
		return new LoggingProgressIndicator(LoggingProgressIndicator.Mode.DEFAULT, useAnsi);
	}

	@Command(name="msrawjava", mixinStandardHelpOptions=true, description="Convert vendor raw files into analysis-ready formats.", versionProvider=VersionProvider.class)
	public static class CliArguments implements Callable<Integer> {
		@Parameters(arity="1..*", paramLabel="PATHS", description="Input files or directories containing .raw or .d files.")
		private List<File> paths=new ArrayList<>();

		@Option(names= {"-f", "--format"}, defaultValue="dia", description="Output format: ${COMPLETION-CANDIDATES}.")
		private OutputFormat format=OutputFormat.dia;

		@Option(names= {"-o", "--output"}, paramLabel="DIR", description="Output directory (default: same directory as input).")
		private Path outputDirPath;

		@Option(names="--log-file", paramLabel="FILE", description="Write logs to a file (overwrites on each run).")
		private Path logFilePath;

		@Option(names="--min-ms1", defaultValue="3.0", description="Minimum MS1 intensity threshold for timsTOF.")
		private float minimumMS1Intensity=3.0f;

		@Option(names="--min-ms2", defaultValue="1.0", description="Minimum MS2 intensity threshold for timsTOF.")
		private float minimumMS2Intensity=1.0f;

		@Option(names="--demux", defaultValue="false", description="Enable staggered window demultiplexing for Thermo DIA.")
		private boolean demultiplex=false;

		@Option(names="--demux-k", defaultValue="7", description="Local approximation size for demux (7-9).")
		private int demuxK=DemuxConfig.DEFAULT_K;

		@Option(names="--demux-interp", defaultValue="cubic", description="Interpolation method for demux: ${COMPLETION-CANDIDATES}.")
		private DemuxInterpolation demuxInterpolation=DemuxInterpolation.cubic;

		@Option(names="--demux-exclude-edges", defaultValue="false", description="Exclude edge sub-windows (single coverage) from demux output.")
		private boolean demuxExcludeEdges=false;

		@Option(names="--demux-ppm", defaultValue="10.0", description="Mass tolerance in ppm for demux ion matching.")
		private double demuxPpm=10.0;

		@Option(names="--batch", defaultValue="false", description="Disable status bar and progress updates.")
		private boolean batch=false;

		@Option(names="--silent", defaultValue="false", description="Suppress all non-error output.")
		private boolean silent=false;

		@Option(names="--no-ansi", defaultValue="false", description="Disable ANSI output, even on TTYs.")
		private boolean noAnsi=false;

		@Override
		public Integer call() throws Exception {
			ConversionParameters params=toParameters();
			configureLogging(params);
			if (!params.isSilent()) {
				Logger.logLine("Welcome to MSRawJava version "+Version.getVersion());
			}
			if (!params.isSilent()) {
				Logger.logLine("Found "+params.getFileList().size()+" starting paths, export format: "+params.getOutType());
			}
			try {
				convertKnownFiles(params);
				Logger.logLine("Finished processing, bye!");
			} finally {
				Logger.close();
			}
			return 0;
		}

		ConversionParameters toParameters() {
			DemuxConfig demuxConfig=DemuxConfig.builder().k(demuxK)
					.interpolationMethod(demuxInterpolation==DemuxInterpolation.cubic?InterpolationMethod.CUBIC_HERMITE:InterpolationMethod.LOG_QUADRATIC)
					.includeEdgeSubWindows(!demuxExcludeEdges).build();

			return ConversionParameters.builder().fileList(paths).outType(format.toOutputType()).outputDirPath(outputDirPath).logFilePath(logFilePath)
					.minimumMS1Intensity(minimumMS1Intensity).minimumMS2Intensity(minimumMS2Intensity).demultiplex(demultiplex)
					.demuxTolerance(new PPMMassTolerance(demuxPpm)).demuxConfig(demuxConfig).batch(batch).silent(silent).noAnsi(noAnsi).build();
		}

		private void configureLogging(ConversionParameters params) throws Exception {
			if (params.isSilent()) {
				Logger.PRINT_TO_STDOUT=false;
				Logger.PRINT_TO_STDERR=true;
			}
			boolean useAnsi=System.console()!=null&&!params.isNoAnsi()&&!params.isBatch()&&!params.isSilent();
			if (useAnsi) {
				PrintStream stdout=System.out;
				PrintStream stderr=System.err;
				Logger.setConsoleStatus(new ConsoleStatus(true, stdout, stderr));
				System.setOut(new PrintStream(java.io.OutputStream.nullOutputStream()));
				System.setErr(new PrintStream(java.io.OutputStream.nullOutputStream()));
			} else {
				Logger.setConsoleStatus(null);
			}
			if (params.getLogFilePath()!=null) {
				Logger.addRecorder(new FileLogRecorder(params.getLogFilePath(), true));
			}
		}
	}

	public enum OutputFormat {
		dia, mgf, mzml;

		public OutputType toOutputType() {
			switch (this) {
				case dia:
					return OutputType.EncyclopeDIA;
				case mgf:
					return OutputType.mgf;
				case mzml:
					return OutputType.mzML;
				default:
					throw new IllegalArgumentException("Unknown output format "+this);
			}
		}
	}

	public enum DemuxInterpolation {
		cubic, logquadratic
	}

	public static class VersionProvider implements CommandLine.IVersionProvider {
		@Override
		public String[] getVersion() {
			return new String[] {Version.getVersion()};
		}
	}
}
