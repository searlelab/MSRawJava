package org.searlelab.msrawjava.io;

import java.nio.file.Path;
import java.util.Objects;

import org.searlelab.msrawjava.API;

/** The destination and terminal status of one conversion. */
@API(status = API.Status.STABLE, since = "v26.7.31")
public final class ConversionResult {
	private final Path outputPath;
	private final ConversionStatus status;

	ConversionResult(Path outputPath, ConversionStatus status) {
		this.outputPath=Objects.requireNonNull(outputPath, "outputPath");
		this.status=Objects.requireNonNull(status, "status");
	}

	@API(status = API.Status.STABLE, since = "v26.7.31")
	public Path getOutputPath() { return outputPath; }
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public ConversionStatus getStatus() { return status; }
}
