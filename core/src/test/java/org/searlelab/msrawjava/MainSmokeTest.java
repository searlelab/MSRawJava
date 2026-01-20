package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import org.mockito.MockedConstruction; // <-- added
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConverters;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile; // <-- added
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.logging.ProgressIndicator;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

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

	private ConversionParameters params(Path start, OutputType out, Path outDir, float ms1, float ms2) {
		ArrayList<java.io.File> files=new ArrayList<>();
		files.add(start.toFile());
		return ConversionParameters.builder()
				.fileList(files)
				.outType(out)
				.outputDirPath(outDir)
				.minimumMS1Intensity(ms1)
				.minimumMS2Intensity(ms2)
				.build();
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

		ConversionParameters p=params(start, OutputType.mgf, outDir, 2.0f, 1.0f);

		try (MockedStatic<RawFileConverters> conv=Mockito.mockStatic(RawFileConverters.class);
				MockedStatic<ThermoServerPool> pool=Mockito.mockStatic(ThermoServerPool.class);
				// prevent ThermoRawFile from trying to open a real gRPC connection
				MockedConstruction<ThermoRawFile> ctor=Mockito.mockConstruction(ThermoRawFile.class, (mock, ctx) -> {
					Mockito.doNothing().when(mock).openFile(any(Path.class));
					Mockito.doNothing().when(mock).close();
				})) {

			pool.when(ThermoServerPool::port).thenReturn(12345); // harmless value
			
			// Static methods return boolean; stub them to succeed.
			conv.when(() -> RawFileConverters.writeStandard(any(ProcessingThreadPool.class), any(StripeFileInterface.class), any(Path.class), any(ConversionParameters.class),
					any(ProgressIndicator.class)))
					.thenReturn(true);
			conv.when(() -> RawFileConverters.writeTims(any(ProcessingThreadPool.class), any(Path.class), any(Path.class), any(ConversionParameters.class), any(ProgressIndicator.class)))
					.thenReturn(true);

			assertDoesNotThrow(() -> Main.convertKnownFiles(p));

			// Thermo server life-cycle when RAW present
			pool.verify(ThermoServerPool::port, times(1));
			pool.verify(ThermoServerPool::shutdown, times(1));

			// Verify writers called with expected paths and any ProgressIndicator
			conv.verify(() -> RawFileConverters.writeStandard(any(ProcessingThreadPool.class), any(StripeFileInterface.class), eq(outDir),
					argThat(paramsArg -> paramsArg.getOutType()==OutputType.mgf), any(ProgressIndicator.class)), times(1));

			conv.verify(() -> RawFileConverters.writeTims(any(ProcessingThreadPool.class), eq(ddir.toAbsolutePath().normalize()), eq(outDir),
					argThat(paramsArg -> paramsArg.getOutType()==OutputType.mgf&&paramsArg.getMinimumMS1Intensity()==2.0f&&paramsArg.getMinimumMS2Intensity()==1.0f),
					any(ProgressIndicator.class)), times(1));
			
		}
	}

	@Test
	void convertKnownFiles_withOnlyTims_skipsThermoPool_andWritesTims() throws Exception {
		Path start=tmp.resolve("onlyd");
		Files.createDirectories(start);
		Path ddir=start.resolve("only.d");
		Files.createDirectories(ddir);

		ConversionParameters p=params(start, OutputType.EncyclopeDIA, null, 3.0f, 1.0f);

		try (MockedStatic<RawFileConverters> conv=Mockito.mockStatic(RawFileConverters.class);
				MockedStatic<ThermoServerPool> pool=Mockito.mockStatic(ThermoServerPool.class)) {

			conv.when(() -> RawFileConverters.writeTims(any(ProcessingThreadPool.class), any(Path.class), any(Path.class), any(ConversionParameters.class), any(ProgressIndicator.class)))
					.thenReturn(true);

			assertDoesNotThrow(() -> Main.convertKnownFiles(p));

			// No RAW files -> Thermo server should not be used
			pool.verify(ThermoServerPool::port, times(0));
			pool.verify(ThermoServerPool::shutdown, times(0));

			Path expectedOut=start; // parent of .d when outputDirPath == null
			conv.verify(() -> RawFileConverters.writeTims(any(ProcessingThreadPool.class), eq(ddir.toAbsolutePath().normalize()), eq(expectedOut),
					argThat(paramsArg -> paramsArg.getOutType()==OutputType.EncyclopeDIA&&paramsArg.getMinimumMS1Intensity()==3.0f&&paramsArg.getMinimumMS2Intensity()==1.0f),
					any(ProgressIndicator.class)), times(1));
			
		}
	}
}
