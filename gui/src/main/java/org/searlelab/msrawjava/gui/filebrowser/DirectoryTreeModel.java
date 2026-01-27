package org.searlelab.msrawjava.gui.filebrowser;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.gui.GUIPreferences;

/**
 * Tree model backing the directory browser.
 */
public class DirectoryTreeModel extends DefaultTreeModel {
	private static final long serialVersionUID=1L;
	private final DirectoryNode virtualRoot;
	final boolean unixFlattenRoot;

	public DirectoryTreeModel(FileSystemView fsv) {
		super(new DirectoryNode(null));
		this.virtualRoot=(DirectoryNode)getRoot();
		virtualRoot.setAllowsChildren(true);
		virtualRoot.setUserObject("My Computer");

		File[] roots=getRoots(fsv);

		this.unixFlattenRoot=roots!=null&&roots.length==1&&File.separator.equals(roots[0].getAbsolutePath());

		if (unixFlattenRoot) {
			// Add immediate subdirectories of "/" directly under “My Computer”
			File root=roots[0];
			File[] kids=root.listFiles(f -> f.isDirectory()&&!f.getName().startsWith("."));
			if (kids!=null) {
				Arrays.sort(kids, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
				for (File k : kids) {
					DirectoryNode child=new DirectoryNode(k);
					child.add(new DefaultMutableTreeNode("loading"));
					virtualRoot.add(child);
				}
			}
		} else {
			// Normal: show filesystem roots as children
			for (File r : roots) {
				DirectoryNode child=new DirectoryNode(r);
				child.add(new DefaultMutableTreeNode("loading"));
				virtualRoot.add(child);
			}
		}
	}

	public static File[] getRoots(FileSystemView fsv) {
		File[] roots=File.listRoots();
		ArrayList<File> rootList=new ArrayList<File>(Arrays.asList(roots));
		File desktop=fsv.getHomeDirectory();
		if (desktop!=null) rootList.add(desktop);

		File docs=fsv.getDefaultDirectory();
		if (docs!=null&&!docs.equals(desktop)) rootList.add(docs);

		return rootList.toArray(new File[0]);
	}

	public void loadChildren(DirectoryNode node, FileSystemView fsv) {
		if (node.isLoaded()||node.getFile()==null) return;
		File dir=node.getFile();
		if (dir!=null&&GUIPreferences.isVerboseGuiLogging()) {
			Logger.logLine("DirectoryTreeModel loaded directory: "+dir.getAbsolutePath());
		}
		node.removeAllChildren();

		// Only subdirectories that are NOT dot-dirs
		File[] kids=dir.listFiles(f -> f.isDirectory()&&!f.getName().startsWith("."));
		if (kids!=null) {
			Arrays.sort(kids, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
			for (File k : kids) {
				DirectoryNode child=new DirectoryNode(k);
				child.add(new DefaultMutableTreeNode("loading"));
				node.add(child);
			}
		}
		node.setLoaded(true);
		nodeStructureChanged(node);
	}

	public boolean isUnixFlattenRoot() {
		return unixFlattenRoot;
	}
}
