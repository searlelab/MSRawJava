package org.searlelab.msrawjava.io;

import java.nio.file.Path;
import java.util.Objects;

import org.searlelab.msrawjava.API;
import org.searlelab.msrawjava.logging.ProgressIndicator;

/** Complete request for converting one supported input. */
@API(status = API.Status.STABLE, since = "v26.7.31")
public final class ConversionRequest {
	private final Path inputPath;
	private final Path outputDirectory;
	private final Path outputPath;
	private final Integer processingThreads;
	private final ConversionOptions options;
	private final ProgressIndicator progressIndicator;

	ConversionRequest(Path inputPath, Path outputDirectory, Path outputPath, Integer processingThreads, ConversionOptions options,
			ProgressIndicator progressIndicator) {
		if (inputPath==null) throw new IllegalArgumentException("inputPath must not be null");
		this.inputPath=inputPath;
		if (outputDirectory!=null&&outputPath!=null) throw new IllegalArgumentException("Specify outputDirectory or outputPath, not both");
		if (processingThreads!=null&&processingThreads<1) throw new IllegalArgumentException("processingThreads must be positive");
		this.outputDirectory=outputDirectory;
		this.outputPath=outputPath;
		this.processingThreads=processingThreads;
		this.options=Objects.requireNonNull(options, "options");
		this.progressIndicator=progressIndicator;
	}

	@API(status = API.Status.STABLE, since = "v26.7.31")
	public static ConversionRequest of(Path inputPath, ConversionOptions options) {
		return new ConversionRequest(inputPath, null, null, null, options, null);
	}

	/** Creates a request whose output is resolved beneath the supplied directory. */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public static ConversionRequest toDirectory(Path inputPath, Path outputDirectory, Integer processingThreads, ConversionOptions options,
			ProgressIndicator progressIndicator) {
		return new ConversionRequest(inputPath, outputDirectory, null, processingThreads, options, progressIndicator);
	}

	/** Creates a request with an explicit output path. */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public static ConversionRequest toPath(Path inputPath, Path outputPath, Integer processingThreads, ConversionOptions options,
			ProgressIndicator progressIndicator) {
		return new ConversionRequest(inputPath, null, outputPath, processingThreads, options, progressIndicator);
	}

	@API(status = API.Status.STABLE, since = "v26.7.31")
	public Path getInputPath() { return inputPath; }
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public Path getOutputDirectory() { return outputDirectory; }
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public Path getOutputPath() { return outputPath; }
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public Integer getProcessingThreads() { return processingThreads; }
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public ConversionOptions getOptions() { return options; }
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public ProgressIndicator getProgressIndicator() { return progressIndicator; }
}
