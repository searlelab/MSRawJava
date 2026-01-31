package org.searlelab.msrawjava.gui.graphing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.UIManager;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;

import gnu.trove.list.array.TFloatArrayList;

public class BoxPlotGenerator {
	private static final int MAX_CATEGORIES=200;
	private static final int MAX_VALUES_PER_CATEGORY=500;

	public static ExtendedChartPanel getBoxplotChart(String title, String xAxisLabel, String yAxisLabel, Map<Comparable<?>, TFloatArrayList> map) {
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
		renderer.setArtifactPaint(Color.black);
		renderer.setUseOutlinePaintForWhiskers(true);
		renderer.setDefaultOutlinePaint(Color.black);
		renderer.setDefaultOutlineStroke(new BasicStroke(1.0f));

		CategoryPlot plot=new CategoryPlot(dataset, xAxis, yAxis, renderer);

		Font axisFont=new Font(BasicChartGenerator.BASE_FONT_NAME, Font.PLAIN, 14);
		Font tickFont=new Font(BasicChartGenerator.BASE_FONT_NAME, Font.PLAIN, 10);

		Color chartBackground=getChartBackground();
		plot.setBackgroundPaint(chartBackground);
		plot.setDomainGridlinePaint(chartBackground);
		plot.setDomainGridlinesVisible(false);
		plot.setRangeGridlinePaint(chartBackground);
		plot.setRangeGridlinesVisible(false);

		Color axisPaint=getChartForeground();
		xAxis.setLabelFont(axisFont);
		xAxis.setTickLabelFont(tickFont);
		xAxis.setLabelPaint(axisPaint);
		xAxis.setTickLabelPaint(axisPaint);
		xAxis.setAxisLinePaint(axisPaint);
		xAxis.setTickMarkPaint(axisPaint);
		xAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
		xAxis.setMaximumCategoryLabelLines(1);
		xAxis.setMaximumCategoryLabelWidthRatio(2f);

		yAxis.setLabelFont(axisFont);
		yAxis.setTickLabelFont(tickFont);
		yAxis.setLabelPaint(axisPaint);
		yAxis.setTickLabelPaint(axisPaint);
		yAxis.setAxisLinePaint(axisPaint);
		yAxis.setTickMarkPaint(axisPaint);

		JFreeChart chart=new JFreeChart(title, axisFont, plot, true);
		chart.setBackgroundPaint(chartBackground);
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

		JFreeChart chart=new JFreeChart(title, axisFont, plot, false);
		chart.setBackgroundPaint(getChartBackground());
		String name=(title==null||title.isBlank())?"empty":title;
		ExtendedChartPanel panel=new ExtendedChartPanel(chart, name, false, 1f);
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
