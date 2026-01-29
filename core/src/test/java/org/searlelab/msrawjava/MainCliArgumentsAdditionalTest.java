package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.algorithms.demux.DemuxConfig;
import org.searlelab.msrawjava.algorithms.demux.DemuxConfig.InterpolationMethod;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.model.PPMMassTolerance;

import picocli.CommandLine;

class MainCliArgumentsAdditionalTest {

	@Test
	void parsesDemuxFlagsAndLoggingOptions() throws Exception {
		CommandLine cmd=new CommandLine(new Main.CliArguments());
		cmd.parseArgs("--demux", "--demux-k", "9", "--demux-interp", "logquadratic", "--demux-exclude-edges", "--demux-ppm", "12.5", "--batch", "--silent",
				"--no-ansi", "--log-file", "run.log", "--min-ms1", "7.5", "--min-ms2", "2.5", "input.raw");
		Main.CliArguments args=(Main.CliArguments)cmd.getCommand();
		ConversionParameters params=args.toParameters();

		assertTrue(params.isDemultiplex());
		assertTrue(params.isBatch());
		assertTrue(params.isSilent());
		assertTrue(params.isNoAnsi());
		assertEquals(1, params.getFileList().size());
		assertEquals(OutputType.EncyclopeDIA, params.getOutType());
		assertEquals(7.5f, params.getMinimumMS1Intensity(), 1e-6);
		assertEquals(2.5f, params.getMinimumMS2Intensity(), 1e-6);
		assertEquals(Path.of("run.log"), params.getLogFilePath());

		DemuxConfig config=params.getDemuxConfig();
		assertEquals(9, config.getK());
		assertEquals(InterpolationMethod.LOG_QUADRATIC, config.getInterpolationMethod());
		assertFalse(config.isIncludeEdgeSubWindows());

		PPMMassTolerance tol=(PPMMassTolerance)params.getDemuxTolerance();
		assertEquals(12.5, tol.getPpmTolerance(), 1e-9);
	}

	@Test
	void outputFormatToOutputTypeMappings() {
		assertEquals(OutputType.EncyclopeDIA, Main.OutputFormat.dia.toOutputType());
		assertEquals(OutputType.mgf, Main.OutputFormat.mgf.toOutputType());
		assertEquals(OutputType.mzML, Main.OutputFormat.mzml.toOutputType());
	}

	@Test
	void discoverDiaFlag_isCaptured() throws Exception {
		CommandLine cmd=new CommandLine(new Main.CliArguments());
		cmd.parseArgs("--discoverDIAFiles", "input.dia");
		Main.CliArguments args=(Main.CliArguments)cmd.getCommand();
		ConversionParameters params=args.toParameters();
		assertTrue(params.isDiscoverDIAFiles());
	}
}
