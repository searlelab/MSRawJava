package org.searlelab.msrawjava.gui.graphing;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;

/**
 * ChartPanel extension with GUI-specific conveniences.
 */
public class ExtendedChartPanel extends ChartPanel {
	private static final long serialVersionUID=1L;

	private final double divider;
	private final String name;
	private ChartLegendDrawerSupport legendDrawerSupport;

	public ExtendedChartPanel(JFreeChart chart, String name, boolean useBuffer, double divider) {
		super(chart, useBuffer);
		this.name=name;
		this.divider=divider;
	}

	public ExtendedChartPanel(JFreeChart chart, String name, double divider) {
		super(chart);
		this.name=name;
		this.divider=divider;
	}

	@Override
	public String getName() {
		return name;
	}

	public double getDivider() {
		return divider;
	}

	public void enableLegendDrawer() {
		if (legendDrawerSupport==null) {
			legendDrawerSupport=new ChartLegendDrawerSupport(this);
		} else {
			legendDrawerSupport.refreshLegendRows();
		}
		if (getChart()!=null&&getChart().getLegend()!=null) {
			getChart().getLegend().setVisible(false);
		}
	}

	public void disableLegendDrawer() {
		if (legendDrawerSupport!=null) {
			legendDrawerSupport.detach();
			legendDrawerSupport=null;
		}
		if (getChart()!=null&&getChart().getLegend()!=null) {
			getChart().getLegend().setVisible(true);
		}
	}

	public boolean isLegendDrawerEnabled() {
		return legendDrawerSupport!=null;
	}

	ChartLegendDrawerSupport getLegendDrawerSupportForTest() {
		return legendDrawerSupport;
	}

	@Override
	public void setChart(JFreeChart chart) {
		super.setChart(chart);
		if (legendDrawerSupport!=null) {
			legendDrawerSupport.refreshLegendRows();
			if (chart!=null&&chart.getLegend()!=null) {
				chart.getLegend().setVisible(false);
			}
		}
	}
}
