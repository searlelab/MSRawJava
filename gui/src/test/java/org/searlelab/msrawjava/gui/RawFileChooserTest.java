package org.searlelab.msrawjava.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RawFileChooserTest {
	@TempDir
	Path directory;

	@Test
	void approvedUnsupportedFoldersReachTheBrowserForFeedbackAndCancellationStaysQuiet() throws Exception {
		Path tsf=Files.createDirectory(directory.resolve("pasef-off.d"));
		Files.createFile(tsf.resolve("analysis.tsf"));
		SwingUtilities.invokeAndWait(() -> {
			for (Path selection : List.of(directory, tsf)) {
				assertEquals(selection.toFile(), MenuManager.chooseRawFile(null, new FixedChooser(selection.toFile(), JFileChooser.APPROVE_OPTION)));
			}
			assertNull(MenuManager.chooseRawFile(null, new FixedChooser(tsf.toFile(), JFileChooser.CANCEL_OPTION)));
		});
	}

	@Test
	void bundlesAreTerminalAndCombinedFilterIsSelected() throws Exception {
		Path bundle=Files.createDirectory(directory.resolve("test.D"));
		Files.createFile(bundle.resolve("analysis.tdf"));
		Path folder=Files.createDirectory(directory.resolve("folder"));
		Path ordinary=Files.createDirectory(directory.resolve("ssh_config.d"));
		Path tsfOnly=Files.createDirectory(directory.resolve("pasef-off.d"));
		Files.createFile(tsfOnly.resolve("analysis.tsf"));
		Path raw=Files.createFile(directory.resolve("test.RAW"));
		Path other=Files.createFile(directory.resolve("notes.txt"));
		SwingUtilities.invokeAndWait(() -> {
			JFileChooser chooser=MenuManager.createRawFileChooser();
			assertFalse(chooser.isTraversable(bundle.toFile()));
			assertTrue(chooser.isTraversable(folder.toFile()));
			assertTrue(chooser.isTraversable(ordinary.toFile()));
			assertTrue(chooser.isTraversable(tsfOnly.toFile()));
			FileFilter combined=chooser.getFileFilter();
			assertTrue(combined.accept(bundle.toFile()));
			assertTrue(combined.accept(raw.toFile()));
			assertFalse(combined.accept(other.toFile()));
			FileFilter bruker=java.util.Arrays.stream(chooser.getChoosableFileFilters())
					.filter(filter -> filter.getDescription().startsWith("Bruker")).findFirst().orElseThrow();
			assertTrue(bruker.accept(bundle.toFile()));
			assertTrue(bruker.accept(folder.toFile()));
			assertTrue(bruker.accept(ordinary.toFile()));
			assertTrue(bruker.accept(tsfOnly.toFile()));
			assertFalse(bruker.accept(raw.toFile()));
		});
	}

	private static final class FixedChooser extends JFileChooser {
		private static final long serialVersionUID=1L;
		private final File selection;
		private final int result;

		FixedChooser(File selection, int result) {
			this.selection=selection;
			this.result=result;
		}

		@Override
		public int showOpenDialog(Component parent) {
			setSelectedFile(selection);
			return result;
		}
	}
}
