package org.searlelab.msrawjava.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Paint;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.PaintScale;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.xy.DefaultXYZDataset;

/**
 * MobilogramHeatmap displays a basic heatmap of mobility-indexed signal to provide a quick, visual sanity check of
 * calibration and extraction across frames/scans. This visualization is for developer testing and diagnostics only and
 * is not part of normal library or CLI operation.
 */
public final class MobilogramHeatmap {
    /** Quick default: 600 m/z bins. */
    public static JFreeChart buildChart(int[] scan, double[] mz, float[] intensity) {
        return buildChart(scan, mz, intensity, 600);
    }
    public static JFreeChart buildChart(float[] ims, double[] mz, float[] intensity, int mzBins) {
    	int[] scan=new int[ims.length];
    	for (int i=0; i<scan.length; i++) {
			scan[i]=Math.round(ims[i]*500);
		}
    	return buildChart(scan, mz, intensity, mzBins);
    }

    /**
     * Build a mobilogram with the requested number of m/z bins.
     * @param scan       int per peak (same length as mz/intensity)
     * @param mz         m/z per peak
     * @param intensity  intensity per peak
     * @param mzBins     number of bins along m/z (e.g., 400–1200 is 800 bins at 1.0 Da)
     */
    public static JFreeChart buildChart(int[] scan, double[] mz, float[] intensity, int mzBins) {
        if (scan == null || mz == null || intensity == null ||
            scan.length != mz.length || mz.length != intensity.length) {
            throw new IllegalArgumentException("scan, mz, intensity must be non-null and same length");
        }
        if (scan.length == 0 || mzBins <= 0) {
            return emptyChart();
        }

        // Scan range (integer bins)
        int sMin = Integer.MAX_VALUE, sMax = Integer.MIN_VALUE;
        for (int s : scan) { if (s < sMin) sMin = s; if (s > sMax) sMax = s; }
        int cols = (sMax - sMin + 1);        // one column per scan
        if (cols <= 0) return emptyChart();

        // m/z range
        double mzMin = Double.POSITIVE_INFINITY, mzMax = Double.NEGATIVE_INFINITY;
        for (double v : mz) { if (v < mzMin) mzMin = v; if (v > mzMax) mzMax = v; }
        if (!Double.isFinite(mzMin) || !Double.isFinite(mzMax) || mzMax <= mzMin) {
            return emptyChart();
        }
        int rows = mzBins;
        final double mzBinHeight = (mzMax - mzMin) / rows;

        // Accumulate summed intensity per (row, col) bin
        double[] grid = new double[rows * cols];
        boolean[] seen = new boolean[rows * cols];

        double max = 0.0;
        for (int i = 0; i < scan.length; i++) {
            int col = scan[i] - sMin;                     // scan bin (width=1)
            if (col < 0 || col >= cols) continue;

            int row = (int) Math.floor((mz[i] - mzMin) / mzBinHeight);
            if (row < 0) row = 0;
            if (row >= rows) row = rows - 1;

            int idx = row * cols + col;
            grid[idx] += intensity[i];
            seen[idx] = true;
            if (grid[idx] > max) max = grid[idx];
        }

        // Build an XYZ dataset: one sample at each bin center; block width/height set below
        int n = rows * cols;
        double[] X = new double[n];
        double[] Y = new double[n];
        double[] Z = new double[n];

        int k = 0;
        for (int r = 0; r < rows; r++) {
            double yCenter = mzMin + (r + 0.5) * mzBinHeight;
            for (int c = 0; c < cols; c++, k++) {
                double xCenter = sMin + (c + 0.5); // scan bin center
                X[k] = xCenter;
                Y[k] = yCenter;
                double z = grid[r * cols + c];
                Z[k] = z; // keep zeros; paint scale will show them as gray
            }
        }

        DefaultXYZDataset ds = new DefaultXYZDataset();
        ds.addSeries("mobilogram", new double[][]{ X, Y, Z });

        // Axes
        NumberAxis xAxis = new NumberAxis("Scan");
        xAxis.setAutoRangeIncludesZero(false);
        NumberAxis yAxis = new NumberAxis("m/z");
        yAxis.setAutoRangeIncludesZero(false);

        // Renderer: draw one block per bin
        XYBlockRenderer r = new XYBlockRenderer();
        r.setBlockWidth(1.0);              // 1 scan wide
        r.setBlockHeight(mzBinHeight);     // one m/z bin tall

        // Paint scale: blue (low) → red (high), gray for zero/empty
        PaintScale scale = new BlueGrayRedScale(0.0, (max > 0 ? max : 1.0));
        r.setPaintScale(scale);

        XYPlot plot = new XYPlot(ds, xAxis, yAxis, r);
        JFreeChart chart = new JFreeChart("TIMS Frame Mobilogram", JFreeChart.DEFAULT_TITLE_FONT, plot, false);

        // Color bar
        NumberAxis zAxis = new NumberAxis("Intensity");
        PaintScaleLegend legend = new PaintScaleLegend(scale, zAxis);
        legend.setPosition(RectangleEdge.RIGHT);
        chart.addSubtitle(legend);

        return chart;
    }

    /** Simple viewer window for quick experiments. */
    public static void show(JFreeChart chart) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame(chart.getTitle().getText());
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.setLayout(new BorderLayout());
            f.add(new ChartPanel(chart), BorderLayout.CENTER);
            f.setSize(1000, 600);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

    private static JFreeChart emptyChart() {
        DefaultXYZDataset ds = new DefaultXYZDataset();
        NumberAxis xAxis = new NumberAxis("Scan");
        NumberAxis yAxis = new NumberAxis("m/z");
        XYBlockRenderer r = new XYBlockRenderer();
        XYPlot plot = new XYPlot(ds, xAxis, yAxis, r);
        return new JFreeChart("TIMS Frame Mobilogram", JFreeChart.DEFAULT_TITLE_FONT, plot, false);
    }

    /** Blue (low) → Red (high); exact zero (or negative) → Gray. */
    private static final class BlueGrayRedScale implements PaintScale {
        private final double lower;
        private final double upper;
        private final Color empty = new Color(160,160,160); // gray

        BlueGrayRedScale(double lower, double upper) {
            this.lower = lower;
            this.upper = (upper > lower) ? upper : lower + 1.0;
        }
        @Override public double getLowerBound() { return lower; }
        @Override public double getUpperBound() { return upper; }
        @Override public Paint getPaint(double value) {
            if (!(value > 0.0)) return empty; // zero, negative, or NaN → gray
            double t = (value - lower) / (upper - lower);
            if (t < 0) t = 0; if (t > 1) t = 1;

            // Interpolate from blue (low) to red (high)
            // blue: (0, 92, 230)  red: (220, 0, 0)
            int r = (int) Math.round(0   + t * (220 - 0));
            int g = (int) Math.round(92  + t * (0 - 92));
            int b = (int) Math.round(230 + t * (0 - 230));
            if (g < 0) g = 0;
            return new Color(r, g, b);
        }
    }
}