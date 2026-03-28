package org.searlelab.msrawjava.gui.visualization;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleConsumer;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.table.JTableHeader;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYBoxAnnotation;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.algorithms.RawSpectrumMergeUtils;
import org.searlelab.msrawjava.gui.GUIPreferences;
import org.searlelab.msrawjava.gui.graphing.BasicChartGenerator;
import org.searlelab.msrawjava.gui.graphing.BoxPlotGenerator;
import org.searlelab.msrawjava.gui.graphing.ExtendedChartPanel;
import org.searlelab.msrawjava.gui.graphing.GraphType;
import org.searlelab.msrawjava.gui.graphing.LegendMode;
import org.searlelab.msrawjava.gui.graphing.HistogramUtils;
import org.searlelab.msrawjava.gui.graphing.XYTrace;
import org.searlelab.msrawjava.gui.graphing.XYTraceInterface;
import org.searlelab.msrawjava.gui.filebrowser.StripeTableCellRenderer;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.io.tims.TIMSPeakPicker;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.PPMMassTolerance;
import org.searlelab.msrawjava.model.PeakWithIMS;
import org.searlelab.msrawjava.model.Range;
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
	private static final String TIC_TOOLTIP="Total ion current across retention time; selected scan ranges are marked when available.";
	private static final String XIC_TOOLTIP="Extracted ion chromatograms for target m/z values in the selected scan type.";
	private static final String SPECTRUM_TOOLTIP="Mass spectrum for the currently selected scan or merged scan selection.";
	private static final String IMS_TOOLTIP="Ion mobility versus m/z view for the selected spectrum.";
	private static final String HISTOGRAM_TOOLTIP="Log10 fragment intensity distribution for the selected spectrum.";
	private static final Color[] XIC_COLORS=new Color[] {
			new Color(0xE6, 0x4A, 0x19), new Color(0x00, 0x79, 0x6B), new Color(0x1E, 0x88, 0xE5), new Color(0x8E, 0x24, 0xAA), new Color(0x6D, 0x4C, 0x41),
			new Color(0x43, 0xA0, 0x47), new Color(0xFB, 0x8C, 0x00), new Color(0x39, 0x49, 0xAB)};

	private final StripeFileInterface stripe;
	private final boolean peakPickAcrossIMS;

	private RawScanTableModel model;
	private JTable table;
	private TableRowSorter<TableModel> rowSorter;
	private JTextField filterField;
	private JComboBox<ScanTypeFilterOption> scanTypeFilter;
	private JLabel xicLabel;
	private JTextField xicField;
	private JComboBox<XicToleranceOption> xicToleranceFilter;
	private JButton extractXicButton;
	private JPanel topChartContainer;
	private JPanel topChartContent;

	private final JSplitPane boxplotSplit=new JSplitPane(JSplitPane.VERTICAL_SPLIT);
	private final JSplitPane rawSplit=new JSplitPane(JSplitPane.VERTICAL_SPLIT);
	private final JSplitPane spectrumSplit=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	private final JSplitPane imsSpectrumSplit=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	private final JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	private final JTabbedPane primaryTabs=new JTabbedPane();

	private List<ScanSummary> allScans=List.of();
	private XYTrace globalChromatogram;
	private float globalMaxTic;
	private XYTrace activeChromatogram;
	private float activeMaxTic;
	private ScanTypeFilterOption activeScanType=ScanTypeFilterOption.allSpectra();

	private ExtendedChartPanel structureChart;
	private ExtendedChartPanel globalChart;
	private long selectionToken=0L;
	private long xicToken=0L;
	private int suppressSplitSave=0;
	private SelectionResult currentSelection;
	private RawBrowserXicUtils.ParsedXicTargets activeParsedXicTargets=RawBrowserXicUtils.ParsedXicTargets.empty();
	private List<RawBrowserXicUtils.XicTarget> activeXicTargets=List.of();
	private List<XYTrace> activeXicTraces=List.of();
	private XicToleranceOption activeXicTolerance=XicToleranceOption.DEFAULT;
	private float activeXicMax=0.0f;
	private boolean xicActive=false;
	private int activeXicExtractionCount=0;
	private volatile XicExtractionProgress activeXicProgress;
	private Timer xicProgressTimer;
	private long xicProgressTimerToken=-1L;
	private ExtendedChartPanel topChromatogramChart;
	private final ArrayList<XYAnnotation> chromatogramSelectionAnnotations=new ArrayList<>();

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

	private static final class XicExtractionResult {
		private final List<XYTrace> traces;
		private final float maxIntensity;

		private XicExtractionResult(List<XYTrace> traces, float maxIntensity) {
			this.traces=traces;
			this.maxIntensity=maxIntensity;
		}
	}

	private static final class XicExtractionProgress {
		private final long token;
		private final Object lock=new Object();
		private final double[] xMinutes;
		private final double[][] traces;
		private int extractedCount;
		private int flushedCount;
		private float maxIntensity;

		private XicExtractionProgress(long token, double[] xMinutes, double[][] traces) {
			this.token=token;
			this.xMinutes=xMinutes;
			this.traces=traces;
			this.extractedCount=0;
			this.flushedCount=0;
			this.maxIntensity=0.0f;
		}
	}

	private static final class ChartAxisView {
		private final boolean hasDomainRange;
		private final double domainLower;
		private final double domainUpper;

		private ChartAxisView(boolean hasDomainRange, double domainLower, double domainUpper) {
			this.hasDomainRange=hasDomainRange;
			this.domainLower=domainLower;
			this.domainUpper=domainUpper;
		}
	}

	private static final class XicToleranceOption {
		private enum Unit {
			PPM,
			DA
		}

		private static final XicToleranceOption DEFAULT=new XicToleranceOption("10 ppm", Unit.PPM, 10.0);

		private final String label;
		private final Unit unit;
		private final double value;

		private XicToleranceOption(String label, Unit unit, double value) {
			this.label=label;
			this.unit=unit;
			this.value=value;
		}

		private double toleranceMz(double mz) {
			if (unit==Unit.DA) return value;
			return Math.abs(mz)*value/1_000_000.0;
		}

		private static XicToleranceOption[] valuesForUi() {
			return new XicToleranceOption[] {new XicToleranceOption("5 ppm", Unit.PPM, 5.0), DEFAULT, new XicToleranceOption("25 ppm", Unit.PPM, 25.0),
					new XicToleranceOption("100 ppm", Unit.PPM, 100.0), new XicToleranceOption("0.4 m/z", Unit.DA, 0.4),
					new XicToleranceOption("1.0 m/z", Unit.DA, 1.0)};
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private static final class XicInputFilter extends DocumentFilter {
		private static boolean isAllowedSingleChar(char c) {
			return RawBrowserXicUtils.isAllowedXicChar(c);
		}

		@Override
		public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
			if (string==null||string.isEmpty()) return;
			if (string.length()==1) {
				char c=string.charAt(0);
				if (!isAllowedSingleChar(c)) return;
				super.insertString(fb, offset, string, attr);
				return;
			}
			super.insertString(fb, offset, RawBrowserXicUtils.sanitizeXicPasteChunk(string), attr);
		}

		@Override
		public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
			if (text==null) {
				super.replace(fb, offset, length, null, attrs);
				return;
			}
			if (text.length()==1) {
				char c=text.charAt(0);
				if (!isAllowedSingleChar(c)) return;
				super.replace(fb, offset, length, text, attrs);
				return;
			}
			super.replace(fb, offset, length, RawBrowserXicUtils.sanitizeXicPasteChunk(text), attrs);
		}
	}

	private static final class ScanTypeFilterOption {
		private enum Kind {
			ALL,
			MS1,
			MS2_RANGE
		}

		private final Kind kind;
		private final Range range;
		private final String label;

		private ScanTypeFilterOption(Kind kind, Range range, String label) {
			this.kind=kind;
			this.range=range;
			this.label=label;
		}

		private static ScanTypeFilterOption allSpectra() {
			return new ScanTypeFilterOption(Kind.ALL, null, "All spectra");
		}

		private static ScanTypeFilterOption ms1() {
			return new ScanTypeFilterOption(Kind.MS1, null, "MS1");
		}

		private static ScanTypeFilterOption ms2Range(Range range) {
			String start=String.format(Locale.ROOT, "%.1f", Math.round(range.getStart()*10.0f)/10.0f);
			String stop=String.format(Locale.ROOT, "%.1f", Math.round(range.getStop()*10.0f)/10.0f);
			return new ScanTypeFilterOption(Kind.MS2_RANGE, range, "MS2 "+start+" to "+stop+" m/z");
		}

		private boolean includes(ScanSummary summary) {
			if (summary==null) return false;
			double precursorMz=summary.getPrecursorMz();
			return switch (kind) {
				case ALL -> true;
				case MS1 -> precursorMz<0.0;
				case MS2_RANGE -> precursorMz>=0.0&&range!=null&&range.contains(precursorMz);
			};
		}

		private boolean isAll() {
			return kind==Kind.ALL;
		}

		private boolean isMs1() {
			return kind==Kind.MS1;
		}

		@Override
		public String toString() {
			return label;
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
		table.setToolTipText("Lists scans from the opened file. Select one or more rows to update the charts.");
		rowSorter=new TableRowSorter<>(table.getModel());
		table.setRowSorter(rowSorter);
		installScanHeaderTooltips();
		installScanCellRenderers();

		filterField=new JTextField();
		filterField.setToolTipText("Filter scans in this table by matching text.");
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

		scanTypeFilter=new JComboBox<>();
		scanTypeFilter.setToolTipText("Filter scans by acquisition type and precursor m/z range.");
		initializeScanTypeFilter();
		scanTypeFilter.addActionListener(e -> updateFilter());

		xicLabel=new JLabel("XIC m/zs: ");
		xicField=new JTextField();
		xicField.setToolTipText("Enter one or more m/z targets (comma or whitespace separated).");
		((AbstractDocument)xicField.getDocument()).setDocumentFilter(new XicInputFilter());

		xicToleranceFilter=new JComboBox<>(XicToleranceOption.valuesForUi());
		xicToleranceFilter.setSelectedItem(XicToleranceOption.DEFAULT);
		xicToleranceFilter.setToolTipText("Mass tolerance used for XIC extraction.");

		extractXicButton=new JButton("Extract XICs");
		extractXicButton.setToolTipText("Extract and plot XIC traces for entered m/z values.");
		extractXicButton.addActionListener(e -> onExtractXicClicked());

		JPanel xicEastPanel=new JPanel(new BorderLayout(6, 0));
		xicEastPanel.add(xicToleranceFilter, BorderLayout.CENTER);
		xicEastPanel.add(extractXicButton, BorderLayout.EAST);

		JPanel xicBar=new JPanel(new BorderLayout(6, 0));
		xicBar.add(xicLabel, BorderLayout.WEST);
		xicBar.add(xicField, BorderLayout.CENTER);
		xicBar.add(xicEastPanel, BorderLayout.EAST);

		topChartContent=new JPanel(new BorderLayout());
		topChartContainer=new JPanel(new BorderLayout());
		topChartContainer.add(xicBar, BorderLayout.NORTH);
		topChartContainer.add(topChartContent, BorderLayout.CENTER);

		JPanel scanTypePanel=new JPanel(new BorderLayout());
		JLabel scanTypeLabel=new JLabel("Scan type:");
		scanTypeLabel.setToolTipText("Filter by MS1 or a specific MS2 isolation window.");
		scanTypePanel.add(scanTypeLabel, BorderLayout.WEST);
		scanTypePanel.add(scanTypeFilter, BorderLayout.CENTER);

		JPanel searchPanel=new JPanel(new BorderLayout());
		JLabel searchLabel=new JLabel("Search:");
		searchLabel.setToolTipText("Filter the scan table by text.");
		searchPanel.add(searchLabel, BorderLayout.WEST);
		searchPanel.add(filterField, BorderLayout.CENTER);

		JPanel left=new JPanel(new BorderLayout());
		JScrollPane scanTableScroll=new JScrollPane(table);
		scanTableScroll.setToolTipText("Scan table for this raw file.");
		left.add(scanTypePanel, BorderLayout.NORTH);
		left.add(scanTableScroll, BorderLayout.CENTER);
		left.add(searchPanel, BorderLayout.SOUTH);

		primaryTabs.addTab("Scans", rawSplit);
		primaryTabs.setToolTipTextAt(primaryTabs.indexOfTab("Scans"), "Scan table, TIC view, and selected-spectrum plots.");
		rawSplit.setTopComponent(topChartContainer);
		rawSplit.setBottomComponent(spectrumSplit);
		primaryTabs.addTab(BOXPLOT_TITLE, boxplotSplit);
		primaryTabs.setToolTipTextAt(primaryTabs.indexOfTab(BOXPLOT_TITLE), "Boxplots summarizing ion injection times.");
		JLabel structureLoading=new JLabel("Loading...");
		structureLoading.setToolTipText("Global structure chart is loading.");
		primaryTabs.addTab(STRUCTURE_TITLE, structureLoading);
		primaryTabs.setToolTipTextAt(primaryTabs.indexOfTab(STRUCTURE_TITLE), "Global acquisition structure chart.");
		JLabel globalLoading=new JLabel("Loading...");
		globalLoading.setToolTipText("Global summary chart is loading.");
		primaryTabs.addTab(GLOBAL_TITLE, globalLoading);
		primaryTabs.setToolTipTextAt(primaryTabs.indexOfTab(GLOBAL_TITLE), "Global summary chart for the opened file.");

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

	private void installScanHeaderTooltips() {
		JTableHeader header=new JTableHeader(table.getColumnModel()) {
			private static final long serialVersionUID=1L;

			@Override
			public String getToolTipText(MouseEvent event) {
				int viewColumn=columnAtPoint(event.getPoint());
				if (viewColumn<0) return null;
				int modelColumn=table.convertColumnIndexToModel(viewColumn);
				return getScanHeaderTooltip(modelColumn);
			}
		};
		header.setToolTipText("Hover a column header to see what it means.");
		table.setTableHeader(header);
	}

	private void installScanCellRenderers() {
		DefaultTableCellRenderer scientificRenderer=new DefaultTableCellRenderer() {
			private static final long serialVersionUID=1L;

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				setHorizontalAlignment(SwingConstants.RIGHT);
				if (value instanceof Number n) {
					setText(StripeTableCellRenderer.formatScientific(n));
				} else {
					setText("");
				}
				return this;
			}
		};
		table.getColumnModel().getColumn(4).setCellRenderer(scientificRenderer);
	}

	private String getScanHeaderTooltip(int modelColumn) {
		return switch (modelColumn) {
			case 0 -> "The table row number for this scan.";
			case 1 -> "The vendor-provided scan or spectrum name.";
			case 2 -> "The scan start time in minutes.";
			case 3 -> "The precursor m/z for this scan (blank for MS1).";
			case 4 -> "Total ion current for this scan.";
			default -> null;
		};
	}

	private void initializeScanTypeFilter() {
		ArrayList<ScanTypeFilterOption> options=new ArrayList<>();
		options.add(ScanTypeFilterOption.allSpectra());
		options.add(ScanTypeFilterOption.ms1());
		if (stripe!=null) {
			Map<Range, ?> ranges=stripe.getRanges();
			if (ranges!=null&&!ranges.isEmpty()) {
				ArrayList<Range> sortedRanges=new ArrayList<>(ranges.keySet());
				sortedRanges.sort(Comparator.naturalOrder());
				for (Range range : sortedRanges) {
					options.add(ScanTypeFilterOption.ms2Range(range));
				}
			}
		}
		scanTypeFilter.setModel(new DefaultComboBoxModel<>(options.toArray(new ScanTypeFilterOption[0])));
		scanTypeFilter.setSelectedIndex(0);
	}

	private void updateFilter() {
		String raw=filterField.getText();
		String search=(raw==null)?"":raw.trim().toLowerCase(Locale.ROOT);
		ScanTypeFilterOption selected=(ScanTypeFilterOption)scanTypeFilter.getSelectedItem();
		if (selected==null) selected=ScanTypeFilterOption.allSpectra();
		boolean scanTypeChanged=selected!=activeScanType;
		if (scanTypeChanged) {
			activeScanType=selected;
			updateActiveTicTrace(selected);
			updateXicControlEnabledState();
			boolean asyncReextract=false;
			if (selected.isAll()) {
				clearXicState();
				resetScan(currentSelection);
			} else if (activeParsedXicTargets.hasAnyTargets()) {
				List<RawBrowserXicUtils.XicTarget> targets=selectTargetsForScanType(activeParsedXicTargets, selected);
				if (targets.isEmpty()) {
					clearXicState();
					resetScan(currentSelection);
				} else {
					asyncReextract=true;
					extractXicTracesAsync(targets, getSelectedXicTolerance());
				}
			}
			if (!asyncReextract) {
				refreshChromatogramChart();
			}
		}
		if (selected.isAll()&&search.isEmpty()) {
			rowSorter.setRowFilter(null);
		} else {
			final String searchText=search;
			final ScanTypeFilterOption scanType=selected;
			rowSorter.setRowFilter(new RowFilter<TableModel, Integer>() {
				@Override
				public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
					ScanSummary summary=model.getSelectedRow(entry.getIdentifier());
					if (!scanType.includes(summary)) return false;
					if (searchText.isEmpty()) return true;
					for (int i=0; i<entry.getValueCount(); i++) {
						Object value=entry.getValue(i);
						if (value==null) continue;
						String cell=value.toString().toLowerCase(Locale.ROOT);
						if (cell.contains(searchText)) return true;
					}
					return false;
				}
			});
		}
		syncSelectionToFilteredRows();
		refreshTopChartForCurrentSelection();
	}

	private void syncSelectionToFilteredRows() {
		if (table.getRowCount()<=0) {
			table.clearSelection();
			resetScan(null);
			return;
		}
		if (table.getSelectedRow()>=0) return;
		table.setRowSelectionInterval(0, 0);
	}

	private void updateActiveTicTrace(ScanTypeFilterOption selected) {
		if (selected==null||selected.isAll()) {
			activeChromatogram=globalChromatogram;
			activeMaxTic=globalMaxTic;
			return;
		}

		ArrayList<Float> xMinutes=new ArrayList<>();
		ArrayList<Float> yTic=new ArrayList<>();
		float max=0.0f;
		for (ScanSummary summary : allScans) {
			if (!selected.includes(summary)) continue;
			float tic=summary.getTic();
			if (!Float.isFinite(tic)) continue;
			float x=summary.getScanStartTime()/60f;
			xMinutes.add(x);
			yTic.add(tic);
			if (tic>max) max=tic;
		}

		float[] xArray=new float[xMinutes.size()];
		float[] yArray=new float[yTic.size()];
		for (int i=0; i<xMinutes.size(); i++) {
			xArray[i]=xMinutes.get(i);
			yArray[i]=yTic.get(i);
		}
		activeChromatogram=new XYTrace(xArray, yArray, GraphType.area, selected.toString()+" TIC", new java.awt.Color(0x55, 0x55, 0xF6), null);
		activeMaxTic=max;
	}

	private void refreshTopChartForCurrentSelection() {
		int[] selection=table.getSelectedRows();
		if (selection.length<=0) {
			updateTopChartSelectionMarkers(Float.NaN, Float.NaN);
			return;
		}
		float minRT=Float.MAX_VALUE;
		float maxRT=-Float.MAX_VALUE;
		for (int row : selection) {
			ScanSummary entry=model.getSelectedRow(table.convertRowIndexToModel(row));
			float rt=entry.getScanStartTime()/60f;
			if (rt<minRT) minRT=rt;
			if (rt>maxRT) maxRT=rt;
		}
		updateTopChartSelectionMarkers(minRT, maxRT);
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
		this.allScans=new ArrayList<>(data.getScans());
		this.globalChromatogram=data.getChromatogram();
		this.globalMaxTic=data.getMaxTic();
		this.activeChromatogram=globalChromatogram;
		this.activeMaxTic=globalMaxTic;
		this.currentSelection=null;
		this.xicActive=false;
		this.activeParsedXicTargets=RawBrowserXicUtils.ParsedXicTargets.empty();
		this.activeXicTargets=List.of();
		this.activeXicTraces=List.of();
		this.activeXicMax=0.0f;
		this.structureChart=data.getStructureChart();
		this.globalChart=data.getGlobalChart();

		model.updateEntries(data.getScans());

		refreshChromatogramChart(false);
		updateXicControlEnabledState();

		ExtendedChartPanel iitByRangeChart=BoxPlotGenerator.getBoxplotChart(null, "Precursor Isolation Window", "Ion Injection Time (ms)", data.getIitByRange());
		iitByRangeChart.setToolTipText("Distribution of ion injection times grouped by precursor isolation window.");
		boxplotSplit.setTopComponent(iitByRangeChart);
		ExtendedChartPanel iitByRtChart=BoxPlotGenerator.getBoxplotChart(null, "Retention Time Bin (min)", "Ion Injection Time (ms)", data.getIitByRt());
		iitByRtChart.setToolTipText("Distribution of ion injection times grouped by retention-time bin.");
		boxplotSplit.setBottomComponent(iitByRtChart);

		if (structureChart!=null) {
			structureChart.setToolTipText("Structure chart showing DIA isolation-window layout over time.");
		}
		if (globalChart!=null) {
			globalChart.setToolTipText("Global chart summarizing signal and acquisition trends across the run.");
		}

		primaryTabs.setComponentAt(primaryTabs.indexOfTab(STRUCTURE_TITLE), structureChart);
		primaryTabs.setComponentAt(primaryTabs.indexOfTab(GLOBAL_TITLE), globalChart);

		updateFilter();

		SwingUtilities.invokeLater(this::applySplitPreferences);
	}

	private void onExtractXicClicked() {
		if (activeScanType==null||activeScanType.isAll()) return;
		RawBrowserXicUtils.ParsedXicTargets parsedTargets=RawBrowserXicUtils.parseXicTargets(xicField.getText());
		List<RawBrowserXicUtils.XicTarget> targets=selectTargetsForScanType(parsedTargets, activeScanType);
		if (targets.isEmpty()) {
			clearXicState();
			refreshChromatogramChart();
			resetScan(currentSelection);
			return;
		}
		activeParsedXicTargets=parsedTargets;
		extractXicTracesAsync(targets, getSelectedXicTolerance());
	}

	private XicToleranceOption getSelectedXicTolerance() {
		XicToleranceOption selected=(XicToleranceOption)xicToleranceFilter.getSelectedItem();
		return selected==null?XicToleranceOption.DEFAULT:selected;
	}

	private List<RawBrowserXicUtils.XicTarget> selectTargetsForScanType(RawBrowserXicUtils.ParsedXicTargets parsedTargets, ScanTypeFilterOption scanType) {
		if (parsedTargets==null||scanType==null||scanType.isAll()) return List.of();
		if (scanType.isMs1()) return parsedTargets.precursorTargets();
		return parsedTargets.fragmentTargets();
	}

	private void extractXicTracesAsync(List<RawBrowserXicUtils.XicTarget> targets, XicToleranceOption toleranceOption) {
		final long token=++xicToken;
		final ScanTypeFilterOption scanTypeAtRequest=activeScanType;
		final List<RawBrowserXicUtils.XicTarget> targetCopy=List.copyOf(targets);
		final XicToleranceOption tolerance=toleranceOption;
		activeXicTargets=targetCopy;
		activeXicTolerance=tolerance;
		activeXicMax=0.0f;
		activeXicTraces=buildEmptyXicTraces(targetCopy);
		xicActive=true;
		activeXicProgress=null;
		refreshChromatogramChart();
		resetScan(currentSelection);

		beginXicExtraction();
		startXicProgressTimer(token);

		new SwingWorker<XicExtractionResult, Void>() {
			@Override
			protected XicExtractionResult doInBackground() throws Exception {
				return extractXicTraceData(token, scanTypeAtRequest, targetCopy, tolerance);
			}

			@Override
			protected void done() {
				try {
					if (token!=xicToken) return;
					XicExtractionResult result=get();
					activeXicTraces=result.traces;
					activeXicMax=result.maxIntensity;
					flushXicProgress(token, true);
					refreshTopChartForCurrentSelection();
				} catch (Exception ex) {
					Logger.logException(ex);
				} finally {
					stopXicProgressTimer(token);
					endXicExtraction();
				}
			}
		}.execute();
	}

	private XicExtractionResult extractXicTraceData(long token, ScanTypeFilterOption scanType, List<RawBrowserXicUtils.XicTarget> targets,
			XicToleranceOption toleranceOption) {
		ArrayList<ScanSummary> sourceScans=new ArrayList<>();
		for (ScanSummary summary : allScans) {
			if (scanType.includes(summary)) sourceScans.add(summary);
		}
		sourceScans.sort((a, b) -> Float.compare(a.getScanStartTime(), b.getScanStartTime()));

		double[] xMinutes=new double[sourceScans.size()];
		double[][] traces=new double[targets.size()][sourceScans.size()];
		XicExtractionProgress progress=new XicExtractionProgress(token, xMinutes, traces);
		activeXicProgress=progress;

		for (int i=0; i<sourceScans.size(); i++) {
			ScanSummary summary=sourceScans.get(i);
			xMinutes[i]=summary.getScanStartTime()/60.0;
			float scanMax=0.0f;
			AcquiredSpectrum spectrum;
			try {
				spectrum=stripe.getSpectrum(summary);
			} catch (Exception e) {
				Logger.logException(e);
				spectrum=null;
			}
			if (spectrum!=null) {
				double[] mz=spectrum.getMassArray();
				float[] intensity=spectrum.getIntensityArray();
				for (int t=0; t<targets.size(); t++) {
					double target=targets.get(t).mz();
					double tol=toleranceOption.toleranceMz(target);
					double sum=RawBrowserXicUtils.sumIntensityWithinTolerance(mz, intensity, target, tol);
					traces[t][i]=sum;
					if (sum>scanMax) scanMax=(float)sum;
				}
			}
			synchronized (progress.lock) {
				if (scanMax>progress.maxIntensity) {
					progress.maxIntensity=scanMax;
				}
				progress.extractedCount=i+1;
			}
		}

		ArrayList<XYTrace> xicTraces=new ArrayList<>();
		for (int t=0; t<targets.size(); t++) {
			RawBrowserXicUtils.XicTarget target=targets.get(t);
			String label=formatXicTargetLabel(target);
			xicTraces.add(new XYTrace(xMinutes, traces[t], GraphType.line, label, getXicColor(t), 3.0f));
		}

		float max;
		synchronized (progress.lock) {
			max=progress.maxIntensity;
		}
		return new XicExtractionResult(xicTraces, max);
	}

	private void clearXicState() {
		xicToken++;
		stopXicProgressTimer(-1L);
		activeXicProgress=null;
		xicActive=false;
		activeParsedXicTargets=RawBrowserXicUtils.ParsedXicTargets.empty();
		activeXicTargets=List.of();
		activeXicTraces=List.of();
		activeXicMax=0.0f;
	}

	private List<XYTrace> buildEmptyXicTraces(List<RawBrowserXicUtils.XicTarget> targets) {
		ArrayList<XYTrace> traces=new ArrayList<>();
		for (int i=0; i<targets.size(); i++) {
			RawBrowserXicUtils.XicTarget target=targets.get(i);
			traces.add(new XYTrace(new double[0], new double[0], GraphType.line, formatXicTargetLabel(target), getXicColor(i), 3.0f));
		}
		return traces;
	}

	private String formatXicTargetLabel(RawBrowserXicUtils.XicTarget target) {
		return String.format(Locale.ROOT, "%s (%.3f m/z)", target.label(), target.mz());
	}

	private void startXicProgressTimer(long token) {
		stopXicProgressTimer(-1L);
		xicProgressTimerToken=token;
		xicProgressTimer=new Timer(200, e -> flushXicProgress(token, false));
		xicProgressTimer.setRepeats(true);
		xicProgressTimer.start();
	}

	private void stopXicProgressTimer(long token) {
		if (xicProgressTimer==null) return;
		if (token>=0L&&xicProgressTimerToken!=token) return;
		xicProgressTimer.stop();
		xicProgressTimer=null;
		xicProgressTimerToken=-1L;
	}

	private void flushXicProgress(long token, boolean flushAll) {
		if (token!=xicToken) {
			stopXicProgressTimer(token);
			return;
		}
		XicExtractionProgress progress=activeXicProgress;
		if (progress==null||progress.token!=token) return;
		if (topChromatogramChart==null||topChromatogramChart.getChart()==null) return;
		XYPlot plot=topChromatogramChart.getChart().getXYPlot();
		if (plot==null) return;

		int startIndex;
		int endIndex;
		float progressMax;
		synchronized (progress.lock) {
			startIndex=progress.flushedCount;
			int extracted=progress.extractedCount;
			endIndex=flushAll?progress.xMinutes.length:extracted;
			if (endIndex<startIndex) endIndex=startIndex;
			progress.flushedCount=endIndex;
			progressMax=progress.maxIntensity;
		}
		if (endIndex>startIndex) {
			int traceCount=Math.min(activeXicTargets.size(), progress.traces.length);
			for (int t=0; t<traceCount; t++) {
				if (!(plot.getDataset(t) instanceof XYSeriesCollection seriesCollection)||seriesCollection.getSeriesCount()<=0) continue;
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
		if (progressMax!=activeXicMax) {
			activeXicMax=progressMax;
			refreshTopChartForCurrentSelection();
		}
	}

	private void beginXicExtraction() {
		activeXicExtractionCount++;
		updateXicBusyCursor();
	}

	private void endXicExtraction() {
		if (activeXicExtractionCount>0) {
			activeXicExtractionCount--;
		}
		updateXicBusyCursor();
	}

	private void resetXicExtractionBusyState() {
		stopXicProgressTimer(-1L);
		activeXicProgress=null;
		activeXicExtractionCount=0;
		updateXicBusyCursor();
	}

	private void updateXicBusyCursor() {
		Cursor cursor=(activeXicExtractionCount>0)?Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR):Cursor.getDefaultCursor();
		setCursor(cursor);
		if (table!=null) table.setCursor(cursor);
		if (topChartContainer!=null) topChartContainer.setCursor(cursor);
		if (topChartContent!=null) topChartContent.setCursor(cursor);
		if (primaryTabs!=null) primaryTabs.setCursor(cursor);
	}

	private void updateXicControlEnabledState() {
		boolean enabled=activeScanType!=null&&!activeScanType.isAll();
		if (xicLabel!=null) xicLabel.setEnabled(enabled);
		if (xicField!=null) xicField.setEnabled(enabled);
		if (xicToleranceFilter!=null) xicToleranceFilter.setEnabled(enabled);
		if (extractXicButton!=null) extractXicButton.setEnabled(enabled);
	}

	private boolean isXicModeActive() {
		return xicActive&&!activeXicTargets.isEmpty();
	}

	private float getActiveChromatogramMax() {
		return isXicModeActive()?activeXicMax:activeMaxTic;
	}

	private void setTopChart(ExtendedChartPanel chart) {
		if (topChartContent==null) return;
		if (topChromatogramChart!=null&&topChromatogramChart!=chart) {
			clearTopChartSelectionMarkers();
		}
		topChartContent.removeAll();
		topChartContent.add(chart, BorderLayout.CENTER);
		topChromatogramChart=chart;
		chromatogramSelectionAnnotations.clear();
		topChartContent.revalidate();
		topChartContent.repaint();
	}

	private void refreshChromatogramChart() {
		refreshChromatogramChart(true);
	}

	private void refreshChromatogramChart(boolean preserveAxisView) {
		ChartAxisView axisView=preserveAxisView?captureTopChartAxisView():null;
		ExtendedChartPanel chart=buildChromatogramChart();
		setTopChart(chart);
		applyTopChartAxisView(axisView, chart);
		refreshTopChartForCurrentSelection();
	}

	private ChartAxisView captureTopChartAxisView() {
		if (topChromatogramChart==null||topChromatogramChart.getChart()==null) return null;
		XYPlot plot=topChromatogramChart.getChart().getXYPlot();
		if (plot==null||plot.getDomainAxis()==null||plot.getRangeAxis()==null) return null;
		double domainLower=plot.getDomainAxis().getLowerBound();
		double domainUpper=plot.getDomainAxis().getUpperBound();
		boolean hasDomainRange=Double.isFinite(domainLower)&&Double.isFinite(domainUpper)&&domainUpper>domainLower;
		return new ChartAxisView(hasDomainRange, domainLower, domainUpper);
	}

	private void applyTopChartAxisView(ChartAxisView axisView, ExtendedChartPanel chart) {
		if (axisView==null||chart==null||chart.getChart()==null) return;
		XYPlot plot=chart.getChart().getXYPlot();
		if (plot==null||plot.getDomainAxis()==null||plot.getRangeAxis()==null) return;
		if (axisView.hasDomainRange) {
			plot.getDomainAxis().setRange(axisView.domainLower, axisView.domainUpper);
		} else {
			plot.getDomainAxis().setAutoRange(true);
		}
		plot.getRangeAxis().setAutoRange(true);
	}

	private void clearTopChartSelectionMarkers() {
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

	private void updateTopChartSelectionMarkers(float minRT, float maxRT) {
		clearTopChartSelectionMarkers();
		if (topChromatogramChart==null) return;
		if (!Float.isFinite(minRT)||!Float.isFinite(maxRT)) return;

		XYPlot plot=topChromatogramChart.getChart().getXYPlot();
		if (plot==null) return;

		double markerMax=Math.max(getActiveChromatogramMax(), 1.0f);
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

	private ExtendedChartPanel buildChromatogramChart() {
		ArrayList<XYTraceInterface> traces=new ArrayList<>();
		LegendMode legendMode=LegendMode.NONE;
		String yAxis="TIC";
		String tooltip=TIC_TOOLTIP;

		if (isXicModeActive()) {
			traces.addAll(activeXicTraces);
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

	private Color getXicColor(int targetIndex) {
		return XIC_COLORS[targetIndex%XIC_COLORS.length];
	}

	private Color withAlpha(Color color, float alpha) {
		return new Color(color.getRed()/255.0f, color.getGreen()/255.0f, color.getBlue()/255.0f, alpha);
	}

	private int getMatchingXicTargetIndex(double mz) {
		if (!isXicModeActive()) return -1;
		for (int i=0; i<activeXicTargets.size(); i++) {
			double target=activeXicTargets.get(i).mz();
			double tolerance=activeXicTolerance.toleranceMz(target);
			if (mz>=target-tolerance&&mz<=target+tolerance) {
				return i;
			}
		}
		return -1;
	}

	private void applySpectrumXicOverlays(ExtendedChartPanel spectrumChart, AcquiredSpectrum spectrum) {
		if (!isXicModeActive()||spectrumChart==null||spectrum==null) return;
		XYPlot plot=spectrumChart.getChart().getXYPlot();
		if (plot==null) return;

		double spectrumMax=Math.max(0.0, MatrixMath.max(spectrum.getIntensityArray()));
		if (spectrumMax<=0.0) spectrumMax=1.0;
		double divider=spectrumChart.getDivider();
		if (!(divider>0.0)) divider=1.0;
		double chartMaxY=spectrumMax/divider;

		for (int i=0; i<activeXicTargets.size(); i++) {
			double target=activeXicTargets.get(i).mz();
			double tol=activeXicTolerance.toleranceMz(target);
			double left=target-tol;
			double right=target+tol;
			Color base=getXicColor(i);
			Color shade=withAlpha(base, 0.2f);
			plot.addAnnotation(new XYBoxAnnotation(left, 0.0, right, chartMaxY, new BasicStroke(1.0f), shade, shade));
		}

		if (!(plot.getRenderer(0) instanceof XYLineAndShapeRenderer renderer)) return;
		if (!(plot.getDataset(0) instanceof XYSeriesCollection dataset)) return;

		for (int seriesIndex=0; seriesIndex<dataset.getSeriesCount(); seriesIndex++) {
			if (dataset.getItemCount(seriesIndex)<2) continue;
			double y=dataset.getYValue(seriesIndex, 1);
			if (Math.abs(y)<1e-12) continue; // baseline or empty peak

			double mz=dataset.getXValue(seriesIndex, 0);
			int targetIndex=getMatchingXicTargetIndex(mz);
			if (targetIndex<0) continue;

			Color color=getXicColor(targetIndex);
			renderer.setSeriesPaint(seriesIndex, color);
			renderer.setSeriesStroke(seriesIndex, new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		}
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
		currentSelection=result;
		if (result==null||result.entries==null||result.entries.isEmpty()) {
			updateTopChartSelectionMarkers(Float.NaN, Float.NaN);
			spectrumSplit.setLeftComponent(new JLabel("No spectrum available"));
			spectrumSplit.setRightComponent(new JLabel(""));
			return;
		}

		AcquiredSpectrum displaySpectrum=result.displaySpectrum;
		if (displaySpectrum==null) {
			updateTopChartSelectionMarkers(Float.NaN, Float.NaN);
			spectrumSplit.setLeftComponent(new JLabel("No spectrum available"));
			spectrumSplit.setRightComponent(new JLabel(""));
			return;
		}
		ExtendedChartPanel spectrumChart=BasicChartGenerator.getChart("m/z", "Intensity", false, new XYTrace(displaySpectrum));
		applySpectrumXicOverlays(spectrumChart, displaySpectrum);
		spectrumChart.setToolTipText(SPECTRUM_TOOLTIP);
		boolean hasIms=displaySpectrum.getIonMobilityArray().isPresent()&&MatrixMath.max(displaySpectrum.getIntensityArray())>0.0f;
		if (hasIms) {
			ExtendedChartPanel imsChart=BasicChartGenerator.getChart("Ion Mobility", "m/z", false, new ImsSpectrumWrapper(displaySpectrum));
			imsChart.setToolTipText(IMS_TOOLTIP);
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
		spectrumHistogram.setToolTipText(HISTOGRAM_TOOLTIP);
		spectrumSplit.setRightComponent(spectrumHistogram);

		updateTopChartSelectionMarkers(result.minRT, result.maxRT);

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
		if (SwingUtilities.isEventDispatchThread()) {
			resetXicExtractionBusyState();
		} else {
			SwingUtilities.invokeLater(this::resetXicExtractionBusyState);
		}
		if (stripe!=null&&stripe.isOpen()) {
			stripe.close();
		}
	}

}
