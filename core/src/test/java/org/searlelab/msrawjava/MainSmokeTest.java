package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.ConversionRequest;
import org.searlelab.msrawjava.io.ConversionResult;
import org.searlelab.msrawjava.io.ConversionStatus;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConversion;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

import picocli.CommandLine;

class MainSmokeTest {

	private PrintStream origOut;
	private PrintStream origErr;
	private java.io.ByteArrayOutputStream outBuf;
	private java.io.ByteArrayOutputStream errBuf;

	@BeforeEach
	void setup() {
		origOut=System.out;
		origErr=System.err;
		outBuf=new java.io.ByteArrayOutputStream();
		errBuf=new java.io.ByteArrayOutputStream();
		System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
	}

	@AfterEach
	void tearDown() {
		System.setOut(origOut);
		System.setErr(origErr);
	}

	private String stdout() {
		return outBuf.toString(StandardCharsets.UTF_8);
	}

	private String stderr() {
		return errBuf.toString(StandardCharsets.UTF_8);
	}

	@TempDir
	Path tmp;

	private ConversionParameters params(Path start, OutputType out, Path outDir, float ms1, float ms2) {
		ArrayList<java.io.File> files=new ArrayList<>();
		files.add(start.toFile());
		return ConversionParameters.builder().fileList(files).outType(out).outputDirPath(outDir).minimumMS1Intensity(ms1).minimumMS2Intensity(ms2).build();
	}

	@Test
	void cliThreadsOptionIsStoredOnlyInConversionParameters() {
		Main.CliArguments args=new Main.CliArguments();
		new CommandLine(args).parseArgs("--threads", "7", tmp.toString());

		assertEquals(7, args.toParameters().getProcessingThreads());
	}

	@Test
	void convertKnownFiles_invokesThermoAndTimsWriters_andManagesThermoPool() throws Exception {
		Path start=tmp.resolve("input");
		Files.createDirectories(start);
		Path raw=start.resolve("file.raw");
		Files.writeString(raw, "dummy");
		Path raw2=start.resolve("file2.raw");
		Files.writeString(raw2, "dummy");
		Path ddir=start.resolve("bundle.d");
		Files.createDirectories(ddir);

		Path outDir=tmp.resolve("out");
		Files.createDirectories(outDir);

		ConversionParameters p=params(start, OutputType.mgf, outDir, 2.0f, 1.0f);

		ConversionResult completed=Mockito.mock(ConversionResult.class);
		Mockito.when(completed.getStatus()).thenReturn(ConversionStatus.COMPLETED);
		try (MockedStatic<RawFileConversion> conv=Mockito.mockStatic(RawFileConversion.class);
				MockedStatic<ThermoServerPool> pool=Mockito.mockStatic(ThermoServerPool.class);
				// prevent ThermoRawFile from trying to open a real gRPC connection
				MockedConstruction<ThermoRawFile> ctor=Mockito.mockConstruction(ThermoRawFile.class, (mock, ctx) -> {
					Mockito.doNothing().when(mock).openFile(any(Path.class));
					Mockito.doNothing().when(mock).close();
				})) {

			pool.when(ThermoServerPool::port).thenReturn(12345); // harmless value

			conv.when(() -> RawFileConversion.convert(any(ConversionRequest.class), any(ProcessingThreadPool.class))).thenReturn(completed);

			assertDoesNotThrow(() -> Main.convertKnownFiles(p));

			// Thermo server life-cycle when RAW present
			pool.verify(ThermoServerPool::port, times(1));
			pool.verify(ThermoServerPool::shutdown, times(1));

			conv.verify(() -> RawFileConversion.convert(argThat(request -> request.getOptions().getOutputType()==OutputType.mgf
					&&request.getOutputDirectory().equals(outDir)
					&&request.getInputPath().toString().endsWith(".raw")), any(ProcessingThreadPool.class)), times(2));
			conv.verify(() -> RawFileConversion.convert(argThat(request -> request.getOptions().getOutputType()==OutputType.mgf
					&&request.getOptions().getMinimumMS1Intensity()==2.0f
					&&request.getOptions().getMinimumMS2Intensity()==1.0f
					&&request.getInputPath().equals(ddir.toAbsolutePath().normalize())), any(ProcessingThreadPool.class)), times(1));

		}
	}

