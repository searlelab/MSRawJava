package org.searlelab.msrawjava.gui.visualization;

import java.util.Locale;

import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;

final class ScanTypeFilterOption {
	enum Kind {
		ALL,
		MS1,
		MS2_RANGE
	}

	final Kind kind;
	final Range range;
	final String label;

	ScanTypeFilterOption(Kind kind, Range range, String label) {
		this.kind=kind;
		this.range=range;
		this.label=label;
	}

	static ScanTypeFilterOption allSpectra() {
		return new ScanTypeFilterOption(Kind.ALL, null, "All spectra");
	}

	static ScanTypeFilterOption ms1() {
		return new ScanTypeFilterOption(Kind.MS1, null, "MS1");
	}

	static ScanTypeFilterOption ms2Range(Range range) {
		String start=String.format(Locale.ROOT, "%.1f", Math.round(range.getStart()*10.0f)/10.0f);
		String stop=String.format(Locale.ROOT, "%.1f", Math.round(range.getStop()*10.0f)/10.0f);
		return new ScanTypeFilterOption(Kind.MS2_RANGE, range, "MS2 "+start+" to "+stop+" m/z");
	}

	boolean includes(ScanSummary summary) {
		if (summary==null) return false;
		switch (kind) {
			case ALL:
				return true;
			case MS1:
				return summary.isPrecursor();
			case MS2_RANGE:
				return !summary.isPrecursor()&&range!=null&&range.contains(summary.getPrecursorMz());
			default:
				return false;
		}
	}

	boolean isAll() {
		return kind==Kind.ALL;
	}

	boolean isMs1() {
		return kind==Kind.MS1;
	}

	@Override
	public String toString() {
		return label;
	}
}
