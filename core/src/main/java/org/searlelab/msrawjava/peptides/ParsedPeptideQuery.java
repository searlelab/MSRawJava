package org.searlelab.msrawjava.peptides;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed peptide token with canonical sequence, precursor charge, and attached mass shifts.
 */
public final class ParsedPeptideQuery {
	private final String originalToken;
	private final String sequence;
	private final int precursorCharge;
	private final double nTermMassShift;
	private final double[] residueMassShifts;
	private final List<PeptideModification> modifications;

	public ParsedPeptideQuery(String originalToken, String sequence, int precursorCharge, double nTermMassShift, double[] residueMassShifts,
			List<PeptideModification> modifications) {
		this.originalToken=originalToken;
		this.sequence=sequence;
		this.precursorCharge=precursorCharge;
		this.nTermMassShift=nTermMassShift;
		this.residueMassShifts=(residueMassShifts==null)?new double[0]:residueMassShifts.clone();
		if (modifications==null||modifications.isEmpty()) {
			this.modifications=List.of();
		} else {
			this.modifications=Collections.unmodifiableList(new ArrayList<>(modifications));
		}
	}

	public String getOriginalToken() {
		return originalToken;
	}

	public String getSequence() {
		return sequence;
	}

	public int getPrecursorCharge() {
		return precursorCharge;
	}

	public double getNTermMassShift() {
		return nTermMassShift;
	}

	public int length() {
		return sequence.length();
	}

	public char residueAt(int index) {
		return sequence.charAt(index);
	}

	public double[] getResidueMassShifts() {
		return residueMassShifts.clone();
	}

	public double getResidueMassShift(int index) {
		if (index<0||index>=residueMassShifts.length) return 0.0;
		return residueMassShifts[index];
	}

	public List<PeptideModification> getModifications() {
		return modifications;
	}
}
