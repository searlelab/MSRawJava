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
import org.searlelab.msrawjava.io.MGFOutputFile;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConverters;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.VendorFile;
import org.searlelab.msrawjava.io.VendorFileFinder;
import org.searlelab.msrawjava.io.VendorFiles;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.mzml.MzmlConstants;
import org.searlelab.msrawjava.io.mzml.MzmlFile;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.io.utils.RawFileStructureTools;
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
	static final String CLI_ABOUT_TEXT="MSRawJava is a command-line tool and Java library for focused, cross-platform mass spectrometry raw-file reading and conversion. "
			+"It supports Thermo .raw, Bruker timsTOF .d, EncyclopeDIA .dia, and mzML inputs, with export to .dia, .mgf, and mzML.\n"
			+"RawFileReader reading tool. Copyright \u00a9 2016 by Thermo Fisher Scientific, Inc. All rights reserved.";

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
		ThermoServerPool.setProcessingThreadLimit(params.getProcessingThreads());
		ProcessingThreadPool pool=ProcessingThreadPool.createWithThreadLimit(params.getProcessingThreads());
		VendorFiles files=new VendorFiles();
		LoggingProgressIndicator indicator=null;
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

		if (files.getThermoFiles().size()>0) {
			Logger.logLine("Found "+files.getThermoFiles().size()+" total "+VendorFile.THERMO.getDisplayName()+" files");
			try {
				Logger.logLine("Setting up "+VendorFile.THERMO.getDisplayName()+" reader...");
				ThermoServerPool.port();

				for (Path path : files.getThermoFiles()) {
					Logger.logLine("Processing "+VendorFile.THERMO.getDisplayName()+" "+path);

					Path outputPath=params.getOutputDirPath()==null?path.getParent():params.getOutputDirPath();
					Logger.logLine("Writing "+params.getOutType()+" file to "+outputPath.toString());

					ThermoRawFile rawFile=new ThermoRawFile();
					rawFile.openFile(path);

					ConversionParameters fileParams=prepareFileParameters(params, rawFile, VendorFile.THERMO);
					fileParams=maybeOverrideOutput(fileParams, path, outputPath, VendorFile.THERMO);
					indicator=createIndicator(fileParams);
					try {
						if (fileParams.getDemultiplex().orElse(false)) {
							RawFileConverters.writeDemux(pool, rawFile, outputPath, fileParams, indicator);
						} else {
							RawFileConverters.writeStandard(pool, rawFile, outputPath, fileParams, indicator);
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
			Logger.logLine("Found "+files.getBrukerDirs().size()+" total "+VendorFile.BRUKER.getDisplayName()+" files");
			for (Path path : files.getBrukerDirs()) {
				Logger.logLine("Processing "+VendorFile.BRUKER.getDisplayName()+" "+path);

				Path outputPath=params.getOutputDirPath()==null?path.getParent():params.getOutputDirPath();
				Logger.logLine("Writing "+params.getOutType()+" file to "+outputPath.toString());

				if (params.getDemultiplex().orElse(false)) {
					Logger.errorLine("Sorry, staggered demultiplexing is not available for "+VendorFile.BRUKER.getDisplayName()
							+" files. Processing without demultiplexing.");
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

		if (files.getDiaFiles().size()>0) {
			Logger.logLine("Found "+files.getDiaFiles().size()+" total "+VendorFile.ENCYCLOPEDIA.getDisplayName()+" files");
			for (Path path : files.getDiaFiles()) {
				Logger.logLine("Processing "+VendorFile.ENCYCLOPEDIA.getDisplayName()+" "+path);

				Path outputPath=params.getOutputDirPath()==null?path.getParent():params.getOutputDirPath();
				Logger.logLine("Writing "+params.getOutType()+" file to "+outputPath.toString());

				EncyclopeDIAFile dia=new EncyclopeDIAFile();
				dia.openFile(path.toFile());

				ConversionParameters fileParams=prepareFileParameters(params, dia, VendorFile.ENCYCLOPEDIA);
				fileParams=maybeOverrideOutput(fileParams, path, outputPath, VendorFile.ENCYCLOPEDIA);
				indicator=createIndicator(fileParams);
				try {
					if (fileParams.getDemultiplex().orElse(false)) {
						RawFileConverters.writeDemux(pool, dia, outputPath, fileParams, indicator);
					} else {
						RawFileConverters.writeStandard(pool, dia, outputPath, fileParams, indicator);
					}
				} finally {
					indicator.close();
				}
				Logger.logLine("Finished writing "+params.getOutType()+" file");
			}
		}

		if (files.getMzmlFiles().size()>0) {
			Logger.logLine("Found "+files.getMzmlFiles().size()+" total "+VendorFile.MZML.getDisplayName()+" files");
			for (Path path : files.getMzmlFiles()) {
				Logger.logLine("Processing "+VendorFile.MZML.getDisplayName()+" "+path);

				Path outputPath=params.getOutputDirPath()==null?path.getParent():params.getOutputDirPath();
				Logger.logLine("Writing "+params.getOutType()+" file to "+outputPath.toString());

				MzmlFile mzml=new MzmlFile();
				mzml.openFile(path.toFile());

				ConversionParameters fileParams=prepareFileParameters(params, mzml, VendorFile.MZML);
				fileParams=maybeOverrideOutput(fileParams, path, outputPath, VendorFile.MZML);
				indicator=createIndicator(fileParams);
				try {
					if (fileParams.getDemultiplex().orElse(false)) {
						RawFileConverters.writeDemux(pool, mzml, outputPath, fileParams, indicator);
					} else {
						RawFileConverters.writeStandard(pool, mzml, outputPath, fileParams, indicator);
					}
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

	private static ConversionParameters prepareFileParameters(ConversionParameters base, StripeFileInterface rawFile, VendorFile source) {
		boolean inferredDemux=source!=VendorFile.BRUKER&&RawFileStructureTools.isStaggered(rawFile.getRanges());
		boolean demux=base.getDemultiplex().orElse(inferredDemux);
		if (source==VendorFile.BRUKER&&demux) {
			demux=false;
		}
		if (base.getPrecursorMarginSize().isPresent()) {
			rawFile.setPrecursorMarginSize(base.getPrecursorMarginSize().get());
		}
		double margin=rawFile.getPrecursorMarginSize();
		if (demux&&margin!=0.0) {
			throw new IllegalArgumentException("--demux true cannot be used with --precursorMarginSize "+margin
					+". Use staggered demultiplexing or precursor margins, not both.");
		}
		return cloneWithResolvedSettings(base, demux);
	}

	private static ConversionParameters maybeOverrideOutput(ConversionParameters base, Path inputPath, Path outputDir, VendorFile source) {
		if (base.getOutputFilePathOverride()!=null) return base;
		String name=inputPath.getFileName().toString();
		boolean isDiaInput=VendorFile.ENCYCLOPEDIA.matchesName(name);
		boolean isMzmlInput=VendorFile.MZML.matchesName(name);

		if (base.getDemultiplex().orElse(false)&&(source==VendorFile.THERMO||source==VendorFile.ENCYCLOPEDIA||source==VendorFile.MZML)) {
			String baseName=stripExtension(name);
			String suffix;
			switch (base.getOutType()) {
				case EncyclopeDIA:
					suffix=".demux"+EncyclopeDIAFile.DIA_EXTENSION;
					break;
				case mzML:
					suffix=".demux"+MzmlConstants.MZML_EXTENSION;
					break;
				case mgf:
					suffix=".demux"+MGFOutputFile.MGF_EXTENSION;
					break;
				default:
					suffix=null;
					break;
			}
			if (suffix!=null) {
				return cloneWithOutputOverride(base, outputDir.resolve(baseName+suffix));
			}
		}

		if (source==VendorFile.ENCYCLOPEDIA&&base.getOutType()==OutputType.EncyclopeDIA&&base.getOutputDirPath()==null&&isDiaInput) {
			String baseName=name.substring(0, name.length()-4);
			Path override=outputDir.resolve(baseName+".2"+EncyclopeDIAFile.DIA_EXTENSION);
			return cloneWithOutputOverride(base, override);
		}

		// Prevent mzML-to-mzML overwrite when output dir is same as input dir
		if (source==VendorFile.MZML&&base.getOutType()==OutputType.mzML&&base.getOutputDirPath()==null&&isMzmlInput) {
			String baseName=name.substring(0, name.length()-5); // strip .mzML
			Path override=outputDir.resolve(baseName+".2"+MzmlConstants.MZML_EXTENSION);
			return cloneWithOutputOverride(base, override);
		}
		return base;
	}

	private static String stripExtension(String name) {
		int idx=name.lastIndexOf('.');
		return (idx>0)?name.substring(0, idx):name;
	}

	private static ConversionParameters cloneWithOutputOverride(ConversionParameters base, Path override) {
		return ConversionParameters.builder().fileList(base.getFileList()).outType(base.getOutType()).outputDirPath(base.getOutputDirPath())
				.minimumMS1Intensity(base.getMinimumMS1Intensity()).minimumMS2Intensity(base.getMinimumMS2Intensity()).demultiplex(base.getDemultiplex())
				.precursorMarginSize(base.getPrecursorMarginSize()).demuxTolerance(base.getDemuxTolerance()).demuxConfig(base.getDemuxConfig())
				.logFilePath(base.getLogFilePath()).batch(base.isBatch()).silent(base.isSilent()).noAnsi(base.isNoAnsi())
				.discoverDIAFiles(base.isDiscoverDIAFiles()).discoverMzMLFiles(base.isDiscoverMzMLFiles()).outputFilePathOverride(override)
				.processingThreads(base.getProcessingThreads()).build();
	}

	private static ConversionParameters cloneWithResolvedSettings(ConversionParameters base, boolean demux) {
		return ConversionParameters.builder().fileList(base.getFileList()).outType(base.getOutType()).outputDirPath(base.getOutputDirPath())
				.minimumMS1Intensity(base.getMinimumMS1Intensity()).minimumMS2Intensity(base.getMinimumMS2Intensity()).demultiplex(demux)
				.precursorMarginSize(base.getPrecursorMarginSize()).demuxTolerance(base.getDemuxTolerance()).demuxConfig(base.getDemuxConfig())
				.logFilePath(base.getLogFilePath()).batch(base.isBatch()).silent(base.isSilent()).noAnsi(base.isNoAnsi())
				.discoverDIAFiles(base.isDiscoverDIAFiles()).discoverMzMLFiles(base.isDiscoverMzMLFiles())
				.outputFilePathOverride(base.getOutputFilePathOverride()).processingThreads(base.getProcessingThreads()).build();
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

		@Option(names="--min-ms1", defaultValue="3.0", description="Minimum MS1 intensity threshold for timsTOF.")
		private float minimumMS1Intensity=3.0f;

		@Option(names="--min-ms2", defaultValue="1.0", description="Minimum MS2 intensity threshold for timsTOF.")
		private float minimumMS2Intensity=1.0f;

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
				spec.commandLine().getOut().println(CLI_ABOUT_TEXT);
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
			DemuxConfig demuxConfig=DemuxConfig.builder().k(demuxK)
					.interpolationMethod(demuxInterpolation==DemuxInterpolation.cubic?InterpolationMethod.CUBIC_HERMITE:InterpolationMethod.LOG_QUADRATIC)
					.includeEdgeSubWindows(!demuxExcludeEdges).build();

			return ConversionParameters.builder().fileList(paths).outType(format.toOutputType()).outputDirPath(outputDirPath).logFilePath(logFilePath)
					.minimumMS1Intensity(minimumMS1Intensity).minimumMS2Intensity(minimumMS2Intensity).demultiplex(demultiplex)
					.precursorMarginSize(Optional.ofNullable(precursorMarginSize)).demuxTolerance(new PPMMassTolerance(demuxPpm)).demuxConfig(demuxConfig)
					.batch(batch).silent(silent).noAnsi(noAnsi).discoverDIAFiles(discoverDIAFiles).discoverMzMLFiles(discoverMzMLFiles)
					.processingThreads(validateThreads()).build();
		}

		private Integer validateThreads() {
			if (threads==null) return null;
			if (threads<1) throw new CommandLine.ParameterException(new CommandLine(this), "--threads must be a positive integer.");
			return threads;
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
