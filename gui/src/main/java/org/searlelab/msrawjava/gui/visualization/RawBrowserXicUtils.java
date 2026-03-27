package org.searlelab.msrawjava.gui.visualization;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Utility methods for Raw Browser XIC parsing and extraction math.
 */
final class RawBrowserXicUtils {
	private RawBrowserXicUtils() {
	}

	static String sanitizeXicText(String text) {
		if (text==null||text.isEmpty()) return "";

		String singleLine=text.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
		StringBuilder sb=new StringBuilder(singleLine.length());
		for (int i=0; i<singleLine.length(); i++) {
			char c=singleLine.charAt(i);
			if ((c>='0'&&c<='9')||c=='.'||c==','||Character.isWhitespace(c)) {
				sb.append(Character.isWhitespace(c)?' ':c);
			} else {
				sb.append(' ');
			}
		}

		String sanitized=sb.toString();
		// Collapse comma/space delimiter runs to a canonical single delimiter.
		sanitized=sanitized.replaceAll("(?:\\s*,\\s*)+", ", ");
		sanitized=sanitized.replaceAll(" {2,}", " ");
		return sanitized.strip();
	}

	static String sanitizeXicPasteChunk(String text) {
		if (text==null||text.isEmpty()) return "";
		String singleLine=text.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
		StringBuilder sb=new StringBuilder(singleLine.length());
		for (int i=0; i<singleLine.length(); i++) {
			char c=singleLine.charAt(i);
			if ((c>='0'&&c<='9')||c=='.'||c==','||Character.isWhitespace(c)) {
				sb.append(Character.isWhitespace(c)?' ':c);
			} else {
				sb.append(' ');
			}
		}
		String sanitized=sb.toString();
		sanitized=sanitized.replaceAll("(?:\\s*,\\s*)+", ", ");
		sanitized=sanitized.replaceAll(" {2,}", " ");
		return sanitized;
	}

	static List<Double> parseTargetMzs(String text) {
		String sanitized=sanitizeXicText(text);
		if (sanitized.isBlank()) return List.of();

		LinkedHashSet<Double> unique=new LinkedHashSet<>();
		String[] tokens=sanitized.split("[,\\s]+");
		for (String token : tokens) {
			if (token==null||token.isBlank()) continue;
			try {
				double mz=Double.parseDouble(token);
				if (Double.isFinite(mz)&&mz>0.0) {
					unique.add(mz);
				}
			} catch (NumberFormatException ignored) {
				// ignore invalid pieces
			}
		}
		return new ArrayList<>(unique);
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
}
