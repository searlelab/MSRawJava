package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
		Main.parseParametersFromCommandline(new String[] {"-h"});
		String out=stdout();
		assertTrue(out.contains("Help (-h):"));
		assertTrue(out.contains("-mgf"));
		assertTrue(out.contains("-mzml"));
		assertTrue(out.contains("-dia"));
	}

	@Test
	void defaultOutputType_isEncyclopeDIA_andCountsStartingPaths() throws Exception {
		Main.parseParametersFromCommandline(new String[] {"input1", "input2"});
		String out=stdout();
		assertTrue(out.contains("Found 2 starting paths, export format: EncyclopeDIA"), "Should report default output type and count of inputs");
	}

	@Test
	void parsesOutputType_mgf_and_countsPaths() throws Exception {
		Main.parseParametersFromCommandline(new String[] {"-mgf", "inA"});
		String out=stdout();
		assertTrue(out.contains("Found 1 starting paths, export format: mgf"));
	}

	@Test
	void parsesOutputType_mzml_and_countsPaths() throws Exception {
		Main.parseParametersFromCommandline(new String[] {"-mzml", "inA", "inB", "inC"});
		String out=stdout();
		assertTrue(out.contains("Found 3 starting paths, export format: mzml"));
	}

	@Test
	void outputDir_missingArgument_reportsError() throws Exception {
		Main.parseParametersFromCommandline(new String[] {"-outputDirPath"});
		String err=stderr()+stdout(); // some loggers print to stdout
		assertTrue(err.toLowerCase().contains("requires a path"), "Should warn that a path is required");
	}

	@Test
	void minMS1Threshold_missingNumber_reportsError() throws Exception {
		Main.parseParametersFromCommandline(new String[] {"-minMS1Threshold"});
		String err=stderr()+stdout();
		assertTrue(err.toLowerCase().contains("requires a number"), "Should warn that a number is required");
	}

	@Test
	void minMS2Threshold_missingNumber_reportsError() throws Exception {
		Main.parseParametersFromCommandline(new String[] {"-minMS2Threshold"});
		String err=stderr()+stdout();
		assertTrue(err.toLowerCase().contains("requires a number"), "Should warn that a number is required");
	}

	@Test
	void combinedFlags_areParsed_andCountReported() throws Exception {
		Main.parseParametersFromCommandline(
				new String[] {"-mzml", "-outputDirPath", "outdir", "-minMS1Threshold", "10.0", "-minMS2Threshold", "5.0", "A", "B"});
		String out=stdout();
		assertTrue(out.contains("Found 2 starting paths, export format: mzml"));
		// Not asserting on thresholds directly (they are used during writing), but absence of errors implies parse success.
		assertFalse((stderr()+out).toLowerCase().contains("requires a"), "No missing-arg errors expected");
	}
}
