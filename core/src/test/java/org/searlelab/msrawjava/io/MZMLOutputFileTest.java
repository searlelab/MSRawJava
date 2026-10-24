package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.searlelab.msrawjava.io.mzml.InstrumentComponent;
import org.searlelab.msrawjava.io.mzml.MzmlFile;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;

class MZMLOutputFileTest {

	@TempDir
	Path tmp;

	private static PrecursorScan ms1(String name, int index, float rtSeconds, double scanLo, double scanHi, double[] mz, float[] inten, float[] ims) {
		return new PrecursorScan(name, index, rtSeconds, 0, scanLo, scanHi, null, mz, inten, ims);
	}

	private static FragmentScan ms2(String name, int index, float rtSeconds, double isoLo, double isoHi, byte z, double[] mz, float[] inten, float[] ims) {
		return new FragmentScan(name, "prec", index, (isoLo+isoHi)/2.0, rtSeconds, 0, null, isoLo, isoHi, mz, inten, ims, z, 0.0, 3000.0);
	}

	@Test
	void writesMinimalMzML_withOneMS1_andOneMS2() throws Exception {
		MZMLOutputFile writer=new MZMLOutputFile();
		assertEquals(".mzML", writer.getFileExtension());

		writer.openFile();
		writer.setFileName("tiny_run", "/data/tiny_run");

		ArrayList<PrecursorScan> ms1s=new ArrayList<>();
		ms1s.add(ms1("ms1-1", 1, 1.234f, 100.0, 1000.0, new double[] {100.0, 200.0}, new float[] {50.0f, 0.0f}, null));

		ArrayList<FragmentScan> ms2s=new ArrayList<>();
		ms2s.add(ms2("ms2-2", 2, 2.5f, 499.9, 500.1, (byte)2, new double[] {150.0, 250.0}, new float[] {10.0f, 0.0f}, null));

		writer.addSpectra(ms1s, ms2s);

		Path out=tmp.resolve("tiny.mzML");
		writer.saveAsFile(out.toFile());
		writer.close();

		String xml=Files.readString(out, StandardCharsets.UTF_8);

		// Root and spectrum list
		assertTrue(xml.contains("<mzML "), "mzML root element should be present");
		assertFalse(xml.contains("SPECTRUM_LIST_COUNT_PLACEHOLDER"), "placeholder should be replaced with count");
		assertTrue(xml.contains("<spectrumList"), "spectrumList must be present");

		// Two spectrum blocks (ids scan=1 and scan=2)
		assertTrue(xml.contains("id=\"scan=1\""));
		assertTrue(xml.contains("id=\"scan=2\""));

		// MS levels
		assertTrue(xml.contains("name=\"ms level\" value=\"1\""));
		assertTrue(xml.contains("name=\"ms level\" value=\"2\""));

		// MS1 metadata (scan start time unit seconds)
		assertTrue(xml.contains("name=\"scan start time\""));
		assertTrue(xml.contains("unitAccession=\"UO:0000010\""), "scan start time should be expressed in seconds");

		// MS2 precursor info (isolation window + selected ion)
		assertTrue(xml.contains("isolationWindow"), "MS2 should contain isolation window");
		assertTrue(xml.contains("selected ion m/z"), "MS2 should contain selected ion m/z");
		assertTrue(xml.contains("name=\"charge state\" value=\"2\""), "MS2 should include charge state");

		// Binary arrays: m/z and intensity arrays present
		assertTrue(xml.contains("<binaryDataArrayList count=\"2\">"));
		assertTrue(xml.contains("name=\"m/z array\""));
		assertTrue(xml.contains("name=\"intensity array\""));
		assertTrue(xml.contains("name=\"64-bit float\""));
		assertTrue(xml.contains("name=\"32-bit float\""));
	}

