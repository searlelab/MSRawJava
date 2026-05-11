package org.searlelab.msrawjava.gui.visualization;

import java.awt.BorderLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.searlelab.msrawjava.gui.graphing.ExtendedChartPanel;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.logging.Logger;

/**
 * Top-level raw browser container.
 */
public class RawBrowserPanel extends JPanel implements AutoCloseable {
	private static final long serialVersionUID=1L;

	private static final String STRUCTURE_TITLE="Structure";
	private static final String GLOBAL_TITLE="Global";
	private static final String BOXPLOT_TITLE="Range Statistics";
	private static final String SETTINGS_TITLE="Settings";

	private final StripeFileInterface stripe;
	private final RawBrowserScansTab scansTab;
	private final RawBrowserRangeStatisticsTab rangeStatisticsTab=new RawBrowserRangeStatisticsTab();
	private final RawBrowserSettingsTab settingsTab;
	private final JTabbedPane primaryTabs=new JTabbedPane();

	public RawBrowserPanel(StripeFileInterface stripe, RawBrowserData data) {
		super(new BorderLayout());
		this.stripe=stripe;
		this.scansTab=new RawBrowserScansTab(stripe, stripe instanceof BrukerTIMSFile);
		this.settingsTab=new RawBrowserSettingsTab(stripe);
		initUi();
		if (data!=null) {
			applyData(data);
		} else {
			startLoad();
		}
	}

	private void initUi() {
		primaryTabs.addTab("Scans", scansTab.getScansComponent());
		primaryTabs.setToolTipTextAt(primaryTabs.indexOfTab("Scans"), "Scan table, TIC view, and selected-spectrum plots.");
		primaryTabs.addTab(BOXPLOT_TITLE, rangeStatisticsTab);
		primaryTabs.setToolTipTextAt(primaryTabs.indexOfTab(BOXPLOT_TITLE), "Boxplots summarizing ion injection times.");
		primaryTabs.addTab(STRUCTURE_TITLE, loadingLabel("Global structure chart is loading."));
		primaryTabs.setToolTipTextAt(primaryTabs.indexOfTab(STRUCTURE_TITLE), "Global acquisition structure chart.");
		primaryTabs.addTab(GLOBAL_TITLE, loadingLabel("Global summary chart is loading."));
		primaryTabs.setToolTipTextAt(primaryTabs.indexOfTab(GLOBAL_TITLE), "Global summary chart for the opened file.");
		primaryTabs.addTab(SETTINGS_TITLE, settingsTab);
		primaryTabs.setToolTipTextAt(primaryTabs.indexOfTab(SETTINGS_TITLE), "File-level metadata from the opened file.");
		scansTab.setShowScansTabAction(() -> primaryTabs.setSelectedIndex(0));
		scansTab.setMainRightComponent(primaryTabs);
		add(scansTab, BorderLayout.CENTER);
	}

	private JLabel loadingLabel(String tooltip) {
		JLabel label=new JLabel("Loading...");
		label.setToolTipText(tooltip);
		return label;
	}

	private void startLoad() {
		new SwingWorker<RawBrowserData, Void>() {
			@Override
			protected RawBrowserData doInBackground() throws Exception {
				return RawBrowserDataLoader.build(stripe);
			}

			@Override
			protected void done() {
				try {
					applyData(get());
				} catch (Exception ex) {
					Logger.logException(ex);
					removeAll();
					add(new JLabel("Cannot parse file."), BorderLayout.CENTER);
					revalidate();
					repaint();
				}
			}
		}.execute();
	}

	private void applyData(RawBrowserData data) {
		scansTab.applyData(data);
		rangeStatisticsTab.applyData(data);
		settingsTab.applyMetadata(data.getMetadata());
		installGlobalChart(STRUCTURE_TITLE, data.getStructureChart(), "Structure chart showing DIA isolation-window layout over time.");
		installGlobalChart(GLOBAL_TITLE, data.getGlobalChart(), "Global chart summarizing signal and acquisition trends across the run.");
	}

	private void installGlobalChart(String title, ExtendedChartPanel chart, String tooltip) {
		if (chart!=null) {
			chart.setToolTipText(tooltip);
			RawBrowserNavigation.installChartArrowNavigation(chart, scansTab::performTableSelectionAction);
		}
		int index=primaryTabs.indexOfTab(title);
		if (index>=0) primaryTabs.setComponentAt(index, chart==null?new JLabel("No chart available"):chart);
	}

	@Override
	public void close() throws Exception {
		if (SwingUtilities.isEventDispatchThread()) {
			scansTab.resetXicExtractionBusyState();
		} else {
			SwingUtilities.invokeLater(scansTab::resetXicExtractionBusyState);
		}
		if (stripe!=null&&stripe.isOpen()) {
			stripe.close();
		}
	}
}
