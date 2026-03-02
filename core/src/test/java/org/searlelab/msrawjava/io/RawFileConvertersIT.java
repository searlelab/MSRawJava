package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.logging.LoggingProgressIndicator;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

class RawFileConvertersIT {
	private static final Path TIMS_D=Path.of("src", "test", "resources", "rawdata", "230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");

	@Test
	void writeTims_toMGF_smoke(@TempDir Path outDir) throws Exception {
		Assumptions.assumeTrue(Files.exists(TIMS_D), "Fixture .d not present: "+TIMS_D);
		ProcessingThreadPool threads=ProcessingThreadPool.createDefault();
		ConversionParameters params=ConversionParameters.builder().outType(OutputType.mgf).minimumMS1Intensity(1.0f).minimumMS2Intensity(1.0f).build();
		RawFileConverters.writeTims(threads, TIMS_D, outDir, params, new LoggingProgressIndicator(LoggingProgressIndicator.Mode.SILENT, false));
		Path mgf=firstWithExt(outDir, ".mgf");
		assertNotNull(mgf, "Output .mgf should exist");
		assertTrue(Files.size(mgf)>0, "MGF should not be empty");

		String content=readHead(mgf, 16384);
		assertTrue(content.contains("BEGIN IONS"));
		assertTrue(content.contains("END IONS"));
		assertTrue(content.contains("TITLE=merged="));
		assertTrue(content.contains("frame="));

		threads.close();
	}

	private static Path firstWithExt(Path dir, String ext) throws IOException {
		try (var s=Files.list(dir)) {
			return s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(ext)).findFirst().orElse(null);
		}
	}

	private static String readHead(Path file, int max) throws IOException {
		byte[] b=Files.readAllBytes(file);
		int n=Math.min(b.length, max);
		return new String(b, 0, n, StandardCharsets.UTF_8);
	}
}
