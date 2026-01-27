package org.searlelab.msrawjava.gui.visualization;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleConsumer;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.algorithms.RawSpectrumMergeUtils;
import org.searlelab.msrawjava.gui.GUIPreferences;
import org.searlelab.msrawjava.gui.graphing.BasicChartGenerator;
import org.searlelab.msrawjava.gui.graphing.ExtendedChartPanel;
import org.searlelab.msrawjava.gui.graphing.GraphType;
import org.searlelab.msrawjava.gui.graphing.HistogramUtils;
import org.searlelab.msrawjava.gui.graphing.XYTrace;
import org.searlelab.msrawjava.gui.graphing.XYTraceInterface;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.io.tims.TIMSPeakPicker;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.PPMMassTolerance;
import org.searlelab.msrawjava.model.PeakWithIMS;
import org.searlelab.msrawjava.model.ScanSummary;

/**
 * Panel for inspecting raw scans, chromatograms, and spectra.
 */
public class RawBrowserPanel extends JPanel implements AutoCloseable {
	private static final long serialVersionUID=1L;

	private static final String STRUCTURE_TITLE="Structure";
	private static final String GLOBAL_TITLE="Global";
	private static final String BOXPLOT_TITLE="Range Statistics";
	private static final float MINIMUM_MS1_INTENSITY=3.0f;
	private static final float MINIMUM_MS2_INTENSITY=1.0f;

	private final StripeFileInterface stripe;
	private final boolean peakPickAcrossIMS;

	private RawScanTableModel model;
	private JTable table;
	private TableRowSorter<TableModel> rowSorter;
	private JTextField filterField;

	private final JSplitPane boxplotSplit=new JSplitPane(JSplitPane.VERTICAL_SPLIT);
	private final JSplitPane rawSplit=new JSplitPane(JSplitPane.VERTICAL_SPLIT);
	private final JSplitPane spectrumSplit=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	private final JSplitPane imsSpectrumSplit=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	private final JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	private final JTabbedPane primaryTabs=new JTabbedPane();

	private XYTrace chromatogram;
	private float maxTic;

	private ExtendedChartPanel structureChart;
	private ExtendedChartPanel globalChart;
	private long selectionToken=0L;
	private int suppressSplitSave=0;

	private static final class SelectionResult {
		private final List<AcquiredSpectrum> entries;
		private final AcquiredSpectrum displaySpectrum;
		private final float minRT;
		private final float maxRT;

		private SelectionResult(List<AcquiredSpectrum> entries, AcquiredSpectrum displaySpectrum, float minRT, float maxRT) {
			this.entries=entries;
			this.displaySpectrum=displaySpectrum;
			this.minRT=minRT;
			this.maxRT=maxRT;
		}
	}

	public RawBrowserPanel(StripeFileInterface stripe, RawBrowserData data) {
		super(new BorderLayout());
		this.stripe=stripe;
		this.peakPickAcrossIMS=stripe instanceof BrukerTIMSFile;

		initUi();
		if (data!=null) {
			applyData(data);
		} else {
			startLoad();
		}
	}

