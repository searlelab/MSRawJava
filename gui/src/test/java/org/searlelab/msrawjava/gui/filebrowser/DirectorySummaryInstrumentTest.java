package org.searlelab.msrawjava.gui.filebrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.table.TableRowSorter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.gui.GUIPreferences;
import org.searlelab.msrawjava.io.VendorFile;
import org.searlelab.msrawjava.io.VendorFiles;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.mzml.InstrumentId;

class DirectorySummaryInstrumentTest {
	@TempDir
	Path directory;

	@Test
	void sortsDistinctNamesAndToleratesMissingMetadata() {
		assertNull(InstrumentNames.clean(" instrument model "));
		assertNull(InstrumentNames.clean("  "));
		assertNull(InstrumentNames.clean(null));
		assertEquals("timsTOF Pro", InstrumentNames.clean(" timsTOF Pro "));
		assertEquals("Astral; timsTOF Pro", InstrumentNames.fromConfigurations(List.of(instrument("timsTOF Pro"), instrument("Astral"),
				instrument("Astral"), instrument("instrument model"), instrument(null))));
		assertNull(InstrumentNames.fromConfigurations(List.of()));
	}

	@Test
	void diaMetadataUsesInstrumentNameThenModelAndHandlesMissingValues() {
		assertEquals("timsTOF Pro", InstrumentNames.fromMetadata(Map.of("meta.InstrumentName", " timsTOF Pro ", "instrument.model", "other")));
		assertEquals("Astral", InstrumentNames.fromMetadata(Map.of("instrument.model", " Astral ")));
		assertEquals("Astral", InstrumentNames.fromMetadata(Map.of("meta.InstrumentName", " ", "instrument.model", "Astral")));
		assertNull(InstrumentNames.fromMetadata(Map.of()));
	}

	@Test
	void diaInstrumentLoadsThroughPreviewAndMetricsCache() throws Exception {
		for (String key : List.of("meta.InstrumentName", "instrument.model")) {
			Path path=directory.resolve(key+".DIA");
			EncyclopeDIAFile writer=new EncyclopeDIAFile();
			try {
				writer.openFile();
				writer.addMetadata(key, "  Test instrument  ");
				writer.saveAsFile(path.toFile());
			} finally {
				writer.close();
			}
			DirectorySummaryRow row=DirectorySummaryRow.fromDia(path);
			DirectorySummarySlowBitsController[] controller=new DirectorySummarySlowBitsController[1];
			SwingUtilities.invokeAndWait(() -> {
				DirectorySummaryModel model=new DirectorySummaryModel();
				model.addRows(List.of(row));
				JTable table=new JTable(model);
				controller[0]=new DirectorySummarySlowBitsController(table, new JScrollPane(table), model, () -> {});
			});
			try {
				java.lang.reflect.Method compute=DirectorySummarySlowBitsController.class.getDeclaredMethod("computeSlowBits", DirectorySummaryRow.class);
				compute.setAccessible(true);
				compute.invoke(controller[0], row);
				assertEquals("Test instrument", row.instrument);
				assertEquals(0f, row.totalTIC);
				assertTrue(row.isSlowBitsReady());
				DirectorySummaryRow cached=DirectorySummaryRow.fromDia(path);
				controller[0].initializeSlowBitsProgress(List.of(cached));
				assertEquals("Test instrument", cached.instrument);
				assertTrue(cached.isSlowBitsReady());
			} finally {
				controller[0].stop();
				SwingUtilities.invokeAndWait(() -> {});
			}
		}
	}

	@Test
	void upgradesOldOrderWithoutOverridingUserPlacement() {
		assertEquals(List.of("file", "vendor", "instrument", "size"), DirectorySummaryPanel.withInstrumentColumn(List.of("file", "vendor", "size")));
		assertEquals(List.of("instrument", "file", "vendor"), DirectorySummaryPanel.withInstrumentColumn(List.of("instrument", "file", "vendor")));
		assertTrue(DirectorySummaryPanel.withInstrumentColumn(List.of()).isEmpty());
		assertEquals(DirectorySummaryColumn.DATE_MODIFIED, DirectorySummaryColumn.legacyByIndex(3));
	}

