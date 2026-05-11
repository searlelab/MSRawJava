package org.searlelab.msrawjava.gui.visualization;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;

import org.searlelab.msrawjava.gui.graphing.ExtendedChartPanel;

final class RawBrowserNavigation {
	static final String CHART_ACTION_PREVIOUS_ROW="rawBrowser.chartSelectPreviousRow";
	static final String CHART_ACTION_NEXT_ROW="rawBrowser.chartSelectNextRow";
	static final String CHART_ACTION_PREVIOUS_ROW_EXTEND="rawBrowser.chartSelectPreviousRowExtend";
	static final String CHART_ACTION_NEXT_ROW_EXTEND="rawBrowser.chartSelectNextRowExtend";

	private RawBrowserNavigation() {
	}

	static int findNearestValueIndex(double target, double[] values) {
		if (!Double.isFinite(target)||values==null||values.length==0) return -1;
		double bestDistance=Double.POSITIVE_INFINITY;
		int bestIndex=-1;
		for (int i=0; i<values.length; i++) {
			double value=values[i];
			if (!Double.isFinite(value)) continue;
			double distance=Math.abs(value-target);
			if (distance<bestDistance) {
				bestDistance=distance;
				bestIndex=i;
			}
		}
		return bestIndex;
	}

	static void installHorizontalRowNavigation(JTable table) {
		if (table==null) return;
		mapHorizontalNavigationToRows(table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT));
		mapHorizontalNavigationToRows(table.getInputMap(JComponent.WHEN_FOCUSED));
	}

	static void mapHorizontalNavigationToRows(InputMap inputMap) {
		if (inputMap==null) return;
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "selectPreviousRow");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "selectNextRow");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0), "selectPreviousRow");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, 0), "selectNextRow");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.SHIFT_DOWN_MASK), "selectPreviousRowExtendSelection");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.SHIFT_DOWN_MASK), "selectNextRowExtendSelection");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, KeyEvent.SHIFT_DOWN_MASK), "selectPreviousRowExtendSelection");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, KeyEvent.SHIFT_DOWN_MASK), "selectNextRowExtendSelection");
	}

	static void mapChartNavigationToRows(InputMap inputMap) {
		if (inputMap==null) return;
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), CHART_ACTION_PREVIOUS_ROW);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), CHART_ACTION_NEXT_ROW);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), CHART_ACTION_PREVIOUS_ROW);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), CHART_ACTION_NEXT_ROW);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_UP, 0), CHART_ACTION_PREVIOUS_ROW);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_DOWN, 0), CHART_ACTION_NEXT_ROW);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0), CHART_ACTION_PREVIOUS_ROW);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, 0), CHART_ACTION_NEXT_ROW);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK), CHART_ACTION_PREVIOUS_ROW_EXTEND);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK), CHART_ACTION_NEXT_ROW_EXTEND);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.SHIFT_DOWN_MASK), CHART_ACTION_PREVIOUS_ROW_EXTEND);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.SHIFT_DOWN_MASK), CHART_ACTION_NEXT_ROW_EXTEND);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_UP, KeyEvent.SHIFT_DOWN_MASK), CHART_ACTION_PREVIOUS_ROW_EXTEND);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_DOWN, KeyEvent.SHIFT_DOWN_MASK), CHART_ACTION_NEXT_ROW_EXTEND);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, KeyEvent.SHIFT_DOWN_MASK), CHART_ACTION_PREVIOUS_ROW_EXTEND);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, KeyEvent.SHIFT_DOWN_MASK), CHART_ACTION_NEXT_ROW_EXTEND);
	}

	static void installChartArrowNavigation(ExtendedChartPanel chart, Consumer<String> tableSelectionAction) {
		if (chart==null) return;
		if (Boolean.TRUE.equals(chart.getClientProperty("rawBrowser.chartArrowNavInstalled"))) return;
		chart.putClientProperty("rawBrowser.chartArrowNavInstalled", Boolean.TRUE);
		chart.setFocusable(true);
		mapChartNavigationToRows(chart.getInputMap(JComponent.WHEN_FOCUSED));
		chart.getActionMap().put(CHART_ACTION_PREVIOUS_ROW, new RowAction(tableSelectionAction, "selectPreviousRow"));
		chart.getActionMap().put(CHART_ACTION_NEXT_ROW, new RowAction(tableSelectionAction, "selectNextRow"));
		chart.getActionMap().put(CHART_ACTION_PREVIOUS_ROW_EXTEND, new RowAction(tableSelectionAction, "selectPreviousRowExtendSelection"));
		chart.getActionMap().put(CHART_ACTION_NEXT_ROW_EXTEND, new RowAction(tableSelectionAction, "selectNextRowExtendSelection"));
	}

	private static final class RowAction extends AbstractAction {
		private static final long serialVersionUID=1L;
		private final Consumer<String> tableSelectionAction;
		private final String actionKey;

		private RowAction(Consumer<String> tableSelectionAction, String actionKey) {
			this.tableSelectionAction=tableSelectionAction;
			this.actionKey=actionKey;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			tableSelectionAction.accept(actionKey);
		}
	}
}
