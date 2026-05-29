package org.searlelab.msrawjava.gui.visualization;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.function.Supplier;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYShapeAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.searlelab.msrawjava.gui.graphing.BasicChartGenerator;
import org.searlelab.msrawjava.gui.graphing.ExtendedChartPanel;

/**
 * Factory helpers for visualization chart creation.
 */
public final class VisualizationCharts {

	public static ExtendedChartPanel getShapeChart(String title, String xAxisLabel, String yAxisLabel, List<XYShapeAnnotation> shapes, List<Point2D> points,
			boolean requireIncludesZero) {
		return getShapeChart(title, xAxisLabel, yAxisLabel, shapes, points, requireIncludesZero, () -> buildPointDataTable(xAxisLabel, yAxisLabel, points));
	}

	public static ExtendedChartPanel getShapeChart(String title, String xAxisLabel, String yAxisLabel, List<XYShapeAnnotation> shapes, List<Point2D> points,
			boolean requireIncludesZero, Supplier<String> dataSupplier) {
		XYSeriesCollection dataset=new XYSeriesCollection();
		XYSeries series=new XYSeries("Shapes");
		if (points!=null) {
			for (Point2D point : points) {
				series.add(new XYDataItem(point.getX(), point.getY()));
			}
		}
		dataset.addSeries(series);

		XYLineAndShapeRenderer renderer=new XYLineAndShapeRenderer();
		renderer.setDefaultLinesVisible(false);
		renderer.setDefaultPaint(new Color(0, 0, 0, 0));
		renderer.setAutoPopulateSeriesShape(false);
		renderer.setSeriesShape(0, new Ellipse2D.Double(0, 0, 0, 0));
		renderer.setDefaultShapesVisible(true);
		if (shapes!=null) {
			for (XYShapeAnnotation shape : shapes) {
				renderer.addAnnotation(shape);
			}
		}

		NumberAxis xAxis=new NumberAxis(xAxisLabel);
		NumberAxis yAxis=new NumberAxis(yAxisLabel);
		xAxis.setAutoRangeIncludesZero(requireIncludesZero);
		yAxis.setAutoRangeIncludesZero(requireIncludesZero);

		XYPlot plot=new XYPlot(dataset, xAxis, yAxis, renderer);

		Font axisFont=new Font(BasicChartGenerator.BASE_FONT_NAME, Font.PLAIN, 14);
		Font tickFont=new Font(BasicChartGenerator.BASE_FONT_NAME, Font.PLAIN, 14);

		BasicChartGenerator.applyCommonAxisStyle(xAxis, axisFont, tickFont);
		BasicChartGenerator.applyCommonAxisStyle(yAxis, axisFont, tickFont);
		BasicChartGenerator.applyCommonPlotStyle(plot);

		JFreeChart chart=new JFreeChart(title, axisFont, plot, true);
		BasicChartGenerator.applyCommonChartStyle(chart);
		if (chart.getLegend()!=null) chart.removeLegend();

		String name=(title==null||title.isBlank())?"shape":title;
		ExtendedChartPanel panel=new ExtendedChartPanel(chart, name, false, 1f);
		BasicChartGenerator.installChartActions(panel, dataSupplier);
		BasicChartGenerator.configureChartPanel(panel);
		return panel;
	}

	private static String buildPointDataTable(String xAxisLabel, String yAxisLabel, List<Point2D> points) {
		StringBuilder sb=new StringBuilder(xAxisLabel);
		sb.append("\t");
		sb.append(yAxisLabel);
		sb.append("\n");
		if (points!=null) {
			for (Point2D point : points) {
				sb.append(point.getX());
				sb.append("\t");
				sb.append(point.getY());
				sb.append("\n");
			}
		}
		return sb.toString();
	}
}
