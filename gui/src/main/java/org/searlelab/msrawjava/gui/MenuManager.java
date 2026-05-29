package org.searlelab.msrawjava.gui;

import java.awt.Desktop;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileFilter;

import org.searlelab.msrawjava.gui.loadingpanels.LoadingPanelShowcaseDialog;
import org.searlelab.msrawjava.io.VendorFile;
import org.searlelab.msrawjava.io.VendorFiles;
import org.searlelab.msrawjava.logging.Logger;

/**
 * Builds and wires application menu actions.
 */
public final class MenuManager {
	private static final String MANUAL_FILE_NAME="MANUAL.pdf";

	private MenuManager() {
	}

	static List<String> defaultMenuOrder() {
		return List.of("File", "View", "Window", "Help");
	}

	public static void install(RawFileBrowser browser) {
		installMainMenu(browser, browser);
	}

	static void installMainMenu(RawFileBrowser browser, Window activeContext) {
		installScreenMenuBar();
		Window contextWindow=activeContext!=null?activeContext:browser;
		WindowMenuModel windowMenuModel=browser.getWindowMenuController().createMenuModel(contextWindow);
		WindowMenuActionHandler windowActions=browser.getWindowMenuController().createActionHandler(contextWindow);
		JMenuBar existingBar=browser.getJMenuBar();
		if (hasExpectedMenuOrder(existingBar)) {
			refreshWindowMenu(existingBar, windowMenuModel, windowActions);
		} else {
			browser.setJMenuBar(createMenuBar(browser, browser, windowMenuModel, windowActions));
		}
		installAboutHandler(browser);
	}

	static void install(Window menuHost, RawFileBrowser browser) {
		if (menuHost instanceof RawFileBrowser) {
			RawFileBrowser rawFileBrowser=(RawFileBrowser)menuHost;
			installMainMenu(rawFileBrowser, rawFileBrowser);
			return;
		}
		Window contextWindow=menuHost!=null?menuHost:browser;
		WindowMenuModel windowMenuModel=browser.getWindowMenuController().createMenuModel(contextWindow);
		WindowMenuActionHandler windowActions=browser.getWindowMenuController().createActionHandler(contextWindow);
		JMenuBar existingBar=getMenuBar(menuHost);
		if (hasExpectedMenuOrder(existingBar)) {
			refreshWindowMenu(existingBar, windowMenuModel, windowActions);
		} else {
			setMenuBar(menuHost, createMenuBar(contextWindow, browser, windowMenuModel, windowActions));
		}
		installAboutHandler(browser);
	}

	public static void install(JDialog dialog, RawFileBrowser browser) {
		if (dialog==null||browser==null) return;
		if (isMac()) return; //FIXME this is kind of a hack. Really the dialogs should get this menubar as well
		install((Window)dialog, browser);
	}

	static JMenuBar createMenuBar(Window menuHost, RawFileBrowser browser, WindowMenuModel windowMenuModel, WindowMenuActionHandler windowActions) {
		JMenuBar bar=new JMenuBar();
		Frame ownerFrame=resolveOwnerFrame(menuHost, browser);

		JMenu file=new JMenu("File");
		file.setToolTipText("File actions for opening data, preferences, and exiting the application.");
		JMenuItem open=new JMenuItem("Open");
		open.setToolTipText("Open a vendor raw file and select it in the browser.");
		open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		open.addActionListener(e -> onOpen(ownerFrame, browser, false));
		JMenuItem preferences=new JMenuItem("Preferences");
		preferences.setToolTipText("Open application preferences.");
		preferences.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		preferences.addActionListener(e -> PreferencesDialog.showDialog(ownerFrame));
		JMenuItem quit=new JMenuItem("Quit");
		quit.setToolTipText("Close the application.");
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
		view.setToolTipText("View and visualization actions.");
		JMenuItem visualize=new JMenuItem("Visualize Raw File");
		visualize.setToolTipText("Open a raw file directly in the visualization dialog.");
		visualize.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		visualize.addActionListener(e -> onOpen(ownerFrame, browser, true));
		view.add(visualize);

		JMenu window=createWindowMenu(windowMenuModel, windowActions);

		JMenu help=new JMenu("Help");
		help.setToolTipText("Help, citation, and educational resources.");
		JMenuItem manual=new JMenuItem("Open Manual");
		manual.setToolTipText("Open the installed "+ProductBranding.PRODUCT_NAME+" manual.");
		manual.addActionListener(e -> openManual(ownerFrame));
		JMenuItem cite=new JMenuItem("How to Cite");
		cite.setToolTipText("Show citation information for "+ProductBranding.PRODUCT_NAME+".");
		cite.addActionListener(e -> HowToCiteDialog.showDialog(ownerFrame));
		JMenuItem demos=new JMenuItem("Educational Demos");
		demos.setToolTipText("Open interactive educational loading-panel demos.");
		demos.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		demos.addActionListener(e -> LoadingPanelShowcaseDialog.showDialog(ownerFrame));
		JMenuItem console=new JMenuItem("Logging Console");
		console.setToolTipText("Show captured standard output and error messages.");
		console.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		console.addActionListener(e -> LoggingConsoleDialog.showDialog(ownerFrame));
		help.add(manual);
		help.add(cite);
		help.add(demos);
		help.add(console);

		bar.add(file);
		bar.add(view);
		bar.add(window);
		bar.add(help);
		return bar;
	}

