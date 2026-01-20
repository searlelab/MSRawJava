package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.OutputType;

import picocli.CommandLine;

class MainTest {

	private PrintStream origOut;
	private PrintStream origErr;
	private ByteArrayOutputStream outBuf;
	private ByteArrayOutputStream errBuf;

	@BeforeEach
	void setup() {
		origOut=System.out;
		origErr=System.err;
		outBuf=new ByteArrayOutputStream();
		errBuf=new ByteArrayOutputStream();
		System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
	}

	@AfterEach
	void teardown() {
		System.setOut(origOut);
		System.setErr(origErr);
	}

	private String stdout() {
		return outBuf.toString(StandardCharsets.UTF_8);
	}

	private String stderr() {
		return errBuf.toString(StandardCharsets.UTF_8);
	}

	@Test
	void helpFlag_printsUsageAndExits() throws Exception {
		CommandLine cmd=new CommandLine(new Main.CliArguments());
		cmd.setOut(new java.io.PrintWriter(System.out, true));
		cmd.execute("-h");
		String out=stdout();
		assertTrue(out.contains("Usage:"), "Should print usage");
		assertTrue(out.contains("--format"), "Should document format flag");
		assertTrue(out.contains("--demux"), "Should document demux flag");
	}

	@Test
	void defaultOutputType_isEncyclopeDIA_andCountsStartingPaths() throws Exception {
		CommandLine cmd=new CommandLine(new Main.CliArguments());
		cmd.parseArgs("input1", "input2");
		Main.CliArguments args=(Main.CliArguments)cmd.getCommand();
		ConversionParameters params=args.toParameters();
		assertEquals(OutputType.EncyclopeDIA, params.getOutType());
		assertEquals(2, params.getFileList().size());
	}

	@Test
	void parsesOutputType_mgf_and_countsPaths() throws Exception {
		CommandLine cmd=new CommandLine(new Main.CliArguments());
		cmd.parseArgs("-f", "mgf", "inA");
		Main.CliArguments args=(Main.CliArguments)cmd.getCommand();
		ConversionParameters params=args.toParameters();
		assertEquals(OutputType.mgf, params.getOutType());
		assertEquals(1, params.getFileList().size());
	}

	@Test
	void parsesOutputType_mzml_and_countsPaths() throws Exception {
		CommandLine cmd=new CommandLine(new Main.CliArguments());
		cmd.parseArgs("-f", "mzml", "inA", "inB", "inC");
		Main.CliArguments args=(Main.CliArguments)cmd.getCommand();
		ConversionParameters params=args.toParameters();
		assertEquals(OutputType.mzml, params.getOutType());
		assertEquals(3, params.getFileList().size());
	}

	@Test
	void outputDir_missingArgument_reportsError() throws Exception {
		CommandLine cmd=new CommandLine(new Main.CliArguments());
		cmd.setErr(new java.io.PrintWriter(System.err, true));
		cmd.execute("-o");
		String err=stderr()+stdout(); // some loggers print to stdout
		assertTrue(err.toLowerCase().contains("missing required"), "Should warn that a path is required");
	}

	@Test
	void minMS1Threshold_missingNumber_reportsError() throws Exception {
		CommandLine cmd=new CommandLine(new Main.CliArguments());
		cmd.setErr(new java.io.PrintWriter(System.err, true));
		cmd.execute("--min-ms1");
		String err=stderr()+stdout();
		assertTrue(err.toLowerCase().contains("missing required"), "Should warn that a number is required");
	}

	@Test
	void minMS2Threshold_missingNumber_reportsError() throws Exception {
		CommandLine cmd=new CommandLine(new Main.CliArguments());
		cmd.setErr(new java.io.PrintWriter(System.err, true));
		cmd.execute("--min-ms2");
		String err=stderr()+stdout();
		assertTrue(err.toLowerCase().contains("missing required"), "Should warn that a number is required");
	}

	@Test
	void combinedFlags_areParsed_andCountReported() throws Exception {
		CommandLine cmd=new CommandLine(new Main.CliArguments());
		cmd.parseArgs("-f", "mzml", "-o", "outdir", "--min-ms1", "10.0", "--min-ms2", "5.0", "A", "B");
		Main.CliArguments args=(Main.CliArguments)cmd.getCommand();
		ConversionParameters params=args.toParameters();
		assertEquals(OutputType.mzml, params.getOutType());
		assertEquals(2, params.getFileList().size());
		assertEquals(10.0f, params.getMinimumMS1Intensity(), 0.0001f);
		assertEquals(5.0f, params.getMinimumMS2Intensity(), 0.0001f);
		assertFalse((stderr()+stdout()).toLowerCase().contains("missing required"), "No missing-arg errors expected");
	}
}
