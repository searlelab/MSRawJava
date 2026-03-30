package org.searlelab.msrawjava.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;

import org.searlelab.msrawjava.COREPreferences;
import org.searlelab.msrawjava.gui.filebrowser.StripeTableCellRenderer;
import org.searlelab.msrawjava.gui.utils.PathDisplayNames;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConverters;
import org.searlelab.msrawjava.io.VendorFile;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.mzml.MzmlFile;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.logging.ProgressIndicator;
import org.searlelab.msrawjava.model.PPMMassTolerance;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

/**
 * Owns the conversion parameter bar, queue, dispatcher and details console.
 * Exposes a "selected paths supplier" so the Queue button can grab whatever
 * the directory table currently has selected.
 */
/**
 * Owns conversion parameters, queueing, dispatch, and details output.
 */
public class ConversionPane extends JPanel {
	private static final long serialVersionUID=1L;

	// ---- prefs keys (kept local to the pane) ----
	private static final String PREF_OUT_TYPE="queue.outType";
	private static final String PREF_THREADS="queue.threads";
	private static final String PREF_DEMULTIPLEX="queue.demultiplex";

	// ---- UI constants ----
	private static final Color GREEN=new Color(0x2e7d32);
	private static final Color RED=new Color(0xc62828);
	private static final Color GRAY=new Color(0x757575);

	// ---- collaborators ----
	private final Preferences prefs;
	private final ProcessingThreadPool pool;

	// ---- supplier of currently selected paths in the directory table (set by RawFileBrowser) ----
	private Supplier<List<Path>> selectedPathsSupplier=List::of;

	// ---- parameter bar widgets ----
	private final JComboBox<OutputType> outTypeBox=new JComboBox<>(OutputType.values());
	private final JCheckBox demultiplexBox=new JCheckBox("Demux");
	private JSpinner threadSpinner;
	private boolean lastDemuxSelection=false;
	private boolean suppressDemuxChange=false;

	// ---- queue UI ----
	private final JobTableModel queueModel=new JobTableModel();
	private final JTable queueTable=new JTable(queueModel);

	// ---- details console ----
	private final JTextArea jobConsole=new JTextArea();
	private final JLabel statusIndicator=new JLabel("●");

	// ---- dispatcher / execution ----
	private final ExecutorService executor=Executors.newCachedThreadPool();
	private final QueueDispatcher dispatcher=new QueueDispatcher(queueModel, executor);

	private JSplitPane queueAndConsole;

