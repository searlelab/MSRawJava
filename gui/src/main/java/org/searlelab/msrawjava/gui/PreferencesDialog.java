package org.searlelab.msrawjava.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

import org.searlelab.msrawjava.COREPreferences;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

/**
 * Dialog for editing GUI preferences.
 */
public class PreferencesDialog extends JDialog {
	private static final long serialVersionUID=1L;

	private final JTextField demuxToleranceField=new JTextField(8);
	private final JTextField minMs1Field=new JTextField(8);
	private final JTextField minMs2Field=new JTextField(8);
	private final JCheckBox verboseCoreBox=new JCheckBox("Enable verbose logging");
	private final JComboBox<ProcessingThreadOption> processingThreadsBox=new JComboBox<>();
	private final JCheckBox askProcessingStartupBox=new JCheckBox("Ask on startup");
	private final JLabel processingRestartLabel=new JLabel("Restart required for thread changes.");

	private final JTextField lastDirField=new JTextField(28);
	private final JComboBox<LookAndFeelOption> lookAndFeelBox=new JComboBox<>();
	private File lastDirSelection=null;

	private boolean resetWindows=false;
	private boolean resetSplits=false;
	private boolean resetTables=false;
	private String lookAndFeelSelection=null;

	public static void showDialog(Frame owner) {
		PreferencesDialog dlg=new PreferencesDialog(owner);
		dlg.setVisible(true);
	}

