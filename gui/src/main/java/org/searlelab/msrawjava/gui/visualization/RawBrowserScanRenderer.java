package org.searlelab.msrawjava.gui.visualization;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYBoxAnnotation;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.gui.GUIPreferences;
import org.searlelab.msrawjava.gui.graphing.BasicChartGenerator;
import org.searlelab.msrawjava.gui.graphing.ChartStyleTransfer;
import org.searlelab.msrawjava.gui.graphing.ExtendedChartPanel;
import org.searlelab.msrawjava.gui.graphing.HistogramUtils;
import org.searlelab.msrawjava.gui.graphing.LegendMode;
import org.searlelab.msrawjava.gui.graphing.XYTrace;
import org.searlelab.msrawjava.gui.graphing.XYTraceInterface;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.model.AcquiredSpectrum;

final class RawBrowserScanRenderer {
	private static final String TIC_TOOLTIP="Total ion current across retention time; selected scan ranges are marked when available.";
	private static final String XIC_TOOLTIP="Extracted ion chromatograms for target m/z values in the selected scan type.";
	private static final String SPECTRUM_TOOLTIP="Mass spectrum for the currently selected scan or merged scan selection.";
	private static final String IMS_TOOLTIP="Ion mobility versus m/z view for the selected spectrum.";
	private static final String HISTOGRAM_TOOLTIP="Log10 fragment intensity distribution for the selected spectrum.";
	private static final int SPECTRUM_HISTOGRAM_TAB_INDEX=0;
	private static final int SPECTRUM_PROPERTIES_TAB_INDEX=1;
	private static final Color[] XIC_COLORS=new Color[] {new Color(0xE6, 0x4A, 0x19), new Color(0x00, 0x79, 0x6B), new Color(0x1E, 0x88, 0xE5),
			new Color(0x8E, 0x24, 0xAA), new Color(0x6D, 0x4C, 0x41), new Color(0x43, 0xA0, 0x47), new Color(0xFB, 0x8C, 0x00), new Color(0x39, 0x49, 0xAB)};
	private final JPanel topChartContent=new JPanel(new BorderLayout());
	private final JSplitPane spectrumSplit=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	private final JSplitPane imsSpectrumSplit=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	private final JTabbedPane spectrumDetailsTabs=new JTabbedPane();
	private final ScanMetadataTableModel scanMetadataModel=new ScanMetadataTableModel();
	private final JScrollPane scanMetadataScroll;
	private final Consumer<String> tableSelectionAction;
	private final DoubleConsumer nearestScanSelector;
	private final Runnable splitPreferenceApplier;
	private ExtendedChartPanel topChromatogramChart;
	private final ArrayList<XYAnnotation> chromatogramSelectionAnnotations=new ArrayList<>();
	private ChartFocusTarget focusedChartTarget=ChartFocusTarget.TOP_CHROMATOGRAM;
	private boolean pendingChartFocusRestore=false;

	private enum ChartFocusTarget {
		TOP_CHROMATOGRAM, SPECTRUM, IMS, HISTOGRAM
	}

	RawBrowserScanRenderer(Consumer<String> tableSelectionAction, DoubleConsumer nearestScanSelector, Runnable splitPreferenceApplier) {
		this.tableSelectionAction=tableSelectionAction;
		this.nearestScanSelector=nearestScanSelector;
		this.splitPreferenceApplier=splitPreferenceApplier;
		this.scanMetadataScroll=new JScrollPane(RawBrowserTables.createScanMetadataTable(scanMetadataModel));
		initializeSpectrumDetailsTabs();
		configureSplits();
	}

