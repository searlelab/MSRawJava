package org.searlelab.msrawjava;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.searlelab.msrawjava.algorithms.demux.DemuxConfig;
import org.searlelab.msrawjava.algorithms.demux.DemuxConfig.InterpolationMethod;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.ConversionOptions;
import org.searlelab.msrawjava.io.ConversionRequest;
import org.searlelab.msrawjava.io.ConversionResult;
import org.searlelab.msrawjava.io.ConversionStatus;
import org.searlelab.msrawjava.io.RawFileConversion;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.VendorFile;
import org.searlelab.msrawjava.io.VendorFileFinder;
import org.searlelab.msrawjava.io.VendorFiles;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
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
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Main is the command-line entry point for MSRawJava. It parses options, discovers vendor inputs via VendorFileFinder,
 * and selects the appropriate reader (e.g., BrukerTIMSFile or ThermoRawFile). The class coordinates batch
 * orchestration, logging, and deterministic serialization for reproducible runs.
 */
public class Main {
	static final String CLI_ABOUT_TEXT_WITH_THERMO=
			"MSRawJava is a command-line tool and Java library for focused, cross-platform mass spectrometry raw-file reading and conversion. "
					+"It supports Thermo .raw, Bruker timsTOF .d, EncyclopeDIA .dia, and mzML inputs, with export to .dia, .mgf, and mzML.\n"
					+"RawFileReader reading tool. Copyright \u00a9 2016 by Thermo Fisher Scientific, Inc. All rights reserved.";
	static final String CLI_ABOUT_TEXT_WITHOUT_THERMO=
			"MSRawJava is a command-line tool and Java library for focused, cross-platform mass spectrometry raw-file reading and conversion. "
					+"This package does not include Thermo .raw reading support. It supports Bruker timsTOF .d, EncyclopeDIA .dia, and mzML inputs, "
					+"with export to .dia, .mgf, and mzML.";

	/** Main CLI entry point for raw file conversion. */
	public static void main(String[] args) throws Exception {
		CommandLine cmd=new CommandLine(new CliArguments());
		cmd.setCaseInsensitiveEnumValuesAllowed(true);
		int exitCode=cmd.execute(args);
		if (exitCode!=0) {
			System.exit(exitCode);
		}
	}

	/**
	 * Discovers vendor files and converts each through the library facade.
	 * @deprecated Library consumers should call {@link RawFileConversion#convert(ConversionRequest)} for one input.
	 */
	@Deprecated
	public static void convertKnownFiles(ConversionParameters params) throws Exception {
		VendorFiles files=new VendorFiles();
		for (File f : params.getFileList()) {
			if (f.exists()&&f.canRead()) {
				VendorFileFinder.findAndAddRawAndD(f.toPath(), files, params.isDiscoverDIAFiles(), params.isDiscoverMzMLFiles());
			}
		}
		if (files.getThermoFiles().isEmpty()&&files.getBrukerDirs().isEmpty()&&files.getDiaFiles().isEmpty()&&files.getMzmlFiles().isEmpty()) {
			String vendors=VendorFile.list().stream().map(VendorFile::getDisplayName).collect(java.util.stream.Collectors.joining(", "));
			Logger.errorLine("No vendor files found ("+vendors+").");
			return;
		}
		List<ConversionStatus> statuses=new ArrayList<>();
		statuses.addAll(convertDiscovered(files.getThermoFiles(), VendorFile.THERMO, params));
		statuses.addAll(convertDiscovered(files.getBrukerDirs(), VendorFile.BRUKER, params));
		statuses.addAll(convertDiscovered(files.getDiaFiles(), VendorFile.ENCYCLOPEDIA, params));
		statuses.addAll(convertDiscovered(files.getMzmlFiles(), VendorFile.MZML, params));
		if (statuses.contains(ConversionStatus.CANCELED)) Logger.logLine("One or more conversions were canceled.");
	}

