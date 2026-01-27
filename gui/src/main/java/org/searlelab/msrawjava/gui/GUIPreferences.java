package org.searlelab.msrawjava.gui;

import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.RowSorter;
import javax.swing.SortOrder;

import org.searlelab.msrawjava.logging.Logger;

public final class GUIPreferences {
	private static final Preferences PREFS=Preferences.userNodeForPackage(GUIPreferences.class);
	private static final String PREF_VERBOSE_GUI_LOGGING="gui.verboseLogging";
	private static final String PREF_LOOK_AND_FEEL="gui.lookAndFeel";
	private static boolean verboseGuiLogging=PREFS.getBoolean(PREF_VERBOSE_GUI_LOGGING, true);

	public static final int DEFAULT_RAW_BROWSER_WIDTH=1280;
	public static final int DEFAULT_RAW_BROWSER_HEIGHT=700;
	public static final double DEFAULT_RAW_BROWSER_MAIN_SPLIT=0.3;
	public static final double DEFAULT_RAW_BROWSER_SCANS_SPLIT=0.5;
	public static final double DEFAULT_RAW_BROWSER_SPECTRUM_SPLIT=0.8;
	public static final double DEFAULT_RAW_BROWSER_IMS_SPLIT=0.5/DEFAULT_RAW_BROWSER_SPECTRUM_SPLIT;
	public static final double DEFAULT_RAW_BROWSER_BOXPLOT_SPLIT=0.5;
	public static final int DEFAULT_RAW_FILE_BROWSER_WIDTH=1280;
	public static final int DEFAULT_RAW_FILE_BROWSER_HEIGHT=700;
	public static final double DEFAULT_RAW_FILE_BROWSER_MAIN_SPLIT=0.3;
	public static final double DEFAULT_RAW_FILE_BROWSER_FILE_SPLIT=0.5;
	public static final double DEFAULT_CONVERSION_PANE_SPLIT=0.75;

	private static final String PREF_LAST_DIR="lastDir";
	private static final String PREF_RAW_BROWSER_WIDTH="rawBrowser.width";
	private static final String PREF_RAW_BROWSER_HEIGHT="rawBrowser.height";
	private static final String PREF_RAW_BROWSER_X="rawBrowser.x";
	private static final String PREF_RAW_BROWSER_Y="rawBrowser.y";
	private static final String PREF_RAW_BROWSER_SPLIT_MAIN="rawBrowser.split.main";
	private static final String PREF_RAW_BROWSER_SPLIT_SCANS="rawBrowser.split.scans";
	private static final String PREF_RAW_BROWSER_SPLIT_SPECTRUM="rawBrowser.split.spectrum";
	private static final String PREF_RAW_BROWSER_SPLIT_IMS="rawBrowser.split.ims";
	private static final String PREF_RAW_BROWSER_SPLIT_BOXPLOT="rawBrowser.split.boxplot";
	private static final String PREF_RAW_FILE_BROWSER_WIDTH="rawFileBrowser.width";
	private static final String PREF_RAW_FILE_BROWSER_HEIGHT="rawFileBrowser.height";
	private static final String PREF_RAW_FILE_BROWSER_X="rawFileBrowser.x";
	private static final String PREF_RAW_FILE_BROWSER_Y="rawFileBrowser.y";
	private static final String PREF_RAW_FILE_BROWSER_SPLIT_MAIN="rawFileBrowser.split.main";
	private static final String PREF_RAW_FILE_BROWSER_SPLIT_FILE="rawFileBrowser.split.file";
	private static final String PREF_DIR_TABLE_SORT_KEYS="rawFileBrowser.table.sortKeys";
	private static final String PREF_DIR_TABLE_COLUMN_ORDER="rawFileBrowser.table.columnOrder";
	private static final String PREF_DIR_TABLE_COLUMN_WIDTHS="rawFileBrowser.table.columnWidths";
	private static final String PREF_CONVERSION_PANE_SPLIT="conversionPane.split";

	private GUIPreferences() {
	}

	public static Preferences getPreferences() {
		logRead("preferences.node", PREFS.absolutePath());
		return PREFS;
	}

