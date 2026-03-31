package org.searlelab.msrawjava.gui;

import java.awt.Component;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.JDialog;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

final class WindowMenuController {
	private final RawFileBrowser browser;
	private final List<JDialog> visualizationWindows=new ArrayList<>();
	private final List<Window> refreshHookWindows=new ArrayList<>();

	WindowMenuController(RawFileBrowser browser) {
		this.browser=Objects.requireNonNull(browser);
		installRefreshHooks(browser);
	}

	void registerVisualizationWindow(JDialog dialog) {
		runOnEdt(() -> {
			if (dialog==null||visualizationWindows.contains(dialog)) return;
			visualizationWindows.add(dialog);
			installRefreshHooks(dialog);
			refreshMenus();
		});
	}

	void unregisterVisualizationWindow(Window window) {
		runOnEdt(() -> {
			if (window==null) return;
			if (visualizationWindows.remove(window)) {
				refreshMenus();
			}
		});
	}

	WindowMenuModel createMenuModel(Window activeWindow) {
		assertEdt();
		int activeVisualizationIndex=indexOfVisualization(activeWindow);
		boolean browserActive=activeWindow==browser;
		List<String> titles=new ArrayList<>(visualizationWindows.size());
		for (JDialog dialog : visualizationWindows) {
			titles.add(dialog.getTitle());
		}
		return WindowMenuModel.create(browserActive, activeVisualizationIndex, titles);
	}

	WindowMenuActionHandler createActionHandler(Window activeWindow) {
		return new WindowMenuActionHandler() {
			@Override
			public void bringRawFileBrowserToFront() {
				focusWindow(browser);
			}

			@Override
			public void activateVisualization(int visualizationIndex) {
				runOnEdt(() -> {
					if (visualizationIndex<0||visualizationIndex>=visualizationWindows.size()) return;
					focusWindow(visualizationWindows.get(visualizationIndex));
				});
			}

			@Override
			public void activatePreviousWindow() {
				focusRelative(activeWindow, -1);
			}

			@Override
			public void activateNextWindow() {
				focusRelative(activeWindow, 1);
			}
		};
	}

	void refreshMenus() {
		runOnEdt(() -> {
			Window activeWindow=findActiveTrackedWindow();
			if (isMac()) {
				MenuManager.installMainMenu(browser, activeWindow);
			} else {
				MenuManager.install(activeWindow, browser);
			}
			if (!isMac()) {
				for (JDialog dialog : List.copyOf(visualizationWindows)) {
					MenuManager.install(dialog, browser);
				}
			}
		});
	}

	static int wrapIndex(int currentIndex, int delta, int size) {
		if (size<=0) return -1;
		int wrapped=(currentIndex+delta)%size;
		return wrapped<0?wrapped+size:wrapped;
	}

	private void focusRelative(Window activeWindow, int delta) {
		runOnEdt(() -> {
			List<Window> cycleOrder=new ArrayList<>(visualizationWindows.size()+1);
			cycleOrder.add(browser);
			cycleOrder.addAll(visualizationWindows);
			int currentIndex=cycleOrder.indexOf(activeWindow);
			if (currentIndex<0) currentIndex=0;
			int targetIndex=wrapIndex(currentIndex, delta, cycleOrder.size());
			if (targetIndex>=0) {
				focusWindow(cycleOrder.get(targetIndex));
			}
		});
	}

	private int indexOfVisualization(Window activeWindow) {
		for (int i=0; i<visualizationWindows.size(); i++) {
			if (visualizationWindows.get(i)==activeWindow) return i;
		}
		return -1;
	}

	private Window findActiveTrackedWindow() {
		for (JDialog dialog : visualizationWindows) {
			if (dialog!=null&&dialog.isActive()) return dialog;
		}
		return browser;
	}

	private void installRefreshHooks(Window window) {
		if (window==null||refreshHookWindows.contains(window)) return;
		refreshHookWindows.add(window);
		window.addWindowListener(new WindowAdapter() {
			@Override
			public void windowActivated(WindowEvent e) {
				refreshMenus();
			}

			@Override
			public void windowOpened(WindowEvent e) {
				refreshMenus();
			}

			@Override
			public void windowDeiconified(WindowEvent e) {
				refreshMenus();
			}
		});
	}

	private static void focusWindow(Window window) {
		runOnEdt(() -> {
			if (window==null) return;
			if (window instanceof Frame frame) {
				frame.setExtendedState(frame.getExtendedState()&~Frame.ICONIFIED);
			}
			if (!window.isVisible()) {
				window.setVisible(true);
			}
			window.toFront();
			window.requestFocus();
			if (window instanceof RootPaneContainer rootPaneContainer) {
				if (!rootPaneContainer.getRootPane().requestFocusInWindow()) {
					Component content=rootPaneContainer.getContentPane();
					if (content!=null) {
						content.requestFocusInWindow();
					}
				}
			} else {
				window.requestFocusInWindow();
			}
		});
	}

	private static void runOnEdt(Runnable action) {
		if (SwingUtilities.isEventDispatchThread()) {
			action.run();
		} else {
			SwingUtilities.invokeLater(action);
		}
	}

	private static void assertEdt() {
		if (!SwingUtilities.isEventDispatchThread()) {
			throw new IllegalStateException("Window menu operations must run on the EDT.");
		}
	}

	private static boolean isMac() {
		String os=System.getProperty("os.name", "").toLowerCase();
		return os.contains("mac");
	}
}
