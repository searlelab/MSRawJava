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
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.searlelab.msrawjava.gui.utils.BackgroundKeyboardListener;
import org.searlelab.msrawjava.gui.loadingpanels.AstralLoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.FTICRLoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.LoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.QuadrupoleLoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.TOFLoadingPanel;
import org.searlelab.msrawjava.gui.visualization.RawBrowserData;
import org.searlelab.msrawjava.gui.visualization.RawBrowserDataLoader;
import org.searlelab.msrawjava.gui.visualization.RawBrowserPanel;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.VendorFile;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.mzml.MzmlFile;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.logging.Logger;

/**
 * Dialog for inspecting file metadata and scan details.
 */
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
		// Use a non-owned dialog so it doesn't stay above the main window on macOS.
		final JDialog dlg=new JDialog((Frame)null, f.getName(), false); // non-modal
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		if (frame instanceof RawFileBrowser) {
			MenuManager.install(dlg, (RawFileBrowser)frame);
		}
		dlg.setSize(GUIPreferences.getRawBrowserWindowSize());
		java.awt.Point location=GUIPreferences.getRawBrowserWindowLocation();
		if (GUIPreferences.isVerboseGuiLogging()) {
			org.searlelab.msrawjava.logging.Logger.logLine("RawBrowser dialog location (saved): "+location);
		}
		java.awt.Point clamped=GUIPreferences.clampToScreens(location, dlg.getSize());
		if (GUIPreferences.isVerboseGuiLogging()) {
			org.searlelab.msrawjava.logging.Logger.logLine("RawBrowser dialog location (clamped): "+clamped);
		}
		if (clamped!=null) {
			dlg.setLocation(clamped);
		} else {
			dlg.setLocationRelativeTo(frame);
		}
		new BackgroundKeyboardListener().addKeyAndContainerListenerRecursively(dlg);

		String loadingText="Scanning "+f.getName();
		final LoadingPanel[] loadingPanels=new LoadingPanel[] {new FTICRLoadingPanel(loadingText), new TOFLoadingPanel(loadingText),
				new QuadrupoleLoadingPanel(loadingText), new AstralLoadingPanel(loadingText)};

		final LoadingPanel loading=loadingPanels[(int)(Math.random()*loadingPanels.length)];

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
		SwingWorker<StripeResult, String> worker=new SwingWorker<>() {
			@Override
			protected StripeResult doInBackground() throws Exception {
				StripeFileInterface stripe=null;
				try {
					VendorFile vendor=VendorFile.fromPath(f.toPath()).orElse(null);
					if (vendor==VendorFile.BRUKER) {
						BrukerTIMSFile raw=new BrukerTIMSFile();
						raw.openFile(f.toPath());
						stripe=raw;
						RawBrowserData data=RawBrowserDataLoader.build(raw);
						return StripeResult.success(raw, data);
					} else if (vendor==VendorFile.THERMO) {
						ThermoRawFile raw=new ThermoRawFile();
						raw.openFile(f.toPath());
						stripe=raw;
						RawBrowserData data=RawBrowserDataLoader.build(raw);
						return StripeResult.success(raw, data);
					} else if (vendor==VendorFile.ENCYCLOPEDIA) {
						EncyclopeDIAFile dia=new EncyclopeDIAFile();
						dia.openFile(f);
						if (dia.needsSpectraTicUpgrade()) {
							final boolean[] upgradeAccepted=new boolean[] {false};
							SwingUtilities.invokeAndWait(() -> {
								int choice=JOptionPane.showConfirmDialog(dlg,
										"This DIA file uses an older schema (0.7.0) without spectra TIC.\nUpgrade to 0.8.0 now and calculate TIC values?",
										"Upgrade DIA Schema", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
								upgradeAccepted[0]=choice==JOptionPane.YES_OPTION;
							});
							if (upgradeAccepted[0]) {
								final JDialog[] waitDialog=new JDialog[1];
								SwingUtilities.invokeAndWait(() -> {
									waitDialog[0]=createUpgradeWaitDialog(dlg, "Upgrading DIA schema and calculating spectra TIC...");
									SwingUtilities.invokeLater(() -> waitDialog[0].setVisible(true));
								});
								try {
									dia.upgradeSchemaToV080();
								} finally {
									SwingUtilities.invokeLater(() -> {
										if (waitDialog[0]!=null) waitDialog[0].dispose();
									});
								}
							}
						}
						stripe=dia;
						RawBrowserData data=RawBrowserDataLoader.build(dia);
						return StripeResult.success(dia, data);
					} else if (vendor==VendorFile.MZML) {
						MzmlFile mzml=new MzmlFile();
						mzml.openFile(f);
						stripe=mzml;
						RawBrowserData data=RawBrowserDataLoader.build(mzml);
						return StripeResult.success(mzml, data);
					} else {
						return StripeResult.error("Unsupported file");
					}
				} catch (Exception e) {
					if (stripe!=null) {
						try {
							stripe.close();
						} catch (Throwable ignore) {
							Logger.errorException(ignore);
						}
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
							try {
								cancelled.stripe.close();
							} catch (Throwable ignore) {
								Logger.errorException(ignore);
							}
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
					Logger.logException(ex);
					dlg.setContentPane(new JLabel("Cannot parse file!"));
					dlg.revalidate();
					dlg.repaint();
				}
			}
		};

		// Cancel load if dialog closes
		dlg.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				worker.cancel(true);
			}

			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				worker.cancel(true);
				if (panelRef[0]!=null) {
					try {
						panelRef[0].close();
					} catch (Throwable ignore) {
						Logger.errorException(ignore);
					}
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

	private static JDialog createUpgradeWaitDialog(JDialog owner, String message) {
		JDialog wait=new JDialog(owner, "Upgrading DIA File", true);
		wait.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		wait.setResizable(false);
		wait.setLayout(new GridBagLayout());
		GridBagConstraints gbc=new GridBagConstraints();
		gbc.gridx=0;
		gbc.gridy=0;
		gbc.anchor=GridBagConstraints.CENTER;
		gbc.insets=new java.awt.Insets(8, 12, 6, 12);
		wait.add(new JLabel(message), gbc);
		gbc.gridy=1;
		gbc.fill=GridBagConstraints.HORIZONTAL;
		gbc.weightx=1.0;
		JProgressBar bar=new JProgressBar();
		bar.setIndeterminate(true);
		wait.add(bar, gbc);
		wait.pack();
		wait.setLocationRelativeTo(owner);
		return wait;
	}

}
