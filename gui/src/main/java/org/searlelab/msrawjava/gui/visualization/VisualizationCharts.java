package org.searlelab.msrawjava.gui.visualization;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYShapeAnnotation;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.searlelab.msrawjava.gui.charts.BasicChartGenerator;
import org.searlelab.msrawjava.gui.charts.ExtendedChartPanel;

import gnu.trove.list.array.TFloatArrayList;

public final class VisualizationCharts {
	private static final int MAX_CATEGORIES = 200;
	private static final int MAX_VALUES_PER_CATEGORY = 500;
	private VisualizationCharts() {
	}

	public static ExtendedChartPanel getBoxplotChart(String title, String xAxisLabel, String yAxisLabel,
			Map<Comparable<?>, TFloatArrayList> map) {
		if (map==null||map.isEmpty()) {
			DefaultCategoryDataset empty=new DefaultCategoryDataset();
			return createEmptyCategoryChart(title, xAxisLabel, yAxisLabel, empty);
		}

		ArrayList<Comparable<?>> keys=new ArrayList<>(map.keySet());
		keys.sort((a, b) -> {
			if (a==null&&b==null) return 0;
			if (a==null) return -1;
			if (b==null) return 1;
			@SuppressWarnings("unchecked")
			Comparable<Object> left=(Comparable<Object>)a;
			return left.compareTo(b);
		});
		if (keys.size()>MAX_CATEGORIES) {
			ArrayList<Comparable<?>> reduced=new ArrayList<>(MAX_CATEGORIES);
			double step=(double)keys.size()/MAX_CATEGORIES;
			for (int i=0; i<MAX_CATEGORIES; i++) {
				int idx=(int)Math.floor(i*step);
				reduced.add(keys.get(idx));
			}
			keys=reduced;
		}

		DefaultBoxAndWhiskerCategoryDataset dataset=new DefaultBoxAndWhiskerCategoryDataset();
		for (Comparable<?> key : keys) {
			TFloatArrayList values=map.get(key);
			List<Double> list=new ArrayList<>();
			if (values!=null) {
				float[] arr=values.toArray();
				int step=Math.max(1, arr.length/MAX_VALUES_PER_CATEGORY);
				for (int i=0; i<arr.length; i+=step) {
					float v=arr[i];
					if (Float.isFinite(v)&&v>0f) {
						list.add((double)v);
					}
				}
			}
			if (!list.isEmpty()) {
				dataset.add(list, xAxisLabel, key);
			}
		}

		CategoryAxis xAxis=new CategoryAxis(xAxisLabel);
		NumberAxis yAxis=new NumberAxis(yAxisLabel);
		yAxis.setAutoRangeIncludesZero(false);
		BoxAndWhiskerRenderer renderer=new BoxAndWhiskerRenderer();
		renderer.setMeanVisible(false);

		CategoryPlot plot=new CategoryPlot(dataset, xAxis, yAxis, renderer);

		Font axisFont=new Font(BasicChartGenerator.BASE_FONT_NAME, Font.PLAIN, 14);
		Font tickFont=new Font(BasicChartGenerator.BASE_FONT_NAME, Font.PLAIN, 10);

		plot.setBackgroundPaint(Color.white);
		plot.setDomainGridlinePaint(Color.white);
		plot.setDomainGridlinesVisible(false);
		plot.setRangeGridlinePaint(Color.white);
		plot.setRangeGridlinesVisible(false);

		xAxis.setLabelFont(axisFont);
		xAxis.setTickLabelFont(tickFont);
		xAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
		xAxis.setMaximumCategoryLabelLines(1);
		xAxis.setMaximumCategoryLabelWidthRatio(2f);

		yAxis.setLabelFont(axisFont);
		yAxis.setTickLabelFont(tickFont);

		JFreeChart chart=new JFreeChart(title, axisFont, plot, true);
		chart.setBackgroundPaint(Color.white);
		chart.setPadding(new RectangleInsets(10, 10, 10, 10));
		if (chart.getLegend()!=null) chart.removeLegend();

		String name=(title==null||title.isBlank())?"boxplot":title;
		ExtendedChartPanel panel=new ExtendedChartPanel(chart, name, false, 1f);
		panel.setMinimumDrawWidth(0);
		panel.setMinimumDrawHeight(0);
		panel.setMaximumDrawWidth(Integer.MAX_VALUE);
		panel.setMaximumDrawHeight(Integer.MAX_VALUE);
		return panel;
	}

	private static ExtendedChartPanel createEmptyCategoryChart(String title, String xAxisLabel, String yAxisLabel, DefaultCategoryDataset dataset) {
		CategoryAxis xAxis=new CategoryAxis(xAxisLabel);
		NumberAxis yAxis=new NumberAxis(yAxisLabel);
		CategoryPlot plot=new CategoryPlot(dataset, xAxis, yAxis, null);

		Font axisFont=new Font(BasicChartGenerator.BASE_FONT_NAME, Font.PLAIN, 14);
		Font tickFont=new Font(BasicChartGenerator.BASE_FONT_NAME, Font.PLAIN, 10);
		xAxis.setLabelFont(axisFont);
		xAxis.setTickLabelFont(tickFont);
		yAxis.setLabelFont(axisFont);
		yAxis.setTickLabelFont(tickFont);

		JFreeChart chart=new JFreeChart(title, axisFont, plot, false);
		chart.setBackgroundPaint(Color.white);
		String name=(title==null||title.isBlank())?"empty":title;
		ExtendedChartPanel panel=new ExtendedChartPanel(chart, name, false, 1f);
		return panel;
	}

	public static ExtendedChartPanel getShapeChart(String title, String xAxisLabel, String yAxisLabel,
			List<XYShapeAnnotation> shapes, List<Point2D> points, boolean requireIncludesZero) {
		XYSeriesCollection dataset=new XYSeriesCollection();
		XYSeries series=new XYSeries("Shapes");
		if (points!=null) {
			for (Point2D point : points) {
				series.add(point.getX(), point.getY());
			}
		}
		dataset.addSeries(series);

		XYLineAndShapeRenderer renderer=new XYLineAndShapeRenderer();
		renderer.setDefaultLinesVisible(false);
		renderer.setDefaultPaint(new Color(0,0,0,0));
		renderer.setAutoPopulateSeriesShape(false);
		renderer.setSeriesShape(0, new Ellipse2D.Double(0,0,0,0));
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

		xAxis.setLabelFont(axisFont);
		xAxis.setTickLabelFont(tickFont);
		yAxis.setLabelFont(axisFont);
		yAxis.setTickLabelFont(tickFont);

		plot.setBackgroundPaint(Color.white);
		plot.setDomainGridlinePaint(Color.white);
		plot.setDomainGridlinesVisible(false);
		plot.setRangeGridlinePaint(Color.white);
		plot.setRangeGridlinesVisible(false);

		JFreeChart chart=new JFreeChart(title, axisFont, plot, true);
		chart.setBackgroundPaint(Color.white);
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
}
