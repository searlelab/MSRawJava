package org.searlelab.msrawjava.gui.filebrowser;

import java.awt.Component;
import java.io.File;

import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 * Renderer for file tree nodes.
 */
public class FileTreeCellRenderer extends DefaultTreeCellRenderer {
	private static final long serialVersionUID=1L;
	private final FileSystemView fsv;

	public FileTreeCellRenderer(FileSystemView fsv) {
		this.fsv=fsv;
	}

	@Override
	public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean exp, boolean leaf, int row, boolean focus) {
		super.getTreeCellRendererComponent(tree, value, sel, exp, leaf, row, focus);
		if (value instanceof DirectoryNode dn) {
			File f=dn.getFile();
			if (f!=null) {
				setIcon(fsv.getSystemIcon(f));
				String name=fsv.getSystemDisplayName(f);
				setText((name==null||name.isBlank())?f.getName():name);
			} else {
				setText("My Computer");
				setIcon(UIManager.getIcon("FileView.computerIcon"));
			}
		}
		return this;
	}
}
