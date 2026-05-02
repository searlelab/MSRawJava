package org.searlelab.msrawjava.peptides;

import java.util.Objects;

/**
 * Shared helpers for parsing and formatting signed charge suffixes in query tokens.
 */
public final class ChargeParsingUtils {

	public static final class ChargeSplit {
		private final String coreText;
		private final int charge;

		public ChargeSplit(String coreText, int charge) {
			this.coreText=coreText;
			this.charge=charge;
		}

		public String coreText() {
			return coreText;
		}

		public int charge() {
			return charge;
		}

		@Override
		public boolean equals(Object obj) {
			if (this==obj) return true;
			if (!(obj instanceof ChargeSplit)) return false;
			ChargeSplit other=(ChargeSplit)obj;
			return charge==other.charge&&Objects.equals(coreText, other.coreText);
		}

		@Override
		public int hashCode() {
			return Objects.hash(coreText, Integer.valueOf(charge));
		}

		@Override
		public String toString() {
			return "ChargeSplit[coreText="+coreText+", charge="+charge+"]";
		}
	}

	private ChargeParsingUtils() {
	}

	public static ChargeSplit splitTrailingCharge(String token, int defaultCharge) {
		if (token==null) return null;
		String trimmed=token.trim();
		if (trimmed.isEmpty()) return null;
		if (!hasBalancedBracketPairs(trimmed)) return null;
		int suffixStart=findChargeSuffixStart(trimmed);
		if (suffixStart<0) return null;

		String suffix=trimmed.substring(suffixStart);
		if (suffix.isEmpty()||!startsWithChargeSign(suffix)) return new ChargeSplit(trimmed, defaultCharge);

		Integer charge=parseChargeSuffix(suffix);
		if (charge==null) return null;

		String core=trimmed.substring(0, suffixStart).trim();
		if (core.isEmpty()) return null;
		return new ChargeSplit(core, charge.intValue());
	}

	public static String formatChargeShorthand(int charge) {
		if (charge==0) throw new IllegalArgumentException("Charge cannot be zero");
		if (charge>0) return "+".repeat(charge);
		return "-".repeat(-charge);
	}

	public static double mzFromNeutralMass(double neutralMass, int charge) {
		if (charge==0) throw new IllegalArgumentException("Charge cannot be zero");
		int absCharge=Math.abs(charge);
		double signedProtons=(charge>0)?absCharge*PeptideMassConstants.PROTON_MASS:-absCharge*PeptideMassConstants.PROTON_MASS;
		return (neutralMass+signedProtons)/absCharge;
	}

	private static int findChargeSuffixStart(String token) {
		if (token.isEmpty()) return 0;
		int last=token.length()-1;
		char end=token.charAt(last);
		if (end=='+'||end=='-') {
			int start=last;
			while (start>0&&token.charAt(start-1)==end) {
				start--;
			}
			return start;
		}
		if (Character.isDigit(end)) {
			int digitStart=last;
			while (digitStart>0&&Character.isDigit(token.charAt(digitStart-1))) {
				digitStart--;
			}
			if (digitStart>0) {
				char sign=token.charAt(digitStart-1);
				if (sign=='+'||sign=='-') return digitStart-1;
			}
		}
		return token.length();
	}

	private static Integer parseChargeSuffix(String suffix) {
		if (suffix==null||suffix.isEmpty()) return null;
		char sign=suffix.charAt(0);
		if (sign!='+'&&sign!='-') return null;
		if (suffix.length()==1) return Integer.valueOf(sign=='+'?1:-1);
		if (suffix.chars().allMatch(c -> c==sign)) {
			return Integer.valueOf(sign=='+'?suffix.length():-suffix.length());
		}
		String magnitudeText=suffix.substring(1);
		if (!magnitudeText.matches("[0-9]+")) return null;
		try {
			int magnitude=Integer.parseInt(magnitudeText);
			if (magnitude<=0) return null;
			return Integer.valueOf(sign=='+'?magnitude:-magnitude);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static boolean startsWithChargeSign(String suffix) {
		char first=suffix.charAt(0);
		return first=='+'||first=='-';
	}

	private static boolean hasBalancedBracketPairs(String text) {
		int square=0;
		int round=0;
		int curly=0;
		for (int i=0; i<text.length(); i++) {
			char c=text.charAt(i);
			switch (c) {
				case '[':
					square++;
					break;
				case ']':
					square--;
					if (square<0) return false;
					break;
				case '(':
					round++;
					break;
				case ')':
					round--;
					if (round<0) return false;
					break;
				case '{':
					curly++;
					break;
				case '}':
					curly--;
					if (curly<0) return false;
					break;
				default:
					break;
			}
		}
		return square==0&&round==0&&curly==0;
	}
}