	public PreferencesDialog(Frame owner) {
		super(owner, "Preferences", true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		buildUi();
		pack();
		setLocationRelativeTo(owner);
	}

	private void buildUi() {
		JPanel content=new JPanel(new BorderLayout());
		content.setBorder(BorderFactory.createTitledBorder("Preferences:"));
		content.setToolTipText("Configure conversion and GUI defaults for MSRawJava.");

		JTabbedPane tabs=new JTabbedPane();
		tabs.addTab("Processing", buildProcessingTab());
		tabs.addTab("Conversion", buildConversionTab());
		tabs.addTab("GUI", buildGuiTab());
		tabs.setToolTipTextAt(0, "Configure processing threads and core logging.");
		tabs.setToolTipTextAt(1, "Configure default conversion thresholds.");
		tabs.setToolTipTextAt(2, "Configure GUI behavior, appearance, and saved layout resets.");
		content.add(tabs, BorderLayout.CENTER);

		JPanel buttons=new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
		JButton ok=new JButton("OK");
		JButton cancel=new JButton("Cancel");
		ok.setToolTipText("Save all preference changes and close this dialog.");
		cancel.setToolTipText("Close this dialog without applying new changes.");
		ok.addActionListener(e -> onOk());
		cancel.addActionListener(e -> dispose());
		buttons.add(ok);
		buttons.add(cancel);
		content.add(buttons, BorderLayout.SOUTH);

		setContentPane(content);
	}

	private JPanel buildProcessingTab() {
		JPanel panel=new JPanel(new GridBagLayout());
		panel.setToolTipText("Processing defaults used by the GUI.");
		GridBagConstraints gbc=new GridBagConstraints();
		gbc.insets=new Insets(6, 6, 6, 6);
		gbc.anchor=GridBagConstraints.WEST;

		loadProcessingThreadOptions();
		processingThreadsBox.setToolTipText("Throttle processing worker threads, or use Max for the CPU-based default.");
		processingRestartLabel.setForeground(new Color(0xc62828));
		processingRestartLabel.setVisible(false);
		processingThreadsBox.addActionListener(e -> {
			askProcessingStartupBox.setSelected(false);
			processingRestartLabel.setVisible(true);
		});
		askProcessingStartupBox.setSelected(COREPreferences.isAskProcessingOnStartup());
		askProcessingStartupBox.setToolTipText("Ask whether this is an instrument computer when the GUI starts.");
		verboseCoreBox.setSelected(COREPreferences.isVerboseCoreLogging());
		verboseCoreBox.setToolTipText("Enable detailed logging from core conversion code.");

		JLabel threadsLabel=new JLabel("Processing threads:");
		threadsLabel.setToolTipText(processingThreadsBox.getToolTipText());
		threadsLabel.setLabelFor(processingThreadsBox);

		gbc.gridx=0;
		gbc.gridy=0;
		panel.add(threadsLabel, gbc);
		gbc.gridx=1;
		panel.add(processingThreadsBox, gbc);

		gbc.gridx=0;
		gbc.gridy=1;
		gbc.gridwidth=2;
		panel.add(askProcessingStartupBox, gbc);
		gbc.gridy=2;
		panel.add(verboseCoreBox, gbc);
		gbc.gridy=3;
		panel.add(processingRestartLabel, gbc);

		return panel;
	}

	private JPanel buildConversionTab() {
		JPanel panel=new JPanel(new GridBagLayout());
		panel.setToolTipText("Conversion defaults used for queued processing jobs.");
		GridBagConstraints gbc=new GridBagConstraints();
		gbc.insets=new Insets(6, 6, 6, 6);
		gbc.anchor=GridBagConstraints.WEST;

		String demuxTip="Set the demultiplexing mass tolerance in ppm.";
		String minMs1Tip="Set the minimum MS1 intensity threshold.";
		String minMs2Tip="Set the minimum MS2 intensity threshold.";
		demuxToleranceField.setText(Double.toString(COREPreferences.getDemuxTolerancePpm()));
		demuxToleranceField.setToolTipText(demuxTip);
		minMs1Field.setText(Float.toString(COREPreferences.getMinimumMS1Intensity()));
		minMs1Field.setToolTipText(minMs1Tip);
		minMs2Field.setText(Float.toString(COREPreferences.getMinimumMS2Intensity()));
		minMs2Field.setToolTipText(minMs2Tip);

		JLabel demuxLabel=new JLabel("Demux tolerance (ppm):");
		demuxLabel.setToolTipText(demuxTip);
		demuxLabel.setLabelFor(demuxToleranceField);

		gbc.gridx=0;
		gbc.gridy=0;
		panel.add(demuxLabel, gbc);
		gbc.gridx=1;
		panel.add(demuxToleranceField, gbc);

		JLabel minMs1Label=new JLabel("Minimum MS1 intensity:");
		minMs1Label.setToolTipText(minMs1Tip);
		minMs1Label.setLabelFor(minMs1Field);

		gbc.gridx=0;
		gbc.gridy=1;
		panel.add(minMs1Label, gbc);
		gbc.gridx=1;
		panel.add(minMs1Field, gbc);

		JLabel minMs2Label=new JLabel("Minimum MS2 intensity:");
		minMs2Label.setToolTipText(minMs2Tip);
		minMs2Label.setLabelFor(minMs2Field);

		gbc.gridx=0;
		gbc.gridy=2;
		panel.add(minMs2Label, gbc);
		gbc.gridx=1;
		panel.add(minMs2Field, gbc);

		return panel;
	}

	private void loadProcessingThreadOptions() {
		processingThreadsBox.removeAllItems();
		Integer current=COREPreferences.getProcessingThreadLimit();
		int defaultThreads=ProcessingThreadPool.defaultThreadCount();
		processingThreadsBox.addItem(new ProcessingThreadOption(1, "1"));
		if (defaultThreads>2) processingThreadsBox.addItem(new ProcessingThreadOption(2, "2"));
		if (defaultThreads>4) processingThreadsBox.addItem(new ProcessingThreadOption(4, "4"));
		processingThreadsBox.addItem(new ProcessingThreadOption(null, "Max"));
		for (int i=0; i<processingThreadsBox.getItemCount(); i++) {
			ProcessingThreadOption option=processingThreadsBox.getItemAt(i);
			if ((current==null&&option.value==null)||(current!=null&&current.equals(option.value))) {
				processingThreadsBox.setSelectedIndex(i);
				break;
			}
		}
	}

	private JPanel buildGuiTab() {
		JPanel panel=new JPanel(new GridBagLayout());
		panel.setToolTipText("GUI defaults for startup behavior, appearance, and saved layouts.");
		GridBagConstraints gbc=new GridBagConstraints();
		gbc.insets=new Insets(6, 6, 6, 6);
		gbc.anchor=GridBagConstraints.WEST;
		gbc.fill=GridBagConstraints.HORIZONTAL;
		gbc.weightx=1.0;

		String lastDirTip="Shows the default directory opened by the browser.";
		String lookAndFeelTip="Select the application look and feel theme.";
		lastDirField.setEditable(false);
		lastDirField.setToolTipText(lastDirTip);
		String lastDir=GUIPreferences.getLastDirectory();
		if (lastDir!=null) lastDirField.setText(lastDir);

		loadLookAndFeelOptions();
		lookAndFeelBox.setToolTipText(lookAndFeelTip);

		JButton browse=new JButton("Browse...");
		browse.setToolTipText("Choose the default directory shown when the app starts.");
		browse.addActionListener(e -> chooseLastDir());

		JLabel lastDirLabel=new JLabel("Last directory:");
		lastDirLabel.setToolTipText(lastDirTip);
		lastDirLabel.setLabelFor(lastDirField);

		gbc.gridx=0;
		gbc.gridy=0;
		panel.add(lastDirLabel, gbc);
		gbc.gridy=1;
		panel.add(lastDirField, gbc);
		gbc.gridx=1;
		gbc.gridy=1;
		gbc.weightx=0.0;
		panel.add(browse, gbc);
		gbc.weightx=1.0;

		JLabel lookAndFeelLabel=new JLabel("Look and feel:");
		lookAndFeelLabel.setToolTipText(lookAndFeelTip);
		lookAndFeelLabel.setLabelFor(lookAndFeelBox);

		gbc.gridx=0;
		gbc.gridy=2;
		gbc.gridwidth=1;
		panel.add(lookAndFeelLabel, gbc);
		gbc.gridx=1;
		panel.add(lookAndFeelBox, gbc);

		JButton resetWindowsButton=new JButton("Reset Window Location and Dimensions");
		resetWindowsButton.setToolTipText("Reset saved window size and position values.");
		resetWindowsButton.addActionListener(e -> {
			resetWindows=true;
			resetWindowsButton.setEnabled(false);
			resetWindowsButton.setText("Will reset on OK");
		});

		JButton resetSplitsButton=new JButton("Reset Split Pane Dimensions");
		resetSplitsButton.setToolTipText("Reset all saved split-pane divider positions.");
		resetSplitsButton.addActionListener(e -> {
			resetSplits=true;
			resetSplitsButton.setEnabled(false);
			resetSplitsButton.setText("Will reset on OK");
		});

		JButton resetTablesButton=new JButton("Reset Table Parameters");
		resetTablesButton.setToolTipText("Reset saved table sorting, order, and column widths.");
		resetTablesButton.addActionListener(e -> {
			resetTables=true;
			resetTablesButton.setEnabled(false);
			resetTablesButton.setText("Will reset on OK");
		});

		gbc.gridx=0;
		gbc.gridy=3;
		gbc.gridwidth=2;
		panel.add(resetWindowsButton, gbc);
		gbc.gridy=4;
		panel.add(resetSplitsButton, gbc);
		gbc.gridy=5;
		panel.add(resetTablesButton, gbc);

		return panel;
	}

	private void loadLookAndFeelOptions() {
		lookAndFeelBox.removeAllItems();
		String osName=System.getProperty("os.name", "").toLowerCase();
		String systemLabel="System";
		if (osName.contains("mac")) {
			systemLabel="System (macOS)";
		} else if (osName.contains("win")) {
			systemLabel="System (Windows)";
		}
		lookAndFeelBox.addItem(new LookAndFeelOption(LookAndFeelManager.LAF_FLAT_LIGHT, "Flat Light"));
		lookAndFeelBox.addItem(new LookAndFeelOption(LookAndFeelManager.LAF_FLAT_DARK, "Flat Dark"));
		lookAndFeelBox.addItem(new LookAndFeelOption(LookAndFeelManager.LAF_SYSTEM, systemLabel));

		String current=GUIPreferences.getLookAndFeelId(LookAndFeelManager.LAF_FLAT_LIGHT);
		for (int i=0; i<lookAndFeelBox.getItemCount(); i++) {
			LookAndFeelOption option=lookAndFeelBox.getItemAt(i);
			if (option.id.equals(current)) {
				lookAndFeelBox.setSelectedIndex(i);
				break;
			}
		}
		lookAndFeelSelection=current;
	}

	private void chooseLastDir() {
		String previous=System.getProperty("apple.awt.fileDialogForDirectories");
		System.setProperty("apple.awt.fileDialogForDirectories", "true");
		try {
			Frame owner=(getOwner() instanceof Frame)?(Frame)getOwner():null;
			FileDialog dialog=new FileDialog(owner, "Select last directory", FileDialog.LOAD);
			dialog.setVisible(true);
			File[] files=dialog.getFiles();
			if (files!=null&&files.length>0) {
				lastDirSelection=files[0];
			} else if (dialog.getDirectory()!=null&&dialog.getFile()!=null) {
				lastDirSelection=new File(dialog.getDirectory(), dialog.getFile());
			}
			if (lastDirSelection!=null) {
				lastDirField.setText(lastDirSelection.getAbsolutePath());
			}
		} finally {
			if (previous==null) {
				System.clearProperty("apple.awt.fileDialogForDirectories");
			} else {
				System.setProperty("apple.awt.fileDialogForDirectories", previous);
			}
		}
	}

	private void onOk() {
		try {
			double demuxPpm=Double.parseDouble(demuxToleranceField.getText().trim());
			float minMs1=Float.parseFloat(minMs1Field.getText().trim());
			float minMs2=Float.parseFloat(minMs2Field.getText().trim());

			COREPreferences.setDemuxTolerancePpm(demuxPpm);
			COREPreferences.setMinimumMS1Intensity(minMs1);
			COREPreferences.setMinimumMS2Intensity(minMs2);
			COREPreferences.setVerboseCoreLogging(verboseCoreBox.isSelected());
			ProcessingThreadOption processingThreads=(ProcessingThreadOption)processingThreadsBox.getSelectedItem();
			COREPreferences.setProcessingThreadLimit(processingThreads==null?null:processingThreads.value);
			COREPreferences.setAskProcessingOnStartup(askProcessingStartupBox.isSelected());

			if (lastDirSelection!=null) {
				GUIPreferences.rememberLastDirectory(lastDirSelection);
			}
			if (resetWindows) {
				GUIPreferences.resetWindowPreferences();
			}
			if (resetSplits) {
				GUIPreferences.resetSplitPanePreferences();
			}
			if (resetTables) {
				GUIPreferences.resetTablePreferences();
			}
			LookAndFeelOption selected=(LookAndFeelOption)lookAndFeelBox.getSelectedItem();
			String lafId=(selected==null)?LookAndFeelManager.LAF_FLAT_LIGHT:selected.id;
			if (!lafId.equals(lookAndFeelSelection)) {
				GUIPreferences.setLookAndFeelId(lafId);
				LookAndFeelManager.applyLookAndFeel(lafId);
				lookAndFeelSelection=lafId;
			}

			dispose();
		} catch (NumberFormatException ex) {
			JOptionPane.showMessageDialog(this, "Please enter valid numeric values.", "Invalid input", JOptionPane.ERROR_MESSAGE);
		}
	}

	private static final class LookAndFeelOption {
		private final String id;
		private final String label;

		private LookAndFeelOption(String id, String label) {
			this.id=id;
			this.label=label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private static final class ProcessingThreadOption {
		private final Integer value;
		private final String label;

		private ProcessingThreadOption(Integer value, String label) {
			this.value=value;
			this.label=label;
		}

		@Override
		public String toString() {
			return label;
		}
	}
}
