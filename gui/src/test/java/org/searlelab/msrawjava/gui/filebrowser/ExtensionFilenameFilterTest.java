package org.searlelab.msrawjava.gui.filebrowser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionFilenameFilterTest {

	@TempDir
	Path tempDir;

	@Test
	void accept_matchesExtensionsAndDirectories() throws Exception {
		ExtensionFilenameFilter filter=new ExtensionFilenameFilter("raw", ".d", "", null);
		File dir=tempDir.toFile();
		Files.createDirectory(tempDir.resolve("subdir"));

		assertTrue(filter.accept(dir, "subdir"));
		assertTrue(filter.accept(dir, "sample.RAW"));
		assertTrue(filter.accept(dir, "analysis.d"));
		assertFalse(filter.accept(dir, "notes.txt"));
	}
}
