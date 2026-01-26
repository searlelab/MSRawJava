package org.searlelab.msrawjava.gui;

import java.awt.Frame;
import java.io.File;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingWorker;

import org.searlelab.msrawjava.gui.loadingpanels.FTICRLoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.LoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.QuadrupoleLoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.TOFLoadingPanel;
import org.searlelab.msrawjava.io.VendorFileFinder;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.gui.visualization.RawBrowserData;
import org.searlelab.msrawjava.gui.visualization.RawBrowserDataLoader;
import org.searlelab.msrawjava.gui.visualization.RawBrowserPanel;

public class FileDetailsDialog {
	private static final class StripeResult {
		private final StripeFileInterface stripe;
		private final String displayName;
		private final RawBrowserData data;
		private final String error;

		private StripeResult(StripeFileInterface stripe, String displayName, RawBrowserData data, String error) {
			this.stripe=stripe;
			this.displayName=displayName;
			this.data=data;
			this.error=error;
		}

		static StripeResult error(String message) {
			return new StripeResult(null, null, null, message);
		}

		static StripeResult success(StripeFileInterface stripe, String displayName, RawBrowserData data) {
			return new StripeResult(stripe, displayName, data, null);
		}
	}

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

	    final RawBrowserPanel[] panelRef=new RawBrowserPanel[1];
	    SwingWorker<StripeResult, String> worker = new SwingWorker<>() {
	        @Override
	        protected StripeResult doInBackground() throws Exception {
	        	StripeFileInterface stripe=null;
	        	try {
		            if (VendorFileFinder.isDotDFile(f.toPath())) {
		                BrukerTIMSFile raw = new BrukerTIMSFile();
		                raw.openFile(f.toPath());
		                stripe=raw;
		                RawBrowserData data=RawBrowserDataLoader.build(raw);
		                return StripeResult.success(raw, f.getName(), data);
		            } else if (VendorFileFinder.isThermoFile(f.toPath())) {
		                ThermoRawFile raw = new ThermoRawFile();
		                raw.openFile(f.toPath());
		                stripe=raw;
		                RawBrowserData data=RawBrowserDataLoader.build(raw);
		                return StripeResult.success(raw, f.getName(), data);
		            } else if (f.getName().toLowerCase().endsWith(".dia")) {
		            	EncyclopeDIAFile dia=new EncyclopeDIAFile();
		            	dia.openFile(f);
		            	stripe=dia;
		            	RawBrowserData data=RawBrowserDataLoader.build(dia);
		            	return StripeResult.success(dia, f.getName(), data);
		            } else {
		                return StripeResult.error("Unsupported file");
		            }
	        	} catch (Exception e) {
	        		if (stripe!=null) {
	        			try { stripe.close(); } catch (Throwable ignore) {}
	        		}
	        		throw e;
	        	}
	        }

	        @Override
	        protected void done() {
	            loading.stop();
	            try {
	                if (isCancelled()) {
	                	StripeResult cancelled=get();
	                	if (cancelled!=null&&cancelled.stripe!=null) {
	                		try { cancelled.stripe.close(); } catch (Throwable ignore) {}
	                	}
	                	dlg.dispose();
	                	return;
	                }
	                StripeResult result=get();
	                if (result==null||result.error!=null) {
	                	dlg.setContentPane(new JLabel(result!=null?result.error:"Cannot parse file!"));
	                } else {
	                	RawBrowserPanel panel=new RawBrowserPanel(result.stripe, result.displayName, result.data);
	                	panelRef[0]=panel;
	                	dlg.setContentPane(panel);
	                }
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
	        @Override public void windowClosed (java.awt.event.WindowEvent e) {
	        	worker.cancel(true);
	        	if (panelRef[0]!=null) {
	        		try { panelRef[0].close(); } catch (Throwable ignore) {}
	        	}
	        }
	    });

	    worker.execute();
	}

}
