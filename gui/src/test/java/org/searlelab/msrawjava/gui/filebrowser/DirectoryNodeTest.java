package org.searlelab.msrawjava.gui.filebrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectoryNodeTest {

	@TempDir
	Path tempDir;

	@Test
	void toString_usesFriendlyLabelForNull() {
		DirectoryNode node=new DirectoryNode(null);
		assertEquals("My Computer", node.toString());
		assertNull(node.getFile());
	}

	@Test
	void toString_usesNameOrPath() {
		File dir=tempDir.toFile();
		DirectoryNode node=new DirectoryNode(dir);
		assertEquals(dir.getName(), node.toString());
		assertEquals(dir, node.getFile());

		DirectoryNode rootNode=new DirectoryNode(new File(File.separator));
		assertEquals(File.separator, rootNode.toString());
	}

	@Test
	void loadedFlag_roundTrips() {
		DirectoryNode node=new DirectoryNode(tempDir.toFile());
		assertFalse(node.isLoaded());
		node.setLoaded(true);
		assertTrue(node.isLoaded());
	}
}