	private void configureSplits() {
		scanMetadataScroll.setMinimumSize(new java.awt.Dimension(150, 80));
		scanMetadataScroll.setPreferredSize(new java.awt.Dimension(220, 180));
		scanMetadataScroll.setToolTipText("Per-scan vendor properties for the selected spectrum.");
		spectrumSplit.setResizeWeight(0.80);
		spectrumSplit.setDividerSize(8);
		spectrumSplit.setContinuousLayout(true);
		spectrumSplit.setOneTouchExpandable(true);
		imsSpectrumSplit.setResizeWeight(0.75);
		imsSpectrumSplit.setDividerSize(8);
		imsSpectrumSplit.setContinuousLayout(true);
		imsSpectrumSplit.setOneTouchExpandable(true);
		RawBrowserSplitPreferences.registerSplitPreference(spectrumSplit, GUIPreferences::setRawBrowserSpectrumSplitRatio);
		RawBrowserSplitPreferences.registerSplitPreference(imsSpectrumSplit, GUIPreferences::setRawBrowserImsSplitRatio);
	}

	private void initializeSpectrumDetailsTabs() {
		spectrumDetailsTabs.addTab("Histogram", new JLabel(""));
		spectrumDetailsTabs.setToolTipTextAt(SPECTRUM_HISTOGRAM_TAB_INDEX, HISTOGRAM_TOOLTIP);
		spectrumDetailsTabs.addTab("Properties", scanMetadataScroll);
		spectrumDetailsTabs.setToolTipTextAt(SPECTRUM_PROPERTIES_TAB_INDEX, "Per-scan vendor properties for the selected spectrum.");
		spectrumDetailsTabs.setEnabledAt(SPECTRUM_PROPERTIES_TAB_INDEX, false);
		spectrumDetailsTabs.setSelectedIndex(SPECTRUM_HISTOGRAM_TAB_INDEX);
	}

	JPanel getTopChartContent() {
		return topChartContent;
	}

	JSplitPane getSpectrumSplit() {
		return spectrumSplit;
	}

	ExtendedChartPanel getTopChromatogramChart() {
		return topChromatogramChart;
	}

	static Color getXicColor(int targetIndex) {
		return XIC_COLORS[targetIndex%XIC_COLORS.length];
	}

	void markPendingChartFocusRestore() {
		pendingChartFocusRestore=true;
	}

	void refreshChromatogramChart(XYTrace activeChromatogram, float activeMaxTic, RawBrowserXicController xicController) {
		refreshChromatogramChart(activeChromatogram, activeMaxTic, xicController, true);
	}

	void refreshChromatogramChart(XYTrace activeChromatogram, float activeMaxTic, RawBrowserXicController xicController, boolean preserveAxisView) {
		ExtendedChartPanel previousChart=preserveAxisView?topChromatogramChart:null;
		ExtendedChartPanel chart=buildChromatogramChart(activeChromatogram, xicController);
		ChartStyleTransfer.apply(previousChart, chart);
		setTopChart(chart);
	}

	void refreshTopChartForSelection(float minRT, float maxRT, float activeMaxTic, RawBrowserXicController xicController) {
		updateTopChartSelectionMarkers(minRT, maxRT, activeMaxTic, xicController);
	}

	void clearTopChartSelectionMarkers() {
		if (topChromatogramChart==null) {
			chromatogramSelectionAnnotations.clear();
			return;
		}
		XYPlot plot=topChromatogramChart.getChart().getXYPlot();
		if (plot==null) {
			chromatogramSelectionAnnotations.clear();
			return;
		}
		for (XYAnnotation annotation : chromatogramSelectionAnnotations) {
			plot.removeAnnotation(annotation);
		}
		chromatogramSelectionAnnotations.clear();
	}

