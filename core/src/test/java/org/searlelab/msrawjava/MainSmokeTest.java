package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.searlelab.msrawjava.io.ExportParameters;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConverters;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.logging.ProgressIndicator;

class MainSmokeTest {

	private PrintStream origOut;
	private java.io.ByteArrayOutputStream outBuf;

	@BeforeEach
	void setup() {
		origOut=System.out;
		outBuf=new java.io.ByteArrayOutputStream();
		System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
	}

	@AfterEach
	void tearDown() {
		System.setOut(origOut);
	}

	private String stdout() {
		return outBuf.toString(StandardCharsets.UTF_8);
	}

	@Test
	void main_withDirectoryThatHasNoVendorFiles_runsToCompletion(@TempDir Path tmp) throws Exception {
		Files.createDirectories(tmp);
		Files.writeString(tmp.resolve("readme.txt"), "no vendors here");

		try (MockedStatic<RawFileConverters> conv=Mockito.mockStatic(RawFileConverters.class);
				MockedStatic<ThermoServerPool> pool=Mockito.mockStatic(ThermoServerPool.class)) {

			assertDoesNotThrow(() -> Main.main(new String[] {tmp.toString()}));

			String out=stdout();
			assertTrue(out.contains("Welcome to MSRawJava version"));
			assertTrue(out.contains("Found 1 starting paths, export format: EncyclopeDIA"));
			conv.verifyNoInteractions();
			pool.verifyNoInteractions();
		}
	}

	@TempDir
	Path tmp;

	private ExportParameters params(Path start, OutputType out, Path outDir, float ms1, float ms2) {
		ArrayList<java.io.File> files=new ArrayList<>();
		files.add(start.toFile());
		return new ExportParameters(files, out, outDir, ms1, ms2);
	}

	@Test
	void convertKnownFiles_invokesThermoAndTimsWriters_andManagesThermoPool() throws Exception {
		Path start=tmp.resolve("input");
		Files.createDirectories(start);
		Path raw=start.resolve("file.raw");
		Files.writeString(raw, "dummy");
		Path ddir=start.resolve("bundle.d");
		Files.createDirectories(ddir);

		Path outDir=tmp.resolve("out");
		Files.createDirectories(outDir);

		ExportParameters p=params(start, OutputType.mgf, outDir, 2.0f, 1.0f);

		try (MockedStatic<RawFileConverters> conv=Mockito.mockStatic(RawFileConverters.class);
				MockedStatic<ThermoServerPool> pool=Mockito.mockStatic(ThermoServerPool.class)) {

			// Static methods return boolean now; stub them to succeed.
			conv.when(() -> RawFileConverters.writeThermo(any(), any(), any(), any(ProgressIndicator.class))).thenReturn(true);
			conv.when(() -> RawFileConverters.writeTims(any(), any(), any(), any(ProgressIndicator.class), anyFloat(), anyFloat())).thenReturn(true);

			assertDoesNotThrow(() -> Main.convertKnownFiles(p));

			// Thermo server life-cycle when RAW present
			pool.verify(ThermoServerPool::port, times(1));
			pool.verify(ThermoServerPool::shutdown, times(1));

			// Verify writers called with expected paths and any ProgressIndicator
			conv.verify(() -> RawFileConverters.writeThermo(eq(raw.toAbsolutePath().normalize()), eq(outDir), eq(OutputType.mgf), any(ProgressIndicator.class)),
					times(1));

			conv.verify(() -> RawFileConverters.writeTims(eq(ddir.toAbsolutePath().normalize()), eq(outDir), eq(OutputType.mgf), any(ProgressIndicator.class),
					eq(2.0f), eq(1.0f)), times(1));
		}
	}

	@Test
	void convertKnownFiles_withOnlyTims_skipsThermoPool_andWritesTims() throws Exception {
		Path start=tmp.resolve("onlyd");
		Files.createDirectories(start);
		Path ddir=start.resolve("only.d");
		Files.createDirectories(ddir);

		ExportParameters p=params(start, OutputType.EncyclopeDIA, null, 3.0f, 1.0f);

		try (MockedStatic<RawFileConverters> conv=Mockito.mockStatic(RawFileConverters.class);
				MockedStatic<ThermoServerPool> pool=Mockito.mockStatic(ThermoServerPool.class)) {

			conv.when(() -> RawFileConverters.writeTims(any(), any(), any(), any(ProgressIndicator.class), anyFloat(), anyFloat())).thenReturn(true);

			assertDoesNotThrow(() -> Main.convertKnownFiles(p));

			// No RAW files -> Thermo server should not be used
			pool.verify(ThermoServerPool::port, times(0));
			pool.verify(ThermoServerPool::shutdown, times(0));

			Path expectedOut=start; // parent of .d when outputDirPath == null
			conv.verify(() -> RawFileConverters.writeTims(eq(ddir.toAbsolutePath().normalize()), eq(expectedOut), eq(OutputType.EncyclopeDIA),
					any(ProgressIndicator.class), eq(3.0f), eq(1.0f)), times(1));
		}
	}
}