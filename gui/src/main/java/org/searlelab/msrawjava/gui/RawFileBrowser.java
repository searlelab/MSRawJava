package org.searlelab.msrawjava.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.gui.charts.BasicChartGenerator;
import org.searlelab.msrawjava.gui.charts.GraphType;
import org.searlelab.msrawjava.gui.charts.XYTrace;
import org.searlelab.msrawjava.gui.filebrowser.DirectoryNode;
import org.searlelab.msrawjava.gui.filebrowser.DirectoryTreeModel;
import org.searlelab.msrawjava.gui.filebrowser.ExtensionFilenameFilter;
import org.searlelab.msrawjava.gui.filebrowser.FileTreeCellRenderer;
import org.searlelab.msrawjava.gui.filebrowser.StripeTableCellRenderer;
import org.searlelab.msrawjava.gui.loadingpanels.FTICRLoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.LoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.QuadrupoleLoadingPanel;
import org.searlelab.msrawjava.gui.loadingpanels.TOFLoadingPanel;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConverters;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.logging.ProgressIndicator;

import com.formdev.flatlaf.extras.FlatSVGIcon;

public class RawFileBrowser extends JFrame {
	private static final long serialVersionUID=1L;
	private static final String PREF_LAST_DIR="lastDir";
	private static final String PREF_OUT_TYPE="queue.outType";
	private static final String PREF_THREADS="queue.threads";
	private static final Color BUTTON_COLOR_BACKGROUND=new Color(250, 245, 235);

	private final Preferences prefs=Preferences.userNodeForPackage(RawFileBrowser.class);

	private final FileSystemView fsv=FileSystemView.getFileSystemView();
	private final Icon msIcon=new FlatSVGIcon("icons/spectrum_file.svg", 16, 16);

	private final JTree dirTree;
	private final DirectoryTreeModel treeModel;

	private final JTable contentTable;
	private final DirectoryTableModel tableModel;

	// Parameter bar widgets
	private JComboBox<OutputType> outTypeBox;
	private JSpinner threadSpinner;

	// Queue UI
	private final JTable queueTable;
	private final JobTableModel queueModel=new JobTableModel();

	private JTextArea jobConsole;
	private JLabel statusIndicator;

	// Dispatcher / execution
	private final ExecutorService executor=Executors.newCachedThreadPool(); // workers are gated by our dispatcher
	private final QueueDispatcher dispatcher=new QueueDispatcher(queueModel, executor);

	private final JSplitPane fileSplit;
	private volatile SwingWorker<JComponent, String> currentLoad;
	private final AtomicLong loadSeq=new AtomicLong();

	// Files shown in the table: directories + files that match this filter
	private final FilenameFilter fileFilter=new ExtensionFilenameFilter("raw", "d");

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
		contentTable.setDefaultRenderer(Object.class, StripeTableCellRenderer.BASE_RENDERER);
		contentTable.setDefaultRenderer(String.class, StripeTableCellRenderer.BASE_RENDERER);
		contentTable.setDefaultRenderer(Date.class, StripeTableCellRenderer.BASE_RENDERER);
		contentTable.setDefaultRenderer(Number.class, StripeTableCellRenderer.BASE_RENDERER);

		contentTable.setDefaultRenderer(Icon.class, StripeTableCellRenderer.ICON_RENDERER);
		contentTable.setDefaultRenderer(Long.class, StripeTableCellRenderer.SIZE_RENDERER); // file sizes
		contentTable.getColumnModel().getColumn(5).setCellRenderer(new QueueButtonRenderer());
		contentTable.getColumnModel().getColumn(5).setCellEditor(new QueueButtonEditor());

		// Nice renderers: file icons & human-readable sizes
		contentTable.getColumnModel().getColumn(0).setPreferredWidth(20);
		contentTable.getColumnModel().getColumn(1).setPreferredWidth(400);
		contentTable.getColumnModel().getColumn(2).setPreferredWidth(90);
		contentTable.getColumnModel().getColumn(3).setPreferredWidth(60);
		contentTable.getColumnModel().getColumn(4).setPreferredWidth(120);
		contentTable.getColumnModel().getColumn(5).setPreferredWidth(64);

