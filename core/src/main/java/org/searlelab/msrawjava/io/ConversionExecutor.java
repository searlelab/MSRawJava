package org.searlelab.msrawjava.io;

import java.nio.file.Path;

import org.searlelab.msrawjava.logging.ProgressIndicator;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

/** Package-private bridge from the library facade to the legacy writer implementation. */
final class ConversionExecutor {
	private ConversionExecutor() {
	}

	static boolean writeStandard(ProcessingThreadPool pool, StripeFileInterface rawFile, Path outputDirectory, ConversionOptions options,
			boolean demultiplex, Path outputPath, ProgressIndicator progress) throws Exception {
		return RawFileConverters.writeStandardInternal(pool, rawFile, outputDirectory, ConversionSettings.fromOptions(options, demultiplex, outputPath), progress);
	}

	static boolean writeDemux(ProcessingThreadPool pool, StripeFileInterface rawFile, Path outputDirectory, ConversionOptions options, Path outputPath,
			ProgressIndicator progress) throws Exception {
		return RawFileConverters.writeDemuxInternal(pool, rawFile, outputDirectory, ConversionSettings.fromOptions(options, true, outputPath), progress);
	}

	static boolean writeTims(ProcessingThreadPool pool, Path input, Path outputDirectory, ConversionOptions options, boolean demultiplex, Path outputPath,
			ProgressIndicator progress) throws Exception {
		return RawFileConverters.writeTimsInternal(pool, input, outputDirectory, ConversionSettings.fromOptions(options, demultiplex, outputPath), progress);
	}
}