	public static boolean isVerboseGuiLogging() {
		logRead(PREF_VERBOSE_GUI_LOGGING, verboseGuiLogging);
		return verboseGuiLogging;
	}

	public static void setVerboseGuiLogging(boolean enabled) {
		verboseGuiLogging=enabled;
		PREFS.putBoolean(PREF_VERBOSE_GUI_LOGGING, enabled);
		logWrite(PREF_VERBOSE_GUI_LOGGING, enabled);
	}

	public static String getLookAndFeelId(String defaultValue) {
		String value=PREFS.get(PREF_LOOK_AND_FEEL, defaultValue);
		logRead(PREF_LOOK_AND_FEEL, value);
		return value;
	}

	public static void setLookAndFeelId(String value) {
		if (value==null) {
			PREFS.remove(PREF_LOOK_AND_FEEL);
			logWrite(PREF_LOOK_AND_FEEL, null);
			return;
		}
		PREFS.put(PREF_LOOK_AND_FEEL, value);
		logWrite(PREF_LOOK_AND_FEEL, value);
	}

	public static String getLastDirectory() {
		String value=PREFS.get(PREF_LAST_DIR, null);
		logRead(PREF_LAST_DIR, value);
		return value;
	}

	public static void rememberLastDirectory(File dir) {
		try {
			if (dir!=null&&dir.isDirectory()) {
				PREFS.put(PREF_LAST_DIR, dir.getAbsolutePath());
				logWrite(PREF_LAST_DIR, dir.getAbsolutePath());
				if (isVerboseGuiLogging()) {
					Logger.logLine("RawFileBrowser remembered directory: "+dir.getAbsolutePath());
				}
			}
		} catch (Exception ignore) {
		}
	}

	public static List<RowSorter.SortKey> getDirectorySummarySortKeys() {
		String raw=PREFS.get(PREF_DIR_TABLE_SORT_KEYS, "");
		logRead(PREF_DIR_TABLE_SORT_KEYS, raw);
		if (raw.isBlank()) return List.of();
		String[] parts=raw.split(",");
		List<RowSorter.SortKey> keys=new ArrayList<>(parts.length);
		for (String part : parts) {
			String[] pieces=part.split(":", 2);
			if (pieces.length!=2) continue;
			try {
				int col=Integer.parseInt(pieces[0]);
				SortOrder order=SortOrder.valueOf(pieces[1]);
				keys.add(new RowSorter.SortKey(col, order));
			} catch (Exception ignore) {
			}
		}
		return keys;
	}

	public static void setDirectorySummarySortKeys(List<? extends RowSorter.SortKey> keys) {
		if (keys==null||keys.isEmpty()) {
			PREFS.remove(PREF_DIR_TABLE_SORT_KEYS);
			logWrite(PREF_DIR_TABLE_SORT_KEYS, null);
			return;
		}
		StringBuilder out=new StringBuilder();
		for (RowSorter.SortKey key : keys) {
			if (key==null) continue;
			if (out.length()>0) out.append(',');
			out.append(key.getColumn()).append(':').append(key.getSortOrder().name());
		}
		if (out.length()==0) {
			PREFS.remove(PREF_DIR_TABLE_SORT_KEYS);
			logWrite(PREF_DIR_TABLE_SORT_KEYS, null);
			return;
		}
		PREFS.put(PREF_DIR_TABLE_SORT_KEYS, out.toString());
		logWrite(PREF_DIR_TABLE_SORT_KEYS, out.toString());
	}

	public static List<Integer> getDirectorySummaryColumnOrder() {
		String raw=PREFS.get(PREF_DIR_TABLE_COLUMN_ORDER, "");
		logRead(PREF_DIR_TABLE_COLUMN_ORDER, raw);
		if (raw.isBlank()) return List.of();
		String[] parts=raw.split(",");
		List<Integer> order=new ArrayList<>(parts.length);
		for (String part : parts) {
			try {
				order.add(Integer.parseInt(part));
			} catch (Exception ignore) {
			}
		}
		return order;
	}

