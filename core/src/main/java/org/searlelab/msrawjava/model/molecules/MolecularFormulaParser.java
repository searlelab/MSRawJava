package org.searlelab.msrawjava.model.molecules;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.searlelab.msrawjava.peptides.ChargeParsingUtils;

public final class MolecularFormulaParser {

	public Optional<ParsedMolecularFormula> parse(String token) {
		ChargeParsingUtils.ChargeSplit chargeSplit=ChargeParsingUtils.splitTrailingCharge(token, 1);
		if (chargeSplit==null) return Optional.empty();
		String formulaText=chargeSplit.coreText();
		Parser parser=new Parser(formulaText);
		Map<Element, Integer> counts=parser.parseSequence('\0');
		if (counts==null||counts.isEmpty()||!parser.isAtEnd()) return Optional.empty();
		double neutralMass=0.0;
		for (Map.Entry<Element, Integer> entry : counts.entrySet()) {
			neutralMass+=entry.getKey().getWeight()*entry.getValue().intValue();
		}
		return Optional.of(new ParsedMolecularFormula(token.trim(), formulaText, chargeSplit.charge(), counts, neutralMass));
	}

	private static final class Parser {
		private final String text;
		private int index=0;

		private Parser(String text) {
			this.text=text;
		}

		private boolean isAtEnd() {
			return index==text.length();
		}

		private Map<Element, Integer> parseSequence(char expectedClose) {
			EnumMap<Element, Integer> counts=new EnumMap<>(Element.class);
			boolean sawTerm=false;
			while (index<text.length()) {
				char c=text.charAt(index);
				if (isCloseBracket(c)) {
					if (expectedClose=='\0'||c!=expectedClose||!sawTerm) return null;
					index++;
					return counts;
				}
				Map<Element, Integer> term=parseTerm();
				if (term==null||term.isEmpty()) return null;
				sawTerm=true;
				merge(counts, term, 1);
			}
			if (expectedClose!='\0') return null;
			return sawTerm?counts:null;
		}

		private Map<Element, Integer> parseTerm() {
			if (index>=text.length()) return null;
			char c=text.charAt(index);
			if (isOpenBracket(c)) {
				index++;
				Map<Element, Integer> group=parseSequence(matchingClose(c));
				if (group==null) return null;
				int multiplier=parseCount();
				if (multiplier<=0) return null;
				EnumMap<Element, Integer> scaled=new EnumMap<>(Element.class);
				merge(scaled, group, multiplier);
				return scaled;
			}
			if (!Character.isUpperCase(c)) return null;
			StringBuilder symbol=new StringBuilder();
			symbol.append(c);
			index++;
			if (index<text.length()&&Character.isLowerCase(text.charAt(index))) {
				symbol.append(text.charAt(index));
				index++;
			}
			Element element=Element.from(symbol.toString()).orElse(null);
			if (element==null) return null;
			int count=parseCount();
			if (count<=0) return null;
			EnumMap<Element, Integer> single=new EnumMap<>(Element.class);
			single.put(element, Integer.valueOf(count));
			return single;
		}

		private int parseCount() {
			if (index>=text.length()||!Character.isDigit(text.charAt(index))) return 1;
			int start=index;
			while (index<text.length()&&Character.isDigit(text.charAt(index))) {
				index++;
			}
			try {
				int count=Integer.parseInt(text.substring(start, index));
				return (count>0)?count:-1;
			} catch (NumberFormatException ex) {
				return -1;
			}
		}

		private static void merge(EnumMap<Element, Integer> target, Map<Element, Integer> source, int multiplier) {
			for (Map.Entry<Element, Integer> entry : source.entrySet()) {
				int value=entry.getValue().intValue()*multiplier;
				target.merge(entry.getKey(), Integer.valueOf(value), (left, right) -> Integer.valueOf(left.intValue()+right.intValue()));
			}
		}

		private static boolean isOpenBracket(char c) {
			return c=='('||c=='['||c=='{';
		}

		private static boolean isCloseBracket(char c) {
			return c==')'||c==']'||c=='}';
		}

		private static char matchingClose(char c) {
			switch (c) {
				case '(':
					return ')';
				case '[':
					return ']';
				case '{':
					return '}';
				default:
					return '\0';
			}
		}
	}
}
