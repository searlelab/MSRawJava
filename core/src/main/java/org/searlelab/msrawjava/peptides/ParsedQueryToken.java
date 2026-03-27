package org.searlelab.msrawjava.peptides;

/**
 * Parsed XIC query token. Exactly one of numeric m/z or peptide query is populated.
 */
public final class ParsedQueryToken {
	private final String originalToken;
	private final Double numericMz;
	private final ParsedPeptideQuery peptideQuery;

	private ParsedQueryToken(String originalToken, Double numericMz, ParsedPeptideQuery peptideQuery) {
		this.originalToken=originalToken;
		this.numericMz=numericMz;
		this.peptideQuery=peptideQuery;
	}

	public static ParsedQueryToken numeric(String originalToken, double mz) {
		return new ParsedQueryToken(originalToken, Double.valueOf(mz), null);
	}

	public static ParsedQueryToken peptide(String originalToken, ParsedPeptideQuery peptideQuery) {
		if (peptideQuery==null) throw new IllegalArgumentException("Peptide query cannot be null");
		return new ParsedQueryToken(originalToken, null, peptideQuery);
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
}
