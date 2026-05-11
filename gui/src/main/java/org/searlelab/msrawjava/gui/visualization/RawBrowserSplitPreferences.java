package org.searlelab.msrawjava.gui.visualization;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleConsumer;

import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

final class RawBrowserSplitPreferences {
	private static int suppressSplitSave=0;

	private RawBrowserSplitPreferences() {
	}

	static void applySplitRatio(JSplitPane pane, double ratio) {
		if (ratio<=0.0||ratio>=1.0) return;
		pane.setResizeWeight(ratio);
		pane.setDividerLocation(ratio);
	}

	static double getSplitRatio(JSplitPane pane) {
		int size=(pane.getOrientation()==JSplitPane.HORIZONTAL_SPLIT)?pane.getWidth():pane.getHeight();
		if (size<10) return -1.0;
		double ratio=pane.getDividerLocation()/(double)size;
		if (ratio<=0.0||ratio>=1.0) return -1.0;
		return ratio;
	}

	static void registerSplitPreference(JSplitPane pane, DoubleConsumer saver) {
		AtomicBoolean dragging=new AtomicBoolean(false);
		installDividerDragListener(pane, dragging, saver);
		pane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
			if (suppressSplitSave>0) return;
			if (!dragging.get()) return;
			double ratio=getSplitRatio(pane);
			if (ratio>0.0) saver.accept(ratio);
		});
	}

	private static void installDividerDragListener(JSplitPane pane, AtomicBoolean dragging, DoubleConsumer saver) {
		if (pane.getClientProperty("rawBrowser.dividerListener")!=null) return;
		pane.putClientProperty("rawBrowser.dividerListener", Boolean.TRUE);
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
					if (suppressSplitSave>0) return;
					double ratio=getSplitRatio(pane);
					if (ratio>0.0) saver.accept(ratio);
				}
			});
		});
	}

	static void withSplitSaveSuppressed(Runnable action) {
		suppressSplitSave++;
		try {
			action.run();
		} finally {
			suppressSplitSave--;
		}
	}
}
