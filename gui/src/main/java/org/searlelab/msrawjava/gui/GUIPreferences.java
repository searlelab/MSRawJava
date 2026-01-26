package org.searlelab.msrawjava.gui;

import java.awt.Dimension;
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

	public static final int DEFAULT_RAW_BROWSER_WIDTH=1280;
	public static final int DEFAULT_RAW_BROWSER_HEIGHT=700;
	public static final double DEFAULT_RAW_BROWSER_MAIN_SPLIT=0.3;
	public static final double DEFAULT_RAW_BROWSER_SCANS_SPLIT=0.5;
	public static final double DEFAULT_RAW_BROWSER_SPECTRUM_SPLIT=0.8;
	public static final double DEFAULT_RAW_BROWSER_IMS_SPLIT=0.5/DEFAULT_RAW_BROWSER_SPECTRUM_SPLIT;
	public static final double DEFAULT_RAW_BROWSER_BOXPLOT_SPLIT=0.5;

	private static final String PREF_LAST_DIR="lastDir";
	private static final String PREF_DIR_SORT_KEYS="dirSummary.sortKeys";
	private static final String PREF_DIR_COLUMN_ORDER="dirSummary.columnOrder";
	private static final String PREF_DIR_COLUMN_WIDTHS="dirSummary.columnWidths";
	private static final String PREF_RAW_BROWSER_WIDTH="rawBrowser.width";
	private static final String PREF_RAW_BROWSER_HEIGHT="rawBrowser.height";
	private static final String PREF_RAW_BROWSER_SPLIT_MAIN="rawBrowser.split.main";
	private static final String PREF_RAW_BROWSER_SPLIT_SCANS="rawBrowser.split.scans";
	private static final String PREF_RAW_BROWSER_SPLIT_SPECTRUM="rawBrowser.split.spectrum";
	private static final String PREF_RAW_BROWSER_SPLIT_IMS="rawBrowser.split.ims";
	private static final String PREF_RAW_BROWSER_SPLIT_BOXPLOT="rawBrowser.split.boxplot";

	private GUIPreferences() {
	}

	public static Preferences getPreferences() {
		return PREFS;
	}

	public static String getLastDirectory() {
		return PREFS.get(PREF_LAST_DIR, null);
	}

	public static void rememberLastDirectory(File dir) {
		try {
			if (dir!=null&&dir.isDirectory()) {
				PREFS.put(PREF_LAST_DIR, dir.getAbsolutePath());
				Logger.logLine("RawFileBrowser remembered directory: "+dir.getAbsolutePath());
			}
		} catch (Exception ignore) {
		}
	}

	public static List<RowSorter.SortKey> getDirectorySummarySortKeys() {
		String raw=PREFS.get(PREF_DIR_SORT_KEYS, "");
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
			PREFS.remove(PREF_DIR_SORT_KEYS);
			return;
		}
		StringBuilder out=new StringBuilder();
		for (RowSorter.SortKey key : keys) {
			if (key==null) continue;
			if (out.length()>0) out.append(',');
			out.append(key.getColumn()).append(':').append(key.getSortOrder().name());
		}
		if (out.length()==0) {
			PREFS.remove(PREF_DIR_SORT_KEYS);
			return;
		}
		PREFS.put(PREF_DIR_SORT_KEYS, out.toString());
	}

	public static List<Integer> getDirectorySummaryColumnOrder() {
		String raw=PREFS.get(PREF_DIR_COLUMN_ORDER, "");
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
			PREFS.remove(PREF_DIR_COLUMN_ORDER);
			return;
		}
		StringBuilder out=new StringBuilder();
		for (Integer idx : order) {
			if (idx==null) continue;
			if (out.length()>0) out.append(',');
			out.append(idx.intValue());
		}
		if (out.length()==0) {
			PREFS.remove(PREF_DIR_COLUMN_ORDER);
			return;
		}
		PREFS.put(PREF_DIR_COLUMN_ORDER, out.toString());
	}

	public static Map<Integer, Integer> getDirectorySummaryColumnWidths() {
		String raw=PREFS.get(PREF_DIR_COLUMN_WIDTHS, "");
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
			PREFS.remove(PREF_DIR_COLUMN_WIDTHS);
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
			PREFS.remove(PREF_DIR_COLUMN_WIDTHS);
			return;
		}
		PREFS.put(PREF_DIR_COLUMN_WIDTHS, out.toString());
	}

	public static Dimension getRawBrowserWindowSize(int defaultWidth, int defaultHeight) {
		int width=PREFS.getInt(PREF_RAW_BROWSER_WIDTH, defaultWidth);
		int height=PREFS.getInt(PREF_RAW_BROWSER_HEIGHT, defaultHeight);
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
	}

	public static double getRawBrowserMainSplitRatio(double defaultValue) {
		return getSplitRatio(PREF_RAW_BROWSER_SPLIT_MAIN, defaultValue);
	}

	public static double getRawBrowserMainSplitRatio() {
		return getRawBrowserMainSplitRatio(DEFAULT_RAW_BROWSER_MAIN_SPLIT);
	}

	public static void setRawBrowserMainSplitRatio(double value) {
		setSplitRatio(PREF_RAW_BROWSER_SPLIT_MAIN, value);
	}

	public static double getRawBrowserScansSplitRatio(double defaultValue) {
		return getSplitRatio(PREF_RAW_BROWSER_SPLIT_SCANS, defaultValue);
	}

	public static double getRawBrowserScansSplitRatio() {
		return getRawBrowserScansSplitRatio(DEFAULT_RAW_BROWSER_SCANS_SPLIT);
	}

	public static void setRawBrowserScansSplitRatio(double value) {
		setSplitRatio(PREF_RAW_BROWSER_SPLIT_SCANS, value);
	}

	public static double getRawBrowserSpectrumSplitRatio(double defaultValue) {
		return getSplitRatio(PREF_RAW_BROWSER_SPLIT_SPECTRUM, defaultValue);
	}

	public static double getRawBrowserSpectrumSplitRatio() {
		return getRawBrowserSpectrumSplitRatio(DEFAULT_RAW_BROWSER_SPECTRUM_SPLIT);
	}

	public static void setRawBrowserSpectrumSplitRatio(double value) {
		setSplitRatio(PREF_RAW_BROWSER_SPLIT_SPECTRUM, value);
	}

	public static double getRawBrowserImsSplitRatio(double defaultValue) {
		return getSplitRatio(PREF_RAW_BROWSER_SPLIT_IMS, defaultValue);
	}

	public static double getRawBrowserImsSplitRatio() {
		return getRawBrowserImsSplitRatio(DEFAULT_RAW_BROWSER_IMS_SPLIT);
	}

	public static void setRawBrowserImsSplitRatio(double value) {
		setSplitRatio(PREF_RAW_BROWSER_SPLIT_IMS, value);
	}

	public static double getRawBrowserBoxplotSplitRatio(double defaultValue) {
		return getSplitRatio(PREF_RAW_BROWSER_SPLIT_BOXPLOT, defaultValue);
	}

	public static double getRawBrowserBoxplotSplitRatio() {
		return getRawBrowserBoxplotSplitRatio(DEFAULT_RAW_BROWSER_BOXPLOT_SPLIT);
	}

	public static void setRawBrowserBoxplotSplitRatio(double value) {
		setSplitRatio(PREF_RAW_BROWSER_SPLIT_BOXPLOT, value);
	}

	private static double getSplitRatio(String key, double defaultValue) {
		double value=PREFS.getDouble(key, defaultValue);
		if (value<=0.0||value>=1.0) return defaultValue;
		return value;
	}

	private static void setSplitRatio(String key, double value) {
		if (value<=0.0||value>=1.0) return;
		PREFS.putDouble(key, value);
	}

	public static void resetAll() {
		PREFS.remove(PREF_LAST_DIR);
		PREFS.remove(PREF_DIR_SORT_KEYS);
		PREFS.remove(PREF_DIR_COLUMN_ORDER);
		PREFS.remove(PREF_DIR_COLUMN_WIDTHS);
		PREFS.remove(PREF_RAW_BROWSER_WIDTH);
		PREFS.remove(PREF_RAW_BROWSER_HEIGHT);
		PREFS.remove(PREF_RAW_BROWSER_SPLIT_MAIN);
		PREFS.remove(PREF_RAW_BROWSER_SPLIT_SCANS);
		PREFS.remove(PREF_RAW_BROWSER_SPLIT_SPECTRUM);
		PREFS.remove(PREF_RAW_BROWSER_SPLIT_IMS);
		PREFS.remove(PREF_RAW_BROWSER_SPLIT_BOXPLOT);
	}
}
