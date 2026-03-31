package org.searlelab.msrawjava.gui;

import java.awt.EventQueue;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleConsumer;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.searlelab.msrawjava.gui.filebrowser.DirectoryNode;
import org.searlelab.msrawjava.gui.filebrowser.DirectorySelectionHintPanel;
import org.searlelab.msrawjava.gui.filebrowser.DirectorySummaryPanel;
import org.searlelab.msrawjava.gui.filebrowser.DirectoryTreeModel;
import org.searlelab.msrawjava.gui.filebrowser.FileTreeCellRenderer;
import org.searlelab.msrawjava.gui.loadingpanels.AstralLoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.FTICRLoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.LoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.QuadrupoleLoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.TOFLoadingPanel;
import org.searlelab.msrawjava.gui.utils.BackgroundKeyboardListener;
import org.searlelab.msrawjava.io.VendorFileFinder;
import org.searlelab.msrawjava.io.VendorFiles;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

/**
 * Main window for browsing raw files and spectra.
 */
public class RawFileBrowser extends JFrame {
	private static final long serialVersionUID=1L;

	private final FileSystemView fsv=FileSystemView.getFileSystemView();

	private final JTree dirTree;
	private final DirectoryTreeModel treeModel;

	private final ConversionPane conversionPane;
	private final JComponent directorySelectionHintPanel=new DirectorySelectionHintPanel();
	private DirectorySummaryPanel currentSummaryPanel;
	private Path pendingSelectionPath;

	private final JSplitPane fileSplit;
	private final JSplitPane leftAndCenter;
	private volatile SwingWorker<JComponent, String> currentLoad;
	private final AtomicLong loadSeq=new AtomicLong();
	private boolean restoringSelection=false;
	private boolean programmaticSelection=false;

	public RawFileBrowser(ProcessingThreadPool pool) {
		super("Raw File Browser");

		treeModel=new DirectoryTreeModel(fsv);
		dirTree=new JTree(treeModel) {
			private static final long serialVersionUID=1L;

			@Override
			public boolean getScrollableTracksViewportWidth() {
				return true;
			}
		};
		dirTree.setScrollsOnExpand(true);
		dirTree.setRootVisible(true);
		dirTree.setShowsRootHandles(true);
		dirTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		dirTree.setCellRenderer(new FileTreeCellRenderer(fsv));
		dirTree.addTreeWillExpandListener(new TreeWillExpandLoader());
		dirTree.addTreeSelectionListener(this::onTreeSelectionChanged);

		JScrollPane treeScroll=new JScrollPane(dirTree);
		treeScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		ReaderStatusPanel statusPanel=new ReaderStatusPanel();
		JPanel treeWithStatus=new JPanel(new java.awt.BorderLayout());
		treeWithStatus.add(treeScroll, java.awt.BorderLayout.CENTER);
		treeWithStatus.add(statusPanel, java.awt.BorderLayout.SOUTH);

		// ---- Top is the directory table (set later); Bottom is the lowerSplit
		conversionPane=new ConversionPane(GUIPreferences.getPreferences(), pool);
		fileSplit=new JSplitPane(JSplitPane.VERTICAL_SPLIT, directorySelectionHintPanel, conversionPane);
		fileSplit.setResizeWeight(GUIPreferences.getRawFileBrowserFileSplitRatio());
		fileSplit.setContinuousLayout(true);
		fileSplit.setOneTouchExpandable(true);
		SwingUtilities.invokeLater(() -> {
			applySplitRatio(fileSplit, GUIPreferences.getRawFileBrowserFileSplitRatio());
			SwingUtilities.invokeLater(conversionPane::applySavedSplitRatio);
		});
		registerSplitPreference(fileSplit, GUIPreferences::setRawFileBrowserFileSplitRatio);

		// ---- Main split (tree on left, fileSplit on right) stays the same
		leftAndCenter=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeWithStatus, fileSplit);
		leftAndCenter.setResizeWeight(GUIPreferences.getRawFileBrowserMainSplitRatio());
		leftAndCenter.setContinuousLayout(true);
		leftAndCenter.setOneTouchExpandable(true);
		SwingUtilities.invokeLater(() -> applySplitRatio(leftAndCenter, GUIPreferences.getRawFileBrowserMainSplitRatio()));
		registerSplitPreference(leftAndCenter, GUIPreferences::setRawFileBrowserMainSplitRatio);

