package org.searlelab.msrawjava.gui.graphing;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.Axis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.title.TextTitle;

/**
 * Copies user-edited chart styling onto rebuilt charts in the same window.
 */
public final class ChartStyleTransfer {
	private ChartStyleTransfer() {
	}

	public static void apply(ExtendedChartPanel sourcePanel, ExtendedChartPanel targetPanel) {
		if (sourcePanel==null||targetPanel==null) return;
		apply(sourcePanel.getChart(), targetPanel.getChart());
	}

	public static void apply(JFreeChart sourceChart, JFreeChart targetChart) {
		if (sourceChart==null||targetChart==null) return;

		copyChartProperties(sourceChart, targetChart);
		Plot sourcePlot=sourceChart.getPlot();
		Plot targetPlot=targetChart.getPlot();
		if (sourcePlot==null||targetPlot==null||!sourcePlot.getClass().equals(targetPlot.getClass())) return;

		copyPlotProperties(sourcePlot, targetPlot);
			if (sourcePlot instanceof XYPlot&&targetPlot instanceof XYPlot) {
				copyXYPlotProperties((XYPlot)sourcePlot, (XYPlot)targetPlot);
			}
	}

	private static void copyChartProperties(JFreeChart sourceChart, JFreeChart targetChart) {
		targetChart.setAntiAlias(sourceChart.getAntiAlias());
		if (sourceChart.getTextAntiAlias()!=null) {
			targetChart.setTextAntiAlias(sourceChart.getTextAntiAlias());
		}
		targetChart.setBackgroundPaint(sourceChart.getBackgroundPaint());
		targetChart.setBorderVisible(sourceChart.isBorderVisible());
		targetChart.setBorderPaint(sourceChart.getBorderPaint());
		targetChart.setBorderStroke(sourceChart.getBorderStroke());
		targetChart.setPadding(sourceChart.getPadding());
		targetChart.setBackgroundImage(sourceChart.getBackgroundImage());
		targetChart.setBackgroundImageAlignment(sourceChart.getBackgroundImageAlignment());
		targetChart.setBackgroundImageAlpha(sourceChart.getBackgroundImageAlpha());
		targetChart.setRenderingHints(sourceChart.getRenderingHints());

		TextTitle sourceTitle=sourceChart.getTitle();
		if (sourceTitle!=null) {
			try {
				targetChart.setTitle((TextTitle)sourceTitle.clone());
			} catch (CloneNotSupportedException e) {
				targetChart.setTitle(sourceTitle.getText());
				TextTitle targetTitle=targetChart.getTitle();
				if (targetTitle!=null) {
					targetTitle.setFont(sourceTitle.getFont());
					targetTitle.setPaint(sourceTitle.getPaint());
					targetTitle.setBackgroundPaint(sourceTitle.getBackgroundPaint());
					targetTitle.setTextAlignment(sourceTitle.getTextAlignment());
				}
			}
		} else {
			targetChart.setTitle((TextTitle)null);
		}
	}

	private static void copyPlotProperties(Plot sourcePlot, Plot targetPlot) {
		targetPlot.setNoDataMessage(sourcePlot.getNoDataMessage());
		targetPlot.setNoDataMessageFont(sourcePlot.getNoDataMessageFont());
		targetPlot.setNoDataMessagePaint(sourcePlot.getNoDataMessagePaint());
		targetPlot.setInsets(sourcePlot.getInsets(), false);
		targetPlot.setBackgroundPaint(sourcePlot.getBackgroundPaint());
		targetPlot.setBackgroundAlpha(sourcePlot.getBackgroundAlpha());
		targetPlot.setBackgroundImage(sourcePlot.getBackgroundImage());
		targetPlot.setBackgroundImageAlignment(sourcePlot.getBackgroundImageAlignment());
		targetPlot.setBackgroundImageAlpha(sourcePlot.getBackgroundImageAlpha());
		targetPlot.setOutlineVisible(sourcePlot.isOutlineVisible());
		targetPlot.setOutlineStroke(sourcePlot.getOutlineStroke());
		targetPlot.setOutlinePaint(sourcePlot.getOutlinePaint());
		targetPlot.setForegroundAlpha(sourcePlot.getForegroundAlpha());
	}

