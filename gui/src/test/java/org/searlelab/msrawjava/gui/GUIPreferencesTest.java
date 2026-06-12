package org.searlelab.msrawjava.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.SortOrder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.gui.GUIPreferences.DirectorySummarySortKeySpec;

class GUIPreferencesTest {
	private static final String SORT_KEYS="rawFileBrowser.table.sortKeys";
	private static final String COLUMN_ORDER="rawFileBrowser.table.columnOrder";
	private static final String COLUMN_WIDTHS="rawFileBrowser.table.columnWidths";

	private Preferences prefs;
	private String savedSortKeys;
	private String savedColumnOrder;
	private String savedColumnWidths;

	@BeforeEach
	void setUp() {
		prefs=GUIPreferences.getPreferences();
		savedSortKeys=prefs.get(SORT_KEYS, null);
		savedColumnOrder=prefs.get(COLUMN_ORDER, null);
		savedColumnWidths=prefs.get(COLUMN_WIDTHS, null);
		prefs.remove(SORT_KEYS);
		prefs.remove(COLUMN_ORDER);
		prefs.remove(COLUMN_WIDTHS);
	}

	@AfterEach
	void tearDown() {
		restore(SORT_KEYS, savedSortKeys);
		restore(COLUMN_ORDER, savedColumnOrder);
		restore(COLUMN_WIDTHS, savedColumnWidths);
	}

	@Test
	void directorySummaryKeyBasedPreferencesRoundTrip() {
		GUIPreferences.setDirectorySummarySortKeySpecs(
				List.of(new DirectorySummarySortKeySpec("date_acquired", SortOrder.DESCENDING), new DirectorySummarySortKeySpec("file", SortOrder.ASCENDING)));
		GUIPreferences.setDirectorySummaryColumnOrderKeys(List.of("file", "date_acquired", "date_modified"));
		GUIPreferences.setDirectorySummaryColumnWidthKeys(Map.of("file", 300, "date_acquired", 120));

		List<DirectorySummarySortKeySpec> sortKeys=GUIPreferences.getDirectorySummarySortKeySpecs();
		assertEquals(2, sortKeys.size());
		assertEquals("date_acquired", sortKeys.get(0).getColumnKey());
		assertEquals(SortOrder.DESCENDING, sortKeys.get(0).getSortOrder());
		assertEquals(List.of("file", "date_acquired", "date_modified"), GUIPreferences.getDirectorySummaryColumnOrderKeys());
		assertEquals(300, GUIPreferences.getDirectorySummaryColumnWidthKeys().get("file"));
		assertEquals("date_acquired:DESCENDING,file:ASCENDING", prefs.get(SORT_KEYS, ""));
		assertEquals("file,date_acquired,date_modified", prefs.get(COLUMN_ORDER, ""));
		assertTrue(prefs.get(COLUMN_WIDTHS, "").contains("date_acquired=120"));
	}

	@Test
	void directorySummaryLegacyIndexPreferencesMapToStableKeys() {
		prefs.put(SORT_KEYS, "3:DESCENDING,5:ASCENDING,99:DESCENDING,bad:ASCENDING");
		prefs.put(COLUMN_ORDER, "1,3,5,99,bad");
		prefs.put(COLUMN_WIDTHS, "1=320,3=110,5=100,99=10,bad=20");

		List<DirectorySummarySortKeySpec> sortKeys=GUIPreferences.getDirectorySummarySortKeySpecs();
		assertEquals(2, sortKeys.size());
		assertEquals("date_modified", sortKeys.get(0).getColumnKey());
		assertEquals("gradient_min", sortKeys.get(1).getColumnKey());
		assertEquals(List.of("file", "date_modified", "gradient_min"), GUIPreferences.getDirectorySummaryColumnOrderKeys());
		assertEquals(320, GUIPreferences.getDirectorySummaryColumnWidthKeys().get("file"));
		assertEquals(110, GUIPreferences.getDirectorySummaryColumnWidthKeys().get("date_modified"));
	}

	@Test
	void directorySummaryPreferenceWritersSaveKeyFormat() {
		prefs.put(SORT_KEYS, "3:DESCENDING");
		prefs.put(COLUMN_ORDER, "1,3");
		prefs.put(COLUMN_WIDTHS, "1=320,3=110");

		GUIPreferences.setDirectorySummarySortKeySpecs(GUIPreferences.getDirectorySummarySortKeySpecs());
		GUIPreferences.setDirectorySummaryColumnOrderKeys(GUIPreferences.getDirectorySummaryColumnOrderKeys());
		GUIPreferences.setDirectorySummaryColumnWidthKeys(GUIPreferences.getDirectorySummaryColumnWidthKeys());

		assertEquals("date_modified:DESCENDING", prefs.get(SORT_KEYS, ""));
		assertEquals("file,date_modified", prefs.get(COLUMN_ORDER, ""));
		assertTrue(prefs.get(COLUMN_WIDTHS, "").contains("file=320"));
		assertTrue(prefs.get(COLUMN_WIDTHS, "").contains("date_modified=110"));
	}

	private void restore(String key, String value) {
		if (value==null) {
			prefs.remove(key);
		} else {
			prefs.put(key, value);
		}
	}
}
