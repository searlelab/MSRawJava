package org.searlelab.msrawjava.gui;

import java.awt.Desktop;
import java.util.Locale;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.searlelab.msrawjava.COREPreferences;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

/**
 * GUI entry point for launching the application.
 */
public class GuiMain {

	public static void main(String[] args) {
		LoggingConsoleDialog.installSystemCapture();
		OpenFileDispatcher opens=new OpenFileDispatcher();
		opens.submitArguments(args);
		boolean mac=System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
		// Register on the main thread, before startup dialogs or browser initialization.
		boolean launcherEvents=opens.registerInstall4j(GuiMain.class.getClassLoader(), mac);
		if (!launcherEvents&&Desktop.isDesktopSupported()) {
			Desktop desktop=Desktop.getDesktop();
			if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) desktop.setOpenFileHandler(event -> opens.submit(event.getFiles()));
		}
		LookAndFeelManager.applyLookAndFeel(GUIPreferences.getLookAndFeelId(LookAndFeelManager.LAF_FLAT_LIGHT));
		askProcessingQuestionIfNeeded();
		ThermoServerPool.setProcessingThreadLimit(COREPreferences.getProcessingThreadLimit());
		ThermoServerPool.startAsync();
		ProcessingThreadPool pool=ProcessingThreadPool.createWithThreadLimit(COREPreferences.getProcessingThreadLimit());

		// Run Thermo server cleanup on JVM shutdown (quit, Ctrl-C, etc.)
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				ThermoServerPool.shutdown();
			} catch (Throwable ignore) {
				Logger.errorException(ignore);
			}
		}, "ThermoServerPool-shutdown"));

		SwingUtilities.invokeLater(() -> {
			RawFileBrowser app=new RawFileBrowser(pool);
			// Also trigger shutdown when the window is closed (belt & suspenders)
			app.addWindowListener(new java.awt.event.WindowAdapter() {
				@Override
				public void windowClosing(java.awt.event.WindowEvent e) {
					try {
						ThermoServerPool.shutdown();
						pool.close();
					} catch (Throwable ignore) {
						Logger.errorException(ignore);
					}
				}
			});
			app.setVisible(true);
			opens.attach(app::openExternalFiles);
		});
	}

	private static void askProcessingQuestionIfNeeded() {
		if (!COREPreferences.isAskProcessingOnStartup()) return;
		Object[] options= {"Yes (min processing)", "No (max processing)"};
		int answer=JOptionPane.showOptionDialog(null, "Is this an instrument computer?\nCan be changed in preferences.", "Processing", JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		if (answer==JOptionPane.YES_OPTION) {
			COREPreferences.setProcessingThreadLimit(2);
			COREPreferences.setAskProcessingOnStartup(false);
		} else if (answer==JOptionPane.NO_OPTION) {
			COREPreferences.setProcessingThreadLimit(null);
			COREPreferences.setAskProcessingOnStartup(false);
		}
	}

}