	private static void copyXYPlotProperties(XYPlot sourcePlot, XYPlot targetPlot) {
		targetPlot.setOrientation(sourcePlot.getOrientation());
		targetPlot.setAxisOffset(sourcePlot.getAxisOffset());
		targetPlot.setDomainAxisLocation(sourcePlot.getDomainAxisLocation(), false);
		targetPlot.setRangeAxisLocation(sourcePlot.getRangeAxisLocation(), false);
		targetPlot.setDomainGridlinesVisible(sourcePlot.isDomainGridlinesVisible());
		targetPlot.setDomainMinorGridlinesVisible(sourcePlot.isDomainMinorGridlinesVisible());
		targetPlot.setDomainGridlineStroke(sourcePlot.getDomainGridlineStroke());
		targetPlot.setDomainMinorGridlineStroke(sourcePlot.getDomainMinorGridlineStroke());
		targetPlot.setDomainGridlinePaint(sourcePlot.getDomainGridlinePaint());
		targetPlot.setDomainMinorGridlinePaint(sourcePlot.getDomainMinorGridlinePaint());
		targetPlot.setRangeGridlinesVisible(sourcePlot.isRangeGridlinesVisible());
		targetPlot.setRangeMinorGridlinesVisible(sourcePlot.isRangeMinorGridlinesVisible());
		targetPlot.setRangeGridlineStroke(sourcePlot.getRangeGridlineStroke());
		targetPlot.setRangeMinorGridlineStroke(sourcePlot.getRangeMinorGridlineStroke());
		targetPlot.setRangeGridlinePaint(sourcePlot.getRangeGridlinePaint());
		targetPlot.setRangeMinorGridlinePaint(sourcePlot.getRangeMinorGridlinePaint());
		targetPlot.setDomainZeroBaselineVisible(sourcePlot.isDomainZeroBaselineVisible());
		targetPlot.setDomainZeroBaselineStroke(sourcePlot.getDomainZeroBaselineStroke());
		targetPlot.setDomainZeroBaselinePaint(sourcePlot.getDomainZeroBaselinePaint());
		targetPlot.setRangeZeroBaselineVisible(sourcePlot.isRangeZeroBaselineVisible());
		targetPlot.setRangeZeroBaselineStroke(sourcePlot.getRangeZeroBaselineStroke());
		targetPlot.setRangeZeroBaselinePaint(sourcePlot.getRangeZeroBaselinePaint());
		targetPlot.setDomainCrosshairVisible(sourcePlot.isDomainCrosshairVisible());
		targetPlot.setDomainCrosshairLockedOnData(sourcePlot.isDomainCrosshairLockedOnData());
		targetPlot.setDomainCrosshairStroke(sourcePlot.getDomainCrosshairStroke());
		targetPlot.setDomainCrosshairPaint(sourcePlot.getDomainCrosshairPaint());
		targetPlot.setRangeCrosshairVisible(sourcePlot.isRangeCrosshairVisible());
		targetPlot.setRangeCrosshairLockedOnData(sourcePlot.isRangeCrosshairLockedOnData());
		targetPlot.setRangeCrosshairStroke(sourcePlot.getRangeCrosshairStroke());
		targetPlot.setRangeCrosshairPaint(sourcePlot.getRangeCrosshairPaint());
		targetPlot.setDomainPannable(sourcePlot.isDomainPannable());
		targetPlot.setRangePannable(sourcePlot.isRangePannable());
		targetPlot.setDatasetRenderingOrder(sourcePlot.getDatasetRenderingOrder());
		targetPlot.setSeriesRenderingOrder(sourcePlot.getSeriesRenderingOrder());

		copyAxis(sourcePlot.getDomainAxis(), targetPlot, true, 0);
		copyAxis(sourcePlot.getRangeAxis(), targetPlot, false, 0);
		for (int i=1; i<targetPlot.getDomainAxisCount(); i++) {
			copyAxis(sourcePlot.getDomainAxis(i), targetPlot, true, i);
		}
		for (int i=1; i<targetPlot.getRangeAxisCount(); i++) {
			copyAxis(sourcePlot.getRangeAxis(i), targetPlot, false, i);
		}

	}

	private static void copyAxis(ValueAxis sourceAxis, XYPlot targetPlot, boolean domain, int index) {
		if (!domain) return; // never copy over the range axis 
		
		if (sourceAxis==null) return;
		ValueAxis targetAxis=domain?targetPlot.getDomainAxis(index):targetPlot.getRangeAxis(index);
		if (targetAxis!=null) {
			copyAxisProperties(sourceAxis, targetAxis);
		}
	}

