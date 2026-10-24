package org.searlelab.msrawjava.io.mzml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InstrumentModelCvTest {

	@Test
	void bundledCatalogResolvesEveryCanonicalNameAndSynonym() throws Exception {
		var input=InstrumentModelCv.class.getResourceAsStream("instrument-models.tsv");
		assertNotNull(input, "The instrument catalog must be bundled with the library");
		try (var reader=new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			String line;
			while ((line=reader.readLine())!=null) {
				if (line.isBlank()||line.startsWith("#")) continue;
				String[] fields=line.split("\t");
				assertTrue(fields.length>=4, line);
				assertTrue("Thermo".equals(fields[2])||"Bruker".equals(fields[2]), line);
				assertTrue("family".equals(fields[3])||"model".equals(fields[3]), line);
				for (int i=1; i<fields.length; i++) {
					if (i==2||i==3) continue; // Vendor ancestry and term kind are not model aliases.
					var model=InstrumentModelCv.find(fields[i]).orElseThrow();
					assertEquals(fields[0], model.accession, line);
					assertEquals(fields[1], model.name, line);
					assertEquals("Thermo".equals(fields[2]), InstrumentModelCv.isThermo(fields[i]), line);
					assertEquals("family".equals(fields[3]), InstrumentModelCv.isGenericModel(fields[i]), line);
				}
			}
		}
	}

	@ParameterizedTest
	@CsvSource({"Stellar, MS:1003409, Stellar", "Exploris 480, MS:1003028, Orbitrap Exploris 480",
			"Exploris 240, MS:1003094, Orbitrap Exploris 240", "Exploris 120, MS:1003095, Orbitrap Exploris 120",
			"Astral, MS:1003378, Orbitrap Astral", "Astral Zoom, MS:1003442, Orbitrap Astral Zoom",
			"Fusion, MS:1002416, Orbitrap Fusion", "Fusion Lumos, MS:1002732, Orbitrap Fusion Lumos",
			"Eclipse, MS:1003029, Orbitrap Eclipse", "Ascend, MS:1003356, Orbitrap Ascend",
			"Q Exactive, MS:1001911, Q Exactive", "Q Exactive Plus, MS:1002634, Q Exactive Plus",
			"Q Exactive HF, MS:1002523, Q Exactive HF", "Q Exactive HF-X, MS:1002877, Q Exactive HF-X",
			"LTQ Orbitrap XL, MS:1000556, LTQ Orbitrap XL", "LTQ Velos ETD, MS:1000856, LTQ Velos/ETD",
			"LTQ Orbitrap Elite, MS:1001910, Orbitrap Elite", "TSQ Altis Plus, MS:1003292, TSQ Altis Plus",
			"timsTOF, MS:1003229, timsTOF", "timsTOF Pro, MS:1003005, timsTOF Pro", "timsTOF Pro 2, MS:1003230, timsTOF Pro 2",
			"timsTOF SCP, MS:1003231, timsTOF SCP", "timsTOF Ultra, MS:1003383, timsTOF Ultra",
			"timsTOF Ultra 2, MS:1003412, timsTOF Ultra 2", "timsTOF HT, MS:1003404, timsTOF HT",
			"timsTOF fleX, MS:1003124, timsTOF fleX", "timsTOF fleX MALDI-2, MS:1003397, timsTOF fleX MALDI-2"})
	void resolvesDistinctModelsAndExactOboSynonyms(String input, String accession, String name) {
		InstrumentId model=InstrumentModelCv.find(input).orElseThrow();
		assertEquals(accession, model.accession);
		assertEquals(name, model.name);
	}

	@ParameterizedTest
	@ValueSource(strings = {"  ORBITRAP   exploris 480  ", "Thermo Scientific Exploris 480", "Thermo Fisher Scientific Orbitrap Exploris 480",
			"Thermo Electron Orbitrap Exploris 480"})
	void normalizesCaseWhitespaceAndVendorPrefixes(String name) {
		assertEquals("MS:1003028", InstrumentModelCv.find(name).orElseThrow().accession);
	}

	@ParameterizedTest
	@ValueSource(strings = {"Thermo Electron instrument model", "Thermo Scientific instrument model", "Thermo Finnigan instrument model"})
	void vendorParentNamesAreNotShortenedByALaterPrefix(String name) {
		assertEquals(name, InstrumentModelCv.find(name).orElseThrow().name);
		assertTrue(InstrumentModelCv.find(name.substring("Thermo ".length())).isEmpty());
	}

	@Test
	void canonicalNamesTakePrecedenceOverShortAliases() {
		assertEquals("MS:1003495", InstrumentModelCv.find("Velos Pro").orElseThrow().accession);
		assertEquals("MS:1003096", InstrumentModelCv.find("Orbitrap Velos Pro").orElseThrow().accession);
		assertEquals("MS:1003230", InstrumentModelCv.find("Bruker Daltonics timsTOF Pro 2").orElseThrow().accession);
	}

	@ParameterizedTest
	@CsvSource({"Stellar, true", "Exploris 480, true", "Q Exactive HF-X, true", "TSQ Altis Plus, true",
			"Finnigan MAT instrument model, true", "timsTOF Pro 2, false", "Bruker Daltonics instrument model, false", "Future model, false"})
	void identifiesThermoModelsFromOboAncestry(String name, boolean expected) {
		assertEquals(expected, InstrumentModelCv.isThermo(name));
	}

	@Test
	void metadataKeyPriorityIsSharedAndSkipsBlankNames() {
		assertEquals("preferred", InstrumentModelCv.modelName(Map.of("meta.InstrumentName", " preferred ", "instrument.model", "model", "instrument.name", "name")).orElseThrow());
		assertEquals("model", InstrumentModelCv.modelName(Map.of("meta.InstrumentName", " ", "instrument.model", " model ", "instrument.name", "name")).orElseThrow());
		assertEquals("name", InstrumentModelCv.modelName(Map.of("meta.InstrumentName", " ", "instrument.model", "", "instrument.name", " name ")).orElseThrow());
		assertTrue(InstrumentModelCv.modelName(Map.of()).isEmpty());
	}

	@Test
	void thermoMetadataDetectionAcceptsEachAvailableSignal() {
		assertTrue(InstrumentModelCv.isThermoMetadata(Map.of("meta.InstrumentVendor", "Thermo Scientific")));
		assertTrue(InstrumentModelCv.isThermoMetadata(Map.of("instrument.model", "Stellar")));
		assertTrue(InstrumentModelCv.isThermoMetadata(Map.of("meta.InstrumentName", " ", "instrument.name", "Exploris 480")));
		assertTrue(InstrumentModelCv.isThermoMetadata(Map.of("thermo.creation_date", "2024-04-10")));
		assertFalse(InstrumentModelCv.isThermoMetadata(Map.of("meta.InstrumentVendor", "Bruker", "instrument.model", "timsTOF Pro 20")));
		assertFalse(InstrumentModelCv.isThermoMetadata(Map.of()));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "instrument model", "Thermo Electron instrument model", "Bruker Daltonics maXis series", "Bruker Daltonics HCT Series"})
	void recognizesMissingAndFamilyModelNames(String name) {
		assertTrue(InstrumentModelCv.isGenericModel(name));
	}

	@ParameterizedTest
	@ValueSource(strings = {"Stellar", "timsTOF Pro 2", "Future model"})
	void specificAndUnknownModelNamesAreNotGenericPlaceholders(String name) {
		assertFalse(InstrumentModelCv.isGenericModel(name));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "Astral Zoom 2", "timsTOF Pro 20", "Exploris 480 prototype", "new Orbitrap model", "Q Exactive HF-X Plus"})
	void unknownModelsAreNotMatchedBySubstring(String name) {
		assertTrue(InstrumentModelCv.find(name).isEmpty());
	}
}