		contentTable.getSelectionModel().addListSelectionListener(e -> {
			if (e.getValueIsAdjusting()) return;
			int viewRow=contentTable.getSelectedRow();
			File f=(viewRow>=0)?tableModel.getFileAt(contentTable.convertRowIndexToModel(viewRow)):null;
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

		// ---- Parameter bar (top of the table) ----
		JPanel tableWithParams=new JPanel(new BorderLayout());
		tableWithParams.add(tableScroll, BorderLayout.CENTER);

		// ---- Right-Bottom: simple JFreeChart (file size) ----
		fileSplit=new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableWithParams, new JPanel());
		fileSplit.setResizeWeight(0.7);
		fileSplit.setContinuousLayout(true);
		fileSplit.setOneTouchExpandable(true);
		SwingUtilities.invokeLater(() -> fileSplit.setDividerLocation(0.7));

		// ---- Main split ----
		JSplitPane leftAndCenter=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, fileSplit);
		leftAndCenter.setResizeWeight(0.30);
		leftAndCenter.setContinuousLayout(true);
		leftAndCenter.setOneTouchExpandable(true);

		// ---- Far-right queue table ----
		queueTable=new JTable(queueModel);
		queueTable.setRowHeight(22);
		queueTable.setFillsViewportHeight(true);
		// No sorter to keep row==job index stable for the renderer
		queueTable.setDefaultRenderer(Float.class, new ProgressRenderer(queueModel));
		queueTable.setDefaultRenderer(String.class, StripeTableCellRenderer.BASE_RENDERER);
		queueTable.setDefaultRenderer(Object.class, StripeTableCellRenderer.BASE_RENDERER);
		queueTable.getColumnModel().getColumn(0).setPreferredWidth(200);
		queueTable.getColumnModel().getColumn(1).setPreferredWidth(40);
		queueTable.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) updateConsoleForSelection();
		});

		JPanel queuePanel=buildQueuePanel(); // toolbar + table
		JSplitPane mainSplit=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftAndCenter, queuePanel);
		mainSplit.setResizeWeight(0.8);
		mainSplit.setContinuousLayout(true);
		mainSplit.setOneTouchExpandable(true);

		setContentPane(mainSplit);
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setSize(1200, 700);
		setLocationByPlatform(true);

		String lastPath=prefs.get(PREF_LAST_DIR, null);
		File startDir=(lastPath!=null)?new File(lastPath):fsv.getHomeDirectory();
		if (startDir==null||!startDir.isDirectory()) startDir=fsv.getHomeDirectory();
		selectDirectoryInTree(startDir);

		// ensure executor shuts down on window close
		this.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				executor.shutdownNow();
			}
		});
	}

	private JPanel buildQueuePanel() {
		JScrollPane queueScroll=new JScrollPane(queueTable);
		queueScroll.setPreferredSize(new Dimension(360, 200));

		// Global controls: Cancel / Restart / Clear
		JButton cancelAll=new JButton("Cancel");
		cancelAll.setBackground(BUTTON_COLOR_BACKGROUND);
		cancelAll.addActionListener(e -> {
			dispatcher.pause(true);
			queueModel.cancelAll();
		});

		JButton restart=new JButton("Restart");
		restart.setBackground(BUTTON_COLOR_BACKGROUND);
		restart.addActionListener(e -> {
			queueModel.requeueFailedAndCanceled(); // NEW: requeue failed/canceled only
			dispatcher.pause(false);
			dispatcher.maybeStart();
		});

		JButton clear=new JButton("Clear");
		clear.setBackground(BUTTON_COLOR_BACKGROUND);
		clear.addActionListener(e -> queueModel.clearUnstartedOrCanceled());

		JPanel tools=new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		tools.add(new JLabel("Queue"));
		tools.add(cancelAll);
		tools.add(restart);
		tools.add(clear);

		// ---- NEW: build a console below the table ----
		JPanel consolePanel=buildConsolePanel();

		// Controls stack (param bar on top, then toolbar)
		JPanel controls=new JPanel(new BorderLayout());
		controls.add(buildParamBar(), BorderLayout.NORTH); // moved here
		controls.add(tools, BorderLayout.SOUTH);

		// Table + Console as a vertical split
		JSplitPane queueAndConsole=new JSplitPane(JSplitPane.VERTICAL_SPLIT, queueScroll, consolePanel);
		queueAndConsole.setResizeWeight(0.90);
		queueAndConsole.setContinuousLayout(true);
		queueAndConsole.setOneTouchExpandable(true);

		JPanel panel=new JPanel(new BorderLayout());
		panel.add(controls, BorderLayout.NORTH);
		panel.add(queueAndConsole, BorderLayout.CENTER);
		return panel;
	}

	private JPanel buildConsolePanel() {
		JPanel panel=new JPanel(new BorderLayout());
		JPanel header=new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		header.add(new JLabel("Details"));

		// small status "light"
		statusIndicator=new JLabel("●");
		statusIndicator.setOpaque(false);
		statusIndicator.setForeground(new Color(0x1976D2)); // blue = running/default
		header.add(statusIndicator);

		panel.add(header, BorderLayout.NORTH);

		jobConsole=new javax.swing.JTextArea();
		jobConsole.setEditable(false);
		jobConsole.setLineWrap(true);
		jobConsole.setWrapStyleWord(true);
		jobConsole.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));

		JScrollPane sp=new JScrollPane(jobConsole);
		sp.setPreferredSize(new Dimension(360, 160));
		panel.add(sp, BorderLayout.CENTER);

		return panel;
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
		int div=sp.getDividerSize();
		int avail=(sp.getOrientation()==JSplitPane.VERTICAL_SPLIT)?Math.max(sp.getHeight()-div, 1):Math.max(sp.getWidth()-div, 1);
		return Math.max(0, Math.min(1, sp.getDividerLocation()/(double)avail));
	}

	private JPanel buildParamBar() {
		JPanel p=new JPanel(new BorderLayout());
		p.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

		// left: output type + threads
		JPanel left=new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

		// Output type
		outTypeBox=new JComboBox<>(OutputType.values());
		String savedType=prefs.get(PREF_OUT_TYPE, OutputType.mzml.name());
		try {
			outTypeBox.setSelectedItem(OutputType.valueOf(savedType));
		} catch (Exception ignore) {
		}
		outTypeBox.addActionListener(e -> prefs.put(PREF_OUT_TYPE, ((OutputType)outTypeBox.getSelectedItem()).name()));

		// Thread count (default = max(1, cores-1))
		int cores=Math.max(1, Runtime.getRuntime().availableProcessors()-1);
		int saved=Math.max(1, prefs.getInt(PREF_THREADS, cores));
		threadSpinner=new JSpinner(new SpinnerNumberModel(saved, 1, 512, 1));
		threadSpinner.addChangeListener(e -> {
			int n=(Integer)threadSpinner.getValue();
			prefs.putInt(PREF_THREADS, n);
			dispatcher.setDesiredParallelism(n); // live-resize without killing running tasks
		});

		left.add(new JLabel("Output:"));
		left.add(outTypeBox);
		left.add(new JLabel("Threads:"));
		left.add(threadSpinner);

		// initialize dispatcher with current spinner value
		dispatcher.setDesiredParallelism((Integer)threadSpinner.getValue());
		return left;
	}

	private void addSelectedToQueue() {
		int[] viewRows=contentTable.getSelectedRows();
		if (viewRows==null||viewRows.length==0) return;
		for (int vr : viewRows) {
			File f=tableModel.getFileAt(contentTable.convertRowIndexToModel(vr));
			addFileToQueue(f);
		}
	}

	private void addFileToQueue(File f) {
		if (f==null) return;
		OutputType outType=(OutputType)outTypeBox.getSelectedItem();
		boolean thermo=isThermo(f);
		boolean bruker=isDotD(f);
		if (!thermo&&!bruker) return;
		Path in=f.toPath();
		Path outDir=f.getParentFile().toPath();
		ConversionJob job=new ConversionJob(in, outDir, outType, thermo?Source.THERMO:Source.TIMS);
		queueModel.enqueue(job);
		dispatcher.maybeStart();
	}

	private void updateConsoleForSelection() {
		int row=queueTable.getSelectedRow();
		if (row<0) {
			jobConsole.setText("");
			statusIndicator.setForeground(new Color(0x757575)); // gray
			return;
		}
		ConversionJob j=queueModel.get(row);
		// header info at top of log
		StringBuilder sb=new StringBuilder();
		sb.append("Source: ").append(j.source==Source.THERMO?"Thermo .raw":"Bruker .d").append('\n');
		sb.append("Output: ").append(j.outType.name()).append('\n');
		sb.append("Path:   ").append(j.input.toString()).append("\n\n");
		sb.append(j.readLog());

		jobConsole.setText(sb.toString());
		jobConsole.setCaretPosition(jobConsole.getDocument().getLength());

		// indicator color
		if (j.state==JobState.DONE&&Boolean.TRUE.equals(j.success)) statusIndicator.setForeground(new Color(0x2e7d32)); // green
		else if (j.state==JobState.FAILED||(j.state==JobState.DONE&&Boolean.FALSE.equals(j.success))) statusIndicator.setForeground(new Color(0xc62828)); // red
		else if (j.state==JobState.CANCELED) statusIndicator.setForeground(new Color(0x757575)); // gray
		else statusIndicator.setForeground(new Color(0x1976D2)); // blue = running/queued
	}

	private void updateFile(Optional<File> maybe) {
		// Cancel any in-flight load
		SwingWorker<JComponent, String> prev=currentLoad;
		if (prev!=null) prev.cancel(true);

		// No file selected -> clear bottom
		if (maybe==null||maybe.isEmpty()) {
			double r=dividerProportion(fileSplit);
			fileSplit.setBottomComponent(new JPanel());
			fileSplit.setDividerLocation(r);
			currentLoad=null;
			return;
		}

		final File f=maybe.get();
		final long mySeq=loadSeq.incrementAndGet(); // detect stale workers

		final LoadingPanel[] loadingPanels=new LoadingPanel[] {new FTICRLoadingPanel("Reading "+f.getName()), new TOFLoadingPanel("Reading "+f.getName()),
				new QuadrupoleLoadingPanel("Reading "+f.getName())};
		final LoadingPanel loading=loadingPanels[(int)(Math.random()*(loadingPanels.length))];

		double r=dividerProportion(fileSplit);
		fileSplit.setBottomComponent(loading);
		fileSplit.setDividerLocation(r);

		currentLoad=new SwingWorker<>() {
			@Override
			protected JComponent doInBackground() throws Exception {
				// Heavy lifting OFF the EDT
				if (isDotD(f)) {
					BrukerTIMSFile raw=new BrukerTIMSFile();
					try {
						raw.openFile(f.toPath());
						Pair<float[], float[]> tic=raw.getTICTrace(); // may block / wait for native
						if (isCancelled()) return null;
						XYTrace trace=new XYTrace(MatrixMath.divide(tic.x, 60.0f), tic.y, GraphType.area,
								OutputType.changeExtension(raw.getOriginalFileName(), ""), null, null);
						return BasicChartGenerator.getChart("Time (min)", "Total Ion Current", false, trace);
					} finally {
						try {
							raw.close();
						} catch (Throwable ignore) {
						}
					}
				} else if (isThermo(f)) {
					ThermoRawFile raw=new ThermoRawFile();
					try {
						raw.openFile(f.toPath());
						Pair<float[], float[]> tic=raw.getTICTrace(); // may wait for gRPC launcher
						if (isCancelled()) return null;
						XYTrace trace=new XYTrace(MatrixMath.divide(tic.x, 60.0f), tic.y, GraphType.area,
								OutputType.changeExtension(raw.getOriginalFileName(), ""), null, null);
						return BasicChartGenerator.getChart("Time (min)", "Total Ion Current", false, trace);
					} finally {
						try {
							raw.close();
						} catch (Throwable ignore) {
						}
					}
				} else {
					// Not a supported file -> empty panel
					return new JPanel();
				}
			}

			@Override
			protected void done() {
				// Only apply if this is still the most recent request and not cancelled
				if (mySeq!=loadSeq.get()) return;
				loading.stop();
				try {
					if (isCancelled()) return;
					JComponent panel=get(); // may throw

					double r=dividerProportion(fileSplit);
					fileSplit.setBottomComponent(panel!=null?panel:new JPanel());
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
		boolean unixFlatten=treeModel.isUnixFlattenRoot();

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

		private static final String[] COLS= {"", "Name", "Size (bytes)", "Type", "Modified", "Queue"};

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
				case 5 -> Object.class;
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
				case 5 -> "Queue";
				default -> null;
			};
		}

		@Override
		public boolean isCellEditable(int r, int c) {
			if (c!=5) return false;
			File f=rows.get(r);
			// allow Thermo .raw files OR Bruker .d directories
			return (f.isFile()&&isThermo(f))||(f.isDirectory()&&isDotD(f));
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

	private enum Source {
		THERMO, TIMS
	}

	private enum JobState {
		QUEUED, RUNNING, DONE, FAILED, CANCELED
	}

	private static final class JobTableModel extends AbstractTableModel {
		private static final long serialVersionUID=1L;
		private final CopyOnWriteArrayList<ConversionJob> jobs=new CopyOnWriteArrayList<>();
		private static final String[] COLS= {"File", "Progress"};

		@Override
		public int getRowCount() {
			return jobs.size();
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
			return (c==1)?Float.class:String.class;
		}

		@Override
		public Object getValueAt(int r, int c) {
			ConversionJob j=jobs.get(r);
			return (c==0)?j.input.getFileName().toString():j.progress;
		}

		void enqueue(ConversionJob j) {
			jobs.add(j);
			int row=jobs.size()-1;
			fireTableRowsInserted(row, row);
		}

		void jobUpdated(ConversionJob j) {
			int idx=jobs.indexOf(j);
			if (idx>=0) SwingUtilities.invokeLater(() -> fireTableRowsUpdated(idx, idx));
		}

		ConversionJob pollNextQueued() {
			for (ConversionJob j : jobs)
				if (j.state==JobState.QUEUED) return j;
			return null;
		}

		void clearUnstartedOrCanceled() {
			boolean removed=jobs.removeIf(j -> j.state==JobState.QUEUED||j.state==JobState.CANCELED);
			if (removed) fireTableDataChanged();
		}

		void cancelAll() {
			for (ConversionJob j : jobs) {
				j.requestCancel();
				if (j.state==JobState.QUEUED) {
					j.state=JobState.CANCELED;
					jobUpdated(j);
				}
			}
		}

		ConversionJob get(int row) {
			return jobs.get(row);
		}

		void requeueFailedAndCanceled() {
			for (ConversionJob j : jobs) {
				if (j.state==JobState.CANCELED||j.state==JobState.FAILED) {
					j.cancelRequested=false;
					j.success=null;
					j.progress=0f;
					j.state=JobState.QUEUED;
					jobUpdated(j);
				}
			}
		}
	}

	private static final class ProgressRenderer implements TableCellRenderer {
		private final JobTableModel model;
		private final JProgressBar bar=new JProgressBar(0, 100);
		private static final Color GREEN=new Color(0x2e7d32);
		private static final Color RED=new Color(0xc62828);
		private static final Color GRAY=new Color(0x757575);

		ProgressRenderer(JobTableModel model) {
			this.model=model;
			bar.setStringPainted(true);
			bar.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
		}

		@Override
		public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			float f=(value instanceof Float)?(Float)value:0f;
			int pct=Math.max(0, Math.min(100, Math.round(f*100f)));
			bar.setValue(pct);

			// default color
			Color fg=UIManager.getColor("ProgressBar.foreground");

			// color by job result
			ConversionJob j=model.get(row);
			if (j.state==JobState.DONE) {
				fg=Boolean.TRUE.equals(j.success)?GREEN:RED;
			} else if (j.state==JobState.FAILED) {
				fg=RED;
			} else if (j.state==JobState.CANCELED) {
				fg=GRAY;
			} else {
				fg=UIManager.getColor("ProgressBar.foreground");
			}
			bar.setForeground(fg);

			if (isSelected) bar.setBackground(table.getSelectionBackground());
			else bar.setBackground(table.getBackground());

			return bar;
		}
	}

	private final class QueueDispatcher {
		private final JobTableModel model;
		private final ExecutorService exec;
		private final AtomicInteger running=new AtomicInteger(0);
		private volatile int desiredParallelism=1;
		private volatile boolean paused=false;

		QueueDispatcher(JobTableModel model, ExecutorService exec) {
			this.model=model;
			this.exec=exec;
		}

		void setDesiredParallelism(int n) {
			desiredParallelism=Math.max(1, n);
			maybeStart();
		}

		void pause(boolean p) {
			paused=p;
		}

		void maybeStart() {
			if (paused) return;
			while (running.get()<desiredParallelism) {
				ConversionJob next=model.pollNextQueued();
				if (next==null) break;
				if (!next.tryMarkRunning()) continue;
				running.incrementAndGet();
				model.jobUpdated(next);
				next.future=exec.submit(() -> {
					try {
						next.run();
					} finally {
						running.decrementAndGet();
						model.jobUpdated(next);
						maybeStart();
					}
				});
			}
		}
	}

	private final class ConversionJob implements Runnable, ProgressIndicator {
		final Path input;
		final Path outputDir;
		final OutputType outType;
		final Source source;

		volatile JobState state=JobState.QUEUED;
		final StringBuilder log=new StringBuilder();
		volatile String message="";
		volatile float progress=0f;
		volatile Boolean success=null; // null=not finished, true/false on finish
		volatile boolean cancelRequested=false;
		volatile Future<?> future;

		ConversionJob(Path input, Path outputDir, OutputType outType, Source source) {
			this.input=input;
			this.outputDir=outputDir;
			this.outType=outType;
			this.source=source;
		}

		String readLog() {
			synchronized (log) {
				return log.toString();
			}
		}

		boolean tryMarkRunning() {
			if (state!=JobState.QUEUED) return false;
			state=JobState.RUNNING;
			return true;
		}

		void requestCancel() {
			cancelRequested=true;
			if (future!=null) future.cancel(true); // best-effort interrupt
		}

		@Override
		public void run() {
			try {
				boolean ok;
				if (source==Source.THERMO) {
					ok=RawFileConverters.writeThermo(input, outputDir, outType, this);
				} else {
					ok=RawFileConverters.writeTims(input, outputDir, outType, this);
				}
				if (cancelRequested) {
					state=JobState.CANCELED;
					success=false;
					update("Canceled", 1f);
				} else if (ok) {
					state=JobState.DONE;
					success=true;
					update("Finished", 1f);
				} else {
					state=JobState.FAILED;
					success=false;
					update("Failed", 1f);
				}
			} catch (Throwable t) {
				state=cancelRequested?JobState.CANCELED:JobState.FAILED;
				success=false;
				update((cancelRequested?"Canceled":("Failed: "+t.getMessage())), 1f);
			} finally {
				queueModel.jobUpdated(this);
			}
		}

		// ---- ProgressIndicator ----
		@Override
		public void update(String msg) {
			update(msg, progress);
		}

		@Override
		public void update(String msg, float totalProgress) {
			message=(msg==null)?"":msg;
			float p=totalProgress;
			if (Float.isNaN(p)||Float.isInfinite(p)) p=0f;
			if (p>1.001f) p=p/100f;
			progress=Math.max(0f, Math.min(1f, p));

			if (message!=null&&!message.isEmpty()) {
				synchronized (log) {
					if (log.length()>0) log.append('\n');
					log.append(message);
				}
			}
			SwingUtilities.invokeLater(() -> {
				queueModel.jobUpdated(this);
				updateConsoleForSelection(); // live-refresh if this job is selected
			});
		}

		@Override
		public float getTotalProgress() {
			return progress;
		}

		@Override
		public boolean isCanceled() {
			return cancelRequested;
		}
	}

	// Renderer: borrow stripe colors/borders from the base renderer
	private static final class QueueButtonRenderer implements TableCellRenderer {
		private final JPanel cell=new JPanel(new GridBagLayout());
		private final JButton btn=new JButton("Queue");

		QueueButtonRenderer() {
			cell.setOpaque(true); // stripe background goes here
			btn.setOpaque(true);
			btn.setContentAreaFilled(true);
			btn.setFocusable(false);
			btn.setMargin(new Insets(0, 6, 0, 6));
			btn.setFont(btn.getFont().deriveFont(btn.getFont().getSize2D()-1f));
			btn.setBackground(BUTTON_COLOR_BACKGROUND);
			btn.setForeground(Color.black);
			btn.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
			btn.setPreferredSize(new Dimension(56, 18));

			GridBagConstraints gbc=new GridBagConstraints();
			gbc.gridx=0;
			gbc.gridy=0;
			gbc.weightx=1.0;
			gbc.weighty=1.0;
			gbc.anchor=GridBagConstraints.CENTER;
			gbc.fill=GridBagConstraints.NONE;
			cell.add(btn, gbc);
		}

		@Override
		public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

			JLabel stripe=(JLabel)org.searlelab.msrawjava.gui.filebrowser.StripeTableCellRenderer.BASE_RENDERER.getTableCellRendererComponent(table, "", false,
					false, row, column);

			cell.setBackground(stripe.getBackground());
			cell.setBorder(stripe.getBorder());
			btn.setForeground(table.getForeground());
			return cell;
		}
	}

	// Editor: same visual treatment, but with a clickable button
	private final class QueueButtonEditor extends AbstractCellEditor implements TableCellEditor, java.awt.event.ActionListener {
		private final JPanel cell=new JPanel(new GridBagLayout());
		private final JButton btn=new JButton("Queue");
		private int editingRow=-1;

		QueueButtonEditor() {
			cell.setOpaque(true);
			btn.setOpaque(true);
			btn.setContentAreaFilled(true);
			btn.setFocusable(false);
			btn.setMargin(new Insets(0, 6, 0, 6));
			btn.setFont(btn.getFont().deriveFont(btn.getFont().getSize2D()-1f));
			btn.setBackground(BUTTON_COLOR_BACKGROUND);
			btn.setForeground(Color.black);
			btn.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
			btn.setPreferredSize(new Dimension(56, 18));
			btn.addActionListener(this);

			GridBagConstraints gbc=new GridBagConstraints();
			gbc.gridx=0;
			gbc.gridy=0;
			gbc.weightx=1.0;
			gbc.weighty=1.0;
			gbc.anchor=GridBagConstraints.CENTER;
			gbc.fill=GridBagConstraints.NONE;
			cell.add(btn, gbc);
		}

		@Override
		public Object getCellEditorValue() {
			return "Queue";
		}

		@Override
		public java.awt.Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
			editingRow=row;

			JLabel stripe=(JLabel)org.searlelab.msrawjava.gui.filebrowser.StripeTableCellRenderer.BASE_RENDERER.getTableCellRendererComponent(table, "", false,
					false, row, column);

			cell.setBackground(stripe.getBackground());
			cell.setBorder(stripe.getBorder());
			btn.setForeground(table.getForeground());
			return cell;
		}

		@Override
		public void actionPerformed(java.awt.event.ActionEvent e) {
			stopCellEditing();
			int modelRow=contentTable.convertRowIndexToModel(editingRow);
			File f=tableModel.getFileAt(modelRow);
			if (f!=null&&((f.isFile()&&isThermo(f))||(f.isDirectory()&&isDotD(f)))) {
				addFileToQueue(f);
			}
		}
	}
}