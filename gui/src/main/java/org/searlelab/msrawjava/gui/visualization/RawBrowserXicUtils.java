package org.searlelab.msrawjava.gui.visualization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import org.searlelab.msrawjava.peptides.ChargeParsingUtils;
import org.searlelab.msrawjava.peptides.PeptideIonGenerator;
import org.searlelab.msrawjava.peptides.PeptideIonTarget;
import org.searlelab.msrawjava.peptides.PeptideQueryParser;
import org.searlelab.msrawjava.peptides.ParsedQueryToken;

/**
 * Utility methods for Raw Browser XIC parsing and extraction math.
 */
final class RawBrowserXicUtils {

	static final class XicTarget {
		private final double mz;
		private final String label;
		private final String sourceToken;

		XicTarget(double mz, String label, String sourceToken) {
			this.mz=mz;
			this.label=label;
			this.sourceToken=sourceToken;
		}

		double mz() {
			return mz;
		}

		String label() {
			return label;
		}

		String sourceToken() {
			return sourceToken;
		}
	}

	static final class ParsedXicTargets {
		private final List<XicTarget> precursorTargets;
		private final List<XicTarget> fragmentTargets;
		private final List<XicInputDiagnostic> diagnostics;

		private ParsedXicTargets(List<XicTarget> precursorTargets, List<XicTarget> fragmentTargets, List<XicInputDiagnostic> diagnostics) {
			this.precursorTargets=precursorTargets;
			this.fragmentTargets=fragmentTargets;
			this.diagnostics=diagnostics;
		}

		static ParsedXicTargets empty() {
			return new ParsedXicTargets(List.of(), List.of(), List.of());
		}

		List<XicTarget> precursorTargets() {
			return precursorTargets;
		}

		List<XicTarget> fragmentTargets() {
			return fragmentTargets;
		}

		boolean hasAnyTargets() {
			return !precursorTargets.isEmpty()||!fragmentTargets.isEmpty();
		}

		List<XicInputDiagnostic> diagnostics() {
			return diagnostics;
		}

		String diagnosticSummary() {
			if (diagnostics.isEmpty()) return "Enter one or more XIC targets.";
			StringJoiner joiner=new StringJoiner("; ");
			for (XicInputDiagnostic diagnostic : diagnostics) {
				joiner.add(diagnostic.message());
			}
			return joiner.toString();
		}
	}

	static final class XicInputDiagnostic {
		enum Status { ACCEPTED, REJECTED, CAVEAT }

		private final String token;
		private final Status status;
		private final String message;

		XicInputDiagnostic(String token, Status status, String message) {
			this.token=token;
			this.status=status;
			this.message=message;
		}

		String token() { return token; }
		Status status() { return status; }
		String message() { return message; }
	}

	private static final PeptideQueryParser QUERY_PARSER=new PeptideQueryParser();
	private static final PeptideIonGenerator ION_GENERATOR=new PeptideIonGenerator();

	private RawBrowserXicUtils() {
	}

	static final class XicPointExtraction {
		final double intensity;
		final double observedMz;
		final double deltaMz;

		XicPointExtraction(double intensity, double observedMz, double deltaMz) {
			this.intensity=intensity;
			this.observedMz=observedMz;
			this.deltaMz=deltaMz;
		}

		boolean hasSignal() {
			return intensity>0.0&&Double.isFinite(observedMz)&&Double.isFinite(deltaMz);
		}
	}

	static String sanitizeXicText(String text) {
		return sanitizeInternal(text, true);
	}

	static String sanitizeXicPasteChunk(String text) {
		return sanitizeInternal(text, false);
	}

	private static String sanitizeInternal(String text, boolean trimEnds) {
		if (text==null||text.isEmpty()) return "";

		String singleLine=text.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
		StringBuilder sb=new StringBuilder(singleLine.length());
		for (int i=0; i<singleLine.length(); i++) {
			char c=singleLine.charAt(i);
			if (isAllowedXicChar(c)) {
				sb.append(Character.isWhitespace(c)?' ':c);
			} else {
				sb.append(' ');
			}
		}

		String sanitized=sb.toString();
		// Collapse delimiter runs to keep pasted tokens compact and one-line.
		sanitized=sanitized.replaceAll(",+", ",");
		sanitized=sanitized.replaceAll(" {2,}", " ");
		sanitized=sanitized.replaceAll("\\s*,\\s*", ", ");
		return trimEnds?sanitized.strip():sanitized;
	}

