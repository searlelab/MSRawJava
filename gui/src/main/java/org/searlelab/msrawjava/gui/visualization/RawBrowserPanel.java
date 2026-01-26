package org.searlelab.msrawjava.gui.visualization;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.algorithms.RawSpectrumMergeUtils;
import org.searlelab.msrawjava.gui.charts.BasicChartGenerator;
import org.searlelab.msrawjava.gui.charts.ExtendedChartPanel;
import org.searlelab.msrawjava.gui.charts.GraphType;
import org.searlelab.msrawjava.gui.charts.XYTrace;
import org.searlelab.msrawjava.gui.charts.XYTraceInterface;
import org.searlelab.msrawjava.gui.GUIPreferences;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.PPMMassTolerance;
import org.searlelab.msrawjava.model.ScanSummary;
import org.searlelab.msrawjava.model.PeakWithIMS;
import org.searlelab.msrawjava.io.tims.TIMSPeakPicker;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;


public class RawBrowserPanel extends JPanel implements AutoCloseable {
	private static final long serialVersionUID=1L;

	private static final String STRUCTURE_TITLE="Structure";
	private static final String GLOBAL_TITLE="Global";
	private static final String BOXPLOT_TITLE="Range Statistics";
	private static final float MINIMUM_MS1_INTENSITY=3.0f;
	private static final float MINIMUM_MS2_INTENSITY=1.0f;
	private static final double DEFAULT_MAIN_SPLIT=0.3;
	private static final double DEFAULT_SCANS_SPLIT=0.5;
	private static final double DEFAULT_SPECTRUM_SPLIT=0.85;
	private static final double DEFAULT_IMS_SPLIT=50.0/85.0;
	private static final double DEFAULT_BOXPLOT_SPLIT=0.5;

	private final StripeFileInterface stripe;
	private final String displayName;
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

	public RawBrowserPanel(StripeFileInterface stripe, String displayName) {
		super(new BorderLayout());
		this.stripe=stripe;
		this.displayName=(displayName==null||displayName.isBlank())?"Raw Browser":displayName;
		this.peakPickAcrossIMS=stripe instanceof BrukerTIMSFile;

		initUi();
		startLoad();
	}

