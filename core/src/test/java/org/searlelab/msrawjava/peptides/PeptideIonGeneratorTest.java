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
		assertEquals("PEPTIDE+2 [M]", targets.get(0).getLabel());
		assertEquals("PEPTIDE+2 [M+1]", targets.get(1).getLabel());
		assertEquals("PEPTIDE+2 [M+2]", targets.get(2).getLabel());
	}

	@Test
	void generateFragmentTargets_buildsBYLaddersWithChargeBounds() {
		ParsedPeptideQuery peptide=parser.parsePeptide("PEPTIDE+3").orElseThrow();
		List<PeptideIonTarget> fragments=generator.generateFragmentTargets(peptide);
		// length=7 -> 6 cuts, b+y for each cut, z=1 and z=2
		assertEquals(24, fragments.size());
		assertTrue(fragments.stream().allMatch(t -> t.getCharge()>=1&&t.getCharge()<=2));
		assertTrue(fragments.stream().anyMatch(t -> t.getLabel().contains(" b1+")));
		assertTrue(fragments.stream().anyMatch(t -> t.getLabel().contains(" y6+")));
		assertTrue(fragments.stream().anyMatch(t -> t.getLabel().contains(" b3++")));
		assertTrue(fragments.stream().anyMatch(t -> t.getLabel().contains(" y4++")));
	}

	@Test
	void generateFragmentTargets_returnsEmptyForChargeOnePrecursors() {
		ParsedPeptideQuery peptide=parser.parsePeptide("PEPTIDE+1").orElseThrow();
		List<PeptideIonTarget> fragments=generator.generateFragmentTargets(peptide);
		assertTrue(fragments.isEmpty());
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
