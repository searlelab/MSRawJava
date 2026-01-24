package org.searlelab.msrawjava.gui;

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

	private static final String PREF_LAST_DIR="lastDir";
	private static final String PREF_DIR_SORT_KEYS="dirSummary.sortKeys";
	private static final String PREF_DIR_COLUMN_ORDER="dirSummary.columnOrder";
	private static final String PREF_DIR_COLUMN_WIDTHS="dirSummary.columnWidths";

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
}
