package org.searlelab.msrawjava.gui.visualization;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.JSplitPane;

import org.searlelab.msrawjava.gui.GUIPreferences;
import org.searlelab.msrawjava.gui.graphing.BoxPlotGenerator;
import org.searlelab.msrawjava.gui.graphing.ExtendedChartPanel;

class RawBrowserRangeStatisticsTab extends JPanel {
	private static final long serialVersionUID=1L;
	private final JSplitPane boxplotSplit=new JSplitPane(JSplitPane.VERTICAL_SPLIT);

	RawBrowserRangeStatisticsTab() {
		super(new BorderLayout());
		boxplotSplit.setContinuousLayout(true);
		boxplotSplit.setOneTouchExpandable(true);
		RawBrowserSplitPreferences.registerSplitPreference(boxplotSplit, GUIPreferences::setRawBrowserBoxplotSplitRatio);
		add(boxplotSplit, BorderLayout.CENTER);
	}

	void applyData(RawBrowserData data) {
		ExtendedChartPanel iitByRangeChart=BoxPlotGenerator.getBoxplotChart(null, "Precursor Isolation Window", "Ion Injection Time (ms)", data.getIitByRange());
		iitByRangeChart.setToolTipText("Distribution of ion injection times grouped by precursor isolation window.");
		ExtendedChartPanel iitByRtChart=BoxPlotGenerator.getBoxplotChart(null, "Retention Time Bin (min)", "Ion Injection Time (ms)", data.getIitByRt());
		iitByRtChart.setToolTipText("Distribution of ion injection times grouped by retention-time bin.");
		boxplotSplit.setTopComponent(iitByRangeChart);
		boxplotSplit.setBottomComponent(iitByRtChart);
		RawBrowserSplitPreferences.applySplitRatio(boxplotSplit, GUIPreferences.getRawBrowserBoxplotSplitRatio());
	}
}
