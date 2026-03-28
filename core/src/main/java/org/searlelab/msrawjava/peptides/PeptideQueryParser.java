package org.searlelab.msrawjava.peptides;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;

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

	private record ChargeSplit(String peptideCore, int charge) {
	}

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
		if (peptide.isEmpty()) return Optional.empty();
		return Optional.of(ParsedQueryToken.peptide(trimmed, peptide.get()));
	}

	/**
	 * Parse a token specifically as a peptide query.
	 */
	public Optional<ParsedPeptideQuery> parsePeptide(String token) {
		if (token==null) return Optional.empty();
		String trimmed=token.trim();
		if (trimmed.isEmpty()) return Optional.empty();

		ChargeSplit chargeSplit=parseChargeSuffix(trimmed);
		if (chargeSplit==null) return Optional.empty();

		String peptideText=stripOuterUnderscores(chargeSplit.peptideCore());
		if (peptideText.isEmpty()) return Optional.empty();

		ArrayList<Character> residues=new ArrayList<>();
		ArrayList<Double> residueMassShift=new ArrayList<>();
		ArrayList<PeptideModification> modifications=new ArrayList<>();
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
				// Unknown named modifications are ignored.
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
		ParsedPeptideQuery parsed=new ParsedPeptideQuery(trimmed, sequence.toString(), chargeSplit.charge(), nTermMassShift, residueShifts, modifications);
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

	private ChargeSplit parseChargeSuffix(String token) {
		int depth=0;
		int suffixStart=token.length();
		for (int i=token.length()-1; i>=0; i--) {
			char c=token.charAt(i);
			if (c==']') {
				depth++;
				continue;
			}
			if (c=='[') {
				depth--;
				if (depth<0) return null;
				continue;
			}
			if (depth==0&&(c=='+'||Character.isDigit(c))) {
				suffixStart=i;
				continue;
			}
			if (depth==0) {
				break;
			}
		}
		if (depth!=0) return null;

		String suffix=token.substring(suffixStart);
		if (suffix.isEmpty()||suffix.charAt(0)!='+') {
			return new ChargeSplit(token, 2);
		}

		int charge;
		if (suffix.matches("\\+[0-9]+")) {
			try {
				charge=Integer.parseInt(suffix.substring(1));
			} catch (NumberFormatException ex) {
				return null;
			}
		} else if (suffix.matches("\\++")) {
			charge=suffix.length();
		} else {
			return null;
		}
		if (charge<=0) return null;

		String peptideCore=token.substring(0, suffixStart).trim();
		if (peptideCore.isEmpty()) return null;
		return new ChargeSplit(peptideCore, charge);
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
