package org.searlelab.msrawjava.gui;

import java.awt.Desktop;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileFilter;

import org.searlelab.msrawjava.io.VendorFileFinder;

public final class MenuManager {
	private MenuManager() {
	}

	public static void install(RawFileBrowser browser) {
		if (isMac()) {
			System.setProperty("apple.laf.useScreenMenuBar", "true");
		}
		browser.setJMenuBar(createMenuBar(browser));
		installAboutHandler(browser);
	}

	public static void install(JDialog dialog, RawFileBrowser browser) {
		if (dialog==null||browser==null) return;
		if (isMac()) {
			System.setProperty("apple.laf.useScreenMenuBar", "true");
			installAboutHandler(browser);
			return;
		}
		dialog.setJMenuBar(createMenuBar(browser));
		installAboutHandler(browser);
	}

	private static JMenuBar createMenuBar(RawFileBrowser browser) {
		JMenuBar bar=new JMenuBar();

		JMenu file=new JMenu("File");
		JMenuItem open=new JMenuItem("Open");
		open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		open.addActionListener(e -> onOpen(browser, false));
		JMenuItem preferences=new JMenuItem("Preferences");
		preferences.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		preferences.addActionListener(e -> PreferencesDialog.showDialog(browser));
		JMenuItem quit=new JMenuItem("Quit");
		quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		quit.addActionListener(e -> {
			browser.dispatchEvent(new WindowEvent(browser, WindowEvent.WINDOW_CLOSING));
			browser.dispose();
			System.exit(0);
		});
		file.add(open);
		file.add(preferences);
		file.addSeparator();
		file.add(quit);

		JMenu view=new JMenu("View");
		JMenuItem visualize=new JMenuItem("Visualize Raw File");
		visualize.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		visualize.addActionListener(e -> onOpen(browser, true));
		view.add(visualize);

		JMenu help=new JMenu("Help");
		JMenuItem cite=new JMenuItem("How to cite");
		cite.addActionListener(e -> HowToCiteDialog.showDialog(browser));
		help.add(cite);

		bar.add(file);
		bar.add(view);
		bar.add(help);
		return bar;
	}

	private static void onOpen(RawFileBrowser browser, boolean visualize) {
		File f=chooseRawFile(browser);
		if (f==null) return;
		File parent=f.isDirectory()?f.getParentFile():f.getParentFile();
		if (parent!=null) {
			GUIPreferences.rememberLastDirectory(parent);
		}
		browser.openAndSelectFile(f, visualize);
	}

	private static File chooseRawFile(Frame parent) {
		JFileChooser chooser=new JFileChooser();
		chooser.setDialogTitle("Select raw file");
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		String lastDir=GUIPreferences.getLastDirectory();
		if (lastDir!=null) {
			File dir=new File(lastDir);
			if (dir.isDirectory()) chooser.setCurrentDirectory(dir);
		}
		chooser.setAcceptAllFileFilterUsed(true);
		chooser.addChoosableFileFilter(new FileFilter() {
			@Override
			public boolean accept(File f) {
				if (f.isDirectory()) return true;
				String name=f.getName().toLowerCase();
				return name.endsWith(".raw")||name.endsWith(".dia")||name.endsWith(".d");
			}

			@Override
			public String getDescription() {
				return "Raw files (*.raw, *.d, *.dia)";
			}
		});
		int result=chooser.showOpenDialog(parent);
		if (result!=JFileChooser.APPROVE_OPTION) return null;
		File selected=chooser.getSelectedFile();
		if (selected==null) return null;
		if (selected.isDirectory()&&VendorFileFinder.isDotDFile(selected.toPath())) return selected;
		if (VendorFileFinder.isThermoFile(selected.toPath())) return selected;
		if (selected.getName().toLowerCase().endsWith(".dia")) return selected;
		return null;
	}

	private static void installAboutHandler(RawFileBrowser browser) {
		if (!Desktop.isDesktopSupported()) return;
		try {
			Desktop.getDesktop().setAboutHandler(e -> HowToCiteDialog.showDialog(browser));
		} catch (UnsupportedOperationException ignore) {
		}
	}

	private static boolean isMac() {
		String os=System.getProperty("os.name", "").toLowerCase();
		return os.contains("mac");
	}
}