	@Test
	void writesIonInjectionTimeInMilliseconds() throws Exception {
		MZMLOutputFile writer=new MZMLOutputFile();
		writer.openFile();
		writer.setFileName("iit_run", "/data/iit_run");

		ArrayList<PrecursorScan> ms1s=new ArrayList<>();
		ms1s.add(new PrecursorScan("ms1-1", 1, 1.0f, 0, 100.0, 1000.0, 0.25f, new double[] {100.0}, new float[] {10.0f}, null));

		ArrayList<FragmentScan> ms2s=new ArrayList<>();
		ms2s.add(
				new FragmentScan("ms2-2", "prec", 2, 500.0, 2.0f, 0, 0.5f, 499.9, 500.1, new double[] {150.0}, new float[] {5.0f}, null, (byte)2, 0.0, 3000.0));

		writer.addSpectra(ms1s, ms2s);

		Path out=tmp.resolve("iit.mzML");
		writer.saveAsFile(out.toFile());
		writer.close();

		String xml=Files.readString(out, StandardCharsets.UTF_8);

		assertTrue(xml.contains("name=\"ion injection time\""), "ion injection time should be present");
		assertTrue(xml.contains("unitAccession=\"UO:0000028\""), "ion injection time should be in milliseconds");
		assertTrue(xml.contains("value=\"250.000\""), "MS1 ion injection time should be 250.000 ms");
		assertTrue(xml.contains("value=\"500.000\""), "MS2 ion injection time should be 500.000 ms");
	}

	@Test
	void writesAsymmetricIsolationWindowOffsetsFromTarget() throws Exception {
		MZMLOutputFile writer=new MZMLOutputFile();
		writer.openFile();
		writer.setFileName("offset_run", "/data/offset_run");

		ArrayList<FragmentScan> ms2s=new ArrayList<>();
		ms2s.add(new FragmentScan("ms2-2", "prec", 2, 784.6066, 2.0f, 0, null, 776.6066, 784.6066, 794.6066, new double[] {150.0},
				new float[] {5.0f}, null, (byte)2, 0.0, 3000.0));

		writer.addSpectra(new ArrayList<>(), ms2s);

		Path out=tmp.resolve("offset.mzML");
		writer.saveAsFile(out.toFile());
		writer.close();

		String xml=Files.readString(out, StandardCharsets.UTF_8);

		assertTrue(xml.contains("accession=\"MS:1000827\" name=\"isolation window target m/z\" value=\"784.606600\""));
		assertTrue(xml.contains("accession=\"MS:1000828\" name=\"isolation window lower offset\" value=\"8.000000\""));
		assertTrue(xml.contains("accession=\"MS:1000829\" name=\"isolation window upper offset\" value=\"10.000000\""));
	}

	@Test
	void handlesNullSourcePathWhenWritingHeader() throws Exception {
		MZMLOutputFile writer=new MZMLOutputFile();
		writer.openFile();
		writer.setFileName("null_source", null);

		ArrayList<PrecursorScan> ms1s=new ArrayList<>();
		ms1s.add(ms1("ms1-1", 1, 1.0f, 100.0, 1000.0, new double[] {100.0}, new float[] {10.0f}, null));

		writer.addSpectra(ms1s, new ArrayList<>());

		Path out=tmp.resolve("null_source.mzML");
		writer.saveAsFile(out.toFile());
		writer.close();

		String xml=Files.readString(out, StandardCharsets.UTF_8);
		assertTrue(xml.contains("<sourceFile "), "sourceFile element should be present");
		assertTrue(xml.contains("location=\"file:///\""), "null source path should default to file:///");
	}

	@Test
	void writesRoundTripMetadataAsUserParams() throws Exception {
		MZMLOutputFile writer=new MZMLOutputFile();
		writer.openFile();
		writer.addMetadata(Map.of("totalPrecursorTIC", "1852649200000", "gradientLength", "1234.5"));
		writer.setFileName("meta_run", "/data/meta_run");

		ArrayList<PrecursorScan> ms1s=new ArrayList<>();
		ms1s.add(ms1("ms1-1", 1, 1.0f, 100.0, 1000.0, new double[] {100.0}, new float[] {10.0f}, null));
		writer.addSpectra(ms1s, new ArrayList<>());

		Path out=tmp.resolve("meta.mzML");
		writer.saveAsFile(out.toFile());
		writer.close();

		String xml=Files.readString(out, StandardCharsets.UTF_8);
		assertTrue(xml.contains("name=\"msrawjava.metadata.totalPrecursorTIC\""), "Expected totalPrecursorTIC metadata userParam");
		assertTrue(xml.contains("value=\"1852649200000\""), "Expected totalPrecursorTIC metadata value");
		assertTrue(xml.contains("name=\"msrawjava.metadata.gradientLength\""), "Expected gradientLength metadata userParam");
	}

