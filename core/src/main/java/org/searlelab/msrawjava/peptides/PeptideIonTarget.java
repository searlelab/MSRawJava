package org.searlelab.msrawjava.peptides;

/**
 * Immutable ion target generated from a peptide query token.
 */
public final class PeptideIonTarget {
	public enum IonKind {
		PRECURSOR,
		B_ION,
		Y_ION
	}

	private final double mz;
	private final String label;
	private final String sourceTokenId;
	private final IonKind ionKind;
	private final int isotopeIndex;
	private final int ionIndex;
	private final int charge;

	public PeptideIonTarget(double mz, String label, String sourceTokenId, IonKind ionKind, int isotopeIndex, int ionIndex, int charge) {
		this.mz=mz;
		this.label=label;
		this.sourceTokenId=sourceTokenId;
		this.ionKind=ionKind;
		this.isotopeIndex=isotopeIndex;
		this.ionIndex=ionIndex;
		this.charge=charge;
	}

	public double getMz() {
		return mz;
	}

	public String getLabel() {
		return label;
	}

	public String getSourceTokenId() {
		return sourceTokenId;
	}

	public IonKind getIonKind() {
		return ionKind;
	}

	public int getIsotopeIndex() {
		return isotopeIndex;
	}

	public int getIonIndex() {
		return ionIndex;
	}

	public int getCharge() {
		return charge;
	}
}
