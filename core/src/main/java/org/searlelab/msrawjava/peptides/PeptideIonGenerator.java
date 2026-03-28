package org.searlelab.msrawjava.peptides;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates precursor isotope and b/y fragment ion targets for parsed peptide queries.
 */
public final class PeptideIonGenerator {

	public List<PeptideIonTarget> generatePrecursorTargets(ParsedPeptideQuery peptide) {
		if (peptide==null||peptide.length()<=0||peptide.getPrecursorCharge()<=0) return List.of();
		double neutralMass=computePeptideNeutralMass(peptide);
		int charge=peptide.getPrecursorCharge();
		String baseLabel=peptide.getSequence()+"+"+charge;

		ArrayList<PeptideIonTarget> targets=new ArrayList<>(3);
		for (int isotope=0; isotope<=2; isotope++) {
			double isotopeNeutral=neutralMass+(isotope*PeptideMassConstants.ISOTOPE_DELTA);
			double mz=(isotopeNeutral+(charge*PeptideMassConstants.PROTON_MASS))/charge;
			String isotopeLabel=(isotope==0)?"M":"M+"+isotope;
			String label=baseLabel+" ["+isotopeLabel+"]";
			targets.add(new PeptideIonTarget(mz, label, peptide.getOriginalToken(), PeptideIonTarget.IonKind.PRECURSOR, isotope, 0, charge));
		}
		return targets;
	}

	public List<PeptideIonTarget> generateFragmentTargets(ParsedPeptideQuery peptide) {
		if (peptide==null||peptide.length()<2) return List.of();
		int precursorCharge=peptide.getPrecursorCharge();
		int fragmentChargeMax=Math.min(2, precursorCharge-1);
		if (fragmentChargeMax<1) return List.of();

		int length=peptide.length();
		int cutCount=length-1;
		double[] residueNeutralMasses=new double[length];
		double totalResidueNeutral=0.0;
		for (int i=0; i<length; i++) {
			double mass=PeptideMassConstants.residueMass(peptide.residueAt(i))+peptide.getResidueMassShift(i);
			residueNeutralMasses[i]=mass;
			totalResidueNeutral+=mass;
		}

		double[] bNeutralByIndex=new double[cutCount+1];
		double[] yNeutralByIndex=new double[cutCount+1];
		double prefixNeutral=0.0;
		for (int cut=1; cut<=cutCount; cut++) {
			prefixNeutral+=residueNeutralMasses[cut-1];
			double suffixNeutral=totalResidueNeutral-prefixNeutral;
			int yIndex=length-cut;
			bNeutralByIndex[cut]=prefixNeutral+peptide.getNTermMassShift();
			yNeutralByIndex[yIndex]=suffixNeutral+PeptideMassConstants.WATER_MASS;
		}

		ArrayList<PeptideIonTarget> targets=new ArrayList<>(cutCount*2*fragmentChargeMax);
		String prefix=peptide.getSequence()+"+"+precursorCharge+" ";
		for (int z=1; z<=fragmentChargeMax; z++) {
			for (int bIndex=1; bIndex<=cutCount; bIndex++) {
				double bNeutral=bNeutralByIndex[bIndex];
				double bMz=(bNeutral+(z*PeptideMassConstants.PROTON_MASS))/z;
				String bLabel=prefix+"b"+bIndex+chargeSuffix(z);
				targets.add(new PeptideIonTarget(bMz, bLabel, peptide.getOriginalToken(), PeptideIonTarget.IonKind.B_ION, -1, bIndex, z));
			}
			for (int yIndex=1; yIndex<=cutCount; yIndex++) {
				double yNeutral=yNeutralByIndex[yIndex];
				double yMz=(yNeutral+(z*PeptideMassConstants.PROTON_MASS))/z;
				String yLabel=prefix+"y"+yIndex+chargeSuffix(z);
				targets.add(new PeptideIonTarget(yMz, yLabel, peptide.getOriginalToken(), PeptideIonTarget.IonKind.Y_ION, -1, yIndex, z));
			}
		}
		return targets;
	}

	public List<PeptideIonTarget> generateAllTargets(ParsedPeptideQuery peptide) {
		ArrayList<PeptideIonTarget> all=new ArrayList<>();
		all.addAll(generatePrecursorTargets(peptide));
		all.addAll(generateFragmentTargets(peptide));
		return all;
	}

	private double computePeptideNeutralMass(ParsedPeptideQuery peptide) {
		double neutral=PeptideMassConstants.WATER_MASS+peptide.getNTermMassShift();
		for (int i=0; i<peptide.length(); i++) {
			neutral+=PeptideMassConstants.residueMass(peptide.residueAt(i));
			neutral+=peptide.getResidueMassShift(i);
		}
		return neutral;
	}

	private static String chargeSuffix(int charge) {
		if (charge<=0) return "";
		return "+".repeat(charge);
	}

}
