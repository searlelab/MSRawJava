package org.searlelab.msrawjava.peptides;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PeptideIonGeneratorTest {

	private final PeptideQueryParser parser=new PeptideQueryParser();
	private final PeptideIonGenerator generator=new PeptideIonGenerator();

	@Test
	void generatePrecursorTargets_includesMonoAndTwoIsotopes() {
		ParsedPeptideQuery peptide=parser.parsePeptide("PEPTIDE+2").orElseThrow();
		List<PeptideIonTarget> targets=generator.generatePrecursorTargets(peptide);
		assertEquals(3, targets.size());

		double neutral=computeNeutralMass(peptide);
		double expectedM=(neutral+(2*PeptideMassConstants.PROTON_MASS))/2.0;
		double expectedM1=((neutral+PeptideMassConstants.ISOTOPE_DELTA)+(2*PeptideMassConstants.PROTON_MASS))/2.0;
		double expectedM2=((neutral+(2*PeptideMassConstants.ISOTOPE_DELTA))+(2*PeptideMassConstants.PROTON_MASS))/2.0;

		assertEquals(expectedM, targets.get(0).getMz(), 1e-9);
		assertEquals(expectedM1, targets.get(1).getMz(), 1e-9);
		assertEquals(expectedM2, targets.get(2).getMz(), 1e-9);
		assertEquals("PEPTIDE++ [M]", targets.get(0).getLabel());
		assertEquals("PEPTIDE++ [M+1]", targets.get(1).getLabel());
		assertEquals("PEPTIDE++ [M+2]", targets.get(2).getLabel());
	}

	@Test
	void generateFragmentTargets_buildsBYLaddersWithChargeBounds() {
		ParsedPeptideQuery peptide=parser.parsePeptide("PEPTIDE+3").orElseThrow();
		List<PeptideIonTarget> fragments=generator.generateFragmentTargets(peptide);
		// length=7 -> 6 cuts, ordered as: b1+..b6+, y1+..y6+, b1++..b6++, y1++..y6++
		assertEquals(24, fragments.size());
		assertTrue(fragments.stream().allMatch(t -> t.getCharge()>=1&&t.getCharge()<=2));
		assertEquals("PEPTIDE+++ b1+", fragments.get(0).getLabel());
		assertEquals("PEPTIDE+++ b6+", fragments.get(5).getLabel());
		assertEquals("PEPTIDE+++ y1+", fragments.get(6).getLabel());
		assertEquals("PEPTIDE+++ y6+", fragments.get(11).getLabel());
		assertEquals("PEPTIDE+++ b1++", fragments.get(12).getLabel());
		assertEquals("PEPTIDE+++ b6++", fragments.get(17).getLabel());
		assertEquals("PEPTIDE+++ y1++", fragments.get(18).getLabel());
		assertEquals("PEPTIDE+++ y6++", fragments.get(23).getLabel());
	}

	@Test
	void generateFragmentTargets_returnsEmptyForChargeOnePrecursors() {
		ParsedPeptideQuery peptide=parser.parsePeptide("PEPTIDE+1").orElseThrow();
		List<PeptideIonTarget> fragments=generator.generateFragmentTargets(peptide);
		assertTrue(fragments.isEmpty());
	}

	@Test
	void generateSignedTargets_supportNegativeChargeLabelsAndMz() {
		ParsedPeptideQuery peptide=parser.parsePeptide("PEPTIDE--").orElseThrow();
		List<PeptideIonTarget> precursors=generator.generatePrecursorTargets(peptide);
		assertEquals("PEPTIDE-- [M]", precursors.get(0).getLabel());

		double neutral=computeNeutralMass(peptide);
		double expected=(neutral-(2*PeptideMassConstants.PROTON_MASS))/2.0;
		assertEquals(expected, precursors.get(0).getMz(), 1e-9);

		List<PeptideIonTarget> fragments=generator.generateFragmentTargets(peptide);
		assertEquals(12, fragments.size());
		assertTrue(fragments.stream().allMatch(t -> t.getCharge()==-1));
		assertEquals("PEPTIDE-- b1-", fragments.get(0).getLabel());
		assertEquals("PEPTIDE-- y6-", fragments.get(11).getLabel());
	}

	private static double computeNeutralMass(ParsedPeptideQuery peptide) {
		double neutral=PeptideMassConstants.WATER_MASS+peptide.getNTermMassShift();
		for (int i=0; i<peptide.length(); i++) {
			neutral+=PeptideMassConstants.residueMass(peptide.residueAt(i));
			neutral+=peptide.getResidueMassShift(i);
		}
		return neutral;
	}
}
