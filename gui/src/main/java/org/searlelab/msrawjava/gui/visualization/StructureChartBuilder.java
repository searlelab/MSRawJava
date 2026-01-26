package org.searlelab.msrawjava.gui.visualization;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jfree.chart.annotations.XYShapeAnnotation;
import org.searlelab.msrawjava.gui.charts.BasicChartGenerator;
import org.searlelab.msrawjava.gui.charts.ExtendedChartPanel;
import org.searlelab.msrawjava.gui.charts.GraphType;
import org.searlelab.msrawjava.gui.charts.XYTrace;
import org.searlelab.msrawjava.gui.charts.XYTraceInterface;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

public final class StructureChartBuilder {
	private static final Color BASE_COLOR=new Color(0, 0, 200);
	private static final Color ALT_COLOR=new Color(100, 100, 255);

	private StructureChartBuilder() {
	}

	public static ExtendedChartPanel buildLocalStructureChart(Map<Range, WindowData> ranges) {
		if (ranges==null||ranges.isEmpty()) {
			return BasicChartGenerator.getChart("m/z", "Retention Time (secs)", false, new XYTraceInterface[0]);
		}
		TreeMap<Range, WindowData> sorted=new TreeMap<>(Comparator.naturalOrder());
		sorted.putAll(ranges);

		List<XYTraceInterface> traces=new ArrayList<>();
		boolean everyOther=false;
		for (Map.Entry<Range, WindowData> entry : sorted.entrySet()) {
			Range range=entry.getKey();
			WindowData data=entry.getValue();
			float rt0=0.0f;
			float rt1=Math.max(0.0f, data.getAverageDutyCycle());
			everyOther=!everyOther;
			Color color=everyOther?BASE_COLOR:ALT_COLOR;

			traces.add(new XYTrace(
					new double[] {range.getStart(), range.getStop()},
					new double[] {rt0, rt0},
					GraphType.squaredline,
					range.toString(),
					color,
					5.0f
			));
			traces.add(new XYTrace(
					new double[] {range.getStart(), range.getStop()},
					new double[] {rt1, rt1},
					GraphType.squaredline,
					range.toString(),
					color,
					5.0f
			));
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
