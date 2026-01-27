package org.searlelab.msrawjava.gui.filebrowser;

import java.io.File;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Tree node representing a directory entry.
 */
public class DirectoryNode extends DefaultMutableTreeNode {
	private static final long serialVersionUID=1L;
	private boolean loaded=false;

	public DirectoryNode(File dir) {
		super(dir==null?"Computer":dir);
		setAllowsChildren(true);
	}

	public boolean isLoaded() {
		return loaded;
	}

	public void setLoaded(boolean loaded) {
		this.loaded=loaded;
	}

	public File getFile() {
		return (getUserObject() instanceof File f)?f:null;
	}

	@Override
	public String toString() {
		File f=getFile();
		return f==null?"My Computer":(f.getName().isEmpty()?f.getPath():f.getName());
	}
}
