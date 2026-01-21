package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.logging.LoggingProgressIndicator;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

class RawFileConvertersTest {
	@Test
	void writeTims_missingDir_throws() throws Exception {
		Path missing=Path.of("src", "test", "resources", "rawdata", "nope_does_not_exist.d");
		ProcessingThreadPool threads=ProcessingThreadPool.createDefault();
		ConversionParameters params=ConversionParameters.builder()
				.outType(OutputType.mgf)
				.minimumMS1Intensity(1.0f)
				.minimumMS2Intensity(1.0f)
				.build();
		assertThrows(Exception.class, () -> RawFileConverters.writeTims(threads, missing, missing.getParent(), params, new LoggingProgressIndicator(LoggingProgressIndicator.Mode.SILENT, false)));
		threads.close();
	}
}