	public static void setDirectorySummaryColumnOrder(List<Integer> order) {
		if (order==null||order.isEmpty()) {
			PREFS.remove(PREF_DIR_TABLE_COLUMN_ORDER);
			logWrite(PREF_DIR_TABLE_COLUMN_ORDER, null);
			return;
		}
		StringBuilder out=new StringBuilder();
		for (Integer idx : order) {
			if (idx==null) continue;
			if (out.length()>0) out.append(',');
			out.append(idx.intValue());
		}
		if (out.length()==0) {
			PREFS.remove(PREF_DIR_TABLE_COLUMN_ORDER);
			logWrite(PREF_DIR_TABLE_COLUMN_ORDER, null);
			return;
		}
		PREFS.put(PREF_DIR_TABLE_COLUMN_ORDER, out.toString());
		logWrite(PREF_DIR_TABLE_COLUMN_ORDER, out.toString());
	}

	public static Map<Integer, Integer> getDirectorySummaryColumnWidths() {
		String raw=PREFS.get(PREF_DIR_TABLE_COLUMN_WIDTHS, "");
		logRead(PREF_DIR_TABLE_COLUMN_WIDTHS, raw);
		if (raw.isBlank()) return Map.of();
		String[] parts=raw.split(",");
		Map<Integer, Integer> widths=new HashMap<>(parts.length);
		for (String part : parts) {
			String[] pieces=part.split("=", 2);
			if (pieces.length!=2) continue;
			try {
				int col=Integer.parseInt(pieces[0]);
				int width=Integer.parseInt(pieces[1]);
				if (width>0) widths.put(col, width);
			} catch (Exception ignore) {
			}
		}
		return widths;
	}

	public static void setDirectorySummaryColumnWidths(Map<Integer, Integer> widths) {
		if (widths==null||widths.isEmpty()) {
			PREFS.remove(PREF_DIR_TABLE_COLUMN_WIDTHS);
			logWrite(PREF_DIR_TABLE_COLUMN_WIDTHS, null);
			return;
		}
		StringBuilder out=new StringBuilder();
		for (Map.Entry<Integer, Integer> entry : widths.entrySet()) {
			if (entry.getKey()==null||entry.getValue()==null) continue;
			int width=entry.getValue().intValue();
			if (width<=0) continue;
			if (out.length()>0) out.append(',');
			out.append(entry.getKey().intValue()).append('=').append(width);
		}
		if (out.length()==0) {
			PREFS.remove(PREF_DIR_TABLE_COLUMN_WIDTHS);
			logWrite(PREF_DIR_TABLE_COLUMN_WIDTHS, null);
			return;
		}
		PREFS.put(PREF_DIR_TABLE_COLUMN_WIDTHS, out.toString());
		logWrite(PREF_DIR_TABLE_COLUMN_WIDTHS, out.toString());
	}

	public static Dimension getRawBrowserWindowSize(int defaultWidth, int defaultHeight) {
		int width=PREFS.getInt(PREF_RAW_BROWSER_WIDTH, defaultWidth);
		int height=PREFS.getInt(PREF_RAW_BROWSER_HEIGHT, defaultHeight);
		logRead(PREF_RAW_BROWSER_WIDTH, width);
		logRead(PREF_RAW_BROWSER_HEIGHT, height);
		if (width<10||height<10) {
			width=defaultWidth;
			height=defaultHeight;
		}
		return new Dimension(width, height);
	}

	public static Dimension getRawBrowserWindowSize() {
		return getRawBrowserWindowSize(DEFAULT_RAW_BROWSER_WIDTH, DEFAULT_RAW_BROWSER_HEIGHT);
	}

	public static void setRawBrowserWindowSize(Dimension size) {
		if (size==null) return;
		if (size.width<10||size.height<10) return;
		PREFS.putInt(PREF_RAW_BROWSER_WIDTH, size.width);
		PREFS.putInt(PREF_RAW_BROWSER_HEIGHT, size.height);
		logWrite(PREF_RAW_BROWSER_WIDTH, size.width);
		logWrite(PREF_RAW_BROWSER_HEIGHT, size.height);
	}