	static boolean isAllowedXicChar(char c) {
		if (Character.isLetterOrDigit(c)||Character.isWhitespace(c)) return true;
		return c=='.'||c==','||c=='+'||c=='-'||c=='['||c==']'||c=='_'||c=='('||c==')'||c=='{'||c=='}';
	}

	static List<String> tokenizeQueryTokens(String text) {
		String sanitized=sanitizeXicText(text);
		if (sanitized.isBlank()) return List.of();

		ArrayList<String> tokens=new ArrayList<>();
		StringBuilder current=new StringBuilder();
		int bracketDepth=0;
		for (int i=0; i<sanitized.length(); i++) {
			char c=sanitized.charAt(i);
			if (c=='[') {
				bracketDepth++;
				current.append(c);
				continue;
			}
			if (c==']') {
				if (bracketDepth>0) bracketDepth--;
				current.append(c);
				continue;
			}
			if (bracketDepth==0&&(c==','||Character.isWhitespace(c))) {
				addToken(tokens, current);
				continue;
			}
			current.append(c);
		}
		addToken(tokens, current);
		return tokens;
	}

	private static void addToken(ArrayList<String> tokens, StringBuilder current) {
		if (current.length()<=0) return;
		String token=current.toString().trim();
		if (!token.isEmpty()) tokens.add(token);
		current.setLength(0);
	}

	static ParsedXicTargets parseXicTargets(String text) {
		List<String> tokens=tokenizeQueryTokens(text);
		if (tokens.isEmpty()) return ParsedXicTargets.empty();

		LinkedHashMap<String, XicTarget> precursorTargets=new LinkedHashMap<>();
		LinkedHashMap<String, XicTarget> fragmentTargets=new LinkedHashMap<>();
		ArrayList<XicInputDiagnostic> diagnostics=new ArrayList<>();
		for (String token : tokens) {
			var parsedToken=QUERY_PARSER.parseToken(token);
			if (parsedToken.isEmpty()) {
				diagnostics.add(new XicInputDiagnostic(token, XicInputDiagnostic.Status.REJECTED, "Rejected: "+token));
				continue;
			}
			ParsedQueryToken parsed=parsedToken.get();
			if (parsed.isNumericMz()) {
				double mz=parsed.getNumericMz();
				XicTarget target=new XicTarget(mz, String.format(Locale.ROOT, "XIC %.4f", mz), token);
				addUniqueTarget(precursorTargets, target);
				addUniqueTarget(fragmentTargets, target);
			} else if (parsed.isMolecularFormula()) {
				String label=parsed.getMolecularFormula().toCanonicalFormulaString()+ChargeParsingUtils.formatChargeShorthand(parsed.getMolecularFormula().getCharge());
				XicTarget target=new XicTarget(parsed.getMolecularFormula().getMz(), label, parsed.getOriginalToken());
				addUniqueTarget(precursorTargets, target);
			} else {
				addPeptideTargets(parsed, precursorTargets, fragmentTargets);
			}
			if (parsed.isPeptide()&&!parsed.getPeptideQuery().getIgnoredNamedModifications().isEmpty()) {
				diagnostics.add(new XicInputDiagnostic(token, XicInputDiagnostic.Status.CAVEAT,
						"Caution: "+token+" ignored "+String.join(", ", parsed.getPeptideQuery().getIgnoredNamedModifications())));
			} else {
				diagnostics.add(new XicInputDiagnostic(token, XicInputDiagnostic.Status.ACCEPTED, "Accepted: "+token));
			}
		}
		return new ParsedXicTargets(List.copyOf(precursorTargets.values()), List.copyOf(fragmentTargets.values()), List.copyOf(diagnostics));
	}

	private static void addPeptideTargets(ParsedQueryToken parsed, Map<String, XicTarget> precursorTargets, Map<String, XicTarget> fragmentTargets) {
		for (PeptideIonTarget ion : ION_GENERATOR.generatePrecursorTargets(parsed.getPeptideQuery())) {
			XicTarget target=new XicTarget(ion.getMz(), ion.getLabel(), parsed.getOriginalToken());
			addUniqueTarget(precursorTargets, target);
		}
		for (PeptideIonTarget ion : ION_GENERATOR.generateFragmentTargets(parsed.getPeptideQuery())) {
			XicTarget target=new XicTarget(ion.getMz(), ion.getLabel(), parsed.getOriginalToken());
			addUniqueTarget(fragmentTargets, target);
		}
	}

