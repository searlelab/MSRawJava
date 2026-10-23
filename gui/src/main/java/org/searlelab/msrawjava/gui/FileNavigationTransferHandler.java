package org.searlelab.msrawjava.gui;

import java.awt.datatransfer.DataFlavor;
import java.awt.GraphicsEnvironment;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.TransferHandler;
import javax.swing.JComponent;

import org.searlelab.msrawjava.logging.Logger;

/** A navigation-only drop target: it never transfers files on disk. */
final class FileNavigationTransferHandler extends TransferHandler {
	private static final long serialVersionUID=1L;
	private final Consumer<List<File>> open;

	FileNavigationTransferHandler(Consumer<List<File>> open) {
		this.open=open;
	}

	/** Use a pane-level target instead of Swing's tree-row selection and auto-expansion feedback. */
	void installOn(JComponent component, Consumer<Boolean> feedback) {
		component.setTransferHandler(this);
		if (GraphicsEnvironment.isHeadless()) return;
		component.setDropTarget(new DropTarget(component, COPY, new DropTargetAdapter() {
			private void acceptDrag(DropTargetDragEvent event) {
				boolean accepted=event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)&&(event.getSourceActions()&COPY)!=0;
				if (accepted) event.acceptDrag(COPY);
				else event.rejectDrag();
				feedback.accept(accepted);
			}

			@Override
			public void dragEnter(DropTargetDragEvent event) {
				acceptDrag(event);
			}

			@Override
			public void dragOver(DropTargetDragEvent event) {
				acceptDrag(event);
			}

			@Override
			public void dropActionChanged(DropTargetDragEvent event) {
				acceptDrag(event);
			}

			@Override
			public void dragExit(DropTargetEvent event) {
				feedback.accept(false);
			}

			@Override
			public void drop(DropTargetDropEvent event) {
				try {
					if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)||(event.getSourceActions()&COPY)==0) {
						event.rejectDrop();
						return;
					}
					event.acceptDrop(COPY);
					event.dropComplete(importData(new TransferSupport(component, event.getTransferable())));
				} finally {
					feedback.accept(false);
				}
			}
		}));
	}

	@Override
	public boolean canImport(TransferSupport support) {
		if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false;
		if (support.isDrop()) {
			// Never acknowledge MOVE: a drag source could otherwise delete the original dataset.
			if ((support.getSourceDropActions()&COPY)==0) return false;
			support.setDropAction(COPY);
			support.setShowDropLocation(false);
		}
		return true;
	}

	@Override
	public boolean importData(TransferSupport support) {
		if (!canImport(support)) return false;
		try {
			Object data=support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
			List<File> files=new ArrayList<>();
			if (data instanceof List<?>) {
				for (Object item : (List<?>)data) {
					if (item instanceof File) files.add((File)item);
				}
			}
			if (files.isEmpty()) return false;
			open.accept(files);
			return true;
		} catch (Exception e) {
			Logger.errorException(e);
			return false;
		}
	}
}
