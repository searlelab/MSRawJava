package org.searlelab.msrawjava.gui;

import javax.swing.SwingUtilities;

import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

import com.formdev.flatlaf.FlatLightLaf;

public class GuiMain {

	public static void main(String[] args) {
		LookAndFeelManager.applyLookAndFeel(GUIPreferences.getLookAndFeelId(LookAndFeelManager.LAF_FLAT_LIGHT));
		ThermoServerPool.startAsync();
		ProcessingThreadPool pool=ProcessingThreadPool.createDefault();

		// Run Thermo server cleanup on JVM shutdown (quit, Ctrl-C, etc.)
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				ThermoServerPool.shutdown();
			} catch (Throwable ignore) {
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
					}
				}
			});
			app.setVisible(true);
		});
	}

}
