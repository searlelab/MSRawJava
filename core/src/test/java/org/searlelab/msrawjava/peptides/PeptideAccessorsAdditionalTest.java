package org.searlelab.msrawjava.peptides;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PeptideAccessorsAdditionalTest {

	private final PeptideQueryParser parser=new PeptideQueryParser();
	private final PeptideIonGenerator generator=new PeptideIonGenerator();

	@Test
	void generatedTargetsExposeAllMetadataFields() {
		ParsedPeptideQuery peptide=parser.parsePeptide("PEPTIDE++").orElseThrow();
		PeptideIonTarget precursor=generator.generatePrecursorTargets(peptide).get(1);

		assertEquals("PEPTIDE++", precursor.getSourceTokenId());
		assertSame(PeptideIonTarget.IonKind.PRECURSOR, precursor.getIonKind());
		assertEquals(1, precursor.getIsotopeIndex());
		assertEquals(0, precursor.getIonIndex());

		PeptideIonTarget fragment=generator.generateFragmentTargets(peptide).get(0);
		assertSame(PeptideIonTarget.IonKind.B_ION, fragment.getIonKind());
		assertEquals(-1, fragment.getIsotopeIndex());
		assertEquals(1, fragment.getIonIndex());
	}

	@Test
	void modificationFactoriesExposePositionResidueMassAndAnnotation() {
		PeptideModification residue=PeptideModification.residue(3, 57.021464, "Carbamidomethyl (C)");
		assertSame(PeptideModification.Position.RESIDUE, residue.getPosition());
		assertEquals(3, residue.getResidueIndex());
		assertEquals(57.021464, residue.getMassShift(), 1e-12);
		assertEquals("Carbamidomethyl (C)", residue.getAnnotation());

		PeptideModification nTerm=PeptideModification.nTerm(42.010565, "Acetyl (Protein N-term)");
		assertSame(PeptideModification.Position.N_TERM, nTerm.getPosition());
		assertEquals(-1, nTerm.getResidueIndex());
	}

	@Test
	void parserCoversNTermNumericModsAndInvalidNumericMods() {
		ParsedPeptideQuery parsed=parser.parsePeptide("[+42.010565]PEPTIDE++").orElseThrow();
		assertEquals(42.010565, parsed.getNTermMassShift(), 1e-12);
		assertEquals(1, parsed.getModifications().size());

		String overflowingNumericMod="9".repeat(400);
		ParsedPeptideQuery overflowMod=parser.parsePeptide("PEP["+overflowingNumericMod+"]TIDE++").orElseThrow();
		assertEquals(0, overflowMod.getModifications().size());
	}

	@Test
	void generatorAllTargetsCombinesPrecursorsAndFragments() {
		ParsedPeptideQuery peptide=parser.parsePeptide("PEPTIDE++").orElseThrow();
		assertEquals(15, generator.generateAllTargets(peptide).size());
	}

	@Test
	void chargeUtilitiesRejectZeroChargeFormattingAndMz() {
		assertThrows(IllegalArgumentException.class, () -> ChargeParsingUtils.formatChargeShorthand(0));
		assertThrows(IllegalArgumentException.class, () -> ChargeParsingUtils.mzFromNeutralMass(100.0, 0));
	}
}
