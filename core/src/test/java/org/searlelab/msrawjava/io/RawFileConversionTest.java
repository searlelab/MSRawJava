package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.searlelab.msrawjava.logging.LoggingProgressIndicator;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class RawFileConversionTest {
	@Test
	void requestAndResultAreCreatedThroughFactories() {
		assertEquals(0, ConversionRequest.class.getConstructors().length, "ConversionRequest constructor should not be public");
		assertEquals(0, ConversionResult.class.getConstructors().length, "ConversionResult constructor should not be public");
	}

	@Test
	void standardConversionReturnsExactOutputAndStatus(@TempDir Path output) throws Exception {
		Path input=fixture("HeLa_16mzst_demux.dia");
		ConversionOptions options=ConversionOptions.builder().outputType(OutputType.mgf).build();
		ConversionResult result=RawFileConversion.convert(ConversionRequest.toDirectory(input, output, 1, options, null));

		assertEquals(ConversionStatus.COMPLETED, result.getStatus());
		assertEquals(output.resolve("HeLa_16mzst_demux.mgf").toAbsolutePath(), result.getOutputPath());
		assertTrue(Files.isRegularFile(result.getOutputPath()));
	}

	@Test
	void automaticDemuxReturnsDemuxOutputPath(@TempDir Path output) throws Exception {
		Path input=fixture("HeLa_16mzst_29to31min.dia");
		ConversionOptions options=ConversionOptions.builder().outputType(OutputType.mgf).build();
		ConversionResult result=RawFileConversion.convert(ConversionRequest.toDirectory(input, output, 1, options, null));

		assertEquals(ConversionStatus.COMPLETED, result.getStatus());
		assertEquals(output.resolve("HeLa_16mzst_29to31min.demux.mgf").toAbsolutePath(), result.getOutputPath());
		assertTrue(Files.isRegularFile(result.getOutputPath()));
	}

	@Test
	void cancellationReturnsCanceledAndResolvedPath(@TempDir Path output) throws Exception {
		Path input=fixture("HeLa_16mzst_demux.dia");
		LoggingProgressIndicator indicator=new LoggingProgressIndicator(LoggingProgressIndicator.Mode.SILENT, false);
		indicator.setCanceled(true);
		ConversionOptions options=ConversionOptions.builder().outputType(OutputType.mgf).build();
		ConversionResult result=RawFileConversion.convert(ConversionRequest.toDirectory(input, output, 1, options, indicator));

		assertEquals(ConversionStatus.CANCELED, result.getStatus());
		assertEquals(output.resolve("HeLa_16mzst_demux.mgf").toAbsolutePath(), result.getOutputPath());
	}

	@Test
	void forcedDemuxUsesDemuxOutputName(@TempDir Path output) throws Exception {
		Path input=fixture("HeLa_16mzst_29to31min.dia");
		ConversionOptions options=ConversionOptions.builder().outputType(OutputType.mgf).demultiplex(true).build();
		ConversionResult result=RawFileConversion.convert(ConversionRequest.toDirectory(input, output, 1, options, null));

		assertEquals(ConversionStatus.COMPLETED, result.getStatus());
		assertEquals(output.resolve("HeLa_16mzst_29to31min.demux.mgf").toAbsolutePath(), result.getOutputPath());
	}

	@Test
	void explicitOutputPathIsReturnedAndUsed(@TempDir Path output) throws Exception {
		Path input=fixture("HeLa_16mzst_demux.dia");
		Path explicit=output.resolve("custom-output.mgf");
		ConversionOptions options=ConversionOptions.builder().outputType(OutputType.mgf).build();
		ConversionResult result=RawFileConversion.convert(ConversionRequest.toPath(input, explicit, 1, options, null));

		assertEquals(explicit.toAbsolutePath(), result.getOutputPath());
		assertTrue(Files.isRegularFile(explicit));
	}

	@Test
	void brukerDemuxRequestIsForwardedVerbatimToWriter(@TempDir Path temp) throws Exception {
		Path input=Files.createDirectory(temp.resolve("sample.d"));
		ConversionOptions options=ConversionOptions.builder().outputType(OutputType.mgf).demultiplex(true).build();
		try (MockedStatic<ConversionExecutor> converters=Mockito.mockStatic(ConversionExecutor.class)) {
			converters.when(() -> ConversionExecutor.writeTims(Mockito.any(), Mockito.eq(input.toAbsolutePath().normalize()), Mockito.eq(temp),
					Mockito.eq(options), Mockito.eq(true), Mockito.any(), Mockito.any())).thenReturn(true);
			ConversionResult result=RawFileConversion.convert(ConversionRequest.toDirectory(input, temp, 1, options, null));
			assertEquals(ConversionStatus.COMPLETED, result.getStatus());
			converters.verify(() -> ConversionExecutor.writeTims(Mockito.any(), Mockito.eq(input.toAbsolutePath().normalize()), Mockito.eq(temp),
					Mockito.eq(options), Mockito.eq(true), Mockito.any(), Mockito.any()));
		}
	}

	@Test
	void brukerDemuxDisabledAllowsAnExplicitPrecursorMargin(@TempDir Path temp) throws Exception {
		Path input=Files.createDirectory(temp.resolve("sample.d"));
		ConversionOptions options=ConversionOptions.builder().outputType(OutputType.mgf).demultiplex(false).precursorMarginSize(0.5).build();
		try (MockedStatic<ConversionExecutor> converters=Mockito.mockStatic(ConversionExecutor.class)) {
			converters.when(() -> ConversionExecutor.writeTims(Mockito.any(), Mockito.eq(input.toAbsolutePath().normalize()), Mockito.eq(temp),
					Mockito.eq(options), Mockito.eq(false), Mockito.any(), Mockito.any())).thenReturn(true);
			ConversionResult result=RawFileConversion.convert(ConversionRequest.toDirectory(input, temp, 1, options, null));
			assertEquals(ConversionStatus.COMPLETED, result.getStatus());
			converters.verify(() -> ConversionExecutor.writeTims(Mockito.any(), Mockito.eq(input.toAbsolutePath().normalize()), Mockito.eq(temp),
					Mockito.eq(options), Mockito.eq(false), Mockito.any(), Mockito.any()));
		}
	}

	@Test
	void brukerDemuxWithZeroPrecursorMarginIsAllowed(@TempDir Path temp) throws Exception {
		Path input=Files.createDirectory(temp.resolve("sample.d"));
		ConversionOptions options=ConversionOptions.builder().outputType(OutputType.mgf).demultiplex(true).precursorMarginSize(0.0).build();
		try (MockedStatic<ConversionExecutor> converters=Mockito.mockStatic(ConversionExecutor.class)) {
			converters.when(() -> ConversionExecutor.writeTims(Mockito.any(), Mockito.eq(input.toAbsolutePath().normalize()), Mockito.eq(temp),
					Mockito.eq(options), Mockito.eq(true), Mockito.any(), Mockito.any())).thenReturn(true);
			ConversionResult result=RawFileConversion.convert(ConversionRequest.toDirectory(input, temp, 1, options, null));
			assertEquals(ConversionStatus.COMPLETED, result.getStatus());
		}
	}

	@Test
	void brukerDemuxAndPrecursorMarginAreRejected(@TempDir Path output) throws Exception {
		Path input=Path.of("src", "test", "resources", "rawdata", "dda_test.d").toAbsolutePath();
		Assumptions.assumeTrue(Files.isDirectory(input), "Fixture missing: "+input);
		ConversionOptions options=ConversionOptions.builder().outputType(OutputType.mgf).demultiplex(true).precursorMarginSize(5.0).build();
		IllegalArgumentException error=assertThrows(IllegalArgumentException.class,
				() -> RawFileConversion.convert(ConversionRequest.toDirectory(input, output, 1, options, null)));
		assertTrue(error.getMessage().contains("--demux true")&&error.getMessage().contains("--precursorMarginSize"));
	}

	@Test
	void nonThermoConversionDoesNotMutateThermoPool(@TempDir Path output) throws Exception {
		Path input=fixture("HeLa_16mzst_demux.dia");
		ConversionOptions options=ConversionOptions.builder().outputType(OutputType.mgf).build();
		try (MockedStatic<ConversionExecutor> converters=Mockito.mockStatic(ConversionExecutor.class);
				MockedStatic<org.searlelab.msrawjava.io.thermo.ThermoServerPool> thermo=Mockito.mockStatic(org.searlelab.msrawjava.io.thermo.ThermoServerPool.class)) {
			converters.when(() -> ConversionExecutor.writeStandard(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(options), Mockito.anyBoolean(),
					Mockito.any(), Mockito.any())).thenReturn(true);
			RawFileConversion.convert(ConversionRequest.toDirectory(input, output, 1, options, null));
			thermo.verifyNoInteractions();
		}
	}

	@Test
	void callerOwnedPoolRejectsProcessingThreadLimitInRequest(@TempDir Path output) throws Exception {
		ConversionRequest request=ConversionRequest.toDirectory(output.resolve("missing.dia"), output, 8,
				ConversionOptions.builder().build(), null);
		try (ProcessingThreadPool pool=ProcessingThreadPool.createWithThreadLimit(1)) {
			IllegalArgumentException error=assertThrows(IllegalArgumentException.class, () -> RawFileConversion.convert(request, pool));
			assertEquals("processingThreads is owned by the caller-supplied pool", error.getMessage());
		}
	}

	@Test
	void publicThermoConversionLeavesAnExistingServerAlive(@TempDir Path output) throws Exception {
		Path input=Files.createFile(output.resolve("sample.raw"));
		ConversionOptions options=ConversionOptions.builder().outputType(OutputType.mgf).build();
		try (MockedStatic<ConversionExecutor> converters=Mockito.mockStatic(ConversionExecutor.class);
				MockedStatic<ThermoServerPool> thermo=Mockito.mockStatic(ThermoServerPool.class);
				MockedConstruction<ThermoRawFile> readers=Mockito.mockConstruction(ThermoRawFile.class, (reader, context) -> {
					Mockito.doNothing().when(reader).openFile(Mockito.any(Path.class));
					Mockito.when(reader.getRanges()).thenReturn(java.util.Collections.emptyMap());
				}) ) {
			thermo.when(ThermoServerPool::isReady).thenReturn(true);
			thermo.when(ThermoServerPool::isStarting).thenReturn(false);
			thermo.when(ThermoServerPool::port).thenReturn(12345);
			converters.when(() -> ConversionExecutor.writeStandard(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(options), Mockito.anyBoolean(),
					Mockito.any(), Mockito.any())).thenReturn(true);

			ConversionResult result=RawFileConversion.convert(ConversionRequest.toDirectory(input, output, 1, options, null));

			assertEquals(ConversionStatus.COMPLETED, result.getStatus());
			thermo.verify(ThermoServerPool::shutdown, Mockito.never());
			thermo.verify(() -> ThermoServerPool.setProcessingThreadLimit(Mockito.any()), Mockito.never());
			assertEquals(1, readers.constructed().size());
		}
	}

	@Test
	void sameFormatInputUsesCollisionSafeName(@TempDir Path temp) throws Exception {
		Path source=fixture("HeLa_16mzst_demux.dia");
		Path input=temp.resolve("sample.dia");
		Files.copy(source, input);
		ConversionOptions options=ConversionOptions.builder().outputType(OutputType.EncyclopeDIA).build();
		ConversionResult result=RawFileConversion.convert(ConversionRequest.of(input, options));

		assertEquals(temp.resolve("sample.2.dia").toAbsolutePath(), result.getOutputPath());
		assertTrue(Files.isRegularFile(result.getOutputPath()));
	}

	@Test
	void rejectsMissingAndUnsupportedInputsBeforeConversion(@TempDir Path temp) throws Exception {
		ConversionOptions options=ConversionOptions.builder().build();
		assertThrows(IOException.class, () -> RawFileConversion.convert(ConversionRequest.of(temp.resolve("missing.raw"), options)));
		Path unsupported=Files.createFile(temp.resolve("input.txt"));
		assertThrows(IllegalArgumentException.class, () -> RawFileConversion.convert(ConversionRequest.of(unsupported, options)));
	}

	private static Path fixture(String name) {
		Path path=Path.of("src", "test", "resources", "rawdata", name).toAbsolutePath();
		Assumptions.assumeTrue(Files.isRegularFile(path), "Fixture missing: "+path);
		return path;
	}
}
