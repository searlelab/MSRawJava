package org.searlelab.msrawjava.gui.visualization;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.AbstractDocument;

import org.searlelab.msrawjava.algorithms.RawSpectrumMergeUtils;
import org.searlelab.msrawjava.gui.GUIPreferences;
import org.searlelab.msrawjava.gui.filebrowser.StripeTableCellRenderer;
import org.searlelab.msrawjava.gui.graphing.GraphType;
import org.searlelab.msrawjava.gui.graphing.XYTrace;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.PPMMassTolerance;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;

class RawBrowserScansTab extends JPanel {
	private static final long serialVersionUID=1L;
	private static final String XIC_EXAMPLE_PEG="371.228, 415.254, 459.280, 503.306, 547.332, 597.359";
	private static final String XIC_EXAMPLE_POLYSILOXANE="[C2H6SiO]5, [C2H6SiO]6, [C2H6SiO]7, [C2H6SiO]8, [C2H6SiO]9, [C2H6SiO]10, [C2H6SiO]11";
	private static final String XIC_EXAMPLE_VATVSLPR="VATVSLPR++";
	private final StripeFileInterface stripe;
	private final boolean peakPickAcrossIMS;
	private Runnable showScansTabAction=() -> {
	};
	private RawScanTableModel model;
	private JTable table;
	private TableRowSorter<TableModel> rowSorter;
	private JTextField filterField;
	private JComboBox<ScanTypeFilterOption> scanTypeFilter;
	private JTextField xicField;
	private JPanel topChartContainer;
	private final JSplitPane rawSplit=new JSplitPane(JSplitPane.VERTICAL_SPLIT);
	private final JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	private List<ScanSummary> allScans=List.of();
	private XYTrace globalChromatogram;
	private float globalMaxTic;
	private XYTrace activeChromatogram;
	private float activeMaxTic;
	private ScanTypeFilterOption activeScanType=ScanTypeFilterOption.allSpectra();
	private long selectionToken=0L;
	private RawBrowserXicController xicController;
	private RawBrowserScanRenderer renderer;
	private SelectionResult currentSelection;

	RawBrowserScansTab(StripeFileInterface stripe, boolean peakPickAcrossIMS) {
		super(new BorderLayout());
		this.stripe=stripe;
		this.peakPickAcrossIMS=peakPickAcrossIMS;
		this.renderer=new RawBrowserScanRenderer(this::performTableSelectionAction, this::selectNearestVisibleScanRow, this::applySplitPreferences);
		this.xicController=new RawBrowserXicController(stripe, this::refreshChromatogramChart, () -> resetScan(currentSelection),
				this::refreshTopChartForCurrentSelection, this::updateXicBusyCursor);
		this.xicController.setRenderer(renderer);
		initUi();
	}

