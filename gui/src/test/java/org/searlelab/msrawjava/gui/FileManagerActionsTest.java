package org.searlelab.msrawjava.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileManagerActionsTest {
	@TempDir
	Path directory;

	@Test
	void menuLabelsMatchTheThreeDesktopsAndKeepTheClickedTarget() throws Exception {
		Path clicked=directory.resolve("clicked.raw");
		SwingUtilities.invokeAndWait(() -> {
			List<Path> requests=new ArrayList<>();
			for (String[] platform : new String[][] {{"Mac OS X", "Show in Finder"}, {"Darwin", "Show in Finder"},
					{"Windows 11", "Show in File Explorer"}, {"Linux", "Show in Files"}}) {
				JMenuItem item=FileManagerActions.menuItem(platform[0], clicked, requests::add);
				assertEquals(platform[1], item.getText());
				item.doClick(0);
			}
			assertEquals(List.of(clicked, clicked, clicked, clicked), requests);
		});
	}

	@Test
	void nativeRevealReceivesTheFileOrDirectoryRatherThanOpeningTheDataset() throws Exception {
		Path raw=Files.createFile(directory.resolve("run with spaces 'quoted' α.raw"));
		Path bundle=Files.createDirectory(directory.resolve("run.d"));
		Path folder=Files.createDirectory(directory.resolve("folder"));
		FakeDesktop desktop=new FakeDesktop(Desktop.Action.BROWSE_FILE_DIR, Desktop.Action.OPEN);
		for (Path path : List.of(raw, bundle, folder)) {
			FileManagerActions.reveal(path, desktop, command -> fail("Native reveal is supported"), "Mac OS X");
		}
		assertEquals(List.of(raw.toFile(), bundle.toFile(), folder.toFile()), desktop.revealed);
		assertTrue(desktop.opened.isEmpty());
	}

	@Test
	void desktopOpenFallbackUsesTheParentForBothFilesAndBundles() throws Exception {
		Path raw=Files.createFile(directory.resolve("run.raw"));
		Path bundle=Files.createDirectory(directory.resolve("run.d"));
		FakeDesktop desktop=new FakeDesktop(Desktop.Action.OPEN);
		for (Path path : List.of(raw, bundle)) {
			FileManagerActions.reveal(path, desktop, command -> fail("Desktop folder opening is supported"), "Linux");
		}
		assertEquals(List.of(directory.toFile(), directory.toFile()), desktop.opened);
		assertTrue(desktop.revealed.isEmpty());
	}

	@Test
	void platformFallbackCommandsKeepPathsInOneArgumentWithoutAShell() throws Exception {
		Path folder=Files.createDirectory(directory.resolve("data folder $literal α"));
		Path file=Files.createFile(folder.resolve("run.raw"));
		for (String[] platform : new String[][] {{"Mac OS X", "open"}, {"Windows 11", "explorer.exe"}, {"Linux", "xdg-open"}}) {
			List<List<String>> commands=new ArrayList<>();
			FileManagerActions.reveal(file, new FakeDesktop(), commands::add, platform[0]);
			assertEquals(List.of(List.of(platform[1], folder.toString())), commands);
		}
	}

	@Test
	void filesystemRootsOpenDirectly() throws Exception {
		Path root=directory.getRoot();
		FakeDesktop desktop=new FakeDesktop(Desktop.Action.BROWSE_FILE_DIR, Desktop.Action.OPEN);
		FileManagerActions.reveal(root, desktop, command -> fail("Desktop folder opening is supported"), "Mac OS X");
		assertEquals(List.of(root.toFile()), desktop.opened);
		assertTrue(desktop.revealed.isEmpty());
	}

	@Test
	void unavailableNativeRevealFallsBackToOpeningTheParent() throws Exception {
		Path file=Files.createFile(directory.resolve("run.raw"));
		FakeDesktop desktop=new FakeDesktop(Desktop.Action.BROWSE_FILE_DIR, Desktop.Action.OPEN);
		desktop.revealUnavailable=true;
		FileManagerActions.reveal(file, desktop, command -> fail("Desktop folder opening is supported"), "Windows 11");
		assertEquals(List.of(directory.toFile()), desktop.opened);
	}

	@Test
	void missingPathsAndLaunchFailuresAreReportedWithoutOpeningADataset() throws Exception {
		FakeDesktop desktop=new FakeDesktop();
		assertThrows(IOException.class, () -> FileManagerActions.reveal(directory.resolve("missing.raw"), desktop,
				command -> fail("Missing paths must not launch anything"), "Linux"));
		Path file=Files.createFile(directory.resolve("run.raw"));
		IOException failure=assertThrows(IOException.class, () -> FileManagerActions.reveal(file, desktop, command -> {
			throw new IOException("File manager is unavailable");
		}, "Linux"));
		assertEquals("File manager is unavailable", failure.getMessage());
		assertTrue(desktop.revealed.isEmpty());
		assertTrue(desktop.opened.isEmpty());
	}

	private static final class FakeDesktop implements FileManagerActions.DesktopAccess {
		private final EnumSet<Desktop.Action> supported=EnumSet.noneOf(Desktop.Action.class);
		private final List<File> revealed=new ArrayList<>();
		private final List<File> opened=new ArrayList<>();
		private boolean revealUnavailable;

		FakeDesktop(Desktop.Action... actions) {
			java.util.Collections.addAll(supported, actions);
		}

		@Override
		public boolean supports(Desktop.Action action) {
			return supported.contains(action);
		}

		@Override
		public void reveal(File file) {
			if (revealUnavailable) throw new UnsupportedOperationException();
			revealed.add(file);
		}

		@Override
		public void open(File folder) {
			opened.add(folder);
		}
	}
}
