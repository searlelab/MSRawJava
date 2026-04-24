package org.searlelab.msrawjava.model.molecules;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

import org.searlelab.msrawjava.peptides.ChargeParsingUtils;

public final class ParsedMolecularFormula {
	private final String originalToken;
	private final String formulaText;
	private final int charge;
	private final Map<Element, Integer> elementCounts;
	private final double neutralMass;

	public ParsedMolecularFormula(String originalToken, String formulaText, int charge, Map<Element, Integer> elementCounts, double neutralMass) {
		if (charge==0) throw new IllegalArgumentException("Charge cannot be zero");
		this.originalToken=originalToken;
		this.formulaText=formulaText;
		this.charge=charge;
		EnumMap<Element, Integer> copy=new EnumMap<>(Element.class);
		if (elementCounts!=null) {
			for (Map.Entry<Element, Integer> entry : elementCounts.entrySet()) {
				if (entry.getKey()==null) continue;
				int count=(entry.getValue()==null)?0:entry.getValue().intValue();
				if (count>0) copy.put(entry.getKey(), Integer.valueOf(count));
			}
		}
		this.elementCounts=Collections.unmodifiableMap(copy);
		this.neutralMass=neutralMass;
	}

	public String getOriginalToken() {
		return originalToken;
	}

	public String getFormulaText() {
		return formulaText;
	}

	public int getCharge() {
		return charge;
	}

	public Map<Element, Integer> getElementCounts() {
		return elementCounts;
	}

	public double getNeutralMass() {
		return neutralMass;
	}

	public int getElementCount(Element element) {
		if (element==null) return 0;
		return elementCounts.getOrDefault(element, Integer.valueOf(0)).intValue();
	}

	public double getMz() {
		return ChargeParsingUtils.mzFromNeutralMass(neutralMass, charge);
	}

	public String toCanonicalFormulaString() {
		StringBuilder sb=new StringBuilder();
		boolean hasCarbon=elementCounts.containsKey(Element.C);
		if (hasCarbon) {
			appendElement(sb, Element.C);
			appendElement(sb, Element.H);
		}
		TreeMap<String, Element> ordered=new TreeMap<>();
		for (Element element : elementCounts.keySet()) {
			if (hasCarbon&&(element==Element.C||element==Element.H)) continue;
			ordered.put(element.name(), element);
		}
		for (Element element : ordered.values()) {
			appendElement(sb, element);
		}
		return sb.toString();
	}

	private void appendElement(StringBuilder sb, Element element) {
		Integer count=elementCounts.get(element);
		if (count==null||count.intValue()<=0) return;
		sb.append(element.name());
		if (count.intValue()!=1) sb.append(count.intValue());
	}
}
