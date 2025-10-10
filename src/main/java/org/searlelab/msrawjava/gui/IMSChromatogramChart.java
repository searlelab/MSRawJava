package org.searlelab.msrawjava.gui;

import java.awt.BorderLayout;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.searlelab.msrawjava.model.Peak;

public class IMSChromatogramChart {
	/** Build dataset: one XYSeries per chromatogram, preserving insertion order. */
	private static XYSeriesCollection buildDataset(ArrayList<ArrayList<Peak>> traces) {
		XYSeriesCollection ds=new XYSeriesCollection();
		if (traces==null) return ds;

		for (int t=0; t<traces.size(); t++) {
			ArrayList<Peak> chrom=traces.get(t);
			if (chrom==null||chrom.isEmpty()) continue;

			XYSeries series=new XYSeries("Trace "+(t+1), false, true);
			for (Peak p : chrom) {
				if (p==null) continue;
				double x=p.ims; // IMS on X
				double y=p.intensity; // intensity on Y
				if (Double.isFinite(x)&&Double.isFinite(y)) {
					series.add(x, y);
				}
			}
			if (series.getItemCount()>0) ds.addSeries(series);
		}
		return ds;
	}

	/** Create the chart (line plot, no shapes). */
	public static JFreeChart buildChart(ArrayList<ArrayList<Peak>> traces) {
		XYSeriesCollection ds=buildDataset(traces);

		JFreeChart chart=ChartFactory.createXYLineChart("IMS Chromatogram", "Ion mobility (1/K₀)", // X label
				"Intensity (a.u.)", // Y label
				ds, PlotOrientation.VERTICAL, (ds.getSeriesCount()>1), // legend only if multiple traces
				true, // tooltips
				false // URLs
		);

		// Clean axes
		var plot=chart.getXYPlot();
		NumberAxis x=(NumberAxis)plot.getDomainAxis();
		x.setAutoRangeIncludesZero(false);
		NumberAxis y=(NumberAxis)plot.getRangeAxis();
		y.setAutoRangeIncludesZero(true);

		// Lines on, shapes off (connect peaks per chromatogram)
		XYLineAndShapeRenderer r=new XYLineAndShapeRenderer(true, false);
		plot.setRenderer(r);

		chart.removeLegend();
		return chart;
	}

	/** Quick viewer. */
	public static void show(JFreeChart chart) {
		SwingUtilities.invokeLater(() -> {
			JFrame f=new JFrame(chart.getTitle().getText());
			f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			f.setLayout(new BorderLayout());
			f.add(new ChartPanel(chart), BorderLayout.CENTER);
			f.setSize(900, 500);
			f.setLocationRelativeTo(null);
			f.setVisible(true);
		});
	}
}