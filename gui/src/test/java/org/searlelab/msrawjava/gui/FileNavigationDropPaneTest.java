package org.searlelab.msrawjava.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.image.BufferedImage;

import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class FileNavigationDropPaneTest {
	@Test
	void feedbackHighlightsTheWholePaneAndClearsWithoutChangingSelection() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			JTree tree=new JTree();
			tree.setSelectionRow(1);
			JScrollPane scroll=new JScrollPane(tree);
			FileNavigationDropPane pane=new FileNavigationDropPane(scroll, tree, new FileNavigationTransferHandler(files -> fail("No drop occurred")));
			pane.setSize(240, 240);
			pane.doLayout();
			scroll.doLayout();
			BufferedImage before=render(pane);
			pane.setDropActive(true);
			BufferedImage active=render(pane);
			assertNotEquals(before.getRGB(1, 120), active.getRGB(1, 120));
			assertNotEquals(before.getRGB(120, 1), active.getRGB(120, 1));
			assertNotEquals(before.getRGB(230, 230), active.getRGB(230, 230));
			assertEquals(1, tree.getSelectionRows()[0]);
			assertNull(tree.getDropLocation());
			pane.setDropActive(false);
			assertArrayEquals(before.getRGB(0, 0, 240, 240, null, 0, 240), render(pane).getRGB(0, 0, 240, 240, null, 0, 240));
		});
	}

	@Test
	void nativeDragFeedbackUsesCopyAndLeavesTreeRowsAlone() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Native drop targets require desktop access");
		SwingUtilities.invokeAndWait(() -> {
			JTree tree=new JTree();
			tree.setSelectionRow(1);
			FileNavigationDropPane pane=new FileNavigationDropPane(new JScrollPane(tree), tree,
					new FileNavigationTransferHandler(files -> fail("No drop occurred")));
			DropTarget target=tree.getDropTarget();
			int[] accepted= {0};
			DropTargetDragEvent event=new DropTargetDragEvent(target.getDropTargetContext(), new Point(20, 40), TransferHandler.COPY,
					TransferHandler.COPY_OR_MOVE) {
				private static final long serialVersionUID=1L;

				@Override
				public boolean isDataFlavorSupported(DataFlavor flavor) {
					return DataFlavor.javaFileListFlavor.equals(flavor);
				}

				@Override
				public void acceptDrag(int action) {
					accepted[0]=action;
				}
			};
			target.dragEnter(event);
			assertEquals(TransferHandler.COPY, accepted[0]);
			assertEquals(1, tree.getSelectionRows()[0]);
			assertNull(tree.getDropLocation());
			target.dragExit(new DropTargetEvent(target.getDropTargetContext()));
			assertEquals(1, tree.getSelectionRows()[0]);
		});
	}

	private static BufferedImage render(FileNavigationDropPane pane) {
		BufferedImage image=new BufferedImage(240, 240, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics=image.createGraphics();
		try {
			pane.paint(graphics);
		} finally {
			graphics.dispose();
		}
		return image;
	}
}