	@Test
	void instrumentIsSortableTextAndSurvivesMetricsCacheRoundTrip() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			DirectorySummaryModel model=new DirectorySummaryModel();
			DirectorySummaryRow first=new DirectorySummaryRow(directory.resolve("a.raw"), VendorFile.THERMO, 0, null);
			first.instrument="timsTOF Pro";
			DirectorySummaryRow second=new DirectorySummaryRow(directory.resolve("b.raw"), VendorFile.THERMO, 0, null);
			second.instrument="Astral";
			DirectorySummaryRow copy=new DirectorySummaryRow(directory.resolve("copy.raw"), VendorFile.THERMO, 0, null);
			copy.applyMetrics(first.toMetrics());
			assertEquals(first.instrument, copy.instrument);
			model.addRows(List.of(first, second));
			TableRowSorter<DirectorySummaryModel> sorter=new TableRowSorter<>(model);
			sorter.setSortKeys(List.of(new RowSorter.SortKey(DirectorySummaryColumn.INSTRUMENT.modelIndex(), SortOrder.ASCENDING)));
			assertEquals(1, sorter.convertRowIndexToModel(0));
			assertEquals("Astral", model.getValueAt(1, DirectorySummaryColumn.INSTRUMENT.modelIndex()));
		});
	}

	@Test
	void revealRemovesOnlyFiltersThatHideRequestedDataset() throws Exception {
		Path mzml=Files.createFile(directory.resolve("test.mzML"));
		VendorFiles files=new VendorFiles();
		files.addMzml(mzml);
		Preferences preferences=GUIPreferences.getPreferences();
		java.util.Map<String, String> saved=new java.util.HashMap<>();
		for (String name : List.of("sortKeys", "columnOrder", "columnWidths", "vendorFilter")) {
			String key="rawFileBrowser.table."+name;
			saved.put(key, preferences.get(key, null));
		}
		DirectorySummaryPanel[] tested=new DirectorySummaryPanel[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				try {
					DirectorySummaryPanel panel=new DirectorySummaryPanel(files);
					tested[0]=panel;
					panel.removeNotify(); // Stop metadata workers before any queued dispatch can run.
					Field search=DirectorySummaryPanel.class.getDeclaredField("searchField");
					search.setAccessible(true);
					JTextField text=(JTextField)search.get(panel);
					Field vendor=DirectorySummaryPanel.class.getDeclaredField("vendorFilter");
					vendor.setAccessible(true);
					JComboBox<?> filter=(JComboBox<?>)vendor.get(panel);
					filter.setSelectedItem(VendorFile.THERMO);
					text.setText("test");
					assertTrue(panel.revealPath(directory.resolve(".").resolve("test.mzML")));
					assertEquals("test", text.getText());
					assertEquals(mzml, panel.getFirstSelectedPath());
					text.setText("hidden");
					assertTrue(panel.revealPath(mzml));
					assertEquals("", text.getText());
					assertEquals("All", filter.getSelectedItem());
					assertFalse(panel.revealPath(directory.resolve("missing.raw")));
				} catch (ReflectiveOperationException e) {
					throw new AssertionError(e);
				}
			});
			SwingUtilities.invokeAndWait(() -> {}); // Drain model and preference events before checking selection and restoring preferences.
			SwingUtilities.invokeAndWait(() -> assertEquals(mzml, tested[0].getFirstSelectedPath()));
		} finally {
			for (java.util.Map.Entry<String, String> entry : saved.entrySet()) {
				if (entry.getValue()==null) preferences.remove(entry.getKey());
				else preferences.put(entry.getKey(), entry.getValue());
			}
		}
	}

	private static InstrumentId instrument(String name) {
		return InstrumentId.builder().setName(name).build();
	}
}
