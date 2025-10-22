package org.searlelab.msrawjava.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.gui.charts.BasicChartGenerator;
import org.searlelab.msrawjava.gui.charts.FTICRLoadingPanel;
import org.searlelab.msrawjava.gui.charts.GraphType;
import org.searlelab.msrawjava.gui.charts.LoadingPanel;
import org.searlelab.msrawjava.gui.charts.QuadrupoleLoadingPanel;
import org.searlelab.msrawjava.gui.charts.TOFLoadingPanel;
import org.searlelab.msrawjava.gui.charts.XYTrace;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.io.utils.Pair;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;

public class RawFileBrowser extends JFrame {
	private static final long serialVersionUID=1L;
	private static final String PREF_LAST_DIR="lastDir";

	private final Preferences prefs=Preferences.userNodeForPackage(RawFileBrowser.class);

	private final FileSystemView fsv=FileSystemView.getFileSystemView();
	private final Icon msIcon=new FlatSVGIcon("icons/spectrum_file.svg", 16, 16);

	private final JTree dirTree;
	private final DirectoryTreeModel treeModel;

	private final JTable contentTable;
	private final DirectoryTableModel tableModel;

	private final JSplitPane fileSplit;
	private volatile SwingWorker<JComponent, String> currentLoad;
	private final java.util.concurrent.atomic.AtomicLong loadSeq = new java.util.concurrent.atomic.AtomicLong();

	// Files shown in the table: directories + files that match this filter
	private final FilenameFilter fileFilter=new ExtensionFilenameFilter("raw", "d");