	private void initUi() {
		model=new RawScanTableModel();
		table=new JTable(model);
		rowSorter=new TableRowSorter<>(table.getModel());
		table.setRowSorter(rowSorter);

		filterField=new JTextField();
		filterField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				updateFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				updateFilter();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				updateFilter();
			}
		});

		JPanel searchPanel=new JPanel(new BorderLayout());
		searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
		searchPanel.add(filterField, BorderLayout.CENTER);

		JPanel left=new JPanel(new BorderLayout());
		left.add(new JScrollPane(table), BorderLayout.CENTER);
		left.add(searchPanel, BorderLayout.SOUTH);

		primaryTabs.addTab("Scans", rawSplit);
		rawSplit.setBottomComponent(spectrumSplit);
		primaryTabs.addTab(BOXPLOT_TITLE, boxplotSplit);
		primaryTabs.addTab(STRUCTURE_TITLE, new JLabel("Loading..."));
		primaryTabs.addTab(GLOBAL_TITLE, new JLabel("Loading..."));

		split.setLeftComponent(left);
		split.setRightComponent(primaryTabs);
		add(split, BorderLayout.CENTER);

		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				updateToSelected();
			}
		});

		boxplotSplit.setContinuousLayout(true);
		boxplotSplit.setOneTouchExpandable(true);
		rawSplit.setContinuousLayout(true);
		rawSplit.setOneTouchExpandable(true);
		split.setContinuousLayout(true);
		split.setOneTouchExpandable(true);

		spectrumSplit.setResizeWeight(0.80);
		spectrumSplit.setDividerSize(8);
		spectrumSplit.setContinuousLayout(true);
		spectrumSplit.setOneTouchExpandable(true);

		imsSpectrumSplit.setResizeWeight(0.75);
		imsSpectrumSplit.setDividerSize(8);
		imsSpectrumSplit.setContinuousLayout(true);
		imsSpectrumSplit.setOneTouchExpandable(true);

		registerSplitPreference(split, GUIPreferences::setRawBrowserMainSplitRatio);
		registerSplitPreference(rawSplit, GUIPreferences::setRawBrowserScansSplitRatio);
		registerSplitPreference(spectrumSplit, GUIPreferences::setRawBrowserSpectrumSplitRatio);
		registerSplitPreference(imsSpectrumSplit, GUIPreferences::setRawBrowserImsSplitRatio);
		registerSplitPreference(boxplotSplit, GUIPreferences::setRawBrowserBoxplotSplitRatio);

		applySplitPreferences();
	}

	private void updateFilter() {
		String text=filterField.getText();
		if (text.trim().isEmpty()) {
			rowSorter.setRowFilter(null);
		} else {
			rowSorter.setRowFilter(RowFilter.regexFilter("(?i)"+text));
		}
	}

	private void startLoad() {
		new SwingWorker<RawBrowserData, Void>() {
			@Override
			protected RawBrowserData doInBackground() throws Exception {
				return buildData();
			}

			@Override
			protected void done() {
				try {
					RawBrowserData data=get();
					applyData(data);
				} catch (Exception ex) {
					Logger.logException(ex);
					setErrorState("Cannot parse file.");
				}
			}
		}.execute();
	}

	private void setErrorState(String message) {
		split.setRightComponent(new JLabel(message));
	}

	private RawBrowserData buildData() throws Exception {
		return RawBrowserDataLoader.build(stripe);
	}

	private void applyData(RawBrowserData data) {
		this.chromatogram=data.getChromatogram();
		this.maxTic=data.getMaxTic();
		this.structureChart=data.getStructureChart();
		this.globalChart=data.getGlobalChart();

		model.updateEntries(data.getScans());

		ExtendedChartPanel ticChart=buildTicChart();
		rawSplit.setTopComponent(ticChart);

		boxplotSplit.setTopComponent(VisualizationCharts.getBoxplotChart(null, "Precursor Isolation Window", "Ion Injection Time (ms)", data.getIitByRange()));
		boxplotSplit.setBottomComponent(VisualizationCharts.getBoxplotChart(null, "Retention Time Bin (min)", "Ion Injection Time (ms)", data.getIitByRt()));

		primaryTabs.setComponentAt(primaryTabs.indexOfTab(STRUCTURE_TITLE), structureChart);
		primaryTabs.setComponentAt(primaryTabs.indexOfTab(GLOBAL_TITLE), globalChart);

		if (model.getRowCount()>0) {
			table.addRowSelectionInterval(0, 0);
		}

		SwingUtilities.invokeLater(this::applySplitPreferences);
	}

	private ExtendedChartPanel buildTicChart(XYTraceInterface... markerTraces) {
		ArrayList<XYTraceInterface> traces=new ArrayList<>();
		if (chromatogram!=null) traces.add(chromatogram);
		if (markerTraces!=null) {
			for (XYTraceInterface marker : markerTraces) {
				traces.add(marker);
			}
		}
		return BasicChartGenerator.getChart("Time (min)", "Precursor TIC", false, traces.toArray(new XYTraceInterface[0]));
	}

	private void updateToSelected() {
		int[] selection=table.getSelectedRows();
		if (selection.length<=0) return;

		ArrayList<ScanSummary> summaries=new ArrayList<>();
		for (int row : selection) {
			ScanSummary entry=model.getSelectedRow(table.convertRowIndexToModel(row));
			summaries.add(entry);
		}
		long token=++selectionToken;
		new SwingWorker<SelectionResult, Void>() {
			@Override
			protected SelectionResult doInBackground() throws Exception {
				ArrayList<AcquiredSpectrum> spectra=new ArrayList<>();
				for (ScanSummary summary : summaries) {
					AcquiredSpectrum spectrum=stripe.getSpectrum(summary);
					if (spectrum!=null) spectra.add(spectrum);
				}
				if (spectra.isEmpty()) return new SelectionResult(spectra, null, Float.NaN, Float.NaN);

				final AcquiredSpectrum merged;
				float minRT=Float.MAX_VALUE;
				float maxRT=-Float.MAX_VALUE;
				if (spectra.size()==1) {
					merged=spectra.get(0);
					float rt=merged.getScanStartTime()/60f;
					minRT=rt;
					maxRT=rt;
				} else {
					merged=RawSpectrumMergeUtils.mergeSpectra(spectra, new PPMMassTolerance(10.0));
					for (AcquiredSpectrum entry : spectra) {
						float rt=entry.getScanStartTime()/60f;
						minRT=Math.min(minRT, rt);
						maxRT=Math.max(maxRT, rt);
					}
				}
				AcquiredSpectrum display=peakPickSpectrumIfIMS(merged);
				return new SelectionResult(spectra, display, minRT, maxRT);
			}

			@Override
			protected void done() {
				if (token!=selectionToken) return;
				try {
					SelectionResult result=get();
					resetScan(result);
					primaryTabs.setSelectedIndex(0);
				} catch (Exception ex) {
					Logger.logException(ex);
				}
			}
		}.execute();
	}

	private void resetScan(SelectionResult result) {
		if (result==null||result.entries==null||result.entries.isEmpty()) {
			rawSplit.setTopComponent(buildTicChart());
			spectrumSplit.setLeftComponent(new JLabel("No spectrum available"));
			spectrumSplit.setRightComponent(new JLabel(""));
			return;
		}

		AcquiredSpectrum displaySpectrum=result.displaySpectrum;
		if (displaySpectrum==null) {
			rawSplit.setTopComponent(buildTicChart());
			spectrumSplit.setLeftComponent(new JLabel("No spectrum available"));
			spectrumSplit.setRightComponent(new JLabel(""));
			return;
		}
		ExtendedChartPanel spectrumChart=BasicChartGenerator.getChart("m/z", "Intensity", false, new XYTrace(displaySpectrum));
		boolean hasIms=displaySpectrum.getIonMobilityArray().isPresent()&&MatrixMath.max(displaySpectrum.getIntensityArray())>0.0f;
		if (hasIms) {
			ExtendedChartPanel imsChart=BasicChartGenerator.getChart("Ion Mobility", "m/z", false, new ImsSpectrumWrapper(displaySpectrum));
			imsSpectrumSplit.setLeftComponent(spectrumChart);
			imsSpectrumSplit.setRightComponent(imsChart);
			applySplitRatio(imsSpectrumSplit, GUIPreferences.getRawBrowserImsSplitRatio());
			spectrumSplit.setLeftComponent(imsSpectrumSplit);
		} else {
			spectrumSplit.setLeftComponent(spectrumChart);
		}

		XYTrace intensityHistogram=HistogramUtils.histogramFromLog10(displaySpectrum.getIntensityArray(), "Log10 Fragment Intensity Distribution");
		ExtendedChartPanel spectrumHistogram=BasicChartGenerator.getChart("Log10 Intensity", "Count (N="+displaySpectrum.getIntensityArray().length+")", false,
				intensityHistogram);
		spectrumSplit.setRightComponent(spectrumHistogram);

		ArrayList<XYTraceInterface> markers=new ArrayList<>();
		if (result.minRT==result.maxRT) {
			markers.add(new XYTrace(new double[] {result.minRT, result.minRT}, new double[] {0, maxTic}, GraphType.dashedline, "marker", java.awt.Color.black,
					2.0f));
		} else {
			markers.add(new XYTrace(new double[] {result.minRT, result.minRT}, new double[] {0, maxTic}, GraphType.dashedline, "marker-min",
					java.awt.Color.black, 2.0f));
			markers.add(new XYTrace(new double[] {result.maxRT, result.maxRT}, new double[] {0, maxTic}, GraphType.dashedline, "marker-max",
					java.awt.Color.black, 2.0f));
		}
		rawSplit.setTopComponent(buildTicChart(markers.toArray(new XYTraceInterface[0])));

		applySplitPreferences();
	}

	private void applySplitPreferences() {
		withSplitSaveSuppressed(() -> {
			applySplitRatio(split, GUIPreferences.getRawBrowserMainSplitRatio());
			applySplitRatio(rawSplit, GUIPreferences.getRawBrowserScansSplitRatio());
			applySplitRatio(spectrumSplit, GUIPreferences.getRawBrowserSpectrumSplitRatio());
			applySplitRatio(imsSpectrumSplit, GUIPreferences.getRawBrowserImsSplitRatio());
			applySplitRatio(boxplotSplit, GUIPreferences.getRawBrowserBoxplotSplitRatio());
		});
	}

	private void applySplitRatio(JSplitPane pane, double ratio) {
		if (ratio<=0.0||ratio>=1.0) return;
		pane.setResizeWeight(ratio);
		pane.setDividerLocation(ratio);
	}

	private double getSplitRatio(JSplitPane pane) {
		int size=(pane.getOrientation()==JSplitPane.HORIZONTAL_SPLIT)?pane.getWidth():pane.getHeight();
		if (size<10) return -1.0;
		double ratio=pane.getDividerLocation()/(double)size;
		if (ratio<=0.0||ratio>=1.0) return -1.0;
		return ratio;
	}

	private void registerSplitPreference(JSplitPane pane, DoubleConsumer saver) {
		AtomicBoolean dragging=new AtomicBoolean(false);
		installDividerDragListener(pane, dragging, saver);
		pane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
			if (suppressSplitSave>0) return;
			if (!dragging.get()) return;
			double ratio=getSplitRatio(pane);
			if (ratio>0.0) saver.accept(ratio);
		});
	}

	private void installDividerDragListener(JSplitPane pane, AtomicBoolean dragging, DoubleConsumer saver) {
		if (pane.getClientProperty("rawBrowser.dividerListener")!=null) return;
		pane.putClientProperty("rawBrowser.dividerListener", Boolean.TRUE);
		SwingUtilities.invokeLater(() -> {
			if (!(pane.getUI() instanceof BasicSplitPaneUI)) return;
			BasicSplitPaneUI ui=(BasicSplitPaneUI)pane.getUI();
			BasicSplitPaneDivider divider=ui.getDivider();
			if (divider==null) return;
			divider.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mousePressed(java.awt.event.MouseEvent e) {
					dragging.set(true);
				}

				@Override
				public void mouseReleased(java.awt.event.MouseEvent e) {
					dragging.set(false);
					if (suppressSplitSave>0) return;
					double ratio=getSplitRatio(pane);
					if (ratio>0.0) saver.accept(ratio);
				}
			});
		});
	}

	private void withSplitSaveSuppressed(Runnable action) {
		suppressSplitSave++;
		try {
			action.run();
		} finally {
			suppressSplitSave--;
		}
	}

	private AcquiredSpectrum peakPickSpectrumIfIMS(AcquiredSpectrum spectrum) {
		if (spectrum==null) return null;
		if (!peakPickAcrossIMS) return spectrum;
		if (spectrum.getIonMobilityArray().isEmpty()) return spectrum;

		double[] mz=spectrum.getMassArray();
		float[] intensity=spectrum.getIntensityArray();
		float[] ims=spectrum.getIonMobilityArray().get();
		if (mz.length==0||intensity.length==0||ims.length==0) return spectrum;

		float minIntensity=(spectrum.getPrecursorMZ()<0.0)?MINIMUM_MS1_INTENSITY:MINIMUM_MS2_INTENSITY;
		ArrayList<PeakWithIMS> peaks=new ArrayList<>(mz.length);
		for (int i=0; i<mz.length; i++) {
			if (intensity[i]>minIntensity) {
				peaks.add(new PeakWithIMS(mz[i], intensity[i], ims[i]));
			}
		}
		if (peaks.isEmpty()) return spectrum;
		ArrayList<PeakWithIMS> picked=TIMSPeakPicker.peakPickAcrossIMS(peaks);
		if (picked==null||picked.isEmpty()) return spectrum;

		picked.sort(null);
		double[] mzOut=new double[picked.size()];
		float[] intensityOut=new float[picked.size()];
		float[] imsOut=new float[picked.size()];
		for (int i=0; i<picked.size(); i++) {
			PeakWithIMS p=picked.get(i);
			mzOut[i]=p.mz;
			intensityOut[i]=p.intensity;
			imsOut[i]=p.ims;
		}

		return new org.searlelab.msrawjava.model.PrecursorScan(spectrum.getSpectrumName(), spectrum.getSpectrumIndex(), spectrum.getScanStartTime(),
				spectrum.getFraction(), spectrum.getIsolationWindowLower(), spectrum.getIsolationWindowUpper(), spectrum.getIonInjectionTime(), mzOut,
				intensityOut, imsOut);
	}

	@Override
	public void close() throws Exception {
		if (stripe!=null&&stripe.isOpen()) {
			stripe.close();
		}
	}

}
