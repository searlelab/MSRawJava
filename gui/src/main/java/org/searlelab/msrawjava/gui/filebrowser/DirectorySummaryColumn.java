package org.searlelab.msrawjava.gui.filebrowser;

import java.util.Date;

public enum DirectorySummaryColumn {
	ROW_NUMBER("row_number", "#", String.class, 50, "The table row number for this file.", false),
	FILE("file", "File", String.class, 320, "The raw file or directory name.", true),
	VENDOR("vendor", "Vendor", String.class, 80, "The detected vendor or file format.", true),
	DATE_MODIFIED("date_modified", "Date Modified", Date.class, 110, "The last modified date reported by the file system.", true),
	DATE_ACQUIRED("date_acquired", "Date Acquired", Date.class, 110, "The acquisition date reported by the raw file metadata.", true),
	SIZE("size", "Size", Long.class, 100, "The total file size on disk.", true),
	GRADIENT_MIN("gradient_min", "Gradient (min)", Float.class, 110, "The gradient length in minutes.", true),
	TOTAL_TIC("total_tic", "Total TIC", Float.class, 110, "The sum of MS1 TIC values across the entire raw file.", true),
	TIC_SPARK("tic_spark", "TIC", SparkData.class, 220, "A compact trace of total ion current across retention time.", false);

	private static final DirectorySummaryColumn[] LEGACY_INDEX_ORDER= {ROW_NUMBER, FILE, VENDOR, DATE_MODIFIED, SIZE, GRADIENT_MIN, TOTAL_TIC, TIC_SPARK};

	public final String key;
	final String label;
	final Class<?> valueClass;
	final int defaultWidth;
	final String tooltip;
	final boolean sortable;

	DirectorySummaryColumn(String key, String label, Class<?> valueClass, int defaultWidth, String tooltip, boolean sortable) {
		this.key=key;
		this.label=label;
		this.valueClass=valueClass;
		this.defaultWidth=defaultWidth;
		this.tooltip=tooltip;
		this.sortable=sortable;
	}

	static DirectorySummaryColumn byModelIndex(int modelIndex) {
		DirectorySummaryColumn[] values=values();
		if (modelIndex<0||modelIndex>=values.length) return null;
		return values[modelIndex];
	}

	public static DirectorySummaryColumn byKey(String key) {
		if (key==null) return null;
		for (DirectorySummaryColumn column : values()) {
			if (column.key.equals(key)) return column;
		}
		return null;
	}

	public static DirectorySummaryColumn legacyByIndex(int index) {
		if (index<0||index>=LEGACY_INDEX_ORDER.length) return null;
		return LEGACY_INDEX_ORDER[index];
	}

	int modelIndex() {
		return ordinal();
	}
}