	private static List<ConversionStatus> convertDiscovered(List<Path> paths, VendorFile vendor, ConversionParameters params) throws Exception {
		List<ConversionStatus> statuses=new ArrayList<>();
		if (paths.isEmpty()) return statuses;
		Logger.logLine("Found "+paths.size()+" total "+vendor.getDisplayName()+" files");
		ThermoServerPool.setProcessingThreadLimit(params.getProcessingThreads());
		try (ProcessingThreadPool pool=ProcessingThreadPool.createWithThreadLimit(params.getProcessingThreads())) {
			if (vendor==VendorFile.THERMO) {
				Logger.logLine("Setting up "+VendorFile.THERMO.getDisplayName()+" reader...");
				ThermoServerPool.port();
			}
			for (Path path : paths) {
				Logger.logLine("Processing "+vendor.getDisplayName()+" "+path);
				Path outputDirectory=params.getOutputDirPath()==null?path.getParent():params.getOutputDirPath();
				Logger.logLine("Writing "+params.getOutType()+" file to "+outputDirectory);
				if (vendor==VendorFile.BRUKER&&params.getDemultiplex().orElse(false)) {
					Logger.errorLine("Sorry, staggered demultiplexing is not available for "+VendorFile.BRUKER.getDisplayName()
							+" files. Processing without demultiplexing.");
				}
				LoggingProgressIndicator indicator=createIndicator(params);
				try {
					ConversionOptions options=ConversionOptions.builder().outputType(params.getOutType())
							.minimumMS1Intensity(params.getMinimumMS1Intensity()).minimumMS2Intensity(params.getMinimumMS2Intensity())
							.demultiplex(params.getDemultiplex()).precursorMarginSize(params.getPrecursorMarginSize())
							.demuxTolerance(params.getDemuxTolerance()).demuxConfig(params.getDemuxConfig()).build();
					Path requestOutputDirectory=params.getOutputFilePathOverride()==null?params.getOutputDirPath():null;
					ConversionRequest request=new ConversionRequest(path, requestOutputDirectory, params.getOutputFilePathOverride(),
						null, options, indicator);
					ConversionResult result=RawFileConversion.convert(request, pool);
					statuses.add(result.getStatus());
					if (result.getStatus()==ConversionStatus.COMPLETED) {
						Logger.logLine("Finished writing "+params.getOutType()+" file");
					} else {
						Logger.logLine("Canceled writing "+params.getOutType()+" file");
					}
				} catch (BrukerTIMSFile.UnsupportedTsfException e) {
					Logger.errorLine(e.getMessage());
				} finally {
					indicator.close();
				}
			}
		} finally {
			if (vendor==VendorFile.THERMO) ThermoServerPool.shutdown();
		}
		return statuses;
	}