	public static java.awt.Point getRawBrowserWindowLocation() {
		int x=PREFS.getInt(PREF_RAW_BROWSER_X, Integer.MIN_VALUE);
		int y=PREFS.getInt(PREF_RAW_BROWSER_Y, Integer.MIN_VALUE);
		logRead(PREF_RAW_BROWSER_X, x);
		logRead(PREF_RAW_BROWSER_Y, y);
		if (x==Integer.MIN_VALUE||y==Integer.MIN_VALUE) return null;
		return new java.awt.Point(x, y);
	}

	public static void setRawBrowserWindowLocation(java.awt.Point location) {
		if (location==null) return;
		PREFS.putInt(PREF_RAW_BROWSER_X, location.x);
		PREFS.putInt(PREF_RAW_BROWSER_Y, location.y);
		logWrite(PREF_RAW_BROWSER_X, location.x);
		logWrite(PREF_RAW_BROWSER_Y, location.y);
	}

	public static double getRawBrowserMainSplitRatio(double defaultValue) {
		return getSplitRatio(PREF_RAW_BROWSER_SPLIT_MAIN, defaultValue);
	}

	public static double getRawBrowserMainSplitRatio() {
		return getRawBrowserMainSplitRatio(DEFAULT_RAW_BROWSER_MAIN_SPLIT);
	}

	public static void setRawBrowserMainSplitRatio(double value) {
		setSplitRatio(PREF_RAW_BROWSER_SPLIT_MAIN, value);
		logWrite(PREF_RAW_BROWSER_SPLIT_MAIN, value);
	}

	public static double getRawBrowserScansSplitRatio(double defaultValue) {
		return getSplitRatio(PREF_RAW_BROWSER_SPLIT_SCANS, defaultValue);
	}

	public static double getRawBrowserScansSplitRatio() {
		return getRawBrowserScansSplitRatio(DEFAULT_RAW_BROWSER_SCANS_SPLIT);
	}

	public static void setRawBrowserScansSplitRatio(double value) {
		setSplitRatio(PREF_RAW_BROWSER_SPLIT_SCANS, value);
		logWrite(PREF_RAW_BROWSER_SPLIT_SCANS, value);
	}

	public static double getRawBrowserSpectrumSplitRatio(double defaultValue) {
		return getSplitRatio(PREF_RAW_BROWSER_SPLIT_SPECTRUM, defaultValue);
	}

	public static double getRawBrowserSpectrumSplitRatio() {
		return getRawBrowserSpectrumSplitRatio(DEFAULT_RAW_BROWSER_SPECTRUM_SPLIT);
	}

	public static void setRawBrowserSpectrumSplitRatio(double value) {
		setSplitRatio(PREF_RAW_BROWSER_SPLIT_SPECTRUM, value);
		logWrite(PREF_RAW_BROWSER_SPLIT_SPECTRUM, value);
	}

	public static double getRawBrowserImsSplitRatio(double defaultValue) {
		return getSplitRatio(PREF_RAW_BROWSER_SPLIT_IMS, defaultValue);
	}

	public static double getRawBrowserImsSplitRatio() {
		return getRawBrowserImsSplitRatio(DEFAULT_RAW_BROWSER_IMS_SPLIT);
	}

	public static void setRawBrowserImsSplitRatio(double value) {
		setSplitRatio(PREF_RAW_BROWSER_SPLIT_IMS, value);
		logWrite(PREF_RAW_BROWSER_SPLIT_IMS, value);
	}

	public static double getRawBrowserBoxplotSplitRatio(double defaultValue) {
		return getSplitRatio(PREF_RAW_BROWSER_SPLIT_BOXPLOT, defaultValue);
	}

	public static double getRawBrowserBoxplotSplitRatio() {
		return getRawBrowserBoxplotSplitRatio(DEFAULT_RAW_BROWSER_BOXPLOT_SPLIT);
	}

	public static void setRawBrowserBoxplotSplitRatio(double value) {
		setSplitRatio(PREF_RAW_BROWSER_SPLIT_BOXPLOT, value);
		logWrite(PREF_RAW_BROWSER_SPLIT_BOXPLOT, value);
	}

