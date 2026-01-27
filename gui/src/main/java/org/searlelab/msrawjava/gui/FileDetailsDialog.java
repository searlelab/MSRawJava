package org.searlelab.msrawjava.gui;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingWorker;

import org.searlelab.msrawjava.gui.utils.BackgroundKeyboardListener;
import org.searlelab.msrawjava.gui.loadingpanels.FTICRLoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.LoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.QuadrupoleLoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.TOFLoadingPanel;
import org.searlelab.msrawjava.gui.visualization.RawBrowserData;
import org.searlelab.msrawjava.gui.visualization.RawBrowserDataLoader;
import org.searlelab.msrawjava.gui.visualization.RawBrowserPanel;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.VendorFileFinder;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;

public class FileDetailsDialog {
	private static final class StripeResult {
		private final StripeFileInterface stripe;
		private final RawBrowserData data;
		private final String error;

		private StripeResult(StripeFileInterface stripe, RawBrowserData data, String error) {
			this.stripe=stripe;
			this.data=data;
			this.error=error;
		}

		static StripeResult error(String message) {
			return new StripeResult(null, null, message);
		}

		static StripeResult success(StripeFileInterface stripe, RawBrowserData data) {
			return new StripeResult(stripe, data, null);
		}
	}

	public static void showFileDetailsDialog(Frame frame, File f) {
	    final JDialog dlg = new JDialog(frame, f.getName(), false); // non-modal
	    dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
	    dlg.setSize(GUIPreferences.getRawBrowserWindowSize());
	    java.awt.Point location=GUIPreferences.getRawBrowserWindowLocation();
	    org.searlelab.msrawjava.logging.Logger.logLine("RawBrowser dialog location (saved): "+location);
	    java.awt.Point clamped=GUIPreferences.clampToScreens(location, dlg.getSize());
	    org.searlelab.msrawjava.logging.Logger.logLine("RawBrowser dialog location (clamped): "+clamped);
	    if (clamped!=null) {
	    	dlg.setLocation(clamped);
	    } else {
	    	dlg.setLocationRelativeTo(frame);
	    }
	    new BackgroundKeyboardListener().addKeyAndContainerListenerRecursively(dlg);

	    final LoadingPanel[] loadingPanels = new LoadingPanel[]{
	            new FTICRLoadingPanel("Reading " + f.getName()),
	            new TOFLoadingPanel("Reading " + f.getName()),
	            new QuadrupoleLoadingPanel("Reading " + f.getName())
	    };
	    final LoadingPanel loading = loadingPanels[(int) (Math.random() * loadingPanels.length)];

	    Dimension loadingSize=new Dimension(900, 350);
	    loading.setPreferredSize(loadingSize);
	    loading.setMaximumSize(loadingSize);
	    java.awt.Container loadingWrapper=new javax.swing.JPanel(new GridBagLayout());
	    GridBagConstraints gbc=new GridBagConstraints();
	    gbc.gridx=0;
	    gbc.gridy=0;
	    gbc.anchor=GridBagConstraints.CENTER;
	    loadingWrapper.add(loading, gbc);
	    dlg.setContentPane(loadingWrapper);
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
		                return StripeResult.success(raw, data);
		            } else if (VendorFileFinder.isThermoFile(f.toPath())) {
		                ThermoRawFile raw = new ThermoRawFile();
		                raw.openFile(f.toPath());
		                stripe=raw;
		                RawBrowserData data=RawBrowserDataLoader.build(raw);
		                return StripeResult.success(raw, data);
		            } else if (f.getName().toLowerCase().endsWith(".dia")) {
		            	EncyclopeDIAFile dia=new EncyclopeDIAFile();
		            	dia.openFile(f);
		            	stripe=dia;
		            	RawBrowserData data=RawBrowserDataLoader.build(dia);
		            	return StripeResult.success(dia, data);
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
	                	RawBrowserPanel panel=new RawBrowserPanel(result.stripe, result.data);
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
	    dlg.addComponentListener(new ComponentAdapter() {
	    	@Override
	    	public void componentResized(ComponentEvent e) {
	    		GUIPreferences.setRawBrowserWindowSize(dlg.getSize());
	    	}
	    	@Override
	    	public void componentMoved(ComponentEvent e) {
	    		GUIPreferences.setRawBrowserWindowLocation(dlg.getLocation());
	    	}
		});

	    worker.execute();
	}

}
