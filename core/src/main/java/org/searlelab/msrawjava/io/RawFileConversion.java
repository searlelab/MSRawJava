package org.searlelab.msrawjava.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.searlelab.msrawjava.API;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.mzml.MzmlConstants;
import org.searlelab.msrawjava.io.mzml.MzmlFile;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.io.utils.RawFileStructureTools;
import org.searlelab.msrawjava.logging.LoggingProgressIndicator;
import org.searlelab.msrawjava.logging.ProgressIndicator;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

/** Library-oriented facade for converting one supported input file. */
@API(status = API.Status.STABLE, since = "v26.7.31")
public final class RawFileConversion {
	private RawFileConversion() {
	}

	/**
	 * Converts one input while owning the worker pool. ThermoServerPool is shared process state; this method only
	 * changes its thread limit and shuts it down when this call observes that no other caller owns or is starting it.
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public static ConversionResult convert(ConversionRequest request) throws Exception {
		ValidatedRequest validated=validate(request);
		boolean ownsThermo=validated.vendor==VendorFile.THERMO&&!ThermoServerPool.isReady()&&!ThermoServerPool.isStarting();
		if (ownsThermo) {
			ThermoServerPool.setProcessingThreadLimit(request.getProcessingThreads());
		}
		try (ProcessingThreadPool pool=ProcessingThreadPool.createWithThreadLimit(request.getProcessingThreads())) {
			if (validated.vendor==VendorFile.THERMO) ThermoServerPool.port();
			return convert(validated, pool);
		} finally {
			if (ownsThermo) ThermoServerPool.shutdown();
		}
	}

	/**
	 * Converts using a caller-owned pool. This is intended for batch/UI orchestrators; the pool and Thermo server
	 * remain alive across calls and must be closed by the owner. The owner is responsible for ThermoServerPool lifecycle.
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public static ConversionResult convert(ConversionRequest request, ProcessingThreadPool pool) throws Exception {
		if (pool==null) throw new IllegalArgumentException("pool must not be null");
		if (request!=null&&request.getProcessingThreads()!=null) {
			throw new IllegalArgumentException("processingThreads is owned by the caller-supplied pool");
		}
		return convert(validate(request), pool);
	}

	/**
	 * Converts an already-open reader using caller-owned reader and worker-pool lifecycles. The request must specify an
	 * explicit output path and must not specify a processing-thread count.
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public static ConversionResult convert(StripeFileInterface source, ConversionRequest request, ProcessingThreadPool pool) throws Exception {
		if (source==null) throw new IllegalArgumentException("source must not be null");
		if (!source.isOpen()) throw new IllegalArgumentException("source must be open");
		if (pool==null) throw new IllegalArgumentException("pool must not be null");
		if (request==null) throw new IllegalArgumentException("request must not be null");
		if (request.getProcessingThreads()!=null) throw new IllegalArgumentException("processingThreads is owned by the caller-supplied pool");
		if (request.getOutputPath()==null) throw new IllegalArgumentException("outputPath must be explicit for a caller-supplied source");
		Path outputPath=request.getOutputPath().toAbsolutePath().normalize();
		Path outputDirectory=outputPath.getParent();
		if (outputDirectory==null) throw new IllegalArgumentException("Cannot determine output directory for "+outputPath);
		ConversionOptions options=request.getOptions();
		boolean demux=options.getDemultiplex().orElse(RawFileStructureTools.isStaggered(source.getRanges()));
		if (demux&&options.getPrecursorMarginSize().isPresent()&&options.getPrecursorMarginSize().get()!=0d) {
			throw new IllegalArgumentException("Demultiplexing cannot be combined with a nonzero precursor margin");
		}
		if (options.getPrecursorMarginSize().isPresent()) source.setPrecursorMarginSize(options.getPrecursorMarginSize().get());
		ProgressIndicator progress=request.getProgressIndicator();
		LoggingProgressIndicator ownedProgress=progress==null?new LoggingProgressIndicator(LoggingProgressIndicator.Mode.BATCH, false):null;
		if (progress==null) progress=ownedProgress;
		try {
			boolean completed=demux?ConversionExecutor.writeDemux(pool, source, outputDirectory, options, outputPath, progress, false)
					:ConversionExecutor.writeStandard(pool, source, outputDirectory, options, false, outputPath, progress, false);
			return new ConversionResult(outputPath, completed?ConversionStatus.COMPLETED:ConversionStatus.CANCELED);
		} finally {
			if (ownedProgress!=null) ownedProgress.close();
		}
	}

	private static ConversionResult convert(ValidatedRequest request, ProcessingThreadPool pool) throws Exception {
		ProgressIndicator progress=request.progress;
		LoggingProgressIndicator ownedProgress=progress==null?new LoggingProgressIndicator(LoggingProgressIndicator.Mode.BATCH, false):null;
		if (progress==null) progress=ownedProgress;
		boolean completed;
		try {
			switch (request.vendor) {
				case THERMO:
					completed=convertThermo(request, pool, progress);
					break;
				case BRUKER:
					if (request.options.getDemultiplex().orElse(false)&&request.options.getPrecursorMarginSize().isPresent()
							&&request.options.getPrecursorMarginSize().get()!=0d) {
						double margin=request.options.getPrecursorMarginSize().get();
						throw new IllegalArgumentException("--demux true cannot be used with --precursorMarginSize "+margin
								+". Use staggered demultiplexing or precursor margins, not both.");
					}
					completed=ConversionExecutor.writeTims(pool, request.input, request.outputDirectory, request.options,
							request.options.getDemultiplex().orElse(false), request.outputPath, progress);
					break;
				case ENCYCLOPEDIA:
					completed=convertStripe(request, pool, progress, new EncyclopeDIAFile());
					break;
				case MZML:
					completed=convertStripe(request, pool, progress, new MzmlFile());
					break;
				default:
					throw new IllegalArgumentException("Unsupported input: "+request.input);
			}
			return new ConversionResult(request.outputPath, completed?ConversionStatus.COMPLETED:ConversionStatus.CANCELED);
		} finally {
			if (ownedProgress!=null) ownedProgress.close();
		}
	}

	private static boolean convertThermo(ValidatedRequest request, ProcessingThreadPool pool, ProgressIndicator progress) throws Exception {
		ThermoRawFile raw=new ThermoRawFile();
		try {
			raw.openFile(request.input);
			return writeStripe(request, pool, progress, raw);
		} finally {
			raw.close();
		}
	}

	private static boolean convertStripe(ValidatedRequest request, ProcessingThreadPool pool, ProgressIndicator progress, StripeFileInterface raw)
			throws Exception {
		try {
			if (raw instanceof EncyclopeDIAFile) ((EncyclopeDIAFile)raw).openFile(request.input.toFile());
			else if (raw instanceof MzmlFile) ((MzmlFile)raw).openFile(request.input.toFile());
			return writeStripe(request, pool, progress, raw);
		} finally {
			raw.close();
		}
	}

	private static boolean writeStripe(ValidatedRequest request, ProcessingThreadPool pool, ProgressIndicator progress, StripeFileInterface raw)
			throws Exception {
		boolean inferred=request.vendor!=VendorFile.BRUKER&&RawFileStructureTools.isStaggered(raw.getRanges());
		boolean demux=request.options.getDemultiplex().orElse(inferred);
		if (!request.explicitOutput) request.outputPath=resolveOutputPath(request.input, request.outputDirectory, request.options, request.vendor, demux,
				request.outputDirectorySpecified);
		if (request.options.getPrecursorMarginSize().isPresent()) raw.setPrecursorMarginSize(request.options.getPrecursorMarginSize().get());
		double margin=raw.getPrecursorMarginSize();
		if (demux&&margin!=0d) {
			throw new IllegalArgumentException("--demux true cannot be used with --precursorMarginSize "+margin
					+". Use staggered demultiplexing or precursor margins, not both.");
		}
		return demux?ConversionExecutor.writeDemux(pool, raw, request.outputDirectory, request.options, request.outputPath, progress, true)
				:ConversionExecutor.writeStandard(pool, raw, request.outputDirectory, request.options, false, request.outputPath, progress, true);
	}

	private static ValidatedRequest validate(ConversionRequest request) throws IOException {
		if (request==null) throw new IllegalArgumentException("request must not be null");
		Path input=request.getInputPath().toAbsolutePath().normalize();
		if (!Files.exists(input)) throw new IOException("Input does not exist: "+input);
		if (!Files.isReadable(input)) throw new IOException("Input is not readable: "+input);
		VendorFile vendor=VendorFile.fromPath(input).orElseThrow(() -> new IllegalArgumentException("Unsupported input: "+input));
		Path outputDirectory=request.getOutputDirectory()==null?input.getParent():request.getOutputDirectory().toAbsolutePath().normalize();
		if (outputDirectory==null) throw new IllegalArgumentException("Cannot determine output directory for "+input);
		Path outputPath=request.getOutputPath()==null?resolveOutputPath(input, outputDirectory, request.getOptions(), vendor,
				request.getOptions().getDemultiplex().orElse(false), request.getOutputDirectory()!=null)
				:request.getOutputPath().toAbsolutePath().normalize();
		return new ValidatedRequest(input, outputDirectory, outputPath, vendor, request.getOptions(), request.getProgressIndicator(),
				request.getOutputPath()!=null, request.getOutputDirectory()!=null);
	}

	private static Path resolveOutputPath(Path input, Path outputDirectory, ConversionOptions options, VendorFile vendor, boolean demux,
			boolean outputDirectorySpecified) {
		String name=input.getFileName().toString();
		if (demux&&vendor!=VendorFile.BRUKER) {
			String suffix=outputSuffix(options.getOutputType());
			return outputDirectory.resolve(stripExtension(name)+".demux"+suffix);
		}
		boolean writingBesideInput=outputDirectory.equals(input.getParent());
		if (vendor==VendorFile.ENCYCLOPEDIA&&options.getOutputType()==OutputType.EncyclopeDIA
				&&(!outputDirectorySpecified||writingBesideInput)) {
			return outputDirectory.resolve(stripExtension(name)+".2"+EncyclopeDIAFile.DIA_EXTENSION);
		}
		if (vendor==VendorFile.MZML&&options.getOutputType()==OutputType.mzML&&(!outputDirectorySpecified||writingBesideInput)) {
			return outputDirectory.resolve(stripExtension(name)+".2"+MzmlConstants.MZML_EXTENSION);
		}
		return options.getOutputType().getOutputFilePath(outputDirectory, name);
	}

	private static String outputSuffix(OutputType outputType) {
		switch (outputType) {
			case EncyclopeDIA: return EncyclopeDIAFile.DIA_EXTENSION;
			case mzML: return MzmlConstants.MZML_EXTENSION;
			case mgf: return MGFOutputFile.MGF_EXTENSION;
			default: throw new IllegalArgumentException("Unsupported output type: "+outputType);
		}
	}

	private static String stripExtension(String name) {
		int index=name.lastIndexOf('.');
		return index>0?name.substring(0, index):name;
	}

	private static final class ValidatedRequest {
		private final Path input;
		private final Path outputDirectory;
		private Path outputPath;
		private final VendorFile vendor;
		private final ConversionOptions options;
		private final ProgressIndicator progress;
		private final boolean explicitOutput;
		private final boolean outputDirectorySpecified;

		private ValidatedRequest(Path input, Path outputDirectory, Path outputPath, VendorFile vendor, ConversionOptions options, ProgressIndicator progress,
				boolean explicitOutput, boolean outputDirectorySpecified) {
			this.input=input;
			this.outputDirectory=outputDirectory;
			this.outputPath=outputPath;
			this.vendor=vendor;
			this.options=options;
			this.progress=progress;
			this.explicitOutput=explicitOutput;
			this.outputDirectorySpecified=outputDirectorySpecified;
		}
	}
}