	public static Dimension getRawFileBrowserWindowSize() {
		int width=PREFS.getInt(PREF_RAW_FILE_BROWSER_WIDTH, DEFAULT_RAW_FILE_BROWSER_WIDTH);
		int height=PREFS.getInt(PREF_RAW_FILE_BROWSER_HEIGHT, DEFAULT_RAW_FILE_BROWSER_HEIGHT);
		logRead(PREF_RAW_FILE_BROWSER_WIDTH, width);
		logRead(PREF_RAW_FILE_BROWSER_HEIGHT, height);
		if (width<10||height<10) {
			width=DEFAULT_RAW_FILE_BROWSER_WIDTH;
			height=DEFAULT_RAW_FILE_BROWSER_HEIGHT;
		}
		return new Dimension(width, height);
	}

	public static void setRawFileBrowserWindowSize(Dimension size) {
		if (size==null) return;
		if (size.width<10||size.height<10) return;
		PREFS.putInt(PREF_RAW_FILE_BROWSER_WIDTH, size.width);
		PREFS.putInt(PREF_RAW_FILE_BROWSER_HEIGHT, size.height);
		logWrite(PREF_RAW_FILE_BROWSER_WIDTH, size.width);
		logWrite(PREF_RAW_FILE_BROWSER_HEIGHT, size.height);
	}

	public static java.awt.Point getRawFileBrowserWindowLocation() {
		int x=PREFS.getInt(PREF_RAW_FILE_BROWSER_X, Integer.MIN_VALUE);
		int y=PREFS.getInt(PREF_RAW_FILE_BROWSER_Y, Integer.MIN_VALUE);
		logRead(PREF_RAW_FILE_BROWSER_X, x);
		logRead(PREF_RAW_FILE_BROWSER_Y, y);
		if (x==Integer.MIN_VALUE||y==Integer.MIN_VALUE) return null;
		return new java.awt.Point(x, y);
	}

	public static void setRawFileBrowserWindowLocation(java.awt.Point location) {
		if (location==null) return;
		PREFS.putInt(PREF_RAW_FILE_BROWSER_X, location.x);
		PREFS.putInt(PREF_RAW_FILE_BROWSER_Y, location.y);
		logWrite(PREF_RAW_FILE_BROWSER_X, location.x);
		logWrite(PREF_RAW_FILE_BROWSER_Y, location.y);
	}

	public static double getRawFileBrowserMainSplitRatio() {
		return getSplitRatio(PREF_RAW_FILE_BROWSER_SPLIT_MAIN, DEFAULT_RAW_FILE_BROWSER_MAIN_SPLIT);
	}

	public static void setRawFileBrowserMainSplitRatio(double value) {
		setSplitRatio(PREF_RAW_FILE_BROWSER_SPLIT_MAIN, value);
		logWrite(PREF_RAW_FILE_BROWSER_SPLIT_MAIN, value);
	}

	public static double getRawFileBrowserFileSplitRatio() {
		return getSplitRatio(PREF_RAW_FILE_BROWSER_SPLIT_FILE, DEFAULT_RAW_FILE_BROWSER_FILE_SPLIT);
	}

	public static void setRawFileBrowserFileSplitRatio(double value) {
		setSplitRatio(PREF_RAW_FILE_BROWSER_SPLIT_FILE, value);
		logWrite(PREF_RAW_FILE_BROWSER_SPLIT_FILE, value);
	}

	public static double getConversionPaneSplitRatio() {
		return getSplitRatio(PREF_CONVERSION_PANE_SPLIT, DEFAULT_CONVERSION_PANE_SPLIT);
	}

	public static void setConversionPaneSplitRatio(double value) {
		setSplitRatio(PREF_CONVERSION_PANE_SPLIT, value);
		logWrite(PREF_CONVERSION_PANE_SPLIT, value);
	}

	private static double getSplitRatio(String key, double defaultValue) {
		double value=PREFS.getDouble(key, defaultValue);
		logRead(key, value);
		if (value<=0.0||value>=1.0) return defaultValue;
		return value;
	}

