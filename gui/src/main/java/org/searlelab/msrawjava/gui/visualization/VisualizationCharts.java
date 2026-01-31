package org.searlelab.msrawjava.gui.visualization;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.List;

import javax.swing.UIManager;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYShapeAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleInsets;
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
		Font tickFont=new Font(BasicChartGenerator.BASE_FONT_NAME, Font.PLAIN, 10);

		Color axisPaint=getChartForeground();
		xAxis.setLabelFont(axisFont);
		xAxis.setTickLabelFont(tickFont);
		xAxis.setLabelPaint(axisPaint);
		xAxis.setTickLabelPaint(axisPaint);
		xAxis.setAxisLinePaint(axisPaint);
		xAxis.setTickMarkPaint(axisPaint);
		yAxis.setLabelFont(axisFont);
		yAxis.setTickLabelFont(tickFont);
		yAxis.setLabelPaint(axisPaint);
		yAxis.setTickLabelPaint(axisPaint);
		yAxis.setAxisLinePaint(axisPaint);
		yAxis.setTickMarkPaint(axisPaint);

		Color chartBackground=getChartBackground();
		plot.setBackgroundPaint(chartBackground);
		plot.setDomainGridlinePaint(chartBackground);
		plot.setDomainGridlinesVisible(false);
		plot.setRangeGridlinePaint(chartBackground);
		plot.setRangeGridlinesVisible(false);

		JFreeChart chart=new JFreeChart(title, axisFont, plot, true);
		chart.setBackgroundPaint(chartBackground);
		chart.setPadding(new RectangleInsets(10, 10, 10, 10));
		if (chart.getLegend()!=null) chart.removeLegend();

		String name=(title==null||title.isBlank())?"shape":title;
		ExtendedChartPanel panel=new ExtendedChartPanel(chart, name, false, 1f);
		panel.setMinimumDrawWidth(0);
		panel.setMinimumDrawHeight(0);
		panel.setMaximumDrawWidth(Integer.MAX_VALUE);
		panel.setMaximumDrawHeight(Integer.MAX_VALUE);
		return panel;
	}

	private static Color getChartBackground() {
		Color bg=UIManager.getColor("Panel.background");
		return (bg!=null)?bg:Color.WHITE;
	}

	private static Color getChartForeground() {
		Color bg=getChartBackground();
		double brightness=0.2126*bg.getRed()+0.7152*bg.getGreen()+0.0722*bg.getBlue();
		return (brightness<128.0)?Color.LIGHT_GRAY:Color.BLACK;
	}
}
