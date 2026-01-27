package org.searlelab.msrawjava.io.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceTreeExtractorErrorTest {

	@TempDir
	Path tmp;

	@Test
	void extractDirectoryRejectsNullArguments() {
		assertThrows(NullPointerException.class, () -> ResourceTreeExtractor.extractDirectory(null, "/resourcetreex", tmp));
		assertThrows(NullPointerException.class, () -> ResourceTreeExtractor.extractDirectory(getClass(), null, tmp));
		assertThrows(NullPointerException.class, () -> ResourceTreeExtractor.extractDirectory(getClass(), "/resourcetreex", null));
	}

	@Test
	void missingResourceThrowsFileNotFound() {
		assertThrows(FileNotFoundException.class, () -> ResourceTreeExtractor.extractDirectory(getClass(), "/does-not-exist", tmp));
	}

	@Test
	void extractDirectoryWithoutLeadingSlashWorksForFileProtocol() throws Exception {
		Path out=tmp.resolve("out");
		ResourceTreeExtractor.extractDirectory(getClass(), "resourcetreex", out);
		assertTrue(Files.exists(out.resolve("alpha.txt")));
		assertTrue(Files.exists(out.resolve("nested/beta.txt")));
	}
}
