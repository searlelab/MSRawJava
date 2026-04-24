package org.searlelab.msrawjava.peptides;

import org.searlelab.msrawjava.model.molecules.ParsedMolecularFormula;

/**
 * Parsed XIC query token. Exactly one of numeric m/z, peptide query, or molecular formula is populated.
 */
public final class ParsedQueryToken {
	private final String originalToken;
	private final Double numericMz;
	private final ParsedPeptideQuery peptideQuery;
	private final ParsedMolecularFormula molecularFormula;

	private ParsedQueryToken(String originalToken, Double numericMz, ParsedPeptideQuery peptideQuery, ParsedMolecularFormula molecularFormula) {
		this.originalToken=originalToken;
		this.numericMz=numericMz;
		this.peptideQuery=peptideQuery;
		this.molecularFormula=molecularFormula;
	}

	public static ParsedQueryToken numeric(String originalToken, double mz) {
		return new ParsedQueryToken(originalToken, Double.valueOf(mz), null, null);
	}

	public static ParsedQueryToken peptide(String originalToken, ParsedPeptideQuery peptideQuery) {
		if (peptideQuery==null) throw new IllegalArgumentException("Peptide query cannot be null");
		return new ParsedQueryToken(originalToken, null, peptideQuery, null);
	}

	public static ParsedQueryToken molecularFormula(String originalToken, ParsedMolecularFormula molecularFormula) {
		if (molecularFormula==null) throw new IllegalArgumentException("Molecular formula cannot be null");
		return new ParsedQueryToken(originalToken, null, null, molecularFormula);
	}

	public String getOriginalToken() {
		return originalToken;
	}

	public boolean isNumericMz() {
		return numericMz!=null;
	}

	public double getNumericMz() {
		if (numericMz==null) throw new IllegalStateException("Not a numeric query");
		return numericMz.doubleValue();
	}

	public boolean isPeptide() {
		return peptideQuery!=null;
	}

	public ParsedPeptideQuery getPeptideQuery() {
		if (peptideQuery==null) throw new IllegalStateException("Not a peptide query");
		return peptideQuery;
	}

	public boolean isMolecularFormula() {
		return molecularFormula!=null;
	}

	public ParsedMolecularFormula getMolecularFormula() {
		if (molecularFormula==null) throw new IllegalStateException("Not a molecular formula query");
		return molecularFormula;
	}
}
