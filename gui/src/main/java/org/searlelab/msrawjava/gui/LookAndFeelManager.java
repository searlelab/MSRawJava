package org.searlelab.msrawjava.gui;

import java.awt.Window;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.StandardChartTheme;
import org.searlelab.msrawjava.logging.Logger;

/**
 * Configures Swing look-and-feel defaults for the application.
 */
public final class LookAndFeelManager {
	public static final String LAF_FLAT_LIGHT="flat-light";
	public static final String LAF_FLAT_DARK="flat-dark";
	public static final String LAF_SYSTEM="system";

	private LookAndFeelManager() {
	}

	public static void applyLookAndFeel(String id) {
		String laf=(id==null)?LAF_FLAT_LIGHT:id;
		try {
			switch (laf) {
				case LAF_FLAT_DARK:
					UIManager.setLookAndFeel(new FlatDarkLaf());
					ChartFactory.setChartTheme(StandardChartTheme.createDarknessTheme());
					break;
				case LAF_SYSTEM:
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
					ChartFactory.setChartTheme(StandardChartTheme.createJFreeTheme());
					break;
				case LAF_FLAT_LIGHT:
				default:
					UIManager.setLookAndFeel(new FlatLightLaf());
					ChartFactory.setChartTheme(StandardChartTheme.createJFreeTheme());
					break;
			}
			for (Window window : Window.getWindows()) {
				SwingUtilities.updateComponentTreeUI(window);
				window.invalidate();
				window.validate();
				window.repaint();
			}
		} catch (Exception e) {
			Logger.logException(e);
		}
	}
}