	private static boolean hasExpectedMenuOrder(JMenuBar menuBar) {
		if (menuBar==null||menuBar.getMenuCount()!=defaultMenuOrder().size()) return false;
		for (int i=0; i<defaultMenuOrder().size(); i++) {
			JMenu menu=menuBar.getMenu(i);
			if (menu==null||!defaultMenuOrder().get(i).equals(menu.getText())) return false;
		}
		return true;
	}

	private static void refreshWindowMenu(JMenuBar menuBar, WindowMenuModel windowMenuModel, WindowMenuActionHandler windowActions) {
		if (menuBar==null) return;
		JMenu windowMenu=menuBar.getMenu(2);
		if (windowMenu==null) return;
		populateWindowMenu(windowMenu, windowMenuModel, windowActions);
		menuBar.revalidate();
		menuBar.repaint();
	}

	static JMenu createWindowMenu(WindowMenuModel windowMenuModel, WindowMenuActionHandler windowActions) {
		JMenu window=new JMenu("Window");
		populateWindowMenu(window, windowMenuModel, windowActions);
		return window;
	}

	private static void populateWindowMenu(JMenu window, WindowMenuModel windowMenuModel, WindowMenuActionHandler windowActions) {
		window.removeAll();
		window.setText("Window");
		window.setToolTipText("Switch between the main browser and open raw-file visualization windows.");

		JMenuItem browserItem=new JMenuItem("Bring "+ProductBranding.PRODUCT_NAME+" to Front");
		browserItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		browserItem.setEnabled(windowMenuModel==null||!windowMenuModel.isBrowserActive());
		browserItem.addActionListener(e -> {
			if (windowActions!=null) windowActions.bringRawFileBrowserToFront();
		});
		window.add(browserItem);
		window.addSeparator();

		if (windowMenuModel!=null) {
			int index=0;
			for (WindowMenuModel.VisualizationItem item : windowMenuModel.getVisualizationItems()) {
				JMenuItem visualizationItem=new JMenuItem(item.getLabel());
				Integer acceleratorDigit=item.getAcceleratorDigit();
				if (acceleratorDigit!=null) {
					visualizationItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0+acceleratorDigit.intValue(), Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
				}
				visualizationItem.setEnabled(!item.isActive());
				final int visualizationIndex=index;
				visualizationItem.addActionListener(e -> {
					if (windowActions!=null) windowActions.activateVisualization(visualizationIndex);
				});
				window.add(visualizationItem);
				index++;
			}
		}

		window.addSeparator();

		JMenuItem previousWindow=new JMenuItem("Previous Window");
		previousWindow.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		previousWindow.addActionListener(e -> {
			if (windowActions!=null) windowActions.activatePreviousWindow();
		});
		window.add(previousWindow);

		JMenuItem nextWindow=new JMenuItem("Next Window");
		nextWindow.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		nextWindow.addActionListener(e -> {
			if (windowActions!=null) windowActions.activateNextWindow();
		});
		window.add(nextWindow);
	}

	private static void onOpen(Frame ownerFrame, RawFileBrowser browser, boolean visualize) {
		File f=chooseRawFile(ownerFrame);
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
				String name=f.getName();
				for (VendorFile vendor : VendorFile.list()) {
					if (vendor.matchesName(name)) return true;
				}
				return false;
			}