	@ParameterizedTest
	@ValueSource(strings = {"instrument.model", "meta.InstrumentName", "instrument.name"})
	void writesStellarCvTermInCommonInstrumentParams(String key) throws Exception {
		Path output=writeInstrumentMetadata(Map.of(key, "  Stellar  ", "instrument.serial_number", "P5"));
		String xml=Files.readString(output);
		String group=xml.substring(xml.indexOf("<referenceableParamGroup id=\"CommonInstrumentParams\">"),
				xml.indexOf("</referenceableParamGroup>"));
		assertTrue(group.contains("accession=\"MS:1003409\" name=\"Stellar\" value=\"\""));
		assertTrue(group.contains("name=\"instrument serial number\" value=\"P5\""));
		assertInstrumentModel(output, "Stellar");
	}

	@Test
	void blankInstrumentNameFallsBackToModel() throws Exception {
		Path output=writeInstrumentMetadata(Map.of("meta.InstrumentName", " ", "instrument.model", "Stellar", "instrument.name", "other"));
		assertTrue(Files.readString(output).contains("accession=\"MS:1003409\" name=\"Stellar\""));
		assertInstrumentModel(output, "Stellar");
	}

	@Test
	void unknownInstrumentModelIsEscapedAndPreservedInCommonParams() throws Exception {
		Path output=writeInstrumentMetadata(Map.of("instrument.model", "Test & \"model\""));
		assertTrue(Files.readString(output).contains("accession=\"MS:1000031\" name=\"instrument model\" value=\"Test &amp; &quot;model&quot;\""));
		assertInstrumentModel(output, "Test & \"model\"");
	}

	@ParameterizedTest
	@CsvSource({"Exploris 480, MS:1003028, Orbitrap Exploris 480", "Orbitrap Astral Zoom, MS:1003442, Orbitrap Astral Zoom",
			"Orbitrap Fusion Lumos, MS:1002732, Orbitrap Fusion Lumos", "Q Exactive HF-X, MS:1002877, Q Exactive HF-X",
			"timsTOF Pro, MS:1003005, timsTOF Pro", "Bruker timsTOF Pro 2, MS:1003230, timsTOF Pro 2",
			"timsTOF SCP, MS:1003231, timsTOF SCP", "timsTOF Ultra 2, MS:1003412, timsTOF Ultra 2"})
	void writesSpecificInstrumentCvTermsAndRoundTripsCanonicalNames(String input, String accession, String name) throws Exception {
		Path output=writeInstrumentMetadata(Map.of("instrument.model", input));
		String xml=Files.readString(output);
		assertTrue(xml.contains("accession=\""+accession+"\" name=\""+name+"\" value=\"\""));
		assertTrue(xml.contains("version=\"4.2.8\""));
		MzmlFile reader=new MzmlFile();
		try {
			reader.openFile(output.toFile());
			var models=reader.getInstrumentConfigurations().keySet();
			assertEquals(1, models.size());
			assertEquals(accession, models.iterator().next().accession);
			assertEquals(name, models.iterator().next().name);
		} finally {
			reader.close();
		}
	}

	@ParameterizedTest
	@CsvSource({"Thermo Scientific, Orbitrap prototype", "Bruker, timsTOF Pro 20"})
	void unknownModelKeepsItsNameEvenWhenVendorIsKnown(String vendor, String name) throws Exception {
		Path output=writeInstrumentMetadata(Map.of("meta.InstrumentVendor", vendor, "instrument.model", name));
		assertTrue(Files.readString(output).contains("accession=\"MS:1000031\" name=\"instrument model\" value=\""+name+"\""));
		assertInstrumentModel(output, name);
	}

	@ParameterizedTest
	@CsvSource({"Thermo Scientific, MS:1000483, Thermo Fisher Scientific instrument model",
			"Bruker, MS:1000122, Bruker Daltonics instrument model"})
	void missingModelFallsBackToVendorCvTerm(String vendor, String accession, String name) throws Exception {
		Path output=writeInstrumentMetadata(Map.of("meta.InstrumentVendor", vendor));
		assertTrue(Files.readString(output).contains("accession=\""+accession+"\" name=\""+name+"\" value=\"\""));
		assertInstrumentModel(output, name);
	}

	@Test
	void missingThermoModelUsesTheSameVendorDetectionForHeaderAndComponents() throws Exception {
		Path output=writeInstrumentMetadata(Map.of("thermo.creation_date", "2024-04-10"));
		assertTrue(Files.readString(output).contains("accession=\"MS:1000483\" name=\"Thermo Fisher Scientific instrument model\""));
		assertInstrumentModel(output, "Thermo Fisher Scientific instrument model");
		assertInstrumentComponent(output, InstrumentComponent.Type.ANALYZER, "MS:1000443", "mass analyzer type");
		assertInstrumentComponent(output, InstrumentComponent.Type.DETECTOR, "MS:1000026", "detector type");
	}

