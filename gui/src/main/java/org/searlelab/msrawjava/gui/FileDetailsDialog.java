package org.searlelab.msrawjava.gui;

import java.awt.Frame;
import java.io.File;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingWorker;

import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.gui.charts.BasicChartGenerator;
import org.searlelab.msrawjava.gui.charts.GraphType;
import org.searlelab.msrawjava.gui.charts.XYTrace;
import org.searlelab.msrawjava.gui.loadingpanels.FTICRLoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.LoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.QuadrupoleLoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.TOFLoadingPanel;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.VendorFileFinder;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.io.utils.Pair;

public class FileDetailsDialog {
	public static void showFileDetailsDialog(Frame frame, File f) {
	    final JDialog dlg = new JDialog(frame, f.getName(), false); // non-modal
	    dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
	    dlg.setSize(800, 500);
	    dlg.setLocationRelativeTo(frame);

	    final LoadingPanel[] loadingPanels = new LoadingPanel[]{
	            new FTICRLoadingPanel("Reading " + f.getName()),
	            new TOFLoadingPanel("Reading " + f.getName()),
	            new QuadrupoleLoadingPanel("Reading " + f.getName())
	    };
	    final LoadingPanel loading = loadingPanels[(int) (Math.random() * loadingPanels.length)];

	    dlg.setContentPane(loading);
	    dlg.setVisible(true);

	    SwingWorker<JComponent, String> worker = new SwingWorker<>() {
	        @Override
	        protected JComponent doInBackground() throws Exception {
	            if (VendorFileFinder.isDotDFile(f.toPath())) {
	                BrukerTIMSFile raw = new BrukerTIMSFile();
	                try {
	                    raw.openFile(f.toPath());
	                    Pair<float[], float[]> tic = raw.getTICTrace();
	                    XYTrace trace = new XYTrace(MatrixMath.divide(tic.x, 60.0f), tic.y, GraphType.area,
	                            OutputType.changeExtension(raw.getOriginalFileName(), ""), null, null);
	                    return BasicChartGenerator.getChart("Time (min)", "Total Ion Current", false, trace);
	                } finally { try { raw.close(); } catch (Throwable ignore) {} }
	            } else if (VendorFileFinder.isThermoFile(f.toPath())) {
	                ThermoRawFile raw = new ThermoRawFile();
	                try {
	                    raw.openFile(f.toPath());
	                    Pair<float[], float[]> tic = raw.getTICTrace();
	                    XYTrace trace = new XYTrace(MatrixMath.divide(tic.x, 60.0f), tic.y, GraphType.area,
	                            OutputType.changeExtension(raw.getOriginalFileName(), ""), null, null);
	                    return BasicChartGenerator.getChart("Time (min)", "Total Ion Current", false, trace);
	                } finally { try { raw.close(); } catch (Throwable ignore) {} }
	            } else {
	                return new JLabel("Unsupported file");
	            }
	        }

	        @Override
	        protected void done() {
	            loading.stop();
	            try {
	                if (isCancelled()) { dlg.dispose(); return; }
	                JComponent panel = get();
	                dlg.setContentPane(panel != null ? panel : new JLabel("Cannot parse file!"));
	                dlg.revalidate();
	                dlg.repaint();
	            } catch (Exception ex) {
	                dlg.setContentPane(new JLabel("Cannot parse file!"));
	                dlg.revalidate();
	                dlg.repaint();
	            }
	        }
	    };

	    // Cancel load if dialog closes
	    dlg.addWindowListener(new java.awt.event.WindowAdapter() {
	        @Override public void windowClosing(java.awt.event.WindowEvent e) { worker.cancel(true); }
	        @Override public void windowClosed (java.awt.event.WindowEvent e) { worker.cancel(true); }
	    });

	    worker.execute();
	}

}
