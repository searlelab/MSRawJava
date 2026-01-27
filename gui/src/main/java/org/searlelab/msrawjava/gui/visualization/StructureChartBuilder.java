package org.searlelab.msrawjava.gui.visualization;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jfree.chart.annotations.XYShapeAnnotation;
import org.searlelab.msrawjava.gui.graphing.BasicChartGenerator;
import org.searlelab.msrawjava.gui.graphing.ExtendedChartPanel;
import org.searlelab.msrawjava.gui.graphing.GraphType;
import org.searlelab.msrawjava.gui.graphing.XYTrace;
import org.searlelab.msrawjava.gui.graphing.XYTraceInterface;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;
import org.searlelab.msrawjava.model.WindowData;

/**
 * Builds structure charts from scan summaries and windows.
 */
public final class StructureChartBuilder {
	private static final Color BASE_COLOR=new Color(0, 0, 200);
	private static final Color ALT_COLOR=new Color(100, 100, 255);

	private StructureChartBuilder() {
	}

	public static ExtendedChartPanel buildLocalStructureChart(Map<Range, WindowData> ranges, List<ScanSummary> summaries) {
		if ((ranges==null||ranges.isEmpty())&&(summaries==null||summaries.isEmpty())) {
			return BasicChartGenerator.getChart("m/z", "Retention Time (secs)", false, new XYTraceInterface[0]);
		}

		HashMap<Range, ArrayList<Float>> stripeRts=new HashMap<>();
		HashMap<Range, ArrayList<Float>> precursorRts=new HashMap<>();
		if (summaries!=null) {
			for (ScanSummary summary : summaries) {
				float rt=summary.getScanStartTime();
				if (summary.isPrecursor()) {
					Range r=new Range((float)summary.getScanWindowLower(), (float)summary.getScanWindowUpper());
					precursorRts.computeIfAbsent(r, k -> new ArrayList<>()).add(rt);
				} else {
					Range r=new Range((float)summary.getIsolationWindowLower(), (float)summary.getIsolationWindowUpper());
					stripeRts.computeIfAbsent(r, k -> new ArrayList<>()).add(rt);
				}
			}
		}

		TreeMap<Range, ArrayList<Float>> sortedStripes=new TreeMap<>(Comparator.naturalOrder());
		sortedStripes.putAll(stripeRts);

		List<XYTraceInterface> traces=new ArrayList<>();
		boolean everyOther=false;
		float firstScan=Float.MAX_VALUE;
		float lastScan=0.0f;

		for (Map.Entry<Range, ArrayList<Float>> entry : sortedStripes.entrySet()) {
			Range range=entry.getKey();
			ArrayList<Float> rts=entry.getValue();
			if (rts.isEmpty()) continue;
			rts.sort(Float::compare);
			float rt0=rts.get(0);
			firstScan=Math.min(firstScan, rt0);
			lastScan=Math.max(lastScan, rt0);
			everyOther=!everyOther;
			Color color=everyOther?BASE_COLOR:ALT_COLOR;

			traces.add(new XYTrace(new double[] {range.getStart(), range.getStop()}, new double[] {rt0, rt0}, GraphType.squaredline, range.toString(), color,
					5.0f));
			if (rts.size()>1) {
				float rt1=rts.get(1);
				firstScan=Math.min(firstScan, rt1);
				lastScan=Math.max(lastScan, rt1);
				traces.add(new XYTrace(new double[] {range.getStart(), range.getStop()}, new double[] {rt1, rt1}, GraphType.squaredline, range.toString(),
						color, 5.0f));
				traces.add(new XYTrace(new double[] {range.getStop(), range.getStop()}, new double[] {rt0, rt1}, GraphType.dashedline, range.toString(),
						Color.gray, 1.0f));
			}
		}

		TreeMap<Range, ArrayList<Float>> sortedPrecursors=new TreeMap<>(Comparator.naturalOrder());
		sortedPrecursors.putAll(precursorRts);
		if (firstScan<Float.MAX_VALUE) {
			float rtRangeMargin=(lastScan-firstScan)*0.2f;
			float minRt=firstScan-rtRangeMargin;
			float maxRt=lastScan+rtRangeMargin;
			for (Map.Entry<Range, ArrayList<Float>> entry : sortedPrecursors.entrySet()) {
				Range range=entry.getKey();
				ArrayList<Float> rts=entry.getValue();
				for (float rt : rts) {
					if (rt>=minRt&&rt<=maxRt) {
						traces.add(new XYTrace(new double[] {range.getStart(), range.getStop()}, new double[] {rt, rt}, GraphType.squaredline, range.toString(),
								Color.LIGHT_GRAY, 5.0f));
					}
				}
			}
		}

		return BasicChartGenerator.getChart("m/z", "Retention Time (secs)", false, traces.toArray(new XYTraceInterface[0]));
	}

	public static ExtendedChartPanel buildGlobalStructureChart(Map<Range, WindowData> ranges) {
		if (ranges==null||ranges.isEmpty()) {
			return VisualizationCharts.getShapeChart(null, "m/z", "Retention Time (min)", List.of(), List.of(), false);
		}

		TreeMap<Range, WindowData> sorted=new TreeMap<>(Comparator.naturalOrder());
		sorted.putAll(ranges);

		List<XYShapeAnnotation> shapes=new ArrayList<>();
		List<Point2D> bounds=new ArrayList<>();
		double minMz=Double.MAX_VALUE;
		double maxMz=-Double.MAX_VALUE;
		double maxRT=0.0;
		boolean everyOther=false;

		for (Map.Entry<Range, WindowData> entry : sorted.entrySet()) {
			Range range=entry.getKey();
			WindowData data=entry.getValue();
			double durationSec=Math.max(0.0, data.getAverageDutyCycle()*data.getNumberOfMSMS());
			double heightMin=durationSec/60.0;

			double x=range.getStart();
			double y=0.0;
			double width=range.getStop()-range.getStart();
			Rectangle2D rect=new Rectangle2D.Double(x, y, width, heightMin);

			everyOther=!everyOther;
			Color fill=everyOther?new Color(BASE_COLOR.getRed(), BASE_COLOR.getGreen(), BASE_COLOR.getBlue(), 127)
					:new Color(ALT_COLOR.getRed(), ALT_COLOR.getGreen(), ALT_COLOR.getBlue(), 127);
			BasicStroke stroke=new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
			shapes.add(new XYShapeAnnotation(rect, stroke, Color.gray, fill));

			minMz=Math.min(minMz, range.getStart());
			maxMz=Math.max(maxMz, range.getStop());
			maxRT=Math.max(maxRT, heightMin);
		}

		if (minMz==Double.MAX_VALUE) {
			return VisualizationCharts.getShapeChart(null, "m/z", "Retention Time (min)", List.of(), List.of(), false);
		}

		bounds.add(new Point2D.Double(minMz, 0.0));
		bounds.add(new Point2D.Double(maxMz, 0.0));
		bounds.add(new Point2D.Double(maxMz, maxRT));
		bounds.add(new Point2D.Double(minMz, maxRT));
		bounds.add(new Point2D.Double(minMz, 0.0));

		return VisualizationCharts.getShapeChart(null, "m/z", "Retention Time (min)", shapes, bounds, false);
	}
}
