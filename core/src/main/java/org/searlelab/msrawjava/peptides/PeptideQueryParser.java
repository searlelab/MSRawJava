package org.searlelab.msrawjava.peptides;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;

import org.searlelab.msrawjava.model.molecules.MolecularFormulaParser;
import org.searlelab.msrawjava.model.molecules.ParsedMolecularFormula;

/**
 * Parser for mixed XIC tokens: numeric m/z values and peptide queries with optional mods and charge suffixes.
 */
public final class PeptideQueryParser {
	private static final String MOD_CARBAMIDOMETHYL="Carbamidomethyl (C)";
	private static final String MOD_OXIDATION="Oxidation (M)";
	private static final String MOD_ACETYL_NTERM="Acetyl (Protein N-term)";
	private static final double MASS_CARBAMIDOMETHYL=57.021464;
	private static final double MASS_OXIDATION=15.994915;
	private static final double MASS_ACETYL_NTERM=42.010565;
	private static final MolecularFormulaParser FORMULA_PARSER=new MolecularFormulaParser();

	/**
	 * Parse a single token as either numeric m/z or peptide query.
	 */
	public Optional<ParsedQueryToken> parseToken(String token) {
		if (token==null) return Optional.empty();
		String trimmed=token.trim();
		if (trimmed.isEmpty()) return Optional.empty();

		Double numeric=parseNumericMz(trimmed);
		if (numeric!=null) {
			return Optional.of(ParsedQueryToken.numeric(trimmed, numeric.doubleValue()));
		}

		Optional<ParsedPeptideQuery> peptide=parsePeptide(trimmed);
		if (peptide.isPresent()) return Optional.of(ParsedQueryToken.peptide(trimmed, peptide.get()));

		Optional<ParsedMolecularFormula> formula=FORMULA_PARSER.parse(trimmed);
		if (formula.isEmpty()) return Optional.empty();
		return Optional.of(ParsedQueryToken.molecularFormula(trimmed, formula.get()));
	}

	/**
	 * Parse a token specifically as a peptide query.
	 */
	public Optional<ParsedPeptideQuery> parsePeptide(String token) {
		if (token==null) return Optional.empty();
		String trimmed=token.trim();
		if (trimmed.isEmpty()) return Optional.empty();

		ChargeParsingUtils.ChargeSplit chargeSplit=ChargeParsingUtils.splitTrailingCharge(trimmed, 2);
		if (chargeSplit==null) return Optional.empty();

		String peptideText=stripOuterUnderscores(chargeSplit.coreText());
		if (peptideText.isEmpty()) return Optional.empty();

		ArrayList<Character> residues=new ArrayList<>();
		ArrayList<Double> residueMassShift=new ArrayList<>();
		ArrayList<PeptideModification> modifications=new ArrayList<>();
		ArrayList<String> ignoredNamedModifications=new ArrayList<>();
		double nTermMassShift=0.0;

		for (int i=0; i<peptideText.length(); i++) {
			char current=peptideText.charAt(i);
			if (Character.isLetter(current)) {
				char residue=Character.toUpperCase(current);
				if (!PeptideMassConstants.isCanonicalResidue(residue)) {
					return Optional.empty();
				}
				residues.add(Character.valueOf(residue));
				residueMassShift.add(Double.valueOf(0.0));
				continue;
			}

			if (current=='[') {
				int end=peptideText.indexOf(']', i+1);
				if (end<0) return Optional.empty();
				String rawMod=peptideText.substring(i+1, end).trim();
				i=end;
				if (rawMod.isEmpty()) continue;

				Double numericShift=parseNumericMod(rawMod);
				if (numericShift!=null) {
					if (residues.isEmpty()) {
						nTermMassShift+=numericShift.doubleValue();
						modifications.add(PeptideModification.nTerm(numericShift.doubleValue(), rawMod));
					} else {
						int residueIndex=residues.size()-1;
						double updated=residueMassShift.get(residueIndex).doubleValue()+numericShift.doubleValue();
						residueMassShift.set(residueIndex, Double.valueOf(updated));
						modifications.add(PeptideModification.residue(residueIndex, numericShift.doubleValue(), rawMod));
					}
					continue;
				}

				String normalizedMod=rawMod.toLowerCase(Locale.ROOT);
				if (normalizedMod.equals(MOD_CARBAMIDOMETHYL.toLowerCase(Locale.ROOT))) {
					if (residues.isEmpty()||residues.get(residues.size()-1).charValue()!='C') return Optional.empty();
					int residueIndex=residues.size()-1;
					double updated=residueMassShift.get(residueIndex).doubleValue()+MASS_CARBAMIDOMETHYL;
					residueMassShift.set(residueIndex, Double.valueOf(updated));
					modifications.add(PeptideModification.residue(residueIndex, MASS_CARBAMIDOMETHYL, MOD_CARBAMIDOMETHYL));
					continue;
				}
				if (normalizedMod.equals(MOD_OXIDATION.toLowerCase(Locale.ROOT))) {
					if (residues.isEmpty()||residues.get(residues.size()-1).charValue()!='M') return Optional.empty();
					int residueIndex=residues.size()-1;
					double updated=residueMassShift.get(residueIndex).doubleValue()+MASS_OXIDATION;
					residueMassShift.set(residueIndex, Double.valueOf(updated));
					modifications.add(PeptideModification.residue(residueIndex, MASS_OXIDATION, MOD_OXIDATION));
					continue;
				}
				if (normalizedMod.equals(MOD_ACETYL_NTERM.toLowerCase(Locale.ROOT))) {
					if (!residues.isEmpty()) return Optional.empty();
					nTermMassShift+=MASS_ACETYL_NTERM;
					modifications.add(PeptideModification.nTerm(MASS_ACETYL_NTERM, MOD_ACETYL_NTERM));
					continue;
				}
				// Unknown named modifications are ignored but retained for callers that need to report the caveat.
				ignoredNamedModifications.add(rawMod);
				continue;
			}

			return Optional.empty();
		}

		if (residues.isEmpty()) return Optional.empty();

		StringBuilder sequence=new StringBuilder(residues.size());
		double[] residueShifts=new double[residueMassShift.size()];
		for (int i=0; i<residues.size(); i++) {
			sequence.append(residues.get(i).charValue());
			residueShifts[i]=residueMassShift.get(i).doubleValue();
		}
		ParsedPeptideQuery parsed=new ParsedPeptideQuery(trimmed, sequence.toString(), chargeSplit.charge(), nTermMassShift, residueShifts, modifications,
				ignoredNamedModifications);
		return Optional.of(parsed);
	}

	private Double parseNumericMz(String token) {
		try {
			double mz=Double.parseDouble(token);
			if (Double.isFinite(mz)&&mz>0.0) return Double.valueOf(mz);
		} catch (NumberFormatException ignored) {
		}
		return null;
	}

	private String stripOuterUnderscores(String text) {
		if (text==null) return "";
		String stripped=text.trim();
		if (stripped.startsWith("_")) stripped=stripped.substring(1);
		if (stripped.endsWith("_")) stripped=stripped.substring(0, stripped.length()-1);
		return stripped.trim();
	}

	private Double parseNumericMod(String modText) {
		String trimmed=modText.trim();
		if (!trimmed.matches("[+-]?[0-9]+(?:\\.[0-9]+)?")) return null;
		try {
			double value=Double.parseDouble(trimmed);
			if (Double.isFinite(value)) return Double.valueOf(value);
		} catch (NumberFormatException ignored) {
		}
		return null;
	}
}
