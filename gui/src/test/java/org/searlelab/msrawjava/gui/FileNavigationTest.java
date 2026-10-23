package org.searlelab.msrawjava.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.TransferHandler.TransferSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileNavigationTest {
	@TempDir
	Path directory;

	@Test
	void resolvesFirstValidDatasetAndTreatsBundlesAsFiles() throws Exception {
		Path raw=Files.createFile(directory.resolve("run.RAW"));
		Path bundle=Files.createDirectory(directory.resolve("run.D"));
		Files.createFile(bundle.resolve("analysis.tdf"));
		FileNavigation.Target target=FileNavigation.resolve(List.of(directory.resolve("missing.raw").toFile(), raw.toFile(), bundle.toFile()), true);
		assertEquals(raw, target.selection);
		assertEquals(directory, target.directory);
		assertEquals(bundle, FileNavigation.resolve(List.of(bundle.toFile()), true).selection);
		assertEquals(directory, FileNavigation.resolve(List.of(bundle.toFile()), true).directory);
	}

	@Test
	void associatedFormatsSelectTheirDatasetAndTdfSelectsTheContainingBundle() throws Exception {
		for (String name : List.of("run.raw", "run.DIA", "run.mzML")) {
			Path file=Files.createFile(directory.resolve(name));
			FileNavigation.Target target=FileNavigation.resolve(List.of(file.toFile()), true);
			assertEquals(directory, target.directory);
			assertEquals(file, target.selection);
		}
		Path bundle=Files.createDirectory(directory.resolve("Bruker α run.D"));
		Path tdf=Files.createFile(bundle.resolve("analysis.tdf"));
		// Association matching is case insensitive, while the reader's analysis.tdf stays unchanged.
		Path upper=Files.createFile(bundle.resolve("other.TDF"));
		for (Path file : List.of(tdf, upper)) {
			for (boolean datasetsOnly : List.of(true, false)) {
				FileNavigation.Target target=FileNavigation.resolve(List.of(file.toFile()), datasetsOnly);
				assertEquals(directory, target.directory);
				assertEquals(bundle, target.selection);
			}
		}
		assertNull(FileNavigation.resolve(List.of(Files.createFile(directory.resolve("unrelated.tdf")).toFile()), true));
	}

	@Test
	void ordinaryDropsNavigateWithoutSelectingAndNormalizePaths() throws Exception {
		Path text=Files.createFile(directory.resolve("notes.txt"));
		assertEquals(directory, FileNavigation.resolve(List.of(text.toFile()), false).directory);
		assertNull(FileNavigation.resolve(List.of(text.toFile()), false).selection);
		assertNull(FileNavigation.resolve(List.of(text.toFile()), true));
		assertEquals(directory, FileNavigation.resolve(List.of(directory.resolve(".").toFile()), false).directory);
		assertNull(FileNavigation.resolve(List.of(directory.toFile()), false).selection);
	}

	@Test
	void dotDirectoriesWithoutTdfNavigateIntoTheDirectory() throws Exception {
		Path ordinary=Files.createDirectory(directory.resolve("ssh_config.d"));
		Path tsfOnly=Files.createDirectory(directory.resolve("pasef-off.d"));
		Files.createFile(tsfOnly.resolve("analysis.tsf"));
		for (Path folder : List.of(ordinary, tsfOnly)) {
			FileNavigation.Target target=FileNavigation.resolve(List.of(folder.toFile()), false);
			assertEquals(folder, target.directory);
			assertNull(target.selection);
			assertNull(FileNavigation.resolve(List.of(folder.toFile()), true));
		}
	}

	@Test
	void delayedSupersededAndCancelledRequestsCannotNavigate() throws Exception {
		Path first=Files.createFile(directory.resolve("first.raw"));
		Path second=Files.createFile(directory.resolve("second.raw"));
		ArrayDeque<Runnable> background=new ArrayDeque<>();
		ArrayDeque<Runnable> ui=new ArrayDeque<>();
		List<Path> selected=new ArrayList<>();
		FileNavigation navigation=new FileNavigation(background::add, ui::add);
		navigation.open(List.of(first.toFile()), true, target -> selected.add(target.selection), () -> fail("valid input"));
		background.remove().run();
		navigation.open(List.of(second.toFile()), true, target -> selected.add(target.selection), () -> fail("valid input"));
		ui.remove().run();
		assertTrue(selected.isEmpty());
		background.remove().run();
		ui.remove().run();
		assertEquals(List.of(second), selected);
		// Opening another dataset in the same directory is still an independent request.
		navigation.open(List.of(first.toFile()), true, target -> selected.add(target.selection), () -> fail("valid input"));
		navigation.cancel();
		background.remove().run();
		ui.remove().run();
		assertEquals(List.of(second), selected);
	}

	@Test
	void invalidRequestsDoNotReplaceTheExistingLocation() {
		FileNavigation navigation=new FileNavigation(Runnable::run, Runnable::run);
		List<String> events=new ArrayList<>();
		navigation.open(List.of(directory.resolve("missing.raw").toFile()), true, target -> events.add("navigate"), () -> events.add("invalid"));
		assertEquals(List.of("invalid"), events);
	}

	@Test
	void dropAcceptsOnlyFileListsAndDoesNotModifyInputs() throws Exception {
		Path raw=Files.createFile(directory.resolve("run.raw"));
		List<File> received=new ArrayList<>();
		FileNavigationTransferHandler handler=new FileNavigationTransferHandler(received::addAll);
		Transferable files=new Transferable() {
			public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {DataFlavor.javaFileListFlavor}; }
			public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.javaFileListFlavor.equals(flavor); }
			public Object getTransferData(DataFlavor flavor) { return List.of(raw.toFile()); }
		};
		assertTrue(handler.importData(new TransferSupport(new JPanel(), files)));
		assertEquals(List.of(raw.toFile()), received);
		assertTrue(Files.isRegularFile(raw));
		assertFalse(handler.canImport(new TransferSupport(new JPanel(), new java.awt.datatransfer.StringSelection("text"))));
	}
}
