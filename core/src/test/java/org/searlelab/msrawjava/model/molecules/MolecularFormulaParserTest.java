package org.searlelab.msrawjava.model.molecules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.peptides.PeptideMassConstants;

class MolecularFormulaParserTest {

	private final MolecularFormulaParser parser=new MolecularFormulaParser();

	@Test
	void parse_computesSimpleFormulaMass() {
		ParsedMolecularFormula parsed=parser.parse("H2O").orElseThrow();
		assertEquals("H2O", parsed.toCanonicalFormulaString());
		assertEquals((2*Element.H.getWeight())+Element.O.getWeight(), parsed.getNeutralMass(), 1e-9);
		assertEquals(1, parsed.getCharge());
	}

	@Test
	void parse_supportsGroupedAndNestedFormulas() {
		assertFormula("CH3(CH2)4OH", "C5H12O");
		assertFormula("Fe2(SO4)3", "Fe2O12S3");
		assertFormula("K4[ON(SO3)2]2", "K4N2O14S4");
	}

	@Test
	void parse_supportsBracketMultipliersAndSignedCharges() {
		ParsedMolecularFormula neutral=parser.parse("[C2H6SiO]6").orElseThrow();
		assertEquals("C12H36O6Si6", neutral.toCanonicalFormulaString());
		assertEquals(445.120034466812, neutral.getMz(), 1e-9);

		ParsedMolecularFormula doublyPositive=parser.parse("[C2H6SiO]6++").orElseThrow();
		assertEquals(2, doublyPositive.getCharge());

		ParsedMolecularFormula doublyNegative=parser.parse("[C2H6SiO]6--").orElseThrow();
		assertEquals(-2, doublyNegative.getCharge());
		double expected=(neutral.getNeutralMass()-(2*PeptideMassConstants.PROTON_MASS))/2.0;
		assertEquals(expected, doublyNegative.getMz(), 1e-9);
	}

	@Test
	void parse_defaultsFormulaChargeToPositiveOneAndSupportsSingleNegative() {
		ParsedMolecularFormula parsed=parser.parse("C6H12O6-").orElseThrow();
		assertEquals(-1, parsed.getCharge());
		assertEquals("C6H12O6", parsed.toCanonicalFormulaString());
	}

	@Test
	void parse_rejectsInvalidFormulas() {
		assertRejected("Xx2");
		assertRejected("()");
		assertRejected("(CH3");
		assertRejected("H0");
		assertRejected("C6H12O6+0");
		assertRejected("C6H12O6-0");
		assertRejected("C6H12O6+-");
		assertRejected("C6H12O6-+");
		assertRejected("C6H12O6++2");
	}

	private void assertFormula(String input, String canonical) {
		Optional<ParsedMolecularFormula> parsed=parser.parse(input);
		assertTrue(parsed.isPresent());
		assertEquals(canonical, parsed.get().toCanonicalFormulaString());
	}

	private void assertRejected(String input) {
		assertFalse(parser.parse(input).isPresent(), input);
	}
}
