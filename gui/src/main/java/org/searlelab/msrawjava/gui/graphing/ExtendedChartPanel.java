package org.searlelab.msrawjava.gui.graphing;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;

public class ExtendedChartPanel extends ChartPanel {
	private static final long serialVersionUID=1L;

	private final double divider;
	private final String name;

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
}
