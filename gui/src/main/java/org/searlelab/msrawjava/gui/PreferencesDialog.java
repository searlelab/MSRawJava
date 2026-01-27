package org.searlelab.msrawjava.gui;

import java.awt.BorderLayout;
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

public class PreferencesDialog extends JDialog {
	private static final long serialVersionUID=1L;

	private final JTextField demuxToleranceField=new JTextField(8);
	private final JTextField minMs1Field=new JTextField(8);
	private final JTextField minMs2Field=new JTextField(8);
	private final JCheckBox verboseCoreBox=new JCheckBox("Enable verbose logging");

	private final JCheckBox verboseGuiBox=new JCheckBox("Enable verbose logging");
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

		JTabbedPane tabs=new JTabbedPane();
		tabs.addTab("Conversion", buildConversionTab());
		tabs.addTab("GUI", buildGuiTab());
		content.add(tabs, BorderLayout.CENTER);

		JPanel buttons=new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
		JButton ok=new JButton("OK");
		JButton cancel=new JButton("Cancel");
		ok.addActionListener(e -> onOk());
		cancel.addActionListener(e -> dispose());
		buttons.add(ok);
		buttons.add(cancel);
		content.add(buttons, BorderLayout.SOUTH);

		setContentPane(content);
	}

	private JPanel buildConversionTab() {
		JPanel panel=new JPanel(new GridBagLayout());
		GridBagConstraints gbc=new GridBagConstraints();
		gbc.insets=new Insets(6, 6, 6, 6);
		gbc.anchor=GridBagConstraints.WEST;

		demuxToleranceField.setText(Double.toString(COREPreferences.getDemuxTolerancePpm()));
		minMs1Field.setText(Float.toString(COREPreferences.getMinimumMS1Intensity()));
		minMs2Field.setText(Float.toString(COREPreferences.getMinimumMS2Intensity()));
		verboseCoreBox.setSelected(COREPreferences.isVerboseCoreLogging());

		gbc.gridx=0;
		gbc.gridy=0;
		panel.add(new JLabel("Demux tolerance (ppm):"), gbc);
		gbc.gridx=1;
		panel.add(demuxToleranceField, gbc);

		gbc.gridx=0;
		gbc.gridy=1;
		panel.add(new JLabel("Minimum MS1 intensity:"), gbc);
		gbc.gridx=1;
		panel.add(minMs1Field, gbc);

		gbc.gridx=0;
		gbc.gridy=2;
		panel.add(new JLabel("Minimum MS2 intensity:"), gbc);
		gbc.gridx=1;
		panel.add(minMs2Field, gbc);

		gbc.gridx=0;
		gbc.gridy=3;
		gbc.gridwidth=2;
		panel.add(verboseCoreBox, gbc);

		return panel;
	}

	private JPanel buildGuiTab() {
		JPanel panel=new JPanel(new GridBagLayout());
		GridBagConstraints gbc=new GridBagConstraints();
		gbc.insets=new Insets(6, 6, 6, 6);
		gbc.anchor=GridBagConstraints.WEST;
		gbc.fill=GridBagConstraints.HORIZONTAL;
		gbc.weightx=1.0;

		lastDirField.setEditable(false);
		String lastDir=GUIPreferences.getLastDirectory();
		if (lastDir!=null) lastDirField.setText(lastDir);

		loadLookAndFeelOptions();

		JButton browse=new JButton("Browse...");
		browse.addActionListener(e -> chooseLastDir());

		gbc.gridx=0;
		gbc.gridy=0;
		panel.add(new JLabel("Last directory:"), gbc);
		gbc.gridy=1;
		panel.add(lastDirField, gbc);
		gbc.gridx=1;
		gbc.gridy=1;
		gbc.weightx=0.0;
		panel.add(browse, gbc);
		gbc.weightx=1.0;

		gbc.gridx=0;
		gbc.gridy=2;
		gbc.gridwidth=1;
		panel.add(new JLabel("Look and feel:"), gbc);
		gbc.gridx=1;
		panel.add(lookAndFeelBox, gbc);

		JButton resetWindowsButton=new JButton("Reset Window Location and Dimensions");
		resetWindowsButton.addActionListener(e -> {
			resetWindows=true;
			resetWindowsButton.setEnabled(false);
			resetWindowsButton.setText("Will reset on OK");
		});

		JButton resetSplitsButton=new JButton("Reset Split Pane Dimensions");
		resetSplitsButton.addActionListener(e -> {
			resetSplits=true;
			resetSplitsButton.setEnabled(false);
			resetSplitsButton.setText("Will reset on OK");
		});

		JButton resetTablesButton=new JButton("Reset Table Parameters");
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

		verboseGuiBox.setSelected(GUIPreferences.isVerboseGuiLogging());
		gbc.gridy=6;
		panel.add(verboseGuiBox, gbc);

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
			GUIPreferences.setVerboseGuiLogging(verboseGuiBox.isSelected());

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
}
