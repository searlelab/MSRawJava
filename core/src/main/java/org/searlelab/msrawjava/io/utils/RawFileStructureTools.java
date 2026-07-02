package org.searlelab.msrawjava.io.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

public final class RawFileStructureTools {
	public static final String METADATA_DATA_ACQUISITION_TYPE="rawFileStructure.dataAcquisitionType";
	public static final String METADATA_IS_STAGGERED="rawFileStructure.isStaggered";
	public static final String METADATA_PRECURSOR_MARGIN_SIZE="rawFileStructure.precursorMarginSize";

	// sometimes we see rounding errors of 0.01 with the Thermo method editor, 
	// and we don't want to get caught in a floating point rounding issue 
	public static final float WINDOW_BOUNDARY_TOLERANCE=0.02f;
	private static final int MINIMUM_REPEATED_WINDOW_COUNT=3;
	private static final double MAX_MARGIN_MZ=3.0;

	private RawFileStructureTools() {
	}

	public static DataAcquisitionType getDataType(Map<Range, WindowData> ranges) {
		if (ranges==null||ranges.isEmpty()) return DataAcquisitionType.DDA;
		if (ranges.size()==1) return DataAcquisitionType.PRM;
		if (ranges.size()>10000) return DataAcquisitionType.DDA;
		for (WindowData data : ranges.values()) {
			if (data.getNumberOfMSMS()<MINIMUM_REPEATED_WINDOW_COUNT) return DataAcquisitionType.DDA;
		}

		ArrayList<Entry> entries=entries(ranges);
		for (int i=0; i<entries.size(); i++) {
			Entry current=entries.get(i);
			boolean lowerJoined=false;
			boolean upperJoined=false;
			boolean hasCoactiveWindow=false;
			for (int j=0; j<entries.size(); j++) {
				if (i==j) continue;
				Entry other=entries.get(j);
				if (!rtOverlaps(current.data, other.data)) continue;
				hasCoactiveWindow=true;
				if (other.range.getStop()<current.range.getStart()-WINDOW_BOUNDARY_TOLERANCE) {
					continue;
				}
				if (other.range.getStart()>current.range.getStop()+WINDOW_BOUNDARY_TOLERANCE) {
					continue;
				}
				if (other.range.getMiddle()<current.range.getMiddle()&&other.range.getStop()>=current.range.getStart()-WINDOW_BOUNDARY_TOLERANCE) {
					lowerJoined=true;
				}
				if (other.range.getMiddle()>current.range.getMiddle()&&other.range.getStart()<=current.range.getStop()+WINDOW_BOUNDARY_TOLERANCE) {
					upperJoined=true;
				}
			}
			if (hasCoactiveWindow&&!lowerJoined&&!upperJoined) return DataAcquisitionType.PRM;
		}
		return DataAcquisitionType.DIA;
	}

	public static boolean isStaggered(Map<Range, WindowData> ranges) {
		if (ranges==null||ranges.size()<3) return false;
		if (getDataType(ranges)!=DataAcquisitionType.DIA) return false;
		ArrayList<ArrayList<Entry>> groups=timeAlignedGroups(entries(ranges));
		for (ArrayList<Entry> group : groups) {
			if (isStaggeredGroup(group)) return true;
		}
		return false;
	}

	public static Optional<Double> getPrecursorMarginSize(Map<Range, WindowData> ranges) {
		if (ranges==null||ranges.size()<2) return Optional.empty();
		if (getDataType(ranges)!=DataAcquisitionType.DIA) return Optional.empty();
		if (isStaggered(ranges)) return Optional.empty();

		ArrayList<Double> overlaps=new ArrayList<>();
		for (ArrayList<Entry> group : timeAlignedGroups(entries(ranges))) {
			group.sort(Comparator.comparingDouble(e -> e.range.getStart()));
			for (int i=1; i<group.size(); i++) {
				Range previous=group.get(i-1).range;
				Range current=group.get(i).range;
				double overlap=previous.getStop()-current.getStart();
				double step=current.getStart()-previous.getStart();
				if (overlap>WINDOW_BOUNDARY_TOLERANCE&&overlap<=MAX_MARGIN_MZ&&step>overlap+WINDOW_BOUNDARY_TOLERANCE) {
					overlaps.add(overlap);
				} else if (overlap>MAX_MARGIN_MZ||overlap>=step-WINDOW_BOUNDARY_TOLERANCE) {
					return Optional.empty();
				}
			}
		}
		if (overlaps.isEmpty()) return Optional.empty();
		double median=median(overlaps);
		for (double overlap : overlaps) {
			if (Math.abs(overlap-median)>Math.max(0.05, median*0.1)) return Optional.empty();
		}
		return Optional.of(median/2.0);
	}