	public static void main(String[] args) {
		FlatLightLaf.setup();
		ThermoServerPool.startAsync();

		// Run Thermo server cleanup on JVM shutdown (quit, Ctrl-C, etc.)
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				ThermoServerPool.shutdown();
			} catch (Throwable ignore) {
			}
		}, "ThermoServerPool-shutdown"));

		SwingUtilities.invokeLater(() -> {
			RawFileBrowser app=new RawFileBrowser();
			// Also trigger shutdown when the window is closed (belt & suspenders)
			app.addWindowListener(new java.awt.event.WindowAdapter() {
				@Override
				public void windowClosing(java.awt.event.WindowEvent e) {
					try {
						ThermoServerPool.shutdown();
					} catch (Throwable ignore) {
					}
				}
			});
			app.setVisible(true);
		});
	}

	public RawFileBrowser() {
		super("Raw File Browser");

		// ---- Left: directory tree (directories only, lazy-loaded) ----
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

		// ---- Right-Top: directory table ----
		tableModel=new DirectoryTableModel(fsv, fileFilter, msIcon);
		contentTable=new JTable(tableModel);
		contentTable.setAutoCreateRowSorter(true);
		contentTable.setRowHeight(22);
		contentTable.setShowGrid(false);

		// List-like selection
		contentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		contentTable.setRowSelectionAllowed(true);
		contentTable.setColumnSelectionAllowed(false);
		contentTable.setCellSelectionEnabled(false);

		// No Swing grid, our border draws the grid instead
		contentTable.setShowGrid(false);
		contentTable.setIntercellSpacing(new Dimension(0, 0));
		contentTable.setFillsViewportHeight(true);

		// Hide any focus rectangle from the PLAF
		contentTable.putClientProperty("Table.showCellFocusIndicator", false);
		UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder());

		// Apply unified renderers to all common types
		StripeBorderRenderer baseRenderer=new StripeBorderRenderer(contentTable);
		IconStripeBorderRenderer iconRend=new IconStripeBorderRenderer(contentTable);
		SizeRendererStripe sizeRend=new SizeRendererStripe(contentTable);

		contentTable.setDefaultRenderer(Object.class, baseRenderer);
		contentTable.setDefaultRenderer(String.class, baseRenderer);
		contentTable.setDefaultRenderer(Date.class, baseRenderer);
		contentTable.setDefaultRenderer(Number.class, baseRenderer); // fallback

		contentTable.setDefaultRenderer(Icon.class, iconRend);
		contentTable.setDefaultRenderer(Long.class, sizeRend); // file sizes

		// Nice renderers: file icons & human-readable sizes
		contentTable.getColumnModel().getColumn(0).setPreferredWidth(20);
		contentTable.getColumnModel().getColumn(1).setPreferredWidth(400);
		contentTable.getColumnModel().getColumn(2).setPreferredWidth(90);
		contentTable.getColumnModel().getColumn(3).setPreferredWidth(60);
		contentTable.getColumnModel().getColumn(4).setPreferredWidth(120);

		contentTable.getSelectionModel().addListSelectionListener(e -> {
		    if (e.getValueIsAdjusting()) return;
		    int viewRow = contentTable.getSelectedRow();
		    File f = (viewRow >= 0)
		            ? tableModel.getFileAt(contentTable.convertRowIndexToModel(viewRow))
		            : null;
		    updateFile(Optional.ofNullable(f));
		});

		// Double-click directories in table to descend in the tree
		contentTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount()==2) {
					int viewRow=contentTable.rowAtPoint(e.getPoint());
					if (viewRow>=0) {
						int modelRow=contentTable.convertRowIndexToModel(viewRow);
						File f=tableModel.getFileAt(modelRow);
						if (f.isDirectory()&&!isDotD(f)) {
							selectDirectoryInTree(f);
						}
					}
				}
			}
		});

		JScrollPane tableScroll=new JScrollPane(contentTable);

		// ---- Right-Bottom: simple JFreeChart (file size) ----

		fileSplit=new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, new JPanel());
		fileSplit.setResizeWeight(0.7);
		fileSplit.setContinuousLayout(true);
		fileSplit.setOneTouchExpandable(true);
		SwingUtilities.invokeLater(() -> fileSplit.setDividerLocation(0.7));

		// ---- Main split ----
		JSplitPane mainSplit=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, fileSplit);
		mainSplit.setResizeWeight(0.35);
		mainSplit.setContinuousLayout(true);
		mainSplit.setOneTouchExpandable(true);

		setContentPane(mainSplit);
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setSize(1100, 700);
		setLocationByPlatform(true);

		String lastPath=prefs.get(PREF_LAST_DIR, null);
		File startDir=(lastPath!=null)?new File(lastPath):fsv.getHomeDirectory();
		if (startDir==null||!startDir.isDirectory()) startDir=fsv.getHomeDirectory();
		selectDirectoryInTree(startDir);
	}

	private static boolean isThermo(File f) {
		if (f==null) return false;
		String name=f.getName().toLowerCase(Locale.ROOT);
		return name.endsWith(".raw");
	}

	private static boolean isDotD(File f) {
		if (f==null) return false;
		String name=f.getName().toLowerCase(Locale.ROOT);
		return name.endsWith(".d");
	}

	private void onTreeSelectionChanged(TreeSelectionEvent e) {
		DirectoryNode node=(DirectoryNode)dirTree.getLastSelectedPathComponent();
		if (node!=null) {
			File dir=node.getFile();
			if (dir!=null&&dir.isDirectory()) {
				tableModel.setDirectory(dir);
				updateFile(Optional.empty());
				rememberLastDirectory(dir);
			}
		}
	}

	private void rememberLastDirectory(File dir) {
		try {
			if (dir!=null&&dir.isDirectory()) {
				prefs.put(PREF_LAST_DIR, dir.getAbsolutePath());
			}
		} catch (Exception ignore) {
		}
	}
	
	private static double dividerProportion(JSplitPane sp) {
	    int div = sp.getDividerSize();
	    int avail = (sp.getOrientation() == JSplitPane.VERTICAL_SPLIT)
	            ? Math.max(sp.getHeight() - div, 1)
	            : Math.max(sp.getWidth() - div, 1);
	    return Math.max(0, Math.min(1, sp.getDividerLocation() / (double) avail));
	}

	private void updateFile(Optional<File> maybe) {
	    // Cancel any in-flight load
	    SwingWorker<JComponent, String> prev = currentLoad;
	    if (prev != null) prev.cancel(true);

	    // No file selected -> clear bottom
	    if (maybe == null || maybe.isEmpty()) {
	        double r = dividerProportion(fileSplit);
	        fileSplit.setBottomComponent(new JPanel());
	        fileSplit.setDividerLocation(r);
	        currentLoad = null;
	        return;
	    }

	    final File f = maybe.get();
	    final long mySeq = loadSeq.incrementAndGet(); // detect stale workers

		final LoadingPanel[] loadingPanels=new LoadingPanel[] {new FTICRLoadingPanel("Reading "+f.getName()), new TOFLoadingPanel("Reading "+f.getName()),
				new QuadrupoleLoadingPanel("Reading "+f.getName())};
		final LoadingPanel loading=loadingPanels[(int)(Math.random()*(loadingPanels.length))];

		double r=dividerProportion(fileSplit);
	    fileSplit.setBottomComponent(loading);
        fileSplit.setDividerLocation(r);

	    currentLoad = new SwingWorker<>() {
	        @Override protected JComponent doInBackground() throws Exception {
	            // Heavy lifting OFF the EDT
	            if (isDotD(f)) {
	                BrukerTIMSFile raw = new BrukerTIMSFile();
	                try {
	                    raw.openFile(f.toPath());
	                    Pair<float[], float[]> tic = raw.getTICTrace(); // may block / wait for native
	                    if (isCancelled()) return null;
	                    XYTrace trace = new XYTrace(MatrixMath.divide(tic.x, 60.0f), tic.y,
	                            GraphType.area, OutputType.changeExtension(raw.getOriginalFileName(), ""), null, null);
	                    return BasicChartGenerator.getChart("Time (min)", "Total Ion Current", false, trace);
	                } finally {
	                    try { raw.close(); } catch (Throwable ignore) {}
	                }
	            } else if (isThermo(f)) {
	                ThermoRawFile raw = new ThermoRawFile();
	                try {
	                    raw.openFile(f.toPath());
	                    Pair<float[], float[]> tic = raw.getTICTrace(); // may wait for gRPC launcher
	                    if (isCancelled()) return null;
	                    XYTrace trace = new XYTrace(MatrixMath.divide(tic.x, 60.0f), tic.y,
	                            GraphType.area, OutputType.changeExtension(raw.getOriginalFileName(), ""), null, null);
	                    return BasicChartGenerator.getChart("Time (min)", "Total Ion Current", false, trace);
	                } finally {
	                    try { raw.close(); } catch (Throwable ignore) {}
	                }
	            } else {
	                // Not a supported file -> empty panel
	                return new JPanel();
	            }
	        }

	        @Override protected void done() {
	            // Only apply if this is still the most recent request and not cancelled
	            if (mySeq != loadSeq.get()) return;
	            loading.stop();
	            try {
	                if (isCancelled()) return;
	                JComponent panel = get(); // may throw
	                
	                double r = dividerProportion(fileSplit);
	                fileSplit.setBottomComponent(panel != null ? panel : new JPanel());
	                fileSplit.setDividerLocation(r);
	            } catch (Exception ex) {
	                fileSplit.setBottomComponent(new JLabel("Cannot parse file!"));
	            }
	        }
	    };

	    currentLoad.execute();
	}

	/** Expand & select a directory in the tree, adding child nodes along the way if needed. */
	private void selectDirectoryInTree(File targetDir) {
		if (targetDir==null) return;
		// Build the path from a root under our virtual root
		DirectoryNode virtualRoot=(DirectoryNode)treeModel.getRoot();
		TreePath path=treePathForDirectory(virtualRoot, targetDir);
		if (path!=null) {
			// Expand along the path
			dirTree.expandPath(path);
			dirTree.setSelectionPath(path);
			dirTree.scrollPathToVisible(path);
		}
	}

	/** Create/locate a TreePath from our virtual root down to the target directory. */
	private TreePath treePathForDirectory(DirectoryNode virtualRoot, File target) {
		if (target==null) return new TreePath(virtualRoot);

		File[] fsRoots=fsv.getRoots();
		boolean unixFlatten=treeModel.unixFlattenRoot;

		// Pick the FS root that contains target (or "/" on Unix).
		File chosenRoot=(fsRoots!=null&&fsRoots.length>0)?fsRoots[0]:target;
		String targetPath=target.getAbsolutePath();
		for (File r : fsRoots) {
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
				treeModel.loadChildren(dn, fsv);
			}
		}

		@Override
		public void treeWillCollapse(TreeExpansionEvent event) {
			/* no-op */ }
	}

	// ---------------- Models & renderers ----------------

	/** Virtual root -> filesystem roots -> directories (lazy). */
	private static class DirectoryTreeModel extends DefaultTreeModel {
		private static final long serialVersionUID=1L;
		private final DirectoryNode virtualRoot;
		final boolean unixFlattenRoot;

		DirectoryTreeModel(FileSystemView fsv) {
			super(new DirectoryNode(null));
			this.virtualRoot=(DirectoryNode)getRoot();
			virtualRoot.setAllowsChildren(true);
			virtualRoot.setUserObject("My Computer");

			File[] roots=fsv.getRoots();
			boolean singleUnixRoot=roots!=null&&roots.length==1&&File.separator.equals(roots[0].getAbsolutePath());
			this.unixFlattenRoot=singleUnixRoot;

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

		void loadChildren(DirectoryNode node, FileSystemView fsv) {
			if (node.loaded||node.getFile()==null) return;
			node.removeAllChildren();
			File dir=node.getFile();

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
			node.loaded=true;
			nodeStructureChanged(node);
		}
	}

	private static class DirectoryNode extends DefaultMutableTreeNode {
		private static final long serialVersionUID=1L;
		private boolean loaded=false;

		DirectoryNode(File dir) {
			super(dir==null?"Computer":dir);
			setAllowsChildren(true);
		}

		File getFile() {
			return (getUserObject() instanceof File f)?f:null;
		}

		@Override
		public String toString() {
			File f=getFile();
			return f==null?"My Computer":(f.getName().isEmpty()?f.getPath():f.getName());
		}
	}

	private static class FileTreeCellRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID=1L;
		private final FileSystemView fsv;

		FileTreeCellRenderer(FileSystemView fsv) {
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

	private static class DirectoryTableModel extends AbstractTableModel {
		private static final long serialVersionUID=1L;
		private final FileSystemView fsv;
		private final FilenameFilter filter;
		private List<File> rows=List.of();
		private final Icon msIcon;

		DirectoryTableModel(FileSystemView fsv, FilenameFilter filter, Icon msIcon) {
			this.fsv=fsv;
			this.filter=filter;
			this.msIcon=msIcon;
		}

		void setDirectory(File dir) {
			File[] all=dir.listFiles();
			if (all==null) {
				rows=List.of();
			} else {
				List<File> list=new ArrayList<>(all.length);
				for (File f : all) {
					if (f.isDirectory()) {
						if (!f.getName().startsWith(".")) {
							list.add(f);
						}
					} else if (filter==null||filter.accept(dir, f.getName())) {
						list.add(f);
					}
				}
				// Sort: directories first, then name
				Collator coll=Collator.getInstance();
				list.sort((a, b) -> {
					if (a.isDirectory()!=b.isDirectory()) return a.isDirectory()?-1:1;
					return coll.compare(a.getName(), b.getName());
				});
				rows=list;
			}
			fireTableDataChanged();
		}

		File getFileAt(int modelRow) {
			if (modelRow<0||modelRow>=rows.size()) return null;
			return rows.get(modelRow);
		}

		private static final String[] COLS= {"", "Name", "Size (bytes)", "Type", "Modified"};

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return COLS.length;
		}

		@Override
		public String getColumnName(int c) {
			return COLS[c];
		}

		@Override
		public Class<?> getColumnClass(int c) {
			return switch (c) {
				case 0 -> Icon.class;
				case 2 -> Long.class;
				case 4 -> Date.class;
				default -> String.class;
			};
		}

		@Override
		public Object getValueAt(int r, int c) {
			File f=rows.get(r);
			return switch (c) {
				case 0 -> isMassSpec(f)?msIcon:fsv.getSystemIcon(f);
				case 1 -> fsv.getSystemDisplayName(f);
				case 2 -> f.isFile()?f.length():null;
				case 3 -> getType(f);
				case 4 -> new Date(f.lastModified());
				default -> null;
			};
		}

		private static String getType(File f) {
			if (isThermo(f)) return "Thermo";
			if (isDotD(f)) return "Bruker";
			return "";
		}

		private static boolean isMassSpec(File f) {
			if (f==null) return false;
			String name=f.getName().toLowerCase(Locale.ROOT);
			return name.endsWith(".raw")||name.endsWith(".d");
		}
	}

	/** Simple extension-based filter (case-insensitive). Directories always accepted. */
	private static class ExtensionFilenameFilter implements FilenameFilter {
		private final Set<String> exts;

		ExtensionFilenameFilter(String... extensions) {
			exts=new HashSet<>();
			for (String e : extensions) {
				if (e!=null&&!e.isBlank()) {
					String s=e.startsWith(".")?e.toLowerCase(Locale.ROOT):"."+e.toLowerCase(Locale.ROOT);
					exts.add(s);
				}
			}
		}

		@Override
		public boolean accept(File dir, String name) {
			File f=new File(dir, name);
			if (f.isDirectory()) return true;
			String lower=name.toLowerCase(Locale.ROOT);
			for (String ex : exts) {
				if (lower.endsWith(ex)) return true;
			}
			return false; // hide non-matching files
		}
	}

	// ---- Unified renderers: stripes + 1px borders + correct selection ----
	private static class StripeBorderRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID=1L;
		private final JTable table;
		private final Color altBg=or(UIManager.getColor("Table.alternateRowColor"), new Color(247, 247, 247)); // very light gray
		private final Color grid=or(UIManager.getColor("Table.gridColor"), new Color(220, 220, 220));

		StripeBorderRenderer(JTable table) {
			this.table=table;
			setOpaque(true);
		}

		@Override
		public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

			super.getTableCellRendererComponent(tbl, value, false, false, row, column);

			// Compute selection from the table itself (robust across LaFs)
			boolean rowSelected=false;
			int[] selectedRows=table.getSelectedRows();
			for (int i=0; i<selectedRows.length; i++) {
				if (selectedRows[i]==row) {
					rowSelected=true;
					break;
				}
			}

			if (rowSelected) {
				setBackground(table.getSelectionBackground());
				setForeground(table.getSelectionForeground());
			} else {
				setForeground(tbl.getForeground());
				setBackground((row%2==0)?Color.WHITE:altBg);
			}

			int top=(row==0)?0:1;
			setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(top, 0, 0, 0, grid),
					BorderFactory.createMatteBorder(0, 2, 0, 2, getBackground())));

			return this;
		}

		private static <T> T or(T a, T b) {
			return (a!=null)?a:b;
		}
	}

	private static class IconStripeBorderRenderer extends StripeBorderRenderer {
		private static final long serialVersionUID=1L;
		IconStripeBorderRenderer(JTable table) {
			super(table);
		}

		@Override
		public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

			super.getTableCellRendererComponent(tbl, "", isSelected, hasFocus, row, column);
			setIcon(value instanceof Icon?(Icon)value:null);
			setHorizontalAlignment(CENTER);
			return this;
		}
	}

	private static class SizeRendererStripe extends StripeBorderRenderer {
		private static final long serialVersionUID=1L;
		SizeRendererStripe(JTable table) {
			super(table);
			setHorizontalAlignment(SwingConstants.RIGHT);
		}

		@Override
		public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

			super.getTableCellRendererComponent(tbl, "", isSelected, hasFocus, row, column);
			if (value instanceof Long l&&l>=0L) {
				setText(humanBytes(l));
			} else {
				setText("");
			}
			return this;
		}

		private static String humanBytes(long b) {
			if (b<1024) return b+" B";
			double kb=b/1024.0;
			if (kb<1024) return String.format(Locale.ROOT, "%.1f KB", kb);
			double mb=kb/1024.0;
			if (mb<1024) return String.format(Locale.ROOT, "%.2f MB", mb);
			double gb=mb/1024.0;
			return String.format(Locale.ROOT, "%.2f GB", gb);
		}
	}
}