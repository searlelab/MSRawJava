package org.searlelab.msrawjava.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

class RawFileBrowserRootTest {
	@Test
	void startupAndDropsShowTheHintWithoutScanningOrRememberingRoots(@TempDir Path directory) throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Browser windows require desktop access");
		Preferences prefs=GUIPreferences.getPreferences();
		assertTrue(prefs instanceof InMemoryPreferencesFactory.MemoryPreferences, "This test requires isolated preferences");
		Map<String, String> saved=new HashMap<>();
		for (String key : prefs.keys()) saved.put(key, prefs.get(key, null));
		File root=directory.toAbsolutePath().getRoot().toFile();
		prefs.put("lastDir", root.getAbsolutePath());
		try {
			SwingUtilities.invokeAndWait(() -> {
				try (ProcessingThreadPool pool=new ProcessingThreadPool(1, 1)) {
					RecordingBrowser browser=new RecordingBrowser(pool);
					try {
						assertHintWithoutLoad(browser);
						prefs.put("lastDir", directory.toString());
						field("navigation").set(browser, new FileNavigation(Runnable::run, Runnable::run));
						Method navigate=RawFileBrowser.class.getDeclaredMethod("navigateFiles", List.class, boolean.class, boolean.class);
						navigate.setAccessible(true);
						navigate.invoke(browser, List.of(root), false, false);
						assertHintWithoutLoad(browser);
						assertEquals(directory.toString(), GUIPreferences.getLastDirectory());
						assertTrue(browser.messages.isEmpty(), "Startup and ordinary root drops need no error dialog");

						// A resolved root-level dataset needs feedback even though its directory is not scanned.
						Method apply=RawFileBrowser.class.getDeclaredMethod("applyNavigationTarget", FileNavigation.Target.class, boolean.class);
						apply.setAccessible(true);
						apply.invoke(browser, new FileNavigation.Target(root.toPath(), root.toPath().resolve("run.raw")), false);
						assertEquals(1, browser.messages.size());
						assertTrue(browser.messages.get(0).contains("root"));
						assertTrue(browser.messages.get(0).contains("subfolder"));
						assertHintWithoutLoad(browser);
						assertEquals(directory.toString(), GUIPreferences.getLastDirectory());

						Path tsf=Files.createDirectory(directory.resolve("pasef-off.d"));
						Files.createFile(tsf.resolve("analysis.tsf"));
						for (Path unsupported : List.of(directory, tsf)) {
							for (boolean visualize : List.of(false, true)) {
								browser.messages.clear();
								browser.openAndSelectFile(unsupported.toFile(), visualize);
								assertEquals(1, browser.messages.size());
								assertTrue(browser.messages.get(0).contains("supported dataset"));
								assertHintWithoutLoad(browser);
								assertEquals(directory.toString(), GUIPreferences.getLastDirectory());
							}
						}
					} finally {
						((ConversionPane)field("conversionPane").get(browser)).shutdown();
						stopStatusTimers(browser);
						browser.dispose();
					}
				} catch (Exception e) {
					throw new AssertionError(e);
				}
			});
		} finally {
			// Drain deferred component events before restoring the in-memory test preferences.
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {});
			prefs.clear();
			for (Map.Entry<String, String> entry : saved.entrySet()) prefs.put(entry.getKey(), entry.getValue());
		}
	}

	private static final class RecordingBrowser extends RawFileBrowser {
		private static final long serialVersionUID=1L;
		final List<String> messages=new ArrayList<>();

		RecordingBrowser(ProcessingThreadPool pool) {
			super(pool);
		}

		@Override
		void showOpenRequestError(String message) {
			messages.add(message);
		}
	}

	private static void assertHintWithoutLoad(RawFileBrowser browser) throws Exception {
		assertNull(field("currentLoad").get(browser), "Filesystem roots must never start a recursive loader");
		assertNull(field("currentDirectory").get(browser));
		assertNull(field("pendingSelectionPath").get(browser));
		JSplitPane split=(JSplitPane)field("fileSplit").get(browser);
		assertSame(field("directorySelectionHintPanel").get(browser), split.getTopComponent());
	}

	private static void stopStatusTimers(Container container) throws Exception {
		for (Component child : container.getComponents()) {
			if (child instanceof ReaderStatusPanel) {
				Field timer=ReaderStatusPanel.class.getDeclaredField("blinkTimer");
				timer.setAccessible(true);
				((Timer)timer.get(child)).stop();
			} else if (child instanceof Container) {
				stopStatusTimers((Container)child);
			}
		}
	}

	private static Field field(String name) throws Exception {
		Field field=RawFileBrowser.class.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}
}
