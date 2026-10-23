package org.searlelab.msrawjava.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class OpenFileDispatcherTest {
	@Test
	void allAssociatedExtensionsWorkThroughStartupAndLaterDesktopNotifications() throws Exception {
		for (boolean mac : List.of(true, false)) {
			for (String extension : List.of("raw", "DIA", "mzML", "TDF")) {
				String path="/Dataset folder/α run."+extension;
				OpenFileDispatcher dispatcher=new OpenFileDispatcher();
				dispatcher.submitArguments(new String[] {path});
				List<List<File>> requests=new ArrayList<>();
				dispatcher.attach(requests::add);
				SwingUtilities.invokeAndWait(() -> {});
				dispatcher.submitNotification(mac?path:"\""+path+"\"", mac);
				SwingUtilities.invokeAndWait(() -> {});
				assertEquals(List.of(List.of(new File(path)), List.of(new File(path))), requests);
			}
		}
	}

	@Test
	void buffersLatestStartupPayloadAndDispatchesOnEdt() throws Exception {
		OpenFileDispatcher dispatcher=new OpenFileDispatcher();
		dispatcher.submitArguments(new String[] {"old.raw"});
		dispatcher.submitArguments(new String[] {"new.raw", "another.raw"});
		List<List<File>> requests=new ArrayList<>();
		dispatcher.attach(files -> {
			assertTrue(SwingUtilities.isEventDispatchThread());
			requests.add(files);
		});
		SwingUtilities.invokeAndWait(() -> {});
		assertEquals(List.of(List.of(new File("new.raw"), new File("another.raw"))), requests);
		dispatcher.submitArguments(new String[] {"later.raw"});
		SwingUtilities.invokeAndWait(() -> {});
		assertEquals(List.of(new File("later.raw")), requests.get(1));
	}

	@Test
	void parsesQuotedWindowsPathsAndPreservesBackslashes() {
		assertEquals(List.of("C:\\Data folder\\α.raw", "D:\\other.raw"),
				OpenFileDispatcher.parseParameters("\"C:\\Data folder\\α.raw\" \"D:\\other.raw\""));
		assertEquals(List.of("/data/a \"quoted\".raw"), OpenFileDispatcher.parseParameters("\"/data/a \\\"quoted\\\".raw\""));
		assertTrue(OpenFileDispatcher.parseParameters("  ").isEmpty());
	}

	@Test
	void macSingleUnquotedFilenameKeepsSpaces() throws Exception {
		OpenFileDispatcher dispatcher=new OpenFileDispatcher();
		dispatcher.submitNotification("/Data folder/α.raw", true);
		List<List<File>> requests=new ArrayList<>();
		dispatcher.attach(requests::add);
		SwingUtilities.invokeAndWait(() -> {});
		assertEquals(List.of(new File("/Data folder/α.raw")), requests.get(0));
	}

	@Test
	void reflectiveAdapterBuffersImmediateCallbackAndForwardsLaterEvents() throws Exception {
		OpenFileDispatcher dispatcher=new OpenFileDispatcher();
		assertTrue(dispatcher.registerInstall4j(getClass().getClassLoader(), false));
		List<List<File>> requests=new ArrayList<>();
		dispatcher.attach(requests::add);
		SwingUtilities.invokeAndWait(() -> {});
		assertEquals(List.of(new File("/startup folder/first.raw")), requests.get(0));
		com.install4j.api.launcher.StartupNotification.listener.startupPerformed("\"/later folder/second.raw\" \"/third.raw\"");
		SwingUtilities.invokeAndWait(() -> {});
		assertEquals(List.of(new File("/later folder/second.raw"), new File("/third.raw")), requests.get(1));
	}

	@Test
	void ordinaryJarLaunchWorksWithoutInstall4jClasses() {
		assertFalse(new OpenFileDispatcher().registerInstall4j(new ClassLoader(null) {}, false));
	}
}