	private static void addUniqueTarget(Map<String, XicTarget> targetMap, XicTarget target) {
		double mz=target.mz();
		if (!Double.isFinite(mz)||mz<=0.0) return;
		String key=target.label()+"|"+Double.doubleToLongBits(mz);
		targetMap.putIfAbsent(key, target);
	}

	static double sumIntensityWithinTolerance(double[] mz, float[] intensity, double targetMz, double toleranceMz) {
		if (mz==null||intensity==null||mz.length==0||intensity.length==0) return 0.0;
		int count=Math.min(mz.length, intensity.length);
		double minMz=targetMz-toleranceMz;
		double maxMz=targetMz+toleranceMz;
		double sum=0.0;
		for (int i=0; i<count; i++) {
			double mass=mz[i];
			float ion=intensity[i];
			if (!Double.isFinite(mass)||!Float.isFinite(ion)) continue;
			if (mass>=minMz&&mass<=maxMz) {
				sum+=ion;
			}
		}
		return sum;
	}

	static XicPointExtraction extractWeightedPointWithinTolerance(double[] mz, float[] intensity, double targetMz, double toleranceMz) {
		if (mz==null||intensity==null||mz.length==0||intensity.length==0) return new XicPointExtraction(0.0, Double.NaN, Double.NaN);
		int count=Math.min(mz.length, intensity.length);
		double minMz=targetMz-toleranceMz;
		double maxMz=targetMz+toleranceMz;
		double sum=0.0;
		double weightedMzSum=0.0;
		for (int i=0; i<count; i++) {
			double mass=mz[i];
			float ion=intensity[i];
			if (!Double.isFinite(mass)||!Float.isFinite(ion)) continue;
			if (mass>=minMz&&mass<=maxMz) {
				sum+=ion;
				weightedMzSum+=mass*ion;
			}
		}
		if (!(sum>0.0)) return new XicPointExtraction(0.0, Double.NaN, Double.NaN);
		double observedMz=weightedMzSum/sum;
		return new XicPointExtraction(sum, observedMz, observedMz-targetMz);
	}

	static double deltaForDisplay(double deltaMz, double targetMz, XicToleranceOption toleranceOption) {
		if (!Double.isFinite(deltaMz)) return Double.NaN;
		XicToleranceOption tolerance=toleranceOption==null?XicToleranceOption.DEFAULT:toleranceOption;
		if (tolerance.unit==XicToleranceOption.Unit.PPM) return deltaMz/targetMz*1_000_000.0;
		return deltaMz;
	}

	static boolean isTargetInScanWindow(double targetMz, double scanWindowLower, double scanWindowUpper) {
		if (!Double.isFinite(targetMz)||targetMz<=0.0) return false;
		if (!Double.isFinite(scanWindowLower)||!Double.isFinite(scanWindowUpper)) return true;
		if (scanWindowUpper<scanWindowLower) return true;
		return targetMz>=scanWindowLower&&targetMz<=scanWindowUpper;
	}

	static boolean isTargetExtractableFromScan(double targetMz, boolean precursorScan, double scanWindowLower, double scanWindowUpper,
			double isolationWindowLower, double isolationWindowUpper) {
		if (!Double.isFinite(targetMz)||targetMz<=0.0) return false;
		if (precursorScan) return true;
		if (!hasUsableScanWindow(scanWindowLower, scanWindowUpper)) return true;
		if (!precursorScan&&sameWindow(scanWindowLower, scanWindowUpper, isolationWindowLower, isolationWindowUpper)) return true;
		return isTargetInScanWindow(targetMz, scanWindowLower, scanWindowUpper);
	}

	private static boolean hasUsableScanWindow(double scanWindowLower, double scanWindowUpper) {
		if (!Double.isFinite(scanWindowLower)||!Double.isFinite(scanWindowUpper)) return false;
		if (scanWindowUpper<scanWindowLower) return false;
		return scanWindowUpper>scanWindowLower&&scanWindowUpper>0.0;
	}

	private static boolean sameWindow(double lowerA, double upperA, double lowerB, double upperB) {
		if (!Double.isFinite(lowerB)||!Double.isFinite(upperB)) return false;
		return Math.abs(lowerA-lowerB)<1e-6&&Math.abs(upperA-upperB)<1e-6;
	}
}
