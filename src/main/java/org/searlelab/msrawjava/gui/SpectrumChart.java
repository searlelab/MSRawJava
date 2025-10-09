package org.searlelab.msrawjava.gui;

import java.awt.BorderLayout;
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

public final class SpectrumChart {
    /** Build a dataset that draws each peak as a vertical "stick". */
    private static XYSeriesCollection sticks(double[] mz, float[] intensity) {
        if (mz == null || intensity == null || mz.length != intensity.length) {
            throw new IllegalArgumentException("mz and intensity must be non-null and same length");
        }
        XYSeries s = new XYSeries("Spectrum", false, true);
        for (int i = 0; i < mz.length; i++) {
            double x = mz[i];
            double y = intensity[i];
            // Skip non-finite or negative intensities
            if (!Double.isFinite(x) || !Double.isFinite(y) || y <= 0.0) continue;

            // Draw a vertical stick by adding two points and a NaN break
            s.add(x, 0.0);
            s.add(x, y);
            s.add(Double.NaN, Double.NaN); // break the line before the next stick
        }
        XYSeriesCollection ds = new XYSeriesCollection();
        ds.addSeries(s);
        return ds;
    }

    /** Create a simple m/z vs intensity stick spectrum. */
    public static JFreeChart buildChart(double[] mz, float[] intensity) {
        var ds = sticks(mz, intensity);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Mass Spectrum",
                "m/z",
                "Intensity (a.u.)",
                ds,
                PlotOrientation.VERTICAL,
                false, // legend
                true,  // tooltips
                false  // URLs
        );

        // Cleaner axes
        NumberAxis x = (NumberAxis) chart.getXYPlot().getDomainAxis();
        x.setAutoRangeIncludesZero(false);
        NumberAxis y = (NumberAxis) chart.getXYPlot().getRangeAxis();
        y.setAutoRangeIncludesZero(true);

        // Sticks: lines on, shapes off
        XYLineAndShapeRenderer r = new XYLineAndShapeRenderer(true, false);
        chart.getXYPlot().setRenderer(r);
        return chart;
    }

    /** Tiny viewer for quick eyeballing. */
    public static void show(JFreeChart chart) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame(chart.getTitle().getText());
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.setLayout(new BorderLayout());
            f.add(new ChartPanel(chart), BorderLayout.CENTER);
            f.setSize(900, 500);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}