	public static Map<String, String> structureMetadata(DataAcquisitionType type, boolean staggered, double precursorMarginSize) {
		LinkedHashMap<String, String> metadata=new LinkedHashMap<>();
		metadata.put(METADATA_DATA_ACQUISITION_TYPE, type.name());
		if (type==DataAcquisitionType.DIA) {
			metadata.put(METADATA_IS_STAGGERED, Boolean.toString(staggered));
			metadata.put(METADATA_PRECURSOR_MARGIN_SIZE, Double.toString(precursorMarginSize));
		}
		return metadata;
	}

	public static Map<Range, WindowData> trimRanges(Map<Range, WindowData> ranges, double margin) {
		if (ranges==null||ranges.isEmpty()||margin<=0.0) return ranges;
		LinkedHashMap<Range, WindowData> trimmed=new LinkedHashMap<>();
		for (Map.Entry<Range, WindowData> entry : ranges.entrySet()) {
			Range range=entry.getKey();
			trimmed.put(trimRange(range, margin), entry.getValue());
		}
		return trimmed;
	}

	public static Range trimRange(Range range, double margin) {
		if (range==null||margin<=0.0) return range;
		double lower=range.getStart()+margin;
		double upper=range.getStop()-margin;
		if (lower>upper) {
			double center=range.getMiddle();
			return new Range(center, center);
		}
		return new Range(lower, upper);
	}

	private static boolean isStaggeredGroup(ArrayList<Entry> group) {
		if (group.size()<3) return false;
		group.sort(Comparator.comparingDouble(e -> e.range.getStart()));
		ArrayList<Double> steps=new ArrayList<>();
		ArrayList<Double> overlaps=new ArrayList<>();
		ArrayList<Double> widths=new ArrayList<>();
		for (Entry entry : group) {
			widths.add((double)entry.range.getRange());
		}
		for (int i=1; i<group.size(); i++) {
			Range previous=group.get(i-1).range;
			Range current=group.get(i).range;
			double step=current.getStart()-previous.getStart();
			double overlap=previous.getStop()-current.getStart();
			if (step<=WINDOW_BOUNDARY_TOLERANCE||overlap<=WINDOW_BOUNDARY_TOLERANCE) return false;
			steps.add(step);
			overlaps.add(overlap);
		}
		double step=median(steps);
		double overlap=median(overlaps);
		double width=median(widths);
		if (Math.abs(overlap-width*0.5)>WINDOW_BOUNDARY_TOLERANCE) return false;
		if (Math.abs(overlap-step)>Math.max(0.05, step*0.1)) return false;
		return consistent(steps, step)&&consistent(overlaps, overlap)&&consistent(widths, width);
	}

	private static boolean consistent(ArrayList<Double> values, double target) {
		for (double value : values) {
			if (Math.abs(value-target)>Math.max(0.05, Math.abs(target)*0.1)) return false;
		}
		return true;
	}

	private static ArrayList<ArrayList<Entry>> timeAlignedGroups(ArrayList<Entry> entries) {
		ArrayList<ArrayList<Entry>> groups=new ArrayList<>();
		for (Entry entry : entries) {
			boolean added=false;
			for (ArrayList<Entry> group : groups) {
				if (rtOverlaps(entry.data, group.get(0).data)) {
					group.add(entry);
					added=true;
					break;
				}
			}
			if (!added) {
				ArrayList<Entry> group=new ArrayList<>();
				group.add(entry);
				groups.add(group);
			}
		}
		return groups;
	}

	private static boolean rtOverlaps(WindowData a, WindowData b) {
		if (a.getRtRange().isEmpty()||b.getRtRange().isEmpty()) return true;
		Range ar=a.getRtRange().get();
		Range br=b.getRtRange().get();
		return ar.getStart()<=br.getStop()+WINDOW_BOUNDARY_TOLERANCE&&br.getStart()<=ar.getStop()+WINDOW_BOUNDARY_TOLERANCE;
	}

	private static ArrayList<Entry> entries(Map<Range, WindowData> ranges) {
		ArrayList<Entry> entries=new ArrayList<>();
		for (Map.Entry<Range, WindowData> entry : ranges.entrySet()) {
			entries.add(new Entry(entry.getKey(), entry.getValue()));
		}
		entries.sort(Comparator.comparingDouble(e -> e.range.getStart()));
		return entries;
	}

	private static double median(ArrayList<Double> values) {
		ArrayList<Double> sorted=new ArrayList<>(values);
		sorted.sort(null);
		int middle=sorted.size()/2;
		if (sorted.size()%2==1) return sorted.get(middle);
		return (sorted.get(middle-1)+sorted.get(middle))/2.0;
	}

	private static final class Entry {
		private final Range range;
		private final WindowData data;

		private Entry(Range range, WindowData data) {
			this.range=range;
			this.data=data;
		}
	}
}
