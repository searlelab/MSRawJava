package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.OutputType;

class MainConversionTest {

	@Test
	void convertKnownFiles_handlesMissingAndDiaInput(@TempDir Path tempDir) throws Exception {
		File missing=tempDir.resolve("missing.raw").toFile();
		ConversionParameters empty=ConversionParameters.builder().addFile(missing).outType(OutputType.mgf).outputDirPath(tempDir).build();
		Main.convertKnownFiles(empty);

		Path dia=Path.of("src", "test", "resources", "rawdata", "HeLa_16mzst_29to31min.dia");
		Assumptions.assumeTrue(Files.exists(dia), "Fixture missing: "+dia);

		ConversionParameters params=ConversionParameters.builder().addFile(dia.toFile()).outType(OutputType.mgf).outputDirPath(tempDir).build();
		Main.convertKnownFiles(params);

		Path expected=tempDir.resolve("HeLa_16mzst_29to31min.mgf");
		assertTrue(Files.exists(expected), "Expected output file at "+expected);
	}

	@Test
	void convertKnownFiles_handlesDiaDemux(@TempDir Path tempDir) throws Exception {
		Path dia=Path.of("src", "test", "resources", "rawdata", "HeLa_16mzst_29to31min.dia");
		Assumptions.assumeTrue(Files.exists(dia), "Fixture missing: "+dia);

		ConversionParameters params=ConversionParameters.builder().addFile(dia.toFile()).outType(OutputType.mgf).outputDirPath(tempDir).demultiplex(true)
				.build();
		Main.convertKnownFiles(params);

		Path expected=tempDir.resolve("HeLa_16mzst_29to31min.demux.mgf");
		assertTrue(Files.exists(expected), "Expected demux output file at "+expected);
	}

	@Test
	void convertKnownFiles_handlesBrukerInput(@TempDir Path tempDir) throws Exception {
		Path ddir=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.isDirectory(ddir), "Fixture missing: "+ddir);

		ConversionParameters params=ConversionParameters.builder().addFile(ddir.toFile()).outType(OutputType.mgf).outputDirPath(tempDir).build();
		Main.convertKnownFiles(params);

		Path expected=tempDir.resolve("dda_test.mgf");
		assertTrue(Files.exists(expected), "Expected Bruker output file at "+expected);
	}

	@Test
	void convertKnownFiles_handlesThermoInput(@TempDir Path tempDir) throws Exception {
		Path raw=Path.of("src", "test", "resources", "rawdata", "Stellar_DDA.raw");
		Assumptions.assumeTrue(Files.isRegularFile(raw), "Fixture missing: "+raw);

		ConversionParameters params=ConversionParameters.builder().addFile(raw.toFile()).outType(OutputType.mgf).outputDirPath(tempDir).build();
		try {
			Main.convertKnownFiles(params);
			Path expected=tempDir.resolve("Stellar_DDA.mgf");
			assertTrue(Files.exists(expected), "Expected Thermo output file at "+expected);
		} catch (Exception e) {
			Throwable root=(e.getCause()!=null)?e.getCause():e;
			assertTrue(root instanceof java.io.IOException||root instanceof java.net.SocketException,
					"Unexpected failure while attempting Thermo conversion: "+root.getClass().getName());
		}
	}

	@Test
	void convertKnownFiles_handlesMzmlInput(@TempDir Path tempDir) throws Exception {
		Path mzml=Path.of("src", "test", "resources", "rawdata", "HeLa_16mzst_29to31min.mzML");
		Assumptions.assumeTrue(Files.isRegularFile(mzml), "Fixture missing: "+mzml);

		ConversionParameters params=ConversionParameters.builder().addFile(mzml.toFile()).outType(OutputType.mgf).outputDirPath(tempDir).discoverMzMLFiles(true)
				.build();
		Main.convertKnownFiles(params);

		Path expected=tempDir.resolve("HeLa_16mzst_29to31min.mgf");
		assertTrue(Files.exists(expected), "Expected mzML output file at "+expected);
	}

	@Test
	void convertKnownFiles_handlesMzmlInputWithDemuxSuffix(@TempDir Path tempDir) throws Exception {
		Path mzml=Path.of("src", "test", "resources", "rawdata", "HeLa_16mzst_29to31min.mzML");
		Assumptions.assumeTrue(Files.isRegularFile(mzml), "Fixture missing: "+mzml);

		ConversionParameters params=ConversionParameters.builder().addFile(mzml.toFile()).outType(OutputType.mgf).outputDirPath(tempDir).discoverMzMLFiles(true)
				.demultiplex(true).build();
		Main.convertKnownFiles(params);

		Path expected=tempDir.resolve("HeLa_16mzst_29to31min.demux.mgf");
		assertTrue(Files.exists(expected), "Expected demux mzML output file at "+expected);
	}

	@Test
	void convertKnownFiles_enforcesMzmlNoOverwriteWhenOutputDirNotProvided(@TempDir Path tempDir) throws Exception {
		Path source=Path.of("src", "test", "resources", "rawdata", "HeLa_16mzst_29to31min.mzML");
		Assumptions.assumeTrue(Files.isRegularFile(source), "Fixture missing: "+source);
		Path local=tempDir.resolve("local.mzML");
		Files.copy(source, local);

		ConversionParameters params=ConversionParameters.builder().addFile(local.toFile()).outType(OutputType.mzML).discoverMzMLFiles(true).build();
		Main.convertKnownFiles(params);

		Path expected=tempDir.resolve("local.2.mzML");
		assertTrue(Files.exists(expected), "Expected no-overwrite mzML output file at "+expected);
	}

	@Test
	void convertKnownFiles_enforcesDiaNoOverwriteWhenOutputDirNotProvided(@TempDir Path tempDir) throws Exception {
		Path source=Path.of("src", "test", "resources", "rawdata", "HeLa_16mzst_29to31min.dia");
		Assumptions.assumeTrue(Files.isRegularFile(source), "Fixture missing: "+source);
		Path local=tempDir.resolve("local.dia");
		Files.copy(source, local);

		ConversionParameters params=ConversionParameters.builder().addFile(local.toFile()).outType(OutputType.EncyclopeDIA).discoverDIAFiles(true).build();
		Main.convertKnownFiles(params);

		Path expected=tempDir.resolve("local.2.dia");
		assertTrue(Files.exists(expected), "Expected no-overwrite DIA output file at "+expected);
	}

	@Test
	void convertKnownFiles_handlesBrukerInputWithDemuxFlag(@TempDir Path tempDir) throws Exception {
		Path ddir=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.isDirectory(ddir), "Fixture missing: "+ddir);

		ConversionParameters params=ConversionParameters.builder().addFile(ddir.toFile()).outType(OutputType.mgf).outputDirPath(tempDir).demultiplex(true)
				.build();
		Main.convertKnownFiles(params);

		Path expected=tempDir.resolve("dda_test.mgf");
		assertTrue(Files.exists(expected), "Expected Bruker demux-flag output file at "+expected);
	}
}