			@Override
			public String getDescription() {
				String extList=VendorFile.list().stream().map(v -> "*"+v.getExtension()).collect(java.util.stream.Collectors.joining(", "));
				return "Raw files ("+extList+")";
			}
		});
		int result=chooser.showOpenDialog(parent);
		if (result!=JFileChooser.APPROVE_OPTION) return null;
		File selected=chooser.getSelectedFile();
		if (selected==null) return null;
		if (VendorFile.BRUKER.matchesPath(selected.toPath())) return selected;
		if (VendorFile.THERMO.matchesPath(selected.toPath())) return selected;
		if (VendorFile.ENCYCLOPEDIA.matchesPath(selected.toPath())) return selected;
		if (VendorFile.MZML.matchesPath(selected.toPath())) return selected;
		return null;
	}

	static File manualFile() {
		String override=System.getProperty("msforest.manual.path");
		if (override!=null&&!override.isBlank()) return new File(override);

		File workingDirectoryManual=new File(MANUAL_FILE_NAME);
		if (workingDirectoryManual.isFile()) return workingDirectoryManual;

		Path appDirectory=applicationDirectory();
		if (appDirectory!=null) {
			Path installedManual=appDirectory.resolve(MANUAL_FILE_NAME);
			if (Files.isRegularFile(installedManual)) return installedManual.toFile();

			Path developmentManual=appDirectory.resolve("../..").normalize().resolve(MANUAL_FILE_NAME);
			if (Files.isRegularFile(developmentManual)) return developmentManual.toFile();
		}

		return appDirectory!=null?appDirectory.resolve(MANUAL_FILE_NAME).toFile():workingDirectoryManual;
	}

	private static Path applicationDirectory() {
		CodeSource codeSource=MenuManager.class.getProtectionDomain().getCodeSource();
		if (codeSource==null) return null;
		URL location=codeSource.getLocation();
		if (location==null) return null;
		try {
			Path path=Path.of(location.toURI());
			return Files.isDirectory(path)?path:path.getParent();
		} catch (IllegalArgumentException|URISyntaxException e) {
			Logger.errorException(e);
			return null;
		}
	}

	private static void openManual(Frame ownerFrame) {
		File manual=manualFile();
		if (!manual.isFile()) {
			showManualError(ownerFrame, "Manual not found:\n"+manual.getAbsolutePath());
			return;
		}
		if (!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
			showManualError(ownerFrame, "This system does not support opening the manual automatically.");
			return;
		}
		try {
			Desktop.getDesktop().open(manual);
		} catch (Exception e) {
			Logger.errorException(e);
			showManualError(ownerFrame, "Could not open the manual:\n"+manual.getAbsolutePath());
		}
	}

	private static void showManualError(Frame ownerFrame, String message) {
		Logger.errorLine(message);
		JOptionPane.showMessageDialog(ownerFrame, message, ProductBranding.PRODUCT_NAME+" Manual", JOptionPane.ERROR_MESSAGE);
	}

	private static void installAboutHandler(RawFileBrowser browser) {
		if (!Desktop.isDesktopSupported()) return;
		try {
			Desktop.getDesktop().setAboutHandler(e -> HowToCiteDialog.showDialog(browser));
		} catch (UnsupportedOperationException ignore) {
			Logger.errorException(ignore);
		}
	}

	private static JMenuBar getMenuBar(Window menuHost) {
		if (menuHost instanceof JFrame) return ((JFrame)menuHost).getJMenuBar();
		if (menuHost instanceof JDialog) return ((JDialog)menuHost).getJMenuBar();
		return null;
	}

	private static void setMenuBar(Window menuHost, JMenuBar menuBar) {
		if (menuHost instanceof JFrame) {
			((JFrame)menuHost).setJMenuBar(menuBar);
			return;
		}
		if (menuHost instanceof JDialog) {
			((JDialog)menuHost).setJMenuBar(menuBar);
		}
	}

	private static Frame resolveOwnerFrame(Window menuHost, RawFileBrowser browser) {
		if (menuHost instanceof Frame) return (Frame)menuHost;
		return browser;
	}

	private static void installScreenMenuBar() {
		if (isMac()) {
			System.setProperty("apple.laf.useScreenMenuBar", "true");
		}
	}

	private static boolean isMac() {
		String os=System.getProperty("os.name", "").toLowerCase();
		return os.contains("mac");
	}
}
