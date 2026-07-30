package org.searlelab.msrawjava.io;

import java.nio.file.Path;
import java.util.Objects;

/** The destination and terminal status of one conversion. */
public final class ConversionResult {
	private final Path outputPath;
	private final ConversionStatus status;

	public ConversionResult(Path outputPath, ConversionStatus status) {
		this.outputPath=Objects.requireNonNull(outputPath, "outputPath");
		this.status=Objects.requireNonNull(status, "status");
	}

	public Path getOutputPath() { return outputPath; }
	public ConversionStatus getStatus() { return status; }
}