	private void initUi() {
		model=new RawScanTableModel();
		table=new JTable(model);
		table.setToolTipText("Lists scans from the opened file. Select one or more rows to update the charts.");
		RawBrowserNavigation.installHorizontalRowNavigation(table);
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
		JLabel xicLabel=new JLabel("XIC m/zs: ");
		xicField=new JTextField();
		xicField.setToolTipText("Enter one or more m/z, peptide, or formula targets with optional signed charges (comma or whitespace separated).");
		((AbstractDocument)xicField.getDocument()).setDocumentFilter(new XicInputFilter());
		JComboBox<XicToleranceOption> xicToleranceFilter=new JComboBox<>(XicToleranceOption.valuesForUi());
		xicToleranceFilter.setSelectedItem(XicToleranceOption.DEFAULT);
		xicToleranceFilter.setToolTipText("Mass tolerance used for XIC extraction.");
		JButton extractXicButton=new JButton("Extract XICs");
		extractXicButton.setToolTipText("Extract and plot XIC traces for entered m/z, peptide, or formula targets.");
		extractXicButton.addActionListener(e -> xicController.extractFromInput(activeScanType));
		JPanel xicEastPanel=new JPanel(new BorderLayout(6, 0));
		xicEastPanel.add(xicToleranceFilter, BorderLayout.CENTER);
		xicEastPanel.add(extractXicButton, BorderLayout.EAST);
		JPanel xicBar=new JPanel(new BorderLayout(6, 0));
		xicBar.add(xicLabel, BorderLayout.WEST);
		xicBar.add(xicField, BorderLayout.CENTER);
		xicBar.add(xicEastPanel, BorderLayout.EAST);
		JPanel xicExamplesPanel=new JPanel(new BorderLayout(6, 0));
		JLabel xicExamplesLabel=new JLabel("Examples: ");
		JPanel xicExamplesButtons=new JPanel();
		xicExamplesButtons.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
		xicExamplesButtons.add(buildXicExampleButton("PEG", XIC_EXAMPLE_PEG));
		xicExamplesButtons.add(buildXicExampleButton("Polysiloxane", XIC_EXAMPLE_POLYSILOXANE));
		xicExamplesButtons.add(buildXicExampleButton("VATVSLPR", XIC_EXAMPLE_VATVSLPR));
		xicExamplesPanel.add(xicExamplesLabel, BorderLayout.WEST);
		xicExamplesPanel.add(xicExamplesButtons, BorderLayout.CENTER);
		JPanel xicControlsPanel=new JPanel(new BorderLayout(0, 6));
		xicControlsPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		xicControlsPanel.add(xicExamplesPanel, BorderLayout.NORTH);
		xicControlsPanel.add(xicBar, BorderLayout.CENTER);
		topChartContainer=new JPanel(new BorderLayout());
		topChartContainer.add(xicControlsPanel, BorderLayout.NORTH);
		topChartContainer.add(renderer.getTopChartContent(), BorderLayout.CENTER);
		xicController.bindControls(xicLabel, xicField, xicToleranceFilter, extractXicButton);
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
		rawSplit.setTopComponent(topChartContainer);
		rawSplit.setBottomComponent(renderer.getSpectrumSplit());
		split.setLeftComponent(left);
		split.setRightComponent(rawSplit);
		add(split, BorderLayout.CENTER);
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				updateToSelected();
			}
		});
		rawSplit.setContinuousLayout(true);
		rawSplit.setOneTouchExpandable(true);
		split.setContinuousLayout(true);
		split.setOneTouchExpandable(true);
		RawBrowserSplitPreferences.registerSplitPreference(split, GUIPreferences::setRawBrowserMainSplitRatio);
		RawBrowserSplitPreferences.registerSplitPreference(rawSplit, GUIPreferences::setRawBrowserScansSplitRatio);
		applySplitPreferences();
	}

	private static Pair<String[], String[]> emptyScanMetadata() {
		return new Pair<>(new String[0], new String[0]);
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
				if (value instanceof Number) {
					setText(StripeTableCellRenderer.formatScientific((Number)value));
				} else {
					setText("");
				}
				return this;
			}
		};
		table.getColumnModel().getColumn(4).setCellRenderer(scientificRenderer);
	}

	private String getScanHeaderTooltip(int modelColumn) {
		switch (modelColumn) {
			case 0:
				return "The table row number for this scan.";
			case 1:
				return "The vendor-provided scan or spectrum name.";
			case 2:
				return "The scan start time in minutes.";
			case 3:
				return "The precursor m/z for this scan (blank for MS1).";
			case 4:
				return "Total ion current for this scan.";
			default:
				return null;
		}
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
			boolean asyncReextract=xicController.handleScanTypeChanged(selected);
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
			renderer.refreshTopChartForSelection(Float.NaN, Float.NaN, activeMaxTic, xicController);
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
		renderer.refreshTopChartForSelection(minRT, maxRT, activeMaxTic, xicController);
	}

	private JButton buildXicExampleButton(String label, String xicValue) {
		JButton button=new JButton(label);
		button.addActionListener(e -> runXicExample(xicValue));
		return button;
	}

	private void runXicExample(String xicValue) {
		selectMs1ScanType();
		xicField.setText(xicValue);
		xicController.extractFromInput(activeScanType);
	}

	private void selectMs1ScanType() {
		selectFirstMatchingItem(scanTypeFilter, option -> option!=null&&option.isMs1());
	}

	static <T> boolean selectFirstMatchingItem(JComboBox<T> comboBox, Predicate<T> predicate) {
		ArrayList<T> options=new ArrayList<>(comboBox.getItemCount());
		for (int i=0; i<comboBox.getItemCount(); i++) {
			options.add(comboBox.getItemAt(i));
		}
		int index=findFirstMatchingIndex(options, predicate);
		if (index<0) return false;
		comboBox.setSelectedIndex(index);
		return true;
	}

	static <T> int findFirstMatchingIndex(List<T> items, Predicate<T> predicate) {
		for (int i=0; i<items.size(); i++) {
			if (predicate.test(items.get(i))) return i;
		}
		return -1;
	}

	Component getScansComponent() {
		return rawSplit;
	}

	void setMainRightComponent(Component component) {
		split.setRightComponent(component);
	}

	void setShowScansTabAction(Runnable action) {
		this.showScansTabAction=action==null?() -> {
		}:action;
	}

	void applyData(RawBrowserData data) {
		this.allScans=new ArrayList<>(data.getScans());
		this.globalChromatogram=data.getChromatogram();
		this.globalMaxTic=data.getMaxTic();
		this.activeChromatogram=globalChromatogram;
		this.activeMaxTic=globalMaxTic;
		this.currentSelection=null;
		xicController.setAllScans(this.allScans);
		xicController.resetDataState();
		model.updateEntries(data.getScans());
		refreshChromatogramChart(false);
		xicController.updateControlEnabledState(activeScanType);
		updateFilter();
		SwingUtilities.invokeLater(this::applySplitPreferences);
	}

	private void refreshChromatogramChart() {
		refreshChromatogramChart(true);
	}

	private void refreshChromatogramChart(boolean preserveAxisView) {
		renderer.refreshChromatogramChart(activeChromatogram, activeMaxTic, xicController, preserveAxisView);
		refreshTopChartForCurrentSelection();
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
				Pair<String[], String[]> metadata=emptyScanMetadata();
				if (summaries.size()==1) {
					try {
						metadata=stripe.getScanMetadata(summaries.get(0));
					} catch (Exception ignored) {
						metadata=emptyScanMetadata();
					}
				}
				if (spectra.isEmpty()) return new SelectionResult(spectra, null, metadata, Float.NaN, Float.NaN);
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
				AcquiredSpectrum display=RawBrowserSpectrumTools.peakPickSpectrumIfIMS(merged, peakPickAcrossIMS);
				return new SelectionResult(spectra, display, metadata, minRT, maxRT);
			}

			@Override
			protected void done() {
				if (token!=selectionToken) return;
				try {
					SelectionResult result=get();
					resetScan(result);
					showScansTabAction.run();
				} catch (Exception ex) {
					Logger.logException(ex);
				}
			}
		}.execute();
	}

	void performTableSelectionAction(String tableActionKey) {
		if (table==null||table.getRowCount()<=0||tableActionKey==null) return;
		if (table.getSelectedRow()<0) {
			table.setRowSelectionInterval(0, 0);
		}
		Action action=table.getActionMap().get(tableActionKey);
		if (action==null) return;
		action.actionPerformed(new ActionEvent(table, ActionEvent.ACTION_PERFORMED, tableActionKey));
		int selectedRow=table.getSelectedRow();
		if (selectedRow>=0&&selectedRow<table.getRowCount()) {
			table.scrollRectToVisible(table.getCellRect(selectedRow, 0, true));
		}
	}

	private void selectNearestVisibleScanRow(double clickedMinutes) {
		if (table==null||table.getRowCount()<=0) return;
		int nearestViewRow=findNearestVisibleScanRow(clickedMinutes);
		if (nearestViewRow<0) return;
		table.setRowSelectionInterval(nearestViewRow, nearestViewRow);
		table.scrollRectToVisible(table.getCellRect(nearestViewRow, 0, true));
	}

	private int findNearestVisibleScanRow(double clickedMinutes) {
		int rowCount=table.getRowCount();
		if (rowCount<=0) return -1;
		double[] rowMinutes=new double[rowCount];
		for (int viewRow=0; viewRow<rowCount; viewRow++) {
			int modelRow=table.convertRowIndexToModel(viewRow);
			ScanSummary summary=model.getSelectedRow(modelRow);
			rowMinutes[viewRow]=summary.getScanStartTime()/60.0;
		}
		return RawBrowserNavigation.findNearestValueIndex(clickedMinutes, rowMinutes);
	}

	private void resetScan(SelectionResult result) {
		currentSelection=result;
		renderer.resetScan(result, xicController, activeMaxTic);
	}

	void resetXicExtractionBusyState() {
		xicController.resetBusyState();
	}

	private void updateXicBusyCursor(Cursor cursor) {
		setCursor(cursor);
		if (table!=null) table.setCursor(cursor);
		if (topChartContainer!=null) topChartContainer.setCursor(cursor);
		if (renderer!=null&&renderer.getTopChartContent()!=null) renderer.getTopChartContent().setCursor(cursor);
	}

	private void applySplitPreferences() {
		RawBrowserSplitPreferences.withSplitSaveSuppressed(() -> {
			RawBrowserSplitPreferences.applySplitRatio(split, GUIPreferences.getRawBrowserMainSplitRatio());
			RawBrowserSplitPreferences.applySplitRatio(rawSplit, GUIPreferences.getRawBrowserScansSplitRatio());
			renderer.applySplitPreferences();
		});
	}
}