	private static void setSplitRatio(String key, double value) {
		if (value<=0.0||value>=1.0) return;
		PREFS.putDouble(key, value);
		logWrite(key, value);
	}

	public static Point clampToScreens(Point location, Dimension size) {
		logRead("window.clamp.input.location", location);
		logRead("window.clamp.input.size", size);
		if (location==null||size==null) return null;
		Rectangle window=new Rectangle(location, size);
		GraphicsEnvironment env=GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] devices=env.getScreenDevices();
		logRead("window.clamp.devices", devices.length);
		for (GraphicsDevice device : devices) {
			Rectangle bounds=device.getDefaultConfiguration().getBounds();
			logRead("window.clamp.device.bounds", bounds);
			if (bounds.intersects(window)) {
				logRead("window.clamp.result", location);
				return location;
			}
		}
		if (devices.length==0) return null;
		Rectangle primary=devices[0].getDefaultConfiguration().getBounds();
		int x=primary.x+Math.max(0, (primary.width-size.width)/2);
		int y=primary.y+Math.max(0, (primary.height-size.height)/2);
		Point clamped=new Point(x, y);
		logRead("window.clamp.result", clamped);
		return clamped;
	}

	public static void resetAll() {
		PREFS.remove(PREF_LAST_DIR);
		logWrite(PREF_LAST_DIR, null);
		PREFS.remove(PREF_DIR_TABLE_SORT_KEYS);
		logWrite(PREF_DIR_TABLE_SORT_KEYS, null);
		PREFS.remove(PREF_DIR_TABLE_COLUMN_ORDER);
		logWrite(PREF_DIR_TABLE_COLUMN_ORDER, null);
		PREFS.remove(PREF_DIR_TABLE_COLUMN_WIDTHS);
		logWrite(PREF_DIR_TABLE_COLUMN_WIDTHS, null);
		PREFS.remove(PREF_RAW_BROWSER_WIDTH);
		logWrite(PREF_RAW_BROWSER_WIDTH, null);
		PREFS.remove(PREF_RAW_BROWSER_HEIGHT);
		logWrite(PREF_RAW_BROWSER_HEIGHT, null);
		PREFS.remove(PREF_RAW_BROWSER_X);
		logWrite(PREF_RAW_BROWSER_X, null);
		PREFS.remove(PREF_RAW_BROWSER_Y);
		logWrite(PREF_RAW_BROWSER_Y, null);
		PREFS.remove(PREF_RAW_BROWSER_SPLIT_MAIN);
		logWrite(PREF_RAW_BROWSER_SPLIT_MAIN, null);
		PREFS.remove(PREF_RAW_BROWSER_SPLIT_SCANS);
		logWrite(PREF_RAW_BROWSER_SPLIT_SCANS, null);
		PREFS.remove(PREF_RAW_BROWSER_SPLIT_SPECTRUM);
		logWrite(PREF_RAW_BROWSER_SPLIT_SPECTRUM, null);
		PREFS.remove(PREF_RAW_BROWSER_SPLIT_IMS);
		logWrite(PREF_RAW_BROWSER_SPLIT_IMS, null);
		PREFS.remove(PREF_RAW_BROWSER_SPLIT_BOXPLOT);
		logWrite(PREF_RAW_BROWSER_SPLIT_BOXPLOT, null);
		PREFS.remove(PREF_RAW_FILE_BROWSER_WIDTH);
		logWrite(PREF_RAW_FILE_BROWSER_WIDTH, null);
		PREFS.remove(PREF_RAW_FILE_BROWSER_HEIGHT);
		logWrite(PREF_RAW_FILE_BROWSER_HEIGHT, null);
		PREFS.remove(PREF_RAW_FILE_BROWSER_X);
		logWrite(PREF_RAW_FILE_BROWSER_X, null);
		PREFS.remove(PREF_RAW_FILE_BROWSER_Y);
		logWrite(PREF_RAW_FILE_BROWSER_Y, null);
		PREFS.remove(PREF_RAW_FILE_BROWSER_SPLIT_MAIN);
		logWrite(PREF_RAW_FILE_BROWSER_SPLIT_MAIN, null);
		PREFS.remove(PREF_RAW_FILE_BROWSER_SPLIT_FILE);
		logWrite(PREF_RAW_FILE_BROWSER_SPLIT_FILE, null);
		PREFS.remove(PREF_CONVERSION_PANE_SPLIT);
		logWrite(PREF_CONVERSION_PANE_SPLIT, null);
		PREFS.remove(PREF_LOOK_AND_FEEL);
		logWrite(PREF_LOOK_AND_FEEL, null);
	}

	public static void resetWindowPreferences() {
		PREFS.remove(PREF_RAW_BROWSER_WIDTH);
		logWrite(PREF_RAW_BROWSER_WIDTH, null);
		PREFS.remove(PREF_RAW_BROWSER_HEIGHT);
		logWrite(PREF_RAW_BROWSER_HEIGHT, null);
		PREFS.remove(PREF_RAW_BROWSER_X);
		logWrite(PREF_RAW_BROWSER_X, null);
		PREFS.remove(PREF_RAW_BROWSER_Y);
		logWrite(PREF_RAW_BROWSER_Y, null);
		PREFS.remove(PREF_RAW_FILE_BROWSER_WIDTH);
		logWrite(PREF_RAW_FILE_BROWSER_WIDTH, null);
		PREFS.remove(PREF_RAW_FILE_BROWSER_HEIGHT);
		logWrite(PREF_RAW_FILE_BROWSER_HEIGHT, null);
		PREFS.remove(PREF_RAW_FILE_BROWSER_X);
		logWrite(PREF_RAW_FILE_BROWSER_X, null);
		PREFS.remove(PREF_RAW_FILE_BROWSER_Y);
		logWrite(PREF_RAW_FILE_BROWSER_Y, null);
	}

	public static void resetSplitPanePreferences() {
		PREFS.remove(PREF_RAW_BROWSER_SPLIT_MAIN);
		logWrite(PREF_RAW_BROWSER_SPLIT_MAIN, null);
		PREFS.remove(PREF_RAW_BROWSER_SPLIT_SCANS);
		logWrite(PREF_RAW_BROWSER_SPLIT_SCANS, null);
		PREFS.remove(PREF_RAW_BROWSER_SPLIT_SPECTRUM);
		logWrite(PREF_RAW_BROWSER_SPLIT_SPECTRUM, null);
		PREFS.remove(PREF_RAW_BROWSER_SPLIT_IMS);
		logWrite(PREF_RAW_BROWSER_SPLIT_IMS, null);
		PREFS.remove(PREF_RAW_BROWSER_SPLIT_BOXPLOT);
		logWrite(PREF_RAW_BROWSER_SPLIT_BOXPLOT, null);
		PREFS.remove(PREF_RAW_FILE_BROWSER_SPLIT_MAIN);
		logWrite(PREF_RAW_FILE_BROWSER_SPLIT_MAIN, null);
		PREFS.remove(PREF_RAW_FILE_BROWSER_SPLIT_FILE);
		logWrite(PREF_RAW_FILE_BROWSER_SPLIT_FILE, null);
		PREFS.remove(PREF_CONVERSION_PANE_SPLIT);
		logWrite(PREF_CONVERSION_PANE_SPLIT, null);
	}

	public static void resetTablePreferences() {
		PREFS.remove(PREF_DIR_TABLE_SORT_KEYS);
		logWrite(PREF_DIR_TABLE_SORT_KEYS, null);
		PREFS.remove(PREF_DIR_TABLE_COLUMN_ORDER);
		logWrite(PREF_DIR_TABLE_COLUMN_ORDER, null);
		PREFS.remove(PREF_DIR_TABLE_COLUMN_WIDTHS);
		logWrite(PREF_DIR_TABLE_COLUMN_WIDTHS, null);
	}

	private static void logRead(String key, Object value) {
		if (!verboseGuiLogging) return;
		Logger.logLine("GUIPreferences read: "+key+"="+String.valueOf(value));
	}

	private static void logWrite(String key, Object value) {
		if (!verboseGuiLogging) return;
		Logger.logLine("GUIPreferences write: "+key+"="+String.valueOf(value));
	}
}