	@Test
	void convertKnownFiles_withOnlyTims_skipsThermoPool_andWritesTims() throws Exception {
		Path start=tmp.resolve("onlyd");
		Files.createDirectories(start);
		Path ddir=start.resolve("only.d");
		Files.createDirectories(ddir);

		ConversionParameters p=params(start, OutputType.EncyclopeDIA, null, 3.0f, 1.0f);

		ConversionResult completed=Mockito.mock(ConversionResult.class);
		Mockito.when(completed.getStatus()).thenReturn(ConversionStatus.COMPLETED);
		try (MockedStatic<RawFileConversion> conv=Mockito.mockStatic(RawFileConversion.class);
				MockedStatic<ThermoServerPool> pool=Mockito.mockStatic(ThermoServerPool.class)) {
			conv.when(() -> RawFileConversion.convert(any(ConversionRequest.class), any(ProcessingThreadPool.class))).thenReturn(completed);

			assertDoesNotThrow(() -> Main.convertKnownFiles(p));

			// No RAW files -> Thermo server should not be used
			pool.verify(ThermoServerPool::port, times(0));
			pool.verify(ThermoServerPool::shutdown, times(0));

			Path expectedOut=start; // parent of .d when outputDirPath == null
			conv.verify(() -> RawFileConversion.convert(argThat(request -> request.getInputPath().equals(ddir.toAbsolutePath().normalize())
					&&request.getOutputDirectory()==null
					&&request.getOptions().getOutputType()==OutputType.EncyclopeDIA
					&&request.getOptions().getMinimumMS1Intensity()==3.0f
					&&request.getOptions().getMinimumMS2Intensity()==1.0f), any(ProcessingThreadPool.class)), times(1));

		}
	}

	@Test
	void convertKnownFiles_skipsUnsupportedTsfAndContinuesWithOtherTimsFiles() throws Exception {
		Path start=tmp.resolve("mixed");
		Files.createDirectories(start);
		Path tsfDir=Files.createDirectory(start.resolve("pasef-off.d"));
		Files.writeString(tsfDir.resolve("analysis.tsf"), "tsf metadata placeholder");
		Path tdfDir=Files.createDirectory(start.resolve("pasef-on.d"));

		ConversionParameters p=params(start, OutputType.mgf, null, 3.0f, 1.0f);
		ConversionResult completed=Mockito.mock(ConversionResult.class);
		Mockito.when(completed.getStatus()).thenReturn(ConversionStatus.COMPLETED);
		try (MockedStatic<RawFileConversion> conv=Mockito.mockStatic(RawFileConversion.class);
				MockedStatic<ThermoServerPool> pool=Mockito.mockStatic(ThermoServerPool.class)) {
			conv.when(() -> RawFileConversion.convert(any(ConversionRequest.class), any(ProcessingThreadPool.class))).thenAnswer(invocation -> {
				ConversionRequest request=invocation.getArgument(0);
				if (request.getInputPath().equals(tsfDir.toAbsolutePath().normalize())) throw new BrukerTIMSFile.UnsupportedTsfException(tsfDir);
				return completed;
			});

			assertDoesNotThrow(() -> Main.convertKnownFiles(p));

			conv.verify(() -> RawFileConversion.convert(argThat(request -> request.getInputPath().equals(tsfDir.toAbsolutePath().normalize())),
					any(ProcessingThreadPool.class)), times(1));
			conv.verify(() -> RawFileConversion.convert(argThat(request -> request.getInputPath().equals(tdfDir.toAbsolutePath().normalize())),
					any(ProcessingThreadPool.class)), times(1));
		}

		assertTrue(stderr().contains("PASEF-off / TSF files are not supported"));
		assertFalse(stderr().contains("UnsupportedTsfException"), "CLI must report unsupported TSF without a stack trace");
	}
}