	public ConversionPane(Preferences prefs, ProcessingThreadPool pool) {
		super(new BorderLayout());
		this.prefs=Objects.requireNonNull(prefs, "prefs");
		this.pool=pool;

		// ---- param bar ----
		JPanel params=buildParamBar();

		// ---- queue table ----
		queueTable.setRowHeight(22);
		queueTable.setFillsViewportHeight(true);
		queueTable.setDefaultRenderer(Float.class, new ProgressRenderer(queueModel));
		queueTable.setDefaultRenderer(String.class, StripeTableCellRenderer.BASE_RENDERER);
		queueTable.setDefaultRenderer(Object.class, StripeTableCellRenderer.BASE_RENDERER);
		queueTable.getColumnModel().getColumn(0).setPreferredWidth(280); // File
		queueTable.getColumnModel().getColumn(1).setPreferredWidth(120); // Progress
		installQueueHeaderTooltips();
		queueTable.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) updateConsoleForSelection();
		});
		JScrollPane queueScroll=new JScrollPane(queueTable);
		queueScroll.setPreferredSize(new Dimension(360, 200));

		// ---- console ----
		JPanel console=buildConsolePanel();

		// ---- queue toolbar ----
		JPanel tools=buildQueueToolbar();

		// ---- pack top controls (params above toolbar) ----
		JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		controls.add(tools);
		controls.add(params);

		// ---- lower split (queue + console) ----
		queueAndConsole=new JSplitPane(JSplitPane.VERTICAL_SPLIT, queueScroll, console);
		queueAndConsole.setResizeWeight(GUIPreferences.getConversionPaneSplitRatio());
		queueAndConsole.setContinuousLayout(true);
		queueAndConsole.setOneTouchExpandable(true);
		registerSplitPreference(queueAndConsole, GUIPreferences::setConversionPaneSplitRatio);

		add(controls, BorderLayout.NORTH);
		add(queueAndConsole, BorderLayout.CENTER);
	}

	public void applySavedSplitRatio() {
		applySplitRatio(queueAndConsole, GUIPreferences.getConversionPaneSplitRatio());
	}

	// Exposed to RawFileBrowser
	public void setSelectedPathsSupplier(Supplier<List<Path>> supplier) {
		this.selectedPathsSupplier=(supplier!=null)?supplier:List::of;
	}

	public void updateDemuxAvailability(List<Path> paths) {
		boolean hasBruker=false;
		if (paths!=null) {
			for (Path path : paths) {
				if (path==null) continue;
				if (VendorFile.BRUKER.matchesPath(path)) {
					hasBruker=true;
					break;
				}
			}
		}
		if (hasBruker) {
			if (demultiplexBox.isEnabled()) {
				lastDemuxSelection=demultiplexBox.isSelected();
			}
			suppressDemuxChange=true;
			try {
				demultiplexBox.setSelected(false);
				demultiplexBox.setEnabled(false);
			} finally {
				suppressDemuxChange=false;
			}
		} else {
			suppressDemuxChange=true;
			try {
				demultiplexBox.setEnabled(true);
				demultiplexBox.setSelected(lastDemuxSelection);
			} finally {
				suppressDemuxChange=false;
			}
		}
	}

	public void shutdown() {
		executor.shutdownNow();
	}

	private JPanel buildParamBar() {
		JPanel bar=new JPanel(new BorderLayout());
		bar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

		JPanel left=new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

		String savedType=prefs.get(PREF_OUT_TYPE, OutputType.mzML.name());
		try {
			outTypeBox.setSelectedItem(OutputType.valueOf(savedType));
		} catch (Exception ignore) {
			Logger.errorException(ignore);
		}
		outTypeBox.addActionListener(e -> {
			OutputType sel=(OutputType)outTypeBox.getSelectedItem();
			if (sel!=null) prefs.put(PREF_OUT_TYPE, sel.name());
		});
		outTypeBox.setToolTipText("Select the output file format used for queued conversions.");

		boolean savedDemux=prefs.getBoolean(PREF_DEMULTIPLEX, false);
		demultiplexBox.setSelected(savedDemux);
		demultiplexBox.setToolTipText("Enable staggered-window demultiplexing for supported files.");
		lastDemuxSelection=savedDemux;
		demultiplexBox.addActionListener(e -> {
			if (suppressDemuxChange) return;
			lastDemuxSelection=demultiplexBox.isSelected();
			prefs.putBoolean(PREF_DEMULTIPLEX, lastDemuxSelection);
		});

		int defaultThreads=Math.max(1, Runtime.getRuntime().availableProcessors()-1);
		int savedThreads=Math.max(1, prefs.getInt(PREF_THREADS, defaultThreads));
		threadSpinner=new JSpinner(new SpinnerNumberModel(savedThreads, 1, 512, 1));
		threadSpinner.setToolTipText("Set how many files can be converted in parallel.");
		threadSpinner.addChangeListener(e -> {
			int n=(Integer)threadSpinner.getValue();
			prefs.putInt(PREF_THREADS, n);
			dispatcher.setDesiredParallelism(n);
		});

		left.add(new JLabel("Output:"));
		left.add(outTypeBox);
		left.add(demultiplexBox);
		left.add(new JLabel("Threads:"));
		left.add(threadSpinner);

		// init dispatcher
		dispatcher.setDesiredParallelism((Integer)threadSpinner.getValue());
		bar.add(left, BorderLayout.WEST);
		return bar;
	}

	private JPanel buildQueueToolbar() {
		JButton queueSelected=new JButton("Queue Selected");
		wireButton(queueSelected);
		queueSelected.setToolTipText("Add any selected files to the processing queue using the output settings.");
		queueSelected.addActionListener(e -> enqueueFromSupplier());

		JButton cancelAll=new JButton("Cancel");
		wireButton(cancelAll);
		cancelAll.setToolTipText("Cancel queued and running conversion jobs.");
		cancelAll.addActionListener(e -> {
			dispatcher.pause(true);
			queueModel.cancelAll();
		});

		JButton restart=new JButton("Restart");
		wireButton(restart);
		restart.setToolTipText("Requeue failed or canceled jobs and resume processing.");
		restart.addActionListener(e -> {
			queueModel.requeueFailedAndCanceled();
			dispatcher.pause(false);
			dispatcher.maybeStart();
		});

		JButton clear=new JButton("Clear");
		wireButton(clear);
		clear.setToolTipText("Remove jobs that have not started or were canceled.");
		clear.addActionListener(e -> queueModel.clearUnstartedOrCanceled());

		JPanel tools=new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		tools.add(new JLabel("<html><b>Queue:"));
		tools.add(queueSelected);
		tools.add(cancelAll);
		tools.add(restart);
		tools.add(clear);
		return tools;
	}

	private void installQueueHeaderTooltips() {
		JTableHeader header=new JTableHeader(queueTable.getColumnModel()) {
			private static final long serialVersionUID=1L;

			@Override
			public String getToolTipText(MouseEvent event) {
				int viewColumn=columnAtPoint(event.getPoint());
				if (viewColumn<0) return null;
				int modelColumn=queueTable.convertColumnIndexToModel(viewColumn);
				return getQueueHeaderTooltip(modelColumn);
			}
		};
		header.setToolTipText("Hover a column header to see what it means.");
		queueTable.setTableHeader(header);
	}

	private String getQueueHeaderTooltip(int modelColumn) {
		return switch (modelColumn) {
			case 0 -> "The queued source file name.";
			case 1 -> "Conversion progress for this queued job.";
			default -> null;
		};
	}

	private JPanel buildConsolePanel() {
		JPanel panel=new JPanel(new BorderLayout());
		JPanel header=new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		String detailsTooltip="Shows progress details on the selected job in the queue.";
		JLabel detailsLabel=new JLabel("Details");
		detailsLabel.setToolTipText(detailsTooltip);
		header.add(detailsLabel);

		statusIndicator.setOpaque(false);
		statusIndicator.setForeground(new Color(0x1976D2)); // blue = running/default
		statusIndicator.setToolTipText(detailsTooltip);
		header.add(statusIndicator);

		panel.add(header, BorderLayout.NORTH);

		jobConsole.setEditable(false);
		jobConsole.setLineWrap(true);
		jobConsole.setWrapStyleWord(true);
		jobConsole.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
		JScrollPane sp=new JScrollPane(jobConsole);
		sp.setPreferredSize(new Dimension(360, 140));
		panel.add(sp, BorderLayout.CENTER);
		return panel;
	}

	private static void wireButton(JButton b) {
		b.setOpaque(true);
		b.setContentAreaFilled(true);
		b.setFocusPainted(false);
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
			if (!dragging.get()) {
				Object event=EventQueue.getCurrentEvent();
				if (!(event instanceof MouseEvent)) return;
			}
			double ratio=getSplitRatio(pane);
			if (ratio>0.0) saver.accept(ratio);
		});
	}

	private void installDividerDragListener(JSplitPane pane, AtomicBoolean dragging, DoubleConsumer saver) {
		if (pane.getClientProperty("conversionPane.dividerListener")!=null) return;
		pane.putClientProperty("conversionPane.dividerListener", Boolean.TRUE);
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

	// ---------- actions ----------

	private void enqueueFromSupplier() {
		List<Path> paths=(selectedPathsSupplier!=null)?selectedPathsSupplier.get():List.of();
		if (paths==null||paths.isEmpty()) return;

		OutputType outType=(OutputType)outTypeBox.getSelectedItem();
		for (Path p : paths) {
			VendorFile vendor=VendorFile.fromPath(p).orElse(null);
			if (vendor==null) continue;

			Path outDir=(p.getParent()!=null)?p.getParent():p;
			boolean demux=demultiplexBox.isSelected();
			ConversionJob job=new ConversionJob(p, outDir, outType, demux, vendor);
			queueModel.enqueue(job);
		}
		dispatcher.maybeStart();
	}

	private void updateConsoleForSelection() {
		int row=queueTable.getSelectedRow();
		if (row<0) {
			jobConsole.setText("");
			statusIndicator.setForeground(GRAY);
			return;
		}
		ConversionJob j=queueModel.get(row);
		StringBuilder sb=new StringBuilder();
		sb.append("Source: ").append(j.vendor.getDisplayName()).append('\n');
		sb.append("Output: ").append(j.outType.name()).append('\n');
		sb.append("Path:   ").append(j.input.toString()).append("\n\n");
		sb.append(j.readLog());

		jobConsole.setText(sb.toString());
		jobConsole.setCaretPosition(jobConsole.getDocument().getLength());

		if (j.state==JobState.DONE&&Boolean.TRUE.equals(j.success)) statusIndicator.setForeground(GREEN);
		else if (j.state==JobState.FAILED||(j.state==JobState.DONE&&Boolean.FALSE.equals(j.success))) statusIndicator.setForeground(RED);
		else if (j.state==JobState.CANCELED) statusIndicator.setForeground(GRAY);
		else statusIndicator.setForeground(new Color(0x1976D2)); // blue
	}

	// ---------- table models & renderers ----------

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
			return (c==0)?PathDisplayNames.displayNameFor(j.input):j.progress;
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

	private static final class ProgressRenderer extends StripeTableCellRenderer {
		private static final long serialVersionUID=1L;

		private final JobTableModel model;
		private final JProgressBar bar=new JProgressBar(0, 100);

		private static final Color GREEN=new Color(0x2e7d32);
		private static final Color RED=new Color(0xc62828);
		private static final Color GRAY=new Color(0x757575);

		ProgressRenderer(JobTableModel model) {
			this.model=model;
			setOpaque(true);

			bar.setStringPainted(true);
			bar.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
			bar.setOpaque(false);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);

			float f=(value instanceof Float)?(Float)value:0f;
			if (Float.isNaN(f)||Float.isInfinite(f)) f=0f;
			int pct=Math.max(0, Math.min(100, Math.round(f*100f)));
			bar.setValue(pct);
			bar.setString(pct+"%");

			bar.setBackground(getBackground());

			int modelRow=(table.getRowSorter()!=null)?table.convertRowIndexToModel(row):row;
			ConversionJob j=model.get(modelRow);
			Color fg;
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

			return this; // return the striped renderer itself
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);

			Insets ins=getInsets();
			int x=ins.left+2;
			int y=ins.top+2;
			int w=Math.max(0, getWidth()-ins.left-ins.right-4);
			int h=Math.max(0, getHeight()-ins.top-ins.bottom-4);

			SwingUtilities.paintComponent(g, bar, this, x, y, w, h);
		}
	}

	// ---------- dispatcher & jobs ----------

	private enum JobState {
		QUEUED, RUNNING, DONE, FAILED, CANCELED
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
		final boolean demultiplex;
		final VendorFile vendor;

		volatile JobState state=JobState.QUEUED;
		final StringBuilder log=new StringBuilder();
		volatile String message="";
		volatile float progress=0f;
		volatile Boolean success=null; // null=not finished, true/false on finish
		volatile boolean cancelRequested=false;
		volatile Future<?> future;

		ConversionJob(Path input, Path outputDir, OutputType outType, boolean demultiplex, VendorFile vendor) {
			this.input=input;
			this.outputDir=outputDir;
			this.outType=outType;
			this.demultiplex=demultiplex;
			this.vendor=vendor;
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
			if (future!=null) future.cancel(true);
		}

		@Override
		public void run() {
			try {
				boolean ok;
				ConversionParameters.Builder builder=ConversionParameters.builder().outType(outType).outputDirPath(outputDir).demultiplex(demultiplex)
						.demuxTolerance(new PPMMassTolerance(COREPreferences.getDemuxTolerancePpm()))
						.minimumMS1Intensity(COREPreferences.getMinimumMS1Intensity()).minimumMS2Intensity(COREPreferences.getMinimumMS2Intensity());
				builder.outputFilePathOverride(resolveOutputOverride(vendor, input, outputDir, outType, demultiplex));
				ConversionParameters params=builder.build();
				if (vendor==VendorFile.THERMO) {
					ThermoRawFile rawFile=new ThermoRawFile();
					rawFile.openFile(input);
					if (demultiplex) {
						ok=RawFileConverters.writeDemux(pool, rawFile, outputDir, params, this);
					} else {
						ok=RawFileConverters.writeStandard(pool, rawFile, outputDir, params, this);
					}
				} else if (vendor==VendorFile.ENCYCLOPEDIA) {
					EncyclopeDIAFile dia=new EncyclopeDIAFile();
					dia.openFile(input.toFile());
					if (demultiplex) {
						ok=RawFileConverters.writeDemux(pool, dia, outputDir, params, this);
					} else {
						ok=RawFileConverters.writeStandard(pool, dia, outputDir, params, this);
					}
				} else if (vendor==VendorFile.MZML) {
					MzmlFile mzml=new MzmlFile();
					mzml.openFile(input.toFile());
					if (demultiplex) {
						ok=RawFileConverters.writeDemux(pool, mzml, outputDir, params, this);
					} else {
						ok=RawFileConverters.writeStandard(pool, mzml, outputDir, params, this);
					}
				} else {
					if (demultiplex) {
						Logger.errorLine("Sorry, staggered demultiplexing is not available for "+VendorFile.BRUKER.getDisplayName()+" files");
					}
					ok=RawFileConverters.writeTims(pool, input, outputDir, params, this);
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
				Logger.errorException(t);
				state=cancelRequested?JobState.CANCELED:JobState.FAILED;
				success=false;
				update((cancelRequested?"Canceled":("Failed: "+t.getMessage())), 1f);
			} finally {
				queueModel.jobUpdated(this);
			}
		}

		private java.nio.file.Path resolveOutputOverride(VendorFile source, Path input, Path outputDir, OutputType outType, boolean demultiplex) {
			String name=PathDisplayNames.displayNameFor(input);
			boolean isDiaInput=VendorFile.ENCYCLOPEDIA.matchesName(name);
			boolean isMzmlInput=VendorFile.MZML.matchesName(name);
			if (demultiplex&&(source==VendorFile.THERMO||source==VendorFile.ENCYCLOPEDIA||source==VendorFile.MZML)) {
				String base=stripExtension(name);
				String suffix;
				if (outType==OutputType.EncyclopeDIA) {
					suffix=".demux"+org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile.DIA_EXTENSION;
				} else if (outType==OutputType.mzML) {
					suffix=".demux"+org.searlelab.msrawjava.io.mzml.MzmlConstants.MZML_EXTENSION;
				} else if (outType==OutputType.mgf) {
					suffix=".demux"+org.searlelab.msrawjava.io.MGFOutputFile.MGF_EXTENSION;
				} else {
					suffix=null;
				}
				return (suffix==null)?null:outputDir.resolve(base+suffix);
			}
			if (source==VendorFile.ENCYCLOPEDIA&&outType==OutputType.EncyclopeDIA&&isDiaInput) {
				String base=name.substring(0, name.length()-4);
				return outputDir.resolve(base+".2"+org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile.DIA_EXTENSION);
			}
			if (source==VendorFile.MZML&&outType==OutputType.mzML&&isMzmlInput) {
				String base=name.substring(0, name.length()-5);
				return outputDir.resolve(base+".2"+org.searlelab.msrawjava.io.mzml.MzmlConstants.MZML_EXTENSION);
			}
			return null;
		}

		private String stripExtension(String name) {
			int idx=name.lastIndexOf('.');
			return (idx>0)?name.substring(0, idx):name;
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

			if (!message.isEmpty()) {
				synchronized (log) {
					if (log.length()>0) log.append('\n');
					log.append(message);
				}
			}

			SwingUtilities.invokeLater(() -> {
				queueModel.jobUpdated(this);
				updateConsoleForSelection();
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
}