	static String getCliAboutText() {
		return ThermoServerPool.isThermoReaderAvailable()?CLI_ABOUT_TEXT_WITH_THERMO:CLI_ABOUT_TEXT_WITHOUT_THERMO;
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
		@Spec
		private CommandSpec spec;

		@Parameters(arity="0..*", paramLabel="PATHS", description="Input files or directories containing Thermo .raw or Bruker .d files (EncyclopeDIA .dia when --discoverDIAFiles is set, mzML when --discoverMzMLFiles is set).")
		private List<File> paths=new ArrayList<>();

		@Option(names="--about", description="Print a short description and third-party reader notice, then exit.")
		private boolean about=false;

		@Option(names= {"-f", "--format"}, defaultValue="dia", description="Output format: ${COMPLETION-CANDIDATES}.")
		private OutputFormat format=OutputFormat.dia;

		@Option(names= {"-o", "--output"}, paramLabel="DIR", description="Output directory (default: same directory as input).")
		private Path outputDirPath;

		@Option(names="--log-file", paramLabel="FILE", description="Write logs to a file (overwrites on each run).")
		private Path logFilePath;

		private float minimumMS1Intensity=3.0f;
		private float minimumMS2Intensity=1.0f;

		@Option(names="--min-ms1", defaultValue="3.0", description="Minimum MS1 intensity threshold for timsTOF.")
		private void setMinimumMS1Intensity(float value) {
			minimumMS1Intensity=validateIntensity(value, "--min-ms1");
		}

		@Option(names="--min-ms2", defaultValue="1.0", description="Minimum MS2 intensity threshold for timsTOF.")
		private void setMinimumMS2Intensity(float value) {
			minimumMS2Intensity=validateIntensity(value, "--min-ms2");
		}

		@Option(names="--demux", arity="1", description="Enable or disable staggered window demultiplexing for DIA.")
		private Boolean demultiplex=null;

		@Option(names="--precursorMarginSize", paramLabel="#", description="Trim this many m/z from each side of MS2 isolation windows.")
		private Double precursorMarginSize=null;

		@Option(names="--demux-k", defaultValue="7", description="Local approximation size for demux (7-9).")
		private int demuxK=DemuxConfig.DEFAULT_K;

		@Option(names="--demux-interp", defaultValue="cubic", description="Interpolation method for demux: ${COMPLETION-CANDIDATES}.")
		private DemuxInterpolation demuxInterpolation=DemuxInterpolation.cubic;

		@Option(names="--demux-exclude-edges", defaultValue="false", description="Exclude edge sub-windows (single coverage) from demux output.")
		private boolean demuxExcludeEdges=false;

		@Option(names="--demux-ppm", defaultValue="10.0", description="Mass tolerance in ppm for demux ion matching.")
		private double demuxPpm=10.0;

		@Option(names="--discoverDIAFiles", defaultValue="false", description="Allow directory discovery of EncyclopeDIA .dia files.")
		private boolean discoverDIAFiles=false;

		@Option(names="--discoverMzMLFiles", defaultValue="false", description="Allow directory discovery of mzML files.")
		private boolean discoverMzMLFiles=false;

		@Option(names="--batch", defaultValue="false", description="Disable status bar and progress updates.")
		private boolean batch=false;

		@Option(names="--silent", defaultValue="false", description="Suppress all non-error output.")
		private boolean silent=false;

		@Option(names="--no-ansi", defaultValue="false", description="Disable ANSI output, even on TTYs.")
		private boolean noAnsi=false;

		@Option(names="--threads", paramLabel="#", description="Processing worker threads. Defaults to max available CPU processing.")
		private Integer threads=null;

		@Override
		public Integer call() throws Exception {
			if (about) {
				spec.commandLine().getOut().println(getCliAboutText());
				return 0;
			}
			if (paths.isEmpty()) {
				throw new ParameterException(spec.commandLine(), "Missing required parameter: PATHS");
			}
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
			float validatedMS1=validateIntensity(minimumMS1Intensity, "--min-ms1");
			float validatedMS2=validateIntensity(minimumMS2Intensity, "--min-ms2");
			DemuxConfig demuxConfig=DemuxConfig.builder().k(demuxK)
					.interpolationMethod(demuxInterpolation==DemuxInterpolation.cubic?InterpolationMethod.CUBIC_HERMITE:InterpolationMethod.LOG_QUADRATIC)
					.includeEdgeSubWindows(!demuxExcludeEdges).build();

			return ConversionParameters.builder().fileList(paths).outType(format.toOutputType()).outputDirPath(outputDirPath).logFilePath(logFilePath)
					.minimumMS1Intensity(validatedMS1).minimumMS2Intensity(validatedMS2).demultiplex(demultiplex)
					.precursorMarginSize(Optional.ofNullable(precursorMarginSize)).demuxTolerance(new PPMMassTolerance(demuxPpm)).demuxConfig(demuxConfig)
					.batch(batch).silent(silent).noAnsi(noAnsi).discoverDIAFiles(discoverDIAFiles).discoverMzMLFiles(discoverMzMLFiles)
					.processingThreads(validateThreads()).build();
		}

		private Integer validateThreads() {
			if (threads==null) return null;
			if (threads<1) throw new CommandLine.ParameterException(new CommandLine(this), "--threads must be a positive integer.");
			return threads;
		}

		private float validateIntensity(float intensity, String option) {
			if (!Float.isFinite(intensity)||intensity<0f) {
				throw new CommandLine.ParameterException(new CommandLine(this), option+" must be finite and nonnegative.");
			}
			return intensity;
		}

		private void configureLogging(ConversionParameters params) throws Exception {
			if (params.isSilent()) {
				Logger.PRINT_TO_STDOUT=false;
				Logger.PRINT_TO_STDERR=true;
			}
			boolean useAnsi=System.console()!=null&&!params.isNoAnsi()&&!params.isBatch()&&!params.isSilent();
			if (useAnsi) {
				PrintStream stdout=Logger.getStdout();
				PrintStream stderr=Logger.getStderr();
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
			String vmName=Version.getJvmName();
			String vmVersion=Version.getJvmVersion();
			String runtimeName=Version.getRuntimeName();
			String runtimeVersion=Version.getRuntimeVersion();
			return new String[] {
					"MSRawJava "+Version.getVersion(),
					"Build date: "+Version.getBuildDate(),
					"JVM: "+vmName+" ("+vmVersion+")",
					"Runtime: "+runtimeName+" ("+runtimeVersion+")"
			};
		}
	}
}