	void appendXicProgress(XicExtractionProgress progress, int traceCount, int startIndex, int endIndex) {
		if (topChromatogramChart==null||topChromatogramChart.getChart()==null) return;
		XYPlot plot=topChromatogramChart.getChart().getXYPlot();
		if (plot==null) return;
		if (endIndex<=startIndex) return;
		int count=Math.min(traceCount, progress.traces.length);
		for (int t=0; t<count; t++) {
			if (!(plot.getDataset(t) instanceof XYSeriesCollection)) continue;
			XYSeriesCollection seriesCollection=(XYSeriesCollection)plot.getDataset(t);
			if (seriesCollection.getSeriesCount()<=0) continue;
			XYSeries series=seriesCollection.getSeries(0);
			series.setNotify(false);
			for (int i=startIndex; i<endIndex; i++) {
				double x=progress.xMinutes[i];
				double y=progress.traces[t][i];
				if (!Double.isFinite(x)||!Double.isFinite(y)) continue;
				series.add(x, y, false);
			}
			series.setNotify(true);
		}
	}

	void applySplitPreferences() {
		RawBrowserSplitPreferences.applySplitRatio(spectrumSplit, GUIPreferences.getRawBrowserSpectrumSplitRatio());
		RawBrowserSplitPreferences.applySplitRatio(imsSpectrumSplit, GUIPreferences.getRawBrowserImsSplitRatio());
	}

	void resetScan(SelectionResult result, RawBrowserXicController xicController, float activeMaxTic) {
		boolean shouldRestoreChartFocus=pendingChartFocusRestore;
		pendingChartFocusRestore=false;
		ExtendedChartPanel previousSpectrumChart=currentSpectrumChartPanel();
		ExtendedChartPanel previousImsChart=componentAsChartPanel(imsSpectrumSplit.getRightComponent());
		ExtendedChartPanel previousHistogramChart=currentHistogramChartPanel();
		if (result==null||result.entries==null||result.entries.isEmpty()) {
			clearSelectionAndSpectrum();
			return;
		}
		AcquiredSpectrum displaySpectrum=result.displaySpectrum;
		if (displaySpectrum==null) {
			clearSelectionAndSpectrum();
			return;
		}
		ExtendedChartPanel spectrumChart=BasicChartGenerator.getChart("m/z", "Intensity", false, new XYTrace(displaySpectrum));
		ChartStyleTransfer.apply(previousSpectrumChart, spectrumChart);
		installChartArrowNavigation(spectrumChart, ChartFocusTarget.SPECTRUM);
		applySpectrumXicOverlays(spectrumChart, displaySpectrum, xicController);
		spectrumChart.setToolTipText(SPECTRUM_TOOLTIP);
		ExtendedChartPanel imsChart=null;
		boolean hasIms=displaySpectrum.getIonMobilityArray().isPresent()&&MatrixMath.max(displaySpectrum.getIntensityArray())>0.0f;
		if (hasIms) {
			imsChart=BasicChartGenerator.getChart("Ion Mobility", "m/z", false, new ImsSpectrumWrapper(displaySpectrum));
			ChartStyleTransfer.apply(previousImsChart, imsChart);
			installChartArrowNavigation(imsChart, ChartFocusTarget.IMS);
			imsChart.setToolTipText(IMS_TOOLTIP);
			imsSpectrumSplit.setLeftComponent(spectrumChart);
			imsSpectrumSplit.setRightComponent(imsChart);
			RawBrowserSplitPreferences.applySplitRatio(imsSpectrumSplit, GUIPreferences.getRawBrowserImsSplitRatio());
			spectrumSplit.setLeftComponent(imsSpectrumSplit);
		} else {
			spectrumSplit.setLeftComponent(spectrumChart);
		}
		XYTrace intensityHistogram=HistogramUtils.histogramFromLog10(displaySpectrum.getIntensityArray(), "Log10 Fragment Intensity Distribution");
		ExtendedChartPanel spectrumHistogram=BasicChartGenerator.getChart("Log10 Intensity", "Count (N="+displaySpectrum.getIntensityArray().length+")", false,
				intensityHistogram);
		ChartStyleTransfer.apply(previousHistogramChart, spectrumHistogram);
		installChartArrowNavigation(spectrumHistogram, ChartFocusTarget.HISTOGRAM);
		spectrumHistogram.setToolTipText(HISTOGRAM_TOOLTIP);
		updateSpectrumRightComponent(spectrumHistogram, result.scanMetadata);
		if (shouldRestoreChartFocus) {
			restoreSpectrumFocusIfNeeded(spectrumChart, imsChart, spectrumHistogram);
		}
		updateTopChartSelectionMarkers(result.minRT, result.maxRT, activeMaxTic, xicController);
		splitPreferenceApplier.run();
	}

