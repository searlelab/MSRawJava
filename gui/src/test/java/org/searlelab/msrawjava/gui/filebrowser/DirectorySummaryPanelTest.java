package org.searlelab.msrawjava.gui.filebrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.VendorFiles;

class DirectorySummaryPanelTest {

	@Test
	void rootPathsUsePathStringInFileNameColumn() {
		Path root=Path.of(System.getProperty("user.dir")).getRoot();
		VendorFiles files=new VendorFiles();
		files.addD(root);

		DirectorySummaryPanel panel=new DirectorySummaryPanel(files);

		assertEquals(1, panel.getTable().getRowCount());
		assertEquals(root.toString(), panel.getTable().getValueAt(0, 1));
	}
}
