package org.searlelab.msrawjava.peptides;

/**
 * Parsed peptide modification with placement context and monoisotopic mass shift.
 */
public final class PeptideModification {
	public enum Position {
		N_TERM,
		RESIDUE
	}

	private final Position position;
	private final int residueIndex;
	private final double massShift;
	private final String annotation;

	private PeptideModification(Position position, int residueIndex, double massShift, String annotation) {
		this.position=position;
		this.residueIndex=residueIndex;
		this.massShift=massShift;
		this.annotation=annotation;
	}

	public static PeptideModification nTerm(double massShift, String annotation) {
		return new PeptideModification(Position.N_TERM, -1, massShift, annotation);
	}

	public static PeptideModification residue(int residueIndex, double massShift, String annotation) {
		if (residueIndex<0) throw new IllegalArgumentException("Residue index must be >= 0");
		return new PeptideModification(Position.RESIDUE, residueIndex, massShift, annotation);
	}

	public Position getPosition() {
		return position;
	}

	public int getResidueIndex() {
		return residueIndex;
	}

	public double getMassShift() {
		return massShift;
	}

	public String getAnnotation() {
		return annotation;
	}
}