	private void clearSelectionAndSpectrum() {
		clearTopChartSelectionMarkers();
		spectrumSplit.setLeftComponent(new JLabel("No spectrum available"));
		spectrumSplit.setRightComponent(new JLabel(""));
		scanMetadataModel.update(emptyScanMetadata());
	}

	private static Pair<String[], String[]> emptyScanMetadata() {
		return new Pair<>(new String[0], new String[0]);
	}

	private void setTopChart(ExtendedChartPanel chart) {
		if (topChromatogramChart!=null&&topChromatogramChart!=chart) {
			clearTopChartSelectionMarkers();
		}
		topChartContent.removeAll();
		topChartContent.add(chart, BorderLayout.CENTER);
		topChromatogramChart=chart;
		chromatogramSelectionAnnotations.clear();
		installChartArrowNavigation(chart, ChartFocusTarget.TOP_CHROMATOGRAM);
		installTopChartClickSelection(chart);
		topChartContent.revalidate();
		topChartContent.repaint();
	}

	private void installChartArrowNavigation(ExtendedChartPanel chart, ChartFocusTarget focusTarget) {
		if (chart==null) return;
		RawBrowserNavigation.installChartArrowNavigation(chart, actionKey -> {
			markPendingChartFocusRestore();
			tableSelectionAction.accept(actionKey);
		});
		chart.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				focusedChartTarget=focusTarget;
				chart.requestFocusInWindow();
			}
		});
	}

	private void installTopChartClickSelection(ExtendedChartPanel chart) {
		if (chart==null) return;
		if (Boolean.TRUE.equals(chart.getClientProperty("rawBrowser.chartSelectionInstalled"))) return;
		chart.putClientProperty("rawBrowser.chartSelectionInstalled", Boolean.TRUE);
		ChartMouseListener listener=new ChartMouseListener() {
			@Override
			public void chartMouseClicked(ChartMouseEvent event) {
				if (event==null||event.getTrigger()==null) return;
				MouseEvent trigger=event.getTrigger();
				if (!SwingUtilities.isLeftMouseButton(trigger)) return;
				double clickedMinutes=resolveChartClickDomainValue(chart, trigger);
				if (!Double.isFinite(clickedMinutes)) return;
				nearestScanSelector.accept(clickedMinutes);
			}

			@Override
			public void chartMouseMoved(ChartMouseEvent event) {
				// no-op
			}
		};
		chart.addChartMouseListener(listener);
	}

	private double resolveChartClickDomainValue(ExtendedChartPanel chart, MouseEvent trigger) {
		if (chart==null||chart.getChart()==null||chart.getChart().getXYPlot()==null) return Double.NaN;
		XYPlot plot=chart.getChart().getXYPlot();
		if (plot.getDomainAxis()==null) return Double.NaN;
		int mouseX=trigger.getX();
		int mouseY=trigger.getY();
		Rectangle2D dataArea=chart.getScreenDataArea(mouseX, mouseY);
		if (dataArea==null||!dataArea.contains(mouseX, mouseY)) return Double.NaN;
		return plot.getDomainAxis().java2DToValue(mouseX, dataArea, plot.getDomainAxisEdge());
	}

	private void updateTopChartSelectionMarkers(float minRT, float maxRT, float activeMaxTic, RawBrowserXicController xicController) {
		clearTopChartSelectionMarkers();
		if (topChromatogramChart==null) return;
		if (!Float.isFinite(minRT)||!Float.isFinite(maxRT)) return;
		XYPlot plot=topChromatogramChart.getChart().getXYPlot();
		if (plot==null) return;
		double markerMax=Math.max(xicController.isXicModeActive()?xicController.getActiveXicMax():activeMaxTic, 1.0f);
		BasicStroke stroke=new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0.0f, new float[] {3.0f, 5.0f}, 0.0f);
		if (Math.abs(minRT-maxRT)<1e-6) {
			XYLineAnnotation marker=new XYLineAnnotation(minRT, 0.0, minRT, markerMax, stroke, java.awt.Color.black);
			chromatogramSelectionAnnotations.add(marker);
			plot.addAnnotation(marker, false);
		} else {
			XYLineAnnotation minMarker=new XYLineAnnotation(minRT, 0.0, minRT, markerMax, stroke, java.awt.Color.black);
			XYLineAnnotation maxMarker=new XYLineAnnotation(maxRT, 0.0, maxRT, markerMax, stroke, java.awt.Color.black);
			chromatogramSelectionAnnotations.add(minMarker);
			chromatogramSelectionAnnotations.add(maxMarker);
			plot.addAnnotation(minMarker, false);
			plot.addAnnotation(maxMarker, false);
		}
		topChromatogramChart.repaint();
	}

	private ExtendedChartPanel buildChromatogramChart(XYTrace activeChromatogram, RawBrowserXicController xicController) {
		ArrayList<XYTraceInterface> traces=new ArrayList<>();
		LegendMode legendMode=LegendMode.NONE;
		String yAxis="TIC";
		String tooltip=TIC_TOOLTIP;
		if (xicController.isXicModeActive()) {
			traces.addAll(xicController.getActiveXicTraces());
			legendMode=LegendMode.DRAWER;
			yAxis="XIC";
			tooltip=XIC_TOOLTIP;
		} else if (activeChromatogram!=null) {
			traces.add(activeChromatogram);
		}
		ExtendedChartPanel chart=BasicChartGenerator.getChart("Time (min)", yAxis, legendMode, traces.toArray(new XYTraceInterface[0]));
		chart.setToolTipText(tooltip);
		return chart;
	}

	private Color withAlpha(Color color, float alpha) {
		return new Color(color.getRed()/255.0f, color.getGreen()/255.0f, color.getBlue()/255.0f, alpha);
	}

	private int getMatchingXicTargetIndex(double mz, RawBrowserXicController xicController) {
		if (!xicController.isXicModeActive()) return -1;
		for (int i=0; i<xicController.getActiveXicTargets().size(); i++) {
			double target=xicController.getActiveXicTargets().get(i).mz();
			double tolerance=xicController.getActiveXicTolerance().toleranceMz(target);
			if (mz>=target-tolerance&&mz<=target+tolerance) {
				return i;
			}
		}
		return -1;
	}

	private void applySpectrumXicOverlays(ExtendedChartPanel spectrumChart, AcquiredSpectrum spectrum, RawBrowserXicController xicController) {
		if (!xicController.isXicModeActive()||spectrumChart==null||spectrum==null) return;
		XYPlot plot=spectrumChart.getChart().getXYPlot();
		if (plot==null) return;
		double spectrumMax=Math.max(0.0, MatrixMath.max(spectrum.getIntensityArray()));
		if (spectrumMax<=0.0) spectrumMax=1.0;
		double divider=spectrumChart.getDivider();
		if (!(divider>0.0)) divider=1.0;
		double chartMaxY=spectrumMax/divider;
		for (int i=0; i<xicController.getActiveXicTargets().size(); i++) {
			double target=xicController.getActiveXicTargets().get(i).mz();
			double tol=xicController.getActiveXicTolerance().toleranceMz(target);
			double left=target-tol;
			double right=target+tol;
			Color base=getXicColor(i);
			Color shade=withAlpha(base, 0.2f);
			plot.addAnnotation(new XYBoxAnnotation(left, 0.0, right, chartMaxY, new BasicStroke(1.0f), shade, shade));
		}
		if (!(plot.getRenderer(0) instanceof XYLineAndShapeRenderer)) return;
		if (!(plot.getDataset(0) instanceof XYSeriesCollection)) return;
		XYLineAndShapeRenderer renderer=(XYLineAndShapeRenderer)plot.getRenderer(0);
		XYSeriesCollection dataset=(XYSeriesCollection)plot.getDataset(0);
		for (int seriesIndex=0; seriesIndex<dataset.getSeriesCount(); seriesIndex++) {
			if (dataset.getItemCount(seriesIndex)<2) continue;
			double y=dataset.getYValue(seriesIndex, 1);
			if (Math.abs(y)<1e-12) continue; // baseline or empty peak
			double mz=dataset.getXValue(seriesIndex, 0);
			int targetIndex=getMatchingXicTargetIndex(mz, xicController);
			if (targetIndex<0) continue;
			Color color=getXicColor(targetIndex);
			renderer.setSeriesPaint(seriesIndex, color);
			renderer.setSeriesStroke(seriesIndex, new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		}
	}

	private void restoreSpectrumFocusIfNeeded(ExtendedChartPanel spectrumChart, ExtendedChartPanel imsChart, ExtendedChartPanel histogramChart) {
		ExtendedChartPanel target=null;
		switch (focusedChartTarget) {
			case SPECTRUM:
				target=spectrumChart;
				break;
			case IMS:
				target=(imsChart!=null)?imsChart:spectrumChart;
				break;
			case HISTOGRAM:
				target=histogramChart;
				break;
			default:
				break;
		}
		if (target!=null) {
			ExtendedChartPanel focusTarget=target;
			SwingUtilities.invokeLater(focusTarget::requestFocusInWindow);
		}
	}

	private void updateSpectrumRightComponent(ExtendedChartPanel spectrumHistogram, Pair<String[], String[]> metadata) {
		int selectedTab=spectrumDetailsTabs.getSelectedIndex();
		scanMetadataModel.update(metadata);
		boolean hasMetadata=scanMetadataModel.getRowCount()>0;
		spectrumDetailsTabs.setComponentAt(SPECTRUM_HISTOGRAM_TAB_INDEX, spectrumHistogram);
		spectrumDetailsTabs.setEnabledAt(SPECTRUM_PROPERTIES_TAB_INDEX, hasMetadata);
		if (selectedTab==SPECTRUM_PROPERTIES_TAB_INDEX&&hasMetadata) {
			spectrumDetailsTabs.setSelectedIndex(SPECTRUM_PROPERTIES_TAB_INDEX);
		} else {
			spectrumDetailsTabs.setSelectedIndex(SPECTRUM_HISTOGRAM_TAB_INDEX);
		}
		spectrumSplit.setRightComponent(spectrumDetailsTabs);
	}

	private ExtendedChartPanel currentHistogramChartPanel() {
		Component right=spectrumSplit.getRightComponent();
		ExtendedChartPanel direct=componentAsChartPanel(right);
		if (direct!=null) return direct;
		if (right==spectrumDetailsTabs) {
			for (int i=0; i<spectrumDetailsTabs.getTabCount(); i++) {
				ExtendedChartPanel tab=componentAsChartPanel(spectrumDetailsTabs.getComponentAt(i));
				if (tab!=null) return tab;
			}
		}
		return null;
	}

	private ExtendedChartPanel componentAsChartPanel(Component component) {
		return component instanceof ExtendedChartPanel?(ExtendedChartPanel)component:null;
	}

	private ExtendedChartPanel currentSpectrumChartPanel() {
		ExtendedChartPanel direct=componentAsChartPanel(spectrumSplit.getLeftComponent());
		if (direct!=null) return direct;
		return componentAsChartPanel(imsSpectrumSplit.getLeftComponent());
	}
}
