package org.searlelab.msrawjava.gui.visualization;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.algorithms.RawSpectrumMergeUtils;
import org.searlelab.msrawjava.gui.charts.AcquiredSpectrumWrapper;
import org.searlelab.msrawjava.gui.charts.BasicChartGenerator;
import org.searlelab.msrawjava.gui.charts.ExtendedChartPanel;
import org.searlelab.msrawjava.gui.charts.GraphType;
import org.searlelab.msrawjava.gui.charts.XYTrace;
import org.searlelab.msrawjava.gui.charts.XYTraceInterface;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.PPMMassTolerance;
import org.searlelab.msrawjava.model.ScanSummary;


public class RawBrowserPanel extends JPanel implements AutoCloseable {
	private static final long serialVersionUID=1L;

	private static final String STRUCTURE_TITLE="Structure";
	private static final String GLOBAL_TITLE="Global";
	private static final String INTENSITY_DISTRIBUTION_TITLE="Intensity Distributions";
	private static final String BOXPLOT_TITLE="Range Statistics";

	private final StripeFileInterface stripe;
	private final String displayName;

	private RawScanTableModel model;
	private JTable table;
	private TableRowSorter<TableModel> rowSorter;
	private JTextField filterField;

	private final JSplitPane boxplotSplit=new JSplitPane(JSplitPane.VERTICAL_SPLIT);
	private final JSplitPane distributionSplit=new JSplitPane(JSplitPane.VERTICAL_SPLIT);
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

	public RawBrowserPanel(StripeFileInterface stripe, String displayName) {
		super(new BorderLayout());
		this.stripe=stripe;
		this.displayName=(displayName==null||displayName.isBlank())?"Raw Browser":displayName;

		initUi();
		startLoad();
	}

	public RawBrowserPanel(StripeFileInterface stripe, String displayName, RawBrowserData data) {
		super(new BorderLayout());
		this.stripe=stripe;
		this.displayName=(displayName==null||displayName.isBlank())?"Raw Browser":displayName;

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
		primaryTabs.addTab(INTENSITY_DISTRIBUTION_TITLE, distributionSplit);
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
		distributionSplit.setContinuousLayout(true);
		distributionSplit.setOneTouchExpandable(true);
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

		JTabbedPane distributionTabs=new JTabbedPane();
		distributionTabs.addTab("Precursor Intensity", BasicChartGenerator.getChart("Log10 Precursor Intensity", "Count", false, data.getPrecursorIntensityHistogram()));
		distributionTabs.addTab("Fragment Intensity", BasicChartGenerator.getChart("Log10 Fragment Intensity", "Count", false, data.getFragmentIntensityHistogram()));
		distributionSplit.setTopComponent(BasicChartGenerator.getChart("Time (min)", "Basepeak Intensity", false, data.getBasepeakTrace()));
		distributionSplit.setBottomComponent(distributionTabs);
		distributionSplit.setDividerLocation(400);

		boxplotSplit.setTopComponent(VisualizationCharts.getBoxplotChart(null, "Precursor Isolation Window", "Ion Injection Time (ms)", data.getIitByRange()));
		boxplotSplit.setBottomComponent(VisualizationCharts.getBoxplotChart(null, "Retention Time Bin (min)", "Ion Injection Time (ms)", data.getIitByRt()));
		boxplotSplit.setDividerLocation(400);

		primaryTabs.setComponentAt(primaryTabs.indexOfTab(STRUCTURE_TITLE), structureChart);
		primaryTabs.setComponentAt(primaryTabs.indexOfTab(GLOBAL_TITLE), globalChart);

		if (model.getRowCount()>0) {
			table.addRowSelectionInterval(0, 0);
		}
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
		int location=split.getDividerLocation();
		if (location<=5) location=400;
		int locationRaw=rawSplit.getDividerLocation();
		if (locationRaw<=5) locationRaw=400;
		int locationSpectrum=spectrumSplit.getDividerLocation();

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

		ExtendedChartPanel spectrumChart=BasicChartGenerator.getChart("m/z", "Intensity", false, new AcquiredSpectrumWrapper(spectrum));
		if (spectrum.getIonMobilityArray().isPresent()&&MatrixMath.max(spectrum.getIntensityArray())>0.0f) {
			ExtendedChartPanel imsChart=BasicChartGenerator.getChart("Ion Mobility", "m/z", false, new ImsSpectrumWrapper(spectrum));
			int dividerLocation=imsSpectrumSplit.getDividerLocation();
			imsSpectrumSplit.setLeftComponent(spectrumChart);
			imsSpectrumSplit.setRightComponent(imsChart);
			if (dividerLocation!=0) imsSpectrumSplit.setDividerLocation(dividerLocation);
			spectrumSplit.setLeftComponent(imsSpectrumSplit);
		} else {
			spectrumSplit.setLeftComponent(spectrumChart);
		}

		XYTrace intensityHistogram=HistogramUtils.histogramFromLog10(spectrum.getIntensityArray(), "Log10 Fragment Intensity Distribution");
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

		if (locationSpectrum<=5) {
			locationSpectrum=Math.round(split.getWidth()*0.8f);
		}
		spectrumSplit.setDividerLocation(locationSpectrum);
		rawSplit.setDividerLocation(locationRaw);
		split.setDividerLocation(location);
	}

	@Override
	public void close() throws Exception {
		if (stripe!=null&&stripe.isOpen()) {
			stripe.close();
		}
	}

}
