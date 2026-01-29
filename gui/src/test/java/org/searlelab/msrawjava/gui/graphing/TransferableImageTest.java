package org.searlelab.msrawjava.gui.graphing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;

import org.junit.jupiter.api.Test;

class TransferableImageTest {

	@Test
	void transferData_returnsImageForImageFlavor() throws Exception {
		BufferedImage image=new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
		TransferableImage transferable=new TransferableImage(image);

		assertTrue(transferable.isDataFlavorSupported(DataFlavor.imageFlavor));
		assertFalse(transferable.isDataFlavorSupported(DataFlavor.stringFlavor));
		assertEquals(1, transferable.getTransferDataFlavors().length);
		assertSame(DataFlavor.imageFlavor, transferable.getTransferDataFlavors()[0]);

		Object data=transferable.getTransferData(DataFlavor.imageFlavor);
		assertSame(image, data);
	}

	@Test
	void transferData_throwsForUnsupportedFlavor() {
		BufferedImage image=new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		TransferableImage transferable=new TransferableImage(image);

		assertThrows(UnsupportedFlavorException.class, () -> transferable.getTransferData(DataFlavor.stringFlavor));
	}

	@Test
	void transferData_throwsWhenImageIsNull() {
		TransferableImage transferable=new TransferableImage(null);

		assertThrows(UnsupportedFlavorException.class, () -> transferable.getTransferData(DataFlavor.imageFlavor));
	}
}