	private static void copyAxisProperties(ValueAxis sourceAxis, ValueAxis targetAxis) {
		if (sourceAxis==null||targetAxis==null) return;
		copyAxisBaseProperties(sourceAxis, targetAxis);
		targetAxis.setVerticalTickLabels(sourceAxis.isVerticalTickLabels());
		targetAxis.setInverted(sourceAxis.isInverted());
		targetAxis.setAutoRange(sourceAxis.isAutoRange());
		targetAxis.setAutoRangeMinimumSize(sourceAxis.getAutoRangeMinimumSize(), false);
		targetAxis.setDefaultAutoRange(sourceAxis.getDefaultAutoRange());
		targetAxis.setLowerMargin(sourceAxis.getLowerMargin());
		targetAxis.setUpperMargin(sourceAxis.getUpperMargin());
		targetAxis.setFixedAutoRange(sourceAxis.getFixedAutoRange());
		if (!sourceAxis.isAutoRange()) {
			targetAxis.setRange(sourceAxis.getRange(), false, false);
		}
		targetAxis.setAutoTickUnitSelection(sourceAxis.isAutoTickUnitSelection(), false);
		targetAxis.setStandardTickUnits(sourceAxis.getStandardTickUnits());
		targetAxis.setMinorTickCount(sourceAxis.getMinorTickCount());
		if (sourceAxis instanceof NumberAxis&&targetAxis instanceof NumberAxis) {
			((NumberAxis)targetAxis).setTickUnit(((NumberAxis)sourceAxis).getTickUnit(), false, false);
			((NumberAxis)targetAxis).setAutoRangeIncludesZero(((NumberAxis)sourceAxis).getAutoRangeIncludesZero());
			((NumberAxis)targetAxis).setAutoRangeStickyZero(((NumberAxis)sourceAxis).getAutoRangeStickyZero());
			((NumberAxis)targetAxis).setRangeType(((NumberAxis)sourceAxis).getRangeType());
			((NumberAxis)targetAxis).setNumberFormatOverride(((NumberAxis)sourceAxis).getNumberFormatOverride());
		}
	}

	private static void copyAxisBaseProperties(Axis sourceAxis, Axis targetAxis) {
		targetAxis.setVisible(sourceAxis.isVisible());
		targetAxis.setLabel(sourceAxis.getLabel());
		targetAxis.setAttributedLabel(sourceAxis.getAttributedLabel());
		targetAxis.setLabelFont(sourceAxis.getLabelFont());
		targetAxis.setLabelPaint(sourceAxis.getLabelPaint());
		targetAxis.setLabelInsets(sourceAxis.getLabelInsets(), false);
		targetAxis.setLabelAngle(sourceAxis.getLabelAngle());
		targetAxis.setLabelLocation(sourceAxis.getLabelLocation());
		targetAxis.setAxisLineVisible(sourceAxis.isAxisLineVisible());
		targetAxis.setAxisLinePaint(sourceAxis.getAxisLinePaint());
		targetAxis.setAxisLineStroke(sourceAxis.getAxisLineStroke());
		targetAxis.setTickLabelsVisible(sourceAxis.isTickLabelsVisible());
		targetAxis.setMinorTickMarksVisible(sourceAxis.isMinorTickMarksVisible());
		targetAxis.setTickLabelFont(sourceAxis.getTickLabelFont());
		targetAxis.setTickLabelPaint(sourceAxis.getTickLabelPaint());
		targetAxis.setTickLabelInsets(sourceAxis.getTickLabelInsets());
		targetAxis.setTickMarksVisible(sourceAxis.isTickMarksVisible());
		targetAxis.setTickMarkInsideLength(sourceAxis.getTickMarkInsideLength());
		targetAxis.setTickMarkOutsideLength(sourceAxis.getTickMarkOutsideLength());
		targetAxis.setTickMarkStroke(sourceAxis.getTickMarkStroke());
		targetAxis.setTickMarkPaint(sourceAxis.getTickMarkPaint());
		targetAxis.setMinorTickMarkInsideLength(sourceAxis.getMinorTickMarkInsideLength());
		targetAxis.setMinorTickMarkOutsideLength(sourceAxis.getMinorTickMarkOutsideLength());
		targetAxis.setFixedDimension(sourceAxis.getFixedDimension());
	}
}