	@ParameterizedTest
	@CsvSource({"Stellar, MS:1000291, linear ion trap, MS:1000116, photomultiplier",
			"Thermo Scientific Stellar, MS:1000291, linear ion trap, MS:1000116, photomultiplier",
			"Exploris 480, MS:1000484, orbitrap, MS:1000624, inductive detector",
			"Q Exactive HF-X, MS:1000484, orbitrap, MS:1000624, inductive detector",
			"timsTOF Pro 2, MS:1000084, time-of-flight, MS:1000114, microchannel plate detector"})
	void writesComponentsWithoutVendorMetadata(String model, String analyzerAccession, String analyzer, String detectorAccession, String detector) throws Exception {
		Path output=writeInstrumentMetadata(Map.of("instrument.model", model));
		assertInstrumentComponent(output, InstrumentComponent.Type.ANALYZER, analyzerAccession, analyzer);
		assertInstrumentComponent(output, InstrumentComponent.Type.DETECTOR, detectorAccession, detector);
	}

	@Test
	void thermoMetadataPreventsGuessingTofForUnknownModels() throws Exception {
		Path output=writeInstrumentMetadata(Map.of("instrument.model", "Future Thermo model", "thermo.creation_date", "2024-04-10"));
		assertInstrumentComponent(output, InstrumentComponent.Type.ANALYZER, "MS:1000443", "mass analyzer type");
		assertInstrumentComponent(output, InstrumentComponent.Type.DETECTOR, "MS:1000026", "detector type");
	}

	@ParameterizedTest
	@ValueSource(strings = {"Astral", "Orbitrap Astral", "Astral Zoom", "Orbitrap Astral Zoom"})
	void astralComponentsIncludeBothAcquisitionPaths(String model) throws Exception {
		Path output=writeInstrumentMetadata(Map.of("instrument.model", model));
		assertTrue(Files.readString(output).contains("<componentList count=\"6\">"));
		MzmlFile reader=new MzmlFile();
		try {
			reader.openFile(output.toFile());
			var components=new ArrayList<>(reader.getInstrumentConfigurations().values());
			assertEquals(List.of("MS:1000073", "MS:1000081", "MS:1000484", "MS:1003379", "MS:1000624", "MS:1000116"),
					components.stream().map(c -> c.accessionId).collect(Collectors.toList()));
			assertEquals(List.of(1, 2, 3, 4, 5, 6), components.stream().map(c -> c.order).collect(Collectors.toList()));
			assertEquals(List.of(InstrumentComponent.Type.SOURCE, InstrumentComponent.Type.ANALYZER, InstrumentComponent.Type.ANALYZER,
					InstrumentComponent.Type.ANALYZER, InstrumentComponent.Type.DETECTOR, InstrumentComponent.Type.DETECTOR),
					components.stream().map(c -> c.type).collect(Collectors.toList()));
		} finally {
			reader.close();
		}
	}

	private void assertInstrumentComponent(Path output, InstrumentComponent.Type type, String accession, String name) throws Exception {
		MzmlFile reader=new MzmlFile();
		try {
			reader.openFile(output.toFile());
			var component=reader.getInstrumentConfigurations().values().stream().filter(c -> c.type==type).findFirst().orElseThrow();
			assertEquals(accession, component.accessionId);
			assertEquals(name, component.name);
		} finally {
			reader.close();
		}
	}

	private Path writeInstrumentMetadata(Map<String, String> metadata) throws Exception {
		Path output=tmp.resolve("instrument.mzML");
		MZMLOutputFile writer=new MZMLOutputFile();
		try {
			writer.openFile();
			writer.addMetadata(metadata);
			writer.setFileName("test.raw", null);
			writer.addSpectra(new ArrayList<>(), new ArrayList<>());
			writer.saveAsFile(output.toFile());
		} finally {
			writer.close();
		}
		return output;
	}

	private void assertInstrumentModel(Path output, String expected) throws Exception {
		MzmlFile reader=new MzmlFile();
		try {
			reader.openFile(output.toFile());
			assertEquals(expected, reader.getInstrumentConfigurations().keySet().iterator().next().name);
		} finally {
			reader.close();
		}
	}
}
