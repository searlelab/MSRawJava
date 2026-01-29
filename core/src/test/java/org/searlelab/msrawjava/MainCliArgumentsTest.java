package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.algorithms.demux.DemuxConfig;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.model.PPMMassTolerance;

import picocli.CommandLine;

class MainCliArgumentsTest {

	@Test
	void toParameters_mapsCommandLineOptions(@TempDir Path tempDir) throws Exception {
		Main.CliArguments args=new Main.CliArguments();
		Path outputDir=tempDir.resolve("out");
		Path logFile=tempDir.resolve("log.txt");

		new CommandLine(args).parseArgs("--format", "mzml", "--output", outputDir.toString(), "--log-file", logFile.toString(), "--min-ms1", "5.5", "--min-ms2",
				"2.5", "--demux", "--demux-k", "9", "--demux-interp", "logquadratic", "--demux-exclude-edges", "--demux-ppm", "12.5", "--discoverDIAFiles",
				"--batch", "--silent", "--no-ansi", "src/test/resources/rawdata/HeLa_16mzst_29to31min.dia");

		ConversionParameters params=args.toParameters();
		assertEquals(OutputType.mzML, params.getOutType());
		assertEquals(outputDir, params.getOutputDirPath());
		assertEquals(logFile, params.getLogFilePath());
		assertEquals(5.5f, params.getMinimumMS1Intensity(), 1e-6f);
		assertEquals(2.5f, params.getMinimumMS2Intensity(), 1e-6f);
		assertTrue(params.isDemultiplex());
		assertTrue(params.isDiscoverDIAFiles());
		assertTrue(params.isBatch());
		assertTrue(params.isSilent());
		assertTrue(params.isNoAnsi());

		assertNotNull(params.getDemuxConfig());
		assertEquals(9, params.getDemuxConfig().getK());
		assertEquals(DemuxConfig.InterpolationMethod.LOG_QUADRATIC, params.getDemuxConfig().getInterpolationMethod());
		assertFalse(params.getDemuxConfig().isIncludeEdgeSubWindows());

		assertTrue(params.getDemuxTolerance() instanceof PPMMassTolerance);
		assertEquals(12.5, ((PPMMassTolerance)params.getDemuxTolerance()).getPpmTolerance(), 1e-9);
	}

	@Test
	void configureLogging_respectsSilentFlag(@TempDir Path tempDir) throws Exception {
		Main.CliArguments args=new Main.CliArguments();
		ConversionParameters params=ConversionParameters.builder().silent(true).logFilePath(tempDir.resolve("log.txt")).build();

		boolean originalStdout=Logger.PRINT_TO_STDOUT;
		boolean originalStderr=Logger.PRINT_TO_STDERR;
		try {
			Method method=Main.CliArguments.class.getDeclaredMethod("configureLogging", ConversionParameters.class);
			method.setAccessible(true);
			method.invoke(args, params);
			assertFalse(Logger.PRINT_TO_STDOUT);
			assertTrue(Logger.PRINT_TO_STDERR);
		} finally {
			Logger.PRINT_TO_STDOUT=originalStdout;
			Logger.PRINT_TO_STDERR=originalStderr;
			Logger.close();
		}
	}

	@Test
	void configureLogging_defaultKeepsConsoleStatusNull() throws Exception {
		Main.CliArguments args=new Main.CliArguments();
		ConversionParameters params=ConversionParameters.builder().silent(false).noAnsi(true).batch(false).build();

		Method method=Main.CliArguments.class.getDeclaredMethod("configureLogging", ConversionParameters.class);
		method.setAccessible(true);
		method.invoke(args, params);

		assertEquals(null, Logger.getConsoleStatus());
	}

	@Test
	void outputFormat_mapsToOutputType() {
		assertEquals(OutputType.EncyclopeDIA, Main.OutputFormat.dia.toOutputType());
		assertEquals(OutputType.mgf, Main.OutputFormat.mgf.toOutputType());
		assertEquals(OutputType.mzML, Main.OutputFormat.mzml.toOutputType());
	}
}