	public RawBrowserPanel(StripeFileInterface stripe, String displayName, RawBrowserData data) {
		super(new BorderLayout());
		this.stripe=stripe;
		this.displayName=(displayName==null||displayName.isBlank())?"Raw Browser":displayName;
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
		new javax.swing.SwingWorker<RawBrowserData, Void>() {
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
		new javax.swing.SwingWorker<List<AcquiredSpectrum>, Void>() {
			@Override
			protected List<AcquiredSpectrum> doInBackground() throws Exception {
				ArrayList<AcquiredSpectrum> spectra=new ArrayList<>();
				for (ScanSummary summary : summaries) {
					AcquiredSpectrum spectrum=stripe.getSpectrum(summary);
					if (spectrum!=null) spectra.add(spectrum);
				}
				return spectra;
			}
			@Override
			protected void done() {
				if (token!=selectionToken) return;
				try {
					List<AcquiredSpectrum> spectra=get();
					resetScan(spectra);
					primaryTabs.setSelectedIndex(0);
				} catch (Exception ex) {
					Logger.logException(ex);
				}
			}
		}.execute();
	}

	private void resetScan(List<AcquiredSpectrum> entries) {
		if (entries==null||entries.isEmpty()) {
			rawSplit.setTopComponent(buildTicChart());
			spectrumSplit.setLeftComponent(new JLabel("No spectrum available"));
			spectrumSplit.setRightComponent(new JLabel(""));
			return;
		}

		final AcquiredSpectrum spectrum;
		float minRT=Float.MAX_VALUE;
		float maxRT=-Float.MAX_VALUE;

		if (entries.size()==1) {
			spectrum=entries.get(0);
			float rt=spectrum.getScanStartTime()/60f;
			minRT=rt;
			maxRT=rt;
		} else {
			spectrum=RawSpectrumMergeUtils.mergeSpectra(entries, new PPMMassTolerance(10.0));
			for (AcquiredSpectrum entry : entries) {
				float rt=entry.getScanStartTime()/60f;
				minRT=Math.min(minRT, rt);
				maxRT=Math.max(maxRT, rt);
			}
		}

		AcquiredSpectrum displaySpectrum=peakPickSpectrumIfIMS(spectrum);
		ExtendedChartPanel spectrumChart=BasicChartGenerator.getChart("m/z", "Intensity", false, new XYTrace(displaySpectrum));
		boolean hasIms=displaySpectrum.getIonMobilityArray().isPresent()&&MatrixMath.max(displaySpectrum.getIntensityArray())>0.0f;
		if (hasIms) {
			ExtendedChartPanel imsChart=BasicChartGenerator.getChart("Ion Mobility", "m/z", false, new ImsSpectrumWrapper(displaySpectrum));
			imsSpectrumSplit.setLeftComponent(spectrumChart);
			imsSpectrumSplit.setRightComponent(imsChart);
			applySplitRatio(imsSpectrumSplit, GUIPreferences.getRawBrowserImsSplitRatio(DEFAULT_IMS_SPLIT));
			spectrumSplit.setLeftComponent(imsSpectrumSplit);
		} else {
			spectrumSplit.setLeftComponent(spectrumChart);
		}

		XYTrace intensityHistogram=HistogramUtils.histogramFromLog10(displaySpectrum.getIntensityArray(), "Log10 Fragment Intensity Distribution");
		ExtendedChartPanel spectrumHistogram=BasicChartGenerator.getChart("Log10 Intensity", "Count (N="+spectrum.getIntensityArray().length+")", false, intensityHistogram);
		spectrumSplit.setRightComponent(spectrumHistogram);

		ArrayList<XYTraceInterface> markers=new ArrayList<>();
		if (minRT==maxRT) {
			markers.add(new XYTrace(new double[] {minRT, minRT}, new double[] {0, maxTic}, GraphType.dashedline, "marker"));
		} else {
			markers.add(new XYTrace(new double[] {minRT, minRT}, new double[] {0, maxTic}, GraphType.dashedline, "marker-min"));
			markers.add(new XYTrace(new double[] {maxRT, maxRT}, new double[] {0, maxTic}, GraphType.dashedline, "marker-max"));
		}
		rawSplit.setTopComponent(buildTicChart(markers.toArray(new XYTraceInterface[0])));

		applySplitPreferences();
	}

	private void applySplitPreferences() {
		withSplitSaveSuppressed(() -> {
			applySplitRatio(split, GUIPreferences.getRawBrowserMainSplitRatio(DEFAULT_MAIN_SPLIT));
			applySplitRatio(rawSplit, GUIPreferences.getRawBrowserScansSplitRatio(DEFAULT_SCANS_SPLIT));
			applySplitRatio(spectrumSplit, GUIPreferences.getRawBrowserSpectrumSplitRatio(DEFAULT_SPECTRUM_SPLIT));
			applySplitRatio(imsSpectrumSplit, GUIPreferences.getRawBrowserImsSplitRatio(DEFAULT_IMS_SPLIT));
			applySplitRatio(boxplotSplit, GUIPreferences.getRawBrowserBoxplotSplitRatio(DEFAULT_BOXPLOT_SPLIT));
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
		pane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
			if (suppressSplitSave>0) return;
			double ratio=getSplitRatio(pane);
			if (ratio>0.0) saver.accept(ratio);
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

		return new org.searlelab.msrawjava.model.PrecursorScan(
				spectrum.getSpectrumName(),
				spectrum.getSpectrumIndex(),
				spectrum.getScanStartTime(),
				spectrum.getFraction(),
				spectrum.getIsolationWindowLower(),
				spectrum.getIsolationWindowUpper(),
				spectrum.getIonInjectionTime(),
				mzOut,
				intensityOut,
				imsOut
		);
	}

	@Override
	public void close() throws Exception {
		if (stripe!=null&&stripe.isOpen()) {
			stripe.close();
		}
	}

}
