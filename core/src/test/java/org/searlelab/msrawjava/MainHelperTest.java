package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.MGFOutputFile;
import org.searlelab.msrawjava.io.mzml.MzmlConstants;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.VendorFile;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.logging.LoggingProgressIndicator;

class MainHelperTest {

	@Test
	void stripExtension_handlesDotsAndNoDots() throws Exception {
		Method method=Main.class.getDeclaredMethod("stripExtension", String.class);
		method.setAccessible(true);
		assertEquals("file", method.invoke(null, "file.txt"));
		assertEquals("archive.tar", method.invoke(null, "archive.tar.gz"));
		assertEquals("noext", method.invoke(null, "noext"));
	}

	@Test
	void maybeOverrideOutput_appliesDemuxSuffixes() throws Exception {
		Method method=Main.class.getDeclaredMethod("maybeOverrideOutput", ConversionParameters.class, Path.class, Path.class, VendorFile.class);
		method.setAccessible(true);

		ConversionParameters base=ConversionParameters.builder().outType(OutputType.mzML).demultiplex(true).build();

		Path input=Path.of("/data/run.raw");
		Path output=Path.of("/out");
		ConversionParameters updated=(ConversionParameters)method.invoke(null, base, input, output, VendorFile.THERMO);
		assertNotNull(updated.getOutputFilePathOverride());
		assertEquals(output.resolve("run.demux"+MzmlConstants.MZML_EXTENSION), updated.getOutputFilePathOverride());

		ConversionParameters mgfBase=ConversionParameters.builder().outType(OutputType.mgf).demultiplex(true).build();
		ConversionParameters mgfUpdated=(ConversionParameters)method.invoke(null, mgfBase, input, output, VendorFile.THERMO);
		assertEquals(output.resolve("run.demux"+MGFOutputFile.MGF_EXTENSION), mgfUpdated.getOutputFilePathOverride());

		ConversionParameters diaBase=ConversionParameters.builder().outType(OutputType.EncyclopeDIA).demultiplex(true).build();
		ConversionParameters diaUpdated=(ConversionParameters)method.invoke(null, diaBase, input, output, VendorFile.THERMO);
		assertEquals(output.resolve("run.demux"+EncyclopeDIAFile.DIA_EXTENSION), diaUpdated.getOutputFilePathOverride());
	}

	@Test
	void maybeOverrideOutput_skipsWhenOverrideAlreadySet() throws Exception {
		Method method=Main.class.getDeclaredMethod("maybeOverrideOutput", ConversionParameters.class, Path.class, Path.class, VendorFile.class);
		method.setAccessible(true);

		Path override=Path.of("/out/custom.mzML");
		ConversionParameters base=ConversionParameters.builder().outType(OutputType.mzML).demultiplex(true).outputFilePathOverride(override).build();

		ConversionParameters updated=(ConversionParameters)method.invoke(null, base, Path.of("/data/run.raw"), Path.of("/out"), VendorFile.THERMO);
		assertSame(base, updated);
		assertEquals(override, updated.getOutputFilePathOverride());
	}

	@Test
	void maybeOverrideOutput_enforcesEncyclopediaDiaSuffix() throws Exception {
		Method method=Main.class.getDeclaredMethod("maybeOverrideOutput", ConversionParameters.class, Path.class, Path.class, VendorFile.class);
		method.setAccessible(true);

		ConversionParameters base=ConversionParameters.builder().outType(OutputType.EncyclopeDIA).demultiplex(false).outputDirPath(null).build();

		Path input=Path.of("/data/sample.dia");
		Path output=Path.of("/out");
		ConversionParameters updated=(ConversionParameters)method.invoke(null, base, input, output, VendorFile.ENCYCLOPEDIA);
		assertEquals(output.resolve("sample.2"+EncyclopeDIAFile.DIA_EXTENSION), updated.getOutputFilePathOverride());
	}

	@Test
	void createIndicator_respectsSilentAndBatchModes() throws Exception {
		Method method=Main.class.getDeclaredMethod("createIndicator", ConversionParameters.class);
		method.setAccessible(true);

		ConversionParameters silent=ConversionParameters.builder().silent(true).build();
		LoggingProgressIndicator indicator=(LoggingProgressIndicator)method.invoke(null, silent);
		assertEquals(LoggingProgressIndicator.Mode.SILENT, getMode(indicator));
		indicator.close();

		ConversionParameters batch=ConversionParameters.builder().batch(true).build();
		indicator=(LoggingProgressIndicator)method.invoke(null, batch);
		assertEquals(LoggingProgressIndicator.Mode.BATCH, getMode(indicator));
		indicator.close();

		ConversionParameters normal=ConversionParameters.builder().build();
		indicator=(LoggingProgressIndicator)method.invoke(null, normal);
		assertEquals(LoggingProgressIndicator.Mode.DEFAULT, getMode(indicator));
		indicator.close();
	}

	private static LoggingProgressIndicator.Mode getMode(LoggingProgressIndicator indicator) throws Exception {
		var field=LoggingProgressIndicator.class.getDeclaredField("mode");
		field.setAccessible(true);
		return (LoggingProgressIndicator.Mode)field.get(indicator);
	}
}
