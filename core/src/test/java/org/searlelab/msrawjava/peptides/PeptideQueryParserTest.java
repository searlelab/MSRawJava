package org.searlelab.msrawjava.peptides;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class PeptideQueryParserTest {

	private final PeptideQueryParser parser=new PeptideQueryParser();

	@Test
	void parseChargeSuffixes_supportsNumericAndPlusNotation() {
		assertCharge("PEPTIDE", 1);
		assertCharge("PEPTIDE+2", 2);
		assertCharge("PEPTIDE++", 2);
		assertCharge("PEPTIDE+3", 3);
		assertCharge("PEPTIDE+++", 3);
	}

	@Test
	void parsePeptide_rejectsNonCanonicalResidues() {
		assertFalse(parser.parsePeptide("PEPTIDEX+2").isPresent());
		assertFalse(parser.parsePeptide("PEPTIDEU+2").isPresent());
	}

	@Test
	void parsePeptide_parsesNumericMods() {
		Optional<ParsedPeptideQuery> parsed=parser.parsePeptide("PEP[+79.966331]TIDE[-17.026549]+2");
		assertTrue(parsed.isPresent());

		double[] shifts=parsed.get().getResidueMassShifts();
		assertEquals(7, shifts.length);
		assertEquals(79.966331, shifts[2], 1e-9);
		assertEquals(-17.026549, shifts[6], 1e-9);
		assertEquals(2, parsed.get().getModifications().size());
	}

	@Test
	void parsePeptide_parsesNamedSpectronautMods() {
		Optional<ParsedPeptideQuery> parsed=parser.parsePeptide("_LTDC[Carbamidomethyl (C)]VVM[Oxidation (M)]R_");
		assertTrue(parsed.isPresent());
		assertEquals("LTDCVVMR", parsed.get().getSequence());
		assertEquals(1, parsed.get().getPrecursorCharge());

		double[] shifts=parsed.get().getResidueMassShifts();
		assertEquals(57.021464, shifts[3], 1e-9);
		assertEquals(15.994915, shifts[6], 1e-9);
	}

	@Test
	void parsePeptide_acceptsNTermAcetylOnlyAtPeptideStart() {
		Optional<ParsedPeptideQuery> parsed=parser.parsePeptide("[Acetyl (Protein N-term)]PEPTIDE+2");
		assertTrue(parsed.isPresent());
		assertEquals(42.010565, parsed.get().getNTermMassShift(), 1e-9);
		assertFalse(parser.parsePeptide("PEP[Acetyl (Protein N-term)]TIDE+2").isPresent());
	}

	@Test
	void parseToken_supportsNumericAndPeptideTokens() {
		Optional<ParsedQueryToken> numeric=parser.parseToken("445.34");
		assertTrue(numeric.isPresent());
		assertTrue(numeric.get().isNumericMz());
		assertEquals(445.34, numeric.get().getNumericMz(), 1e-12);

		Optional<ParsedQueryToken> peptide=parser.parseToken("PEPTIDE++");
		assertTrue(peptide.isPresent());
		assertTrue(peptide.get().isPeptide());
		assertEquals(2, peptide.get().getPeptideQuery().getPrecursorCharge());
	}

	private void assertCharge(String token, int expectedCharge) {
		Optional<ParsedPeptideQuery> parsed=parser.parsePeptide(token);
		assertTrue(parsed.isPresent());
		assertEquals(expectedCharge, parsed.get().getPrecursorCharge());
	}
}