		setContentPane(leftAndCenter);
		MenuManager.install(this);

		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setSize(GUIPreferences.getRawFileBrowserWindowSize());
		Point location=GUIPreferences.getRawFileBrowserWindowLocation();
		if (GUIPreferences.isVerboseGuiLogging()) {
			Logger.logLine("RawFileBrowser location (saved): "+location);
		}
		Point clamped=GUIPreferences.clampToScreens(location, getSize());
		if (GUIPreferences.isVerboseGuiLogging()) {
			Logger.logLine("RawFileBrowser location (clamped): "+clamped);
		}
		if (clamped!=null) {
			setLocation(clamped);
		} else {
			setLocationByPlatform(true);
		}
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				GUIPreferences.setRawFileBrowserWindowSize(getSize());
			}

			@Override
			public void componentMoved(ComponentEvent e) {
				GUIPreferences.setRawFileBrowserWindowLocation(getLocation());
			}
		});
		new BackgroundKeyboardListener().addKeyAndContainerListenerRecursively(this);

		String lastPath=GUIPreferences.getLastDirectory();
		File startDir=(lastPath!=null)?new File(lastPath):fsv.getHomeDirectory();
		if (startDir==null||!startDir.isDirectory()) startDir=fsv.getHomeDirectory();
		selectDirectoryInTree(startDir);

		// ensure executor shuts down on window close
		this.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				conversionPane.shutdown();
			}
		});
	}

	private void onTreeSelectionChanged(TreeSelectionEvent e) {
		if (restoringSelection) {
			restoringSelection=false;
			return;
		}
		DirectoryNode node=(DirectoryNode)dirTree.getLastSelectedPathComponent();
		if (node==null) return;

		File dir=node.getFile();
		if (dir==null) {
			showDirectorySelectionHint();
			return;
		}
		if (!dir.isDirectory()) return;
		if (isFileSystemRoot(dir)) {
			showDirectorySelectionHint();
			return;
		}

		Object event=EventQueue.getCurrentEvent();
		boolean userEvent=isUserEvent(event);
		String eventType=(event==null)?"null":event.getClass().getName();
		if (GUIPreferences.isVerboseGuiLogging()) {
			Logger.logLine("RawFileBrowser selection event: userEvent="+userEvent+", eventType="+eventType);
		}
		if (!programmaticSelection&&userEvent) {
			node.setLoaded(false);
			treeModel.loadChildren(node, fsv);
			GUIPreferences.rememberLastDirectory(dir);
		}
		updateDirectory(dir);
	}

	private void showDirectorySelectionHint() {
		conversionPane.setSelectedPathsSupplier(java.util.List::of);
		conversionPane.updateDemuxAvailability(java.util.List.of());
		setTopComponent(directorySelectionHintPanel);
	}

	private static boolean isFileSystemRoot(File directory) {
		if (directory==null) return false;
		try {
			Path normalizedPath=directory.toPath().toAbsolutePath().normalize();
			return normalizedPath.getParent()==null;
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private void updateDirectory(File dir) {
		if (GUIPreferences.isVerboseGuiLogging()) {
			Logger.logLine("RawFileBrowser opened directory: "+dir.getAbsolutePath());
		}
		// Cancel any in-flight worker (directory or file loader)
		SwingWorker<JComponent, String> prev=currentLoad;
		if (prev!=null) prev.cancel(true);

		final long mySeq=loadSeq.incrementAndGet();

		// Random loading panel (reuse your set)
		String loadingText="Scanning "+dir.getName();
		final LoadingPanel[] loadingPanels=new LoadingPanel[] {new FTICRLoadingPanel(loadingText), new TOFLoadingPanel(loadingText),
				new QuadrupoleLoadingPanel(loadingText), new AstralLoadingPanel(loadingText)};
		final LoadingPanel loading=loadingPanels[(int)(Math.random()*loadingPanels.length)];

		// Show loader in the TOP half
		double r=dividerProportion(fileSplit);
		fileSplit.setTopComponent(loading);
		fileSplit.setDividerLocation(r);

		currentLoad=new SwingWorker<>() {
			@Override
			protected JComponent doInBackground() {
				try {
					VendorFiles files=VendorFileFinder.findAndAddRawAndD(dir.toPath(), true, true);
					if (isCancelled()) return null;

					DirectorySummaryPanel panel=new DirectorySummaryPanel(files);
					panel.getTable().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
					return panel;
				} catch (Throwable t) {
					String message="Error scanning directory: "+t.getMessage();
					Logger.errorLine(message);
					Logger.errorException(t);
					return new JLabel(message);
				}
			}

			@Override
			protected void done() {
				if (mySeq!=loadSeq.get()) return;
				loading.stop();

				try {
					if (isCancelled()) return;
					JComponent top=get(); // may be a DirectorySummaryPanel or a JLabel on error

					if (top instanceof DirectorySummaryPanel panel) {
						installSummaryInteractions(panel); // double-click + right-click menu
						conversionPane.setSelectedPathsSupplier(() -> (panel!=null)?panel.getSelectedPaths():java.util.List.of());
						conversionPane.updateDemuxAvailability(panel.getSelectedPaths());
						setTopComponent(panel);
					} else {
						conversionPane.setSelectedPathsSupplier(java.util.List::of); // nothing to queue
						conversionPane.updateDemuxAvailability(java.util.List.of());
						setTopComponent(top!=null?top:new JPanel());
					}
				} catch (Exception ex) {
					Logger.errorLine("Cannot build directory table.");
					Logger.errorException(ex);
					setTopComponent(new JLabel("Cannot build directory table."));
				}
			}
		};

		currentLoad.execute();
	}

	private void applySplitRatio(JSplitPane pane, double ratio) {
		if (ratio<=0.0||ratio>=1.0) return;
		pane.setResizeWeight(ratio);
		pane.setDividerLocation(ratio);
	}

	private double getSplitRatio(JSplitPane pane) {
		int size=(pane.getOrientation()==JSplitPane.HORIZONTAL_SPLIT)?pane.getWidth():pane.getHeight();
		if (size<10) return -1.0;
		double ratio=pane.getDividerLocation()/(double)size;
		if (ratio<=0.0||ratio>=1.0) return -1.0;
		return ratio;
	}

	private void registerSplitPreference(JSplitPane pane, DoubleConsumer saver) {
		AtomicBoolean dragging=new AtomicBoolean(false);
		installDividerDragListener(pane, dragging, saver);
		pane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
			if (!dragging.get()) return;
			double ratio=getSplitRatio(pane);
			if (ratio>0.0) saver.accept(ratio);
		});
	}

	private void installDividerDragListener(JSplitPane pane, AtomicBoolean dragging, DoubleConsumer saver) {
		if (pane.getClientProperty("rawFileBrowser.dividerListener")!=null) return;
		pane.putClientProperty("rawFileBrowser.dividerListener", Boolean.TRUE);
		SwingUtilities.invokeLater(() -> {
			if (!(pane.getUI() instanceof BasicSplitPaneUI)) return;
			BasicSplitPaneUI ui=(BasicSplitPaneUI)pane.getUI();
			BasicSplitPaneDivider divider=ui.getDivider();
			if (divider==null) return;
			divider.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mousePressed(java.awt.event.MouseEvent e) {
					dragging.set(true);
				}

				@Override
				public void mouseReleased(java.awt.event.MouseEvent e) {
					dragging.set(false);
					double ratio=getSplitRatio(pane);
					if (ratio>0.0) saver.accept(ratio);
				}
			});
		});
	}

	private void setTopComponent(JComponent c) {
		double r=dividerProportion(fileSplit);
		fileSplit.setTopComponent(c);
		fileSplit.setDividerLocation(r);
		if (c instanceof DirectorySummaryPanel panel) {
			currentSummaryPanel=panel;
			applyPendingSelection(panel);
		} else {
			currentSummaryPanel=null;
		}
	}

	private void applyPendingSelection(DirectorySummaryPanel panel) {
		if (panel==null||pendingSelectionPath==null) return;
		Path pending=pendingSelectionPath;
		pendingSelectionPath=null;
		SwingUtilities.invokeLater(() -> panel.selectPath(pending));
	}

	public void openAndSelectFile(File file, boolean visualize) {
		if (file==null) return;
		File parent=file.isDirectory()?file.getParentFile():file.getParentFile();
		if (parent==null) return;
		pendingSelectionPath=file.toPath();
		selectDirectoryInTree(parent);
		if (currentSummaryPanel!=null) {
			currentSummaryPanel.selectPath(file.toPath());
		}
		if (visualize) {
			FileDetailsDialog.showFileDetailsDialog(this, file);
		}
	}

	private void installSummaryInteractions(DirectorySummaryPanel panel) {
		JTable tbl=panel.getTable();

		// Double-click opens chart dialog
		tbl.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount()==2&&SwingUtilities.isLeftMouseButton(e)) {
					int vr=tbl.rowAtPoint(e.getPoint());
					Path p=panel.getPathAtViewRow(vr);
					if (p!=null) FileDetailsDialog.showFileDetailsDialog(RawFileBrowser.this, p.toFile());
				}
			}

			@Override
			public void mousePressed(MouseEvent e) {
				maybeShowPopup(e, panel);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				maybeShowPopup(e, panel);
			}
		});
		tbl.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				conversionPane.updateDemuxAvailability(panel.getSelectedPaths());
			}
		});
	}

	private void maybeShowPopup(MouseEvent e, DirectorySummaryPanel panel) {
		if (!e.isPopupTrigger()) return;
		JTable tbl=panel.getTable();
		int vr=tbl.rowAtPoint(e.getPoint());
		Path p=panel.getPathAtViewRow(vr);
		if (vr<0||p==null) return;

		JPopupMenu menu=new JPopupMenu();

		JMenuItem visualize=new JMenuItem("Visualize");
		visualize.setToolTipText("Open the selected raw file in the visualization dialog.");
		visualize.addActionListener(ae -> FileDetailsDialog.showFileDetailsDialog(RawFileBrowser.this, p.toFile()));

		JMenuItem showDir=new JMenuItem("Show Enclosing Folder");
		showDir.setToolTipText("Jump the directory tree to this file's enclosing folder.");
		showDir.addActionListener(ae -> {
			File f=p.toFile();
			File parent=f.isDirectory()?f:f.getParentFile();
			if (parent!=null&&parent.isDirectory()) {
				selectDirectoryInTree(parent);
			}
		});

		JMenuItem selectAll=new JMenuItem("Select All");
		selectAll.setToolTipText("Select all files currently visible in the directory table.");
		selectAll.addActionListener(ae -> tbl.selectAll());

		menu.add(visualize);
		menu.add(showDir);
		menu.addSeparator();
		menu.add(selectAll);
		menu.show(tbl, e.getX(), e.getY());
	}

	private static double dividerProportion(JSplitPane sp) {
		int div=sp.getDividerSize();
		int avail=(sp.getOrientation()==JSplitPane.VERTICAL_SPLIT)?Math.max(sp.getHeight()-div, 1):Math.max(sp.getWidth()-div, 1);
		return Math.max(0, Math.min(1, sp.getDividerLocation()/(double)avail));
	}

	/** Expand & select a directory in the tree, adding child nodes along the way if needed. */
	private void selectDirectoryInTree(File targetDir) {
		if (targetDir==null) return;
		// Build the path from a root under our virtual root
		DirectoryNode virtualRoot=(DirectoryNode)treeModel.getRoot();
		TreePath path=treePathForDirectory(virtualRoot, targetDir);
		if (path!=null) {
			// Expand along the path
			programmaticSelection=true;
			try {
				dirTree.expandPath(path);
				dirTree.setSelectionPath(path);
				dirTree.scrollPathToVisible(path);
			} finally {
				programmaticSelection=false;
			}
		}
	}

	/** Create/locate a TreePath from our virtual root down to the target directory. */
	private TreePath treePathForDirectory(DirectoryNode virtualRoot, File target) {
		if (target==null) return new TreePath(virtualRoot);

		File[] roots=DirectoryTreeModel.getRoots(fsv);

		boolean unixFlatten=treeModel.isUnixFlattenRoot();

		// Pick the FS root that contains target (or "/" on Unix).
		File chosenRoot=(roots!=null&&roots.length>0)?roots[0]:target;
		String targetPath=target.getAbsolutePath();
		for (File r : roots) {
			if (targetPath.startsWith(r.getAbsolutePath())) {
				chosenRoot=r;
				break;
			}
		}

		List<File> chain=fileChainFromRoot(chosenRoot, target);

		DirectoryNode cur=virtualRoot;
		TreePath curPath=new TreePath(new Object[] {virtualRoot});

		// If not flattening, add the FS root node first (C:, D:, /Volumes/…, etc.)
		if (!unixFlatten) {
			DirectoryNode rootNode=ensureChildNodeFor(virtualRoot, chosenRoot);
			cur=rootNode;
			curPath=curPath.pathByAddingChild(cur);
		}

		// Walk subdirectories (skip index 0 which is the chosen root itself)
		int start=1; // skip the root level in 'chain'
		for (int i=start; i<chain.size(); i++) {
			File piece=chain.get(i);
			treeModel.loadChildren(cur, fsv);
			DirectoryNode next=findChildByFile(cur, piece);
			if (next==null) break; // may be hidden/permission issue
			cur=next;
			curPath=curPath.pathByAddingChild(cur);
		}
		return curPath;
	}

	/** Ensure that virtualRoot has a child node for file f; return it. */
	private DirectoryNode ensureChildNodeFor(DirectoryNode parent, File f) {
		DirectoryNode child=findChildByFile(parent, f);
		if (child!=null) return child;
		child=new DirectoryNode(f);
		parent.add(child);
		// add placeholder so it's expandable
		child.add(new DefaultMutableTreeNode("loading"));
		((DefaultTreeModel)dirTree.getModel()).nodesWereInserted(parent, new int[] {parent.getChildCount()-1});
		return child;
	}

	private static DirectoryNode findChildByFile(DirectoryNode parent, File f) {
		Enumeration<?> e=parent.children();
		while (e.hasMoreElements()) {
			Object o=e.nextElement();
			if (o instanceof DirectoryNode dn) {
				if (Objects.equals(fileKey(dn.getFile()), fileKey(f))) return dn;
			}
		}
		return null;
	}

	private static Object fileKey(File f) {
		// Use absolute path as key; good enough for navigation
		return f==null?null:f.getAbsolutePath();
	}

	private static List<File> fileChainFromRoot(File root, File target) {
		LinkedList<File> list=new LinkedList<>();
		File cur=target;
		while (cur!=null) {
			list.addFirst(cur);
			if (Objects.equals(fileKey(cur), fileKey(root))) break;
			cur=cur.getParentFile();
		}
		return list;
	}

	private final class TreeWillExpandLoader implements TreeWillExpandListener {
		@Override
		public void treeWillExpand(TreeExpansionEvent event) {
			Object last=event.getPath().getLastPathComponent();
			if (last instanceof DirectoryNode dn) {
				Object evt=EventQueue.getCurrentEvent();
				if (!programmaticSelection&&isUserEvent(evt)) {
					dn.setLoaded(false);
				}
				treeModel.loadChildren(dn, fsv);
			}
		}

		@Override
		public void treeWillCollapse(TreeExpansionEvent event) {
		}
	}

	private static boolean isUserEvent(Object event) {
		return event instanceof MouseEvent||event instanceof KeyEvent;
	}
}
