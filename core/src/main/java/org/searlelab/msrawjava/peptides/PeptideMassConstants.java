package org.searlelab.msrawjava.peptides;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Monoisotopic peptide mass constants used for query parsing and ion generation.
 */
public final class PeptideMassConstants {
	public static final double PROTON_MASS=1.007276466812;
	public static final double WATER_MASS=18.0105646837;
	public static final double ISOTOPE_DELTA=1.003354835336;

	private static final Map<Character, Double> RESIDUE_MONOISOTOPIC_MASS;
	static {
		LinkedHashMap<Character, Double> masses=new LinkedHashMap<>();
		masses.put('A', 71.037113805);
		masses.put('R', 156.101111050);
		masses.put('N', 114.042927470);
		masses.put('D', 115.026943065);
		masses.put('C', 103.009184505);
		masses.put('E', 129.042593135);
		masses.put('Q', 128.058577540);
		masses.put('G', 57.021463735);
		masses.put('H', 137.058911875);
		masses.put('I', 113.084064015);
		masses.put('L', 113.084064015);
		masses.put('K', 128.094963050);
		masses.put('M', 131.040484645);
		masses.put('F', 147.068413945);
		masses.put('P', 97.052763875);
		masses.put('S', 87.032028435);
		masses.put('T', 101.047678505);
		masses.put('W', 186.079312980);
		masses.put('Y', 163.063328575);
		masses.put('V', 99.068413945);
		RESIDUE_MONOISOTOPIC_MASS=Collections.unmodifiableMap(masses);
	}

	private PeptideMassConstants() {
	}

	public static boolean isCanonicalResidue(char residue) {
		return RESIDUE_MONOISOTOPIC_MASS.containsKey(Character.toUpperCase(residue));
	}

	public static double residueMass(char residue) {
		Double mass=RESIDUE_MONOISOTOPIC_MASS.get(Character.toUpperCase(residue));
		if (mass==null) throw new IllegalArgumentException("Unsupported residue: "+residue);
		return mass.doubleValue();
	}

	public static Map<Character, Double> canonicalResidueMasses() {
		return RESIDUE_MONOISOTOPIC_MASS;
	}
}
