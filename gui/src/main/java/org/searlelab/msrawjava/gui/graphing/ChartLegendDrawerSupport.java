package org.searlelab.msrawjava.gui.graphing;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.LegendItem;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.LegendItemSource;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.xy.XYDataset;

/**
 * Swing overlay that provides a hover-open legend drawer for chart panels.
 */
final class ChartLegendDrawerSupport {
	private static final int GLYPH_SIZE=22;
	private static final int MARGIN=8;
	private static final int MIN_DRAWER_WIDTH=220;
	private static final int MAX_DRAWER_WIDTH=320;
	private static final double DRAWER_WIDTH_RATIO=0.45;
	private static final int HIDE_DELAY_MS=200;
	private static final double TRACE_CLICK_TOLERANCE_PX=8.0;
	private static final Color HALO_COLOR=new Color(255, 235, 59, 140);
	private static final Color HALO_OUTER_COLOR=new Color(255, 235, 59, 90);

	private final ExtendedChartPanel chartPanel;
	private final JButton glyphButton;
	private final JPanel drawerPanel;
	private final JLabel titleLabel;
	private final JToggleButton pinToggle;
	private final JTextField filterField;
	private final JPanel rowsPanel;
	private final JPanel rowsContainer;
	private final JScrollPane scrollPane;
	private final JLabel emptyLabel;
	private final Timer hideTimer;
	private final MouseAdapter hoverListener;
	private final ComponentAdapter resizeListener;
	private final DocumentListener filterListener;
	private final ChartMouseListener chartMouseListener;

	private final ArrayList<LegendRow> legendRows=new ArrayList<>();
	private final ArrayList<XYAnnotation> selectedHaloAnnotations=new ArrayList<>();
	private LayoutManager priorLayout;
	private boolean attached=false;
	private String selectedLegendKey;

	private static final class LegendRow {
		private final String legendKey;
		private final String normalizedLabel;
		private final int datasetIndex;
		private final int seriesIndex;
		private final JPanel component;
		private final LegendSwatch swatch;

		private LegendRow(String legendKey, String normalizedLabel, int datasetIndex, int seriesIndex, JPanel component, LegendSwatch swatch) {
			this.legendKey=legendKey;
			this.normalizedLabel=normalizedLabel;
			this.datasetIndex=datasetIndex;
			this.seriesIndex=seriesIndex;
			this.component=component;
			this.swatch=swatch;
		}
	}

	private static final class TraceHit {
		private final int datasetIndex;
		private final int seriesIndex;
		private final double distanceSq;

		private TraceHit(int datasetIndex, int seriesIndex, double distanceSq) {
			this.datasetIndex=datasetIndex;
			this.seriesIndex=seriesIndex;
			this.distanceSq=distanceSq;
		}
	}

	ChartLegendDrawerSupport(ExtendedChartPanel chartPanel) {
		this.chartPanel=chartPanel;
		this.glyphButton=buildGlyphButton();
		this.titleLabel=new JLabel("Legend", SwingConstants.LEFT);
		this.pinToggle=new JToggleButton("Pin");
		this.filterField=new JTextField();
		this.rowsPanel=new JPanel();
		this.rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
		this.rowsPanel.setOpaque(false);
		this.rowsContainer=new JPanel(new BorderLayout());
		this.rowsContainer.setOpaque(false);
		this.rowsContainer.add(rowsPanel, BorderLayout.NORTH);
		this.scrollPane=new JScrollPane(rowsContainer);
		this.emptyLabel=new JLabel("No legend entries", SwingConstants.LEFT);
		this.emptyLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		this.emptyLabel.setForeground(getMutedForeground());
		this.drawerPanel=buildDrawerPanel();
		this.hideTimer=new Timer(HIDE_DELAY_MS, e -> {
			if (!isPinned()) hideDrawer();
		});
		this.hideTimer.setRepeats(false);

		this.hoverListener=new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hideTimer.stop();
				showDrawer();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				handleMouseExited(e);
			}
		};

		this.resizeListener=new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				updateOverlayBounds();
			}
		};

		this.filterListener=new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				applyFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				applyFilter();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				applyFilter();
			}
		};
		this.chartMouseListener=new ChartMouseListener() {
			@Override
			public void chartMouseClicked(ChartMouseEvent event) {
				handleChartClick(event);
			}

			@Override
			public void chartMouseMoved(ChartMouseEvent event) {
				// no-op
			}
		};

		attach();
	}

	void attach() {
		if (attached) return;
		attached=true;
		priorLayout=chartPanel.getLayout();
		chartPanel.setLayout(null);
		chartPanel.add(glyphButton);
		chartPanel.add(drawerPanel);
		installHoverListeners();
		installBehaviorListeners();
		hideDrawer();
		refreshLegendRows();
		updateOverlayBounds();
	}

	void detach() {
		if (!attached) return;
		attached=false;
		hideTimer.stop();
		uninstallHoverListeners();
		filterField.getDocument().removeDocumentListener(filterListener);
		chartPanel.removeComponentListener(resizeListener);
		chartPanel.removeChartMouseListener(chartMouseListener);
		chartPanel.remove(glyphButton);
		chartPanel.remove(drawerPanel);
		if (priorLayout!=null) {
			chartPanel.setLayout(priorLayout);
		}
		clearSelectedTraceHalo();
		chartPanel.revalidate();
		chartPanel.repaint();
	}

	void refreshLegendRows() {
		legendRows.clear();
		selectedLegendKey=null;
		clearSelectedTraceHalo();
		for (LegendItem item : extractLegendItems()) {
			if (item==null) continue;
			String label=item.getLabel();
			if (label==null) label="";
			String legendKey=buildLegendKey(item);
			JPanel row=buildLegendRow(item, label, legendKey);
			int datasetIndex=item.getDatasetIndex();
			int seriesIndex=item.getSeriesIndex();
			LegendSwatch swatch=(LegendSwatch)((JPanel)row).getComponent(0);
			legendRows.add(new LegendRow(legendKey, label.toLowerCase(Locale.ROOT), datasetIndex, seriesIndex, row, swatch));
		}

		titleLabel.setText("Legend ("+legendRows.size()+")");
		boolean hasRows=!legendRows.isEmpty();
		glyphButton.setVisible(hasRows);
		pinToggle.setEnabled(hasRows);
		if (!hasRows) {
			pinToggle.setSelected(false);
			hideDrawer();
		}
		applyFilter();
	}

	boolean isGlyphVisibleForTest() {
		return glyphButton.isVisible();
	}

	int getLegendRowCountForTest() {
		return legendRows.size();
	}

	int getVisibleLegendRowCountForTest() {
		int count=0;
		for (Component component : rowsPanel.getComponents()) {
			if (component==emptyLabel) continue;
			if (component.isVisible()) count++;
		}
		return count;
	}

	void setFilterTextForTest(String text) {
		filterField.setText(text==null?"":text);
	}

	void selectLegendIndexForTest(int index) {
		if (index<0||index>=legendRows.size()) return;
		selectLegendEntry(legendRows.get(index).legendKey);
	}

	void selectTraceForTest(int datasetIndex, int seriesIndex) {
		selectLegendEntryFromTrace(datasetIndex, seriesIndex);
	}

	int getSelectedLegendCountForTest() {
		int count=0;
		for (LegendRow row : legendRows) {
			if (row.legendKey.equals(selectedLegendKey)) count++;
		}
		return count;
	}

	int getSelectedHaloAnnotationCountForTest() {
		return selectedHaloAnnotations.size();
	}

	private JButton buildGlyphButton() {
		JButton button=new JButton("\u2261");
		button.setToolTipText("Show legend");
		button.setFocusable(false);
		button.setMargin(new java.awt.Insets(0, 0, 0, 0));
		button.setBorder(BorderFactory.createLineBorder(getBorderColor()));
		button.setBackground(getPanelBackground());
		button.setForeground(getPanelForeground());
		return button;
	}

	private JPanel buildDrawerPanel() {
		JPanel panel=new JPanel(new BorderLayout(6, 6));
		panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(getBorderColor()), BorderFactory.createEmptyBorder(6, 6, 6, 6)));
		panel.setBackground(getPanelBackground());
		panel.setOpaque(true);

		JPanel header=new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);
		titleLabel.setForeground(getPanelForeground());
		header.add(titleLabel, BorderLayout.CENTER);
		pinToggle.setToolTipText("Keep legend drawer open");
		pinToggle.setFocusable(false);
		header.add(pinToggle, BorderLayout.EAST);

		filterField.setToolTipText("Filter legend entries");
		filterField.putClientProperty("JTextField.placeholderText", "Filter legend...");

		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getViewport().setOpaque(false);
		scrollPane.setOpaque(false);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel topPanel=new JPanel(new BorderLayout(0, 6));
		topPanel.setOpaque(false);
		topPanel.add(header, BorderLayout.NORTH);
		topPanel.add(filterField, BorderLayout.SOUTH);

		panel.add(topPanel, BorderLayout.NORTH);
		panel.add(scrollPane, BorderLayout.CENTER);

		panel.setVisible(false);
		return panel;
	}

	private void installBehaviorListeners() {
		pinToggle.addActionListener(e -> {
			if (isPinned()) {
				hideTimer.stop();
				showDrawer();
			} else if (!isPointerInsideHotZone()) {
				hideDrawer();
			}
		});

		glyphButton.addActionListener(e -> {
			if (drawerPanel.isVisible()&&!isPinned()) {
				hideDrawer();
			} else {
				showDrawer();
			}
		});

		filterField.getDocument().addDocumentListener(filterListener);
		chartPanel.addComponentListener(resizeListener);
		chartPanel.addChartMouseListener(chartMouseListener);
	}

	private void installHoverListeners() {
		addHoverListenersRecursive(glyphButton);
		addHoverListenersRecursive(drawerPanel);
	}

	private void uninstallHoverListeners() {
		removeHoverListenersRecursive(glyphButton);
		removeHoverListenersRecursive(drawerPanel);
	}

	private void addHoverListenersRecursive(Component component) {
		component.addMouseListener(hoverListener);
		if (component instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				addHoverListenersRecursive(child);
			}
		}
	}

	private void removeHoverListenersRecursive(Component component) {
		component.removeMouseListener(hoverListener);
		if (component instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				removeHoverListenersRecursive(child);
			}
		}
	}

	private void handleMouseExited(MouseEvent event) {
		if (isPinned()||!drawerPanel.isVisible()) return;
		Point point=SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), chartPanel);
		if (isPointInsideHotZone(point)) return;
		hideTimer.restart();
	}

	private boolean isPointerInsideHotZone() {
		Point mouse;
		try {
			mouse=chartPanel.getMousePosition();
		} catch (RuntimeException ex) {
			return false;
		}
		if (mouse==null) return false;
		return isPointInsideHotZone(mouse);
	}

	private boolean isPointInsideHotZone(Point point) {
		if (point==null) return false;
		if (glyphButton.isVisible()&&glyphButton.getBounds().contains(point)) return true;
		return drawerPanel.isVisible()&&drawerPanel.getBounds().contains(point);
	}

	private boolean isPinned() {
		return pinToggle.isSelected();
	}

	private void showDrawer() {
		if (legendRows.isEmpty()) return;
		hideTimer.stop();
		drawerPanel.setVisible(true);
		updateOverlayBounds();
		chartPanel.repaint();
	}

	private void hideDrawer() {
		drawerPanel.setVisible(false);
		chartPanel.repaint();
	}

	private void updateOverlayBounds() {
		int width=Math.max(0, chartPanel.getWidth());
		int height=Math.max(0, chartPanel.getHeight());
		if (width<=0||height<=0) return;

		int glyphX=Math.max(MARGIN, width-GLYPH_SIZE-MARGIN);
		glyphButton.setBounds(glyphX, MARGIN, GLYPH_SIZE, GLYPH_SIZE);

		int availableWidth=Math.max(0, width-(2*MARGIN));
		int desired=(int)Math.round(width*DRAWER_WIDTH_RATIO);
		int drawerWidth=Math.min(MAX_DRAWER_WIDTH, Math.max(MIN_DRAWER_WIDTH, desired));
		drawerWidth=Math.min(drawerWidth, availableWidth);
		int drawerHeight=Math.max(80, height-(2*MARGIN));
		int drawerX=Math.max(MARGIN, width-drawerWidth-MARGIN);
		drawerPanel.setBounds(drawerX, MARGIN, drawerWidth, drawerHeight);
		scrollPane.setPreferredSize(new Dimension(drawerWidth-12, Math.max(40, drawerHeight-70)));
		drawerPanel.revalidate();
	}

	private JPanel buildLegendRow(LegendItem item, String label, String legendKey) {
		JPanel row=new JPanel(new BorderLayout(8, 0));
		row.setOpaque(true);
		row.setBackground(getPanelBackground());
		row.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		LegendSwatch swatch=new LegendSwatch(item);
		swatch.setPreferredSize(new Dimension(20, 14));
		row.add(swatch, BorderLayout.WEST);

		JLabel text=new JLabel(label);
		text.setForeground(getPanelForeground());
		text.setToolTipText(label);
		row.add(text, BorderLayout.CENTER);

		int rowHeight=Math.max(22, row.getPreferredSize().height);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowHeight));
		row.setPreferredSize(new Dimension(row.getPreferredSize().width, rowHeight));

		MouseAdapter clickListener=new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				selectLegendEntry(legendKey);
			}
		};
		addClickListenersRecursive(row, clickListener);
		addHoverListenersRecursive(row);
		return row;
	}

	private void addClickListenersRecursive(Component component, MouseAdapter listener) {
		component.addMouseListener(listener);
		if (component instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				addClickListenersRecursive(child, listener);
			}
		}
	}

	private void applyFilter() {
		String query=filterField.getText();
		String normalized=(query==null)?"":query.trim().toLowerCase(Locale.ROOT);

		rowsPanel.removeAll();
		int visibleCount=0;
		for (LegendRow row : legendRows) {
			boolean visible=normalized.isEmpty()||row.normalizedLabel.contains(normalized);
			if (!visible) continue;
			applyRowSelectionStyle(row, row.legendKey.equals(selectedLegendKey));
			rowsPanel.add(row.component);
			visibleCount++;
		}

		if (visibleCount==0) {
			emptyLabel.setText(legendRows.isEmpty()?"No legend entries":"No legend entries match filter");
			rowsPanel.add(emptyLabel);
		}
		rowsPanel.revalidate();
		rowsPanel.repaint();
	}

	private void selectLegendEntry(String legendKey) {
		if (legendKey==null||legendKey.isBlank()) return;
		if (legendKey.equals(selectedLegendKey)) {
			clearLegendSelection();
			return;
		}
		selectedLegendKey=legendKey;
		for (LegendRow row : legendRows) {
			applyRowSelectionStyle(row, row.legendKey.equals(legendKey));
			if (row.legendKey.equals(legendKey)) {
				applySelectedTraceHalo(row);
			}
		}
		rowsPanel.repaint();
	}

	private void clearLegendSelection() {
		selectedLegendKey=null;
		clearSelectedTraceHalo();
		for (LegendRow row : legendRows) {
			applyRowSelectionStyle(row, false);
		}
		rowsPanel.repaint();
	}

	private void handleChartClick(ChartMouseEvent event) {
		if (event==null) return;
		MouseEvent trigger=event.getTrigger();
		ChartEntity entity=event.getEntity();
		JFreeChart chart=chartPanel.getChart();
		if (!(chart!=null&&chart.getPlot() instanceof XYPlot xyPlot)) return;
		if (entity instanceof XYItemEntity xyEntity) {
			XYDataset dataset=xyEntity.getDataset();
			if (dataset==null) return;
			int datasetIndex=resolveDatasetIndex(xyPlot, dataset);
			if (datasetIndex>=0) {
				selectLegendEntryFromTrace(datasetIndex, xyEntity.getSeriesIndex());
				return;
			}
		}
		TraceHit hit=findNearestTraceHit(xyPlot, trigger);
		if (hit==null) return;
		selectLegendEntryFromTrace(hit.datasetIndex, hit.seriesIndex);
	}

	private TraceHit findNearestTraceHit(XYPlot xyPlot, MouseEvent trigger) {
		if (xyPlot==null||trigger==null) return null;
		Rectangle2D dataArea=chartPanel.getScreenDataArea(trigger.getX(), trigger.getY());
		if (dataArea==null||dataArea.isEmpty()) {
			dataArea=chartPanel.getScreenDataArea();
		}
		if (dataArea==null||dataArea.isEmpty()) return null;
		if (xyPlot.getDomainAxis()==null||xyPlot.getRangeAxis()==null) return null;

		double clickX=trigger.getX();
		double clickY=trigger.getY();
		double toleranceSq=TRACE_CLICK_TOLERANCE_PX*TRACE_CLICK_TOLERANCE_PX;
		TraceHit bestHit=null;
		for (int datasetIndex=0; datasetIndex<xyPlot.getDatasetCount(); datasetIndex++) {
			XYDataset dataset=xyPlot.getDataset(datasetIndex);
			if (dataset==null) continue;
			for (int seriesIndex=0; seriesIndex<dataset.getSeriesCount(); seriesIndex++) {
				int itemCount=dataset.getItemCount(seriesIndex);
				if (itemCount<2) continue;
				for (int i=1; i<itemCount; i++) {
					double x1=dataset.getXValue(seriesIndex, i-1);
					double y1=dataset.getYValue(seriesIndex, i-1);
					double x2=dataset.getXValue(seriesIndex, i);
					double y2=dataset.getYValue(seriesIndex, i);
					if (!Double.isFinite(x1)||!Double.isFinite(y1)||!Double.isFinite(x2)||!Double.isFinite(y2)) continue;
					double sx1=xyPlot.getDomainAxis().valueToJava2D(x1, dataArea, xyPlot.getDomainAxisEdge());
					double sy1=xyPlot.getRangeAxis().valueToJava2D(y1, dataArea, xyPlot.getRangeAxisEdge());
					double sx2=xyPlot.getDomainAxis().valueToJava2D(x2, dataArea, xyPlot.getDomainAxisEdge());
					double sy2=xyPlot.getRangeAxis().valueToJava2D(y2, dataArea, xyPlot.getRangeAxisEdge());
					double distanceSq=distancePointToSegmentSq(clickX, clickY, sx1, sy1, sx2, sy2);
					if (distanceSq>toleranceSq) continue;
					if (bestHit==null||distanceSq<bestHit.distanceSq) {
						bestHit=new TraceHit(datasetIndex, seriesIndex, distanceSq);
					}
				}
			}
		}
		return bestHit;
	}

	private double distancePointToSegmentSq(double px, double py, double x1, double y1, double x2, double y2) {
		double dx=x2-x1;
		double dy=y2-y1;
		if (dx==0.0&&dy==0.0) {
			double sx=px-x1;
			double sy=py-y1;
			return (sx*sx)+(sy*sy);
		}
		double t=((px-x1)*dx+(py-y1)*dy)/((dx*dx)+(dy*dy));
		double clampedT=Math.max(0.0, Math.min(1.0, t));
		double closestX=x1+(clampedT*dx);
		double closestY=y1+(clampedT*dy);
		double ex=px-closestX;
		double ey=py-closestY;
		return (ex*ex)+(ey*ey);
	}

	private void selectLegendEntryFromTrace(int datasetIndex, int seriesIndex) {
		LegendRow row=findLegendRowForTrace(datasetIndex, seriesIndex);
		if (row==null) return;
		selectLegendEntry(row.legendKey);
	}

	private LegendRow findLegendRowForTrace(int datasetIndex, int seriesIndex) {
		for (LegendRow row : legendRows) {
			if (row.datasetIndex==datasetIndex&&row.seriesIndex==seriesIndex) {
				return row;
			}
		}
		JFreeChart chart=chartPanel.getChart();
		if (!(chart!=null&&chart.getPlot() instanceof XYPlot xyPlot)) return null;
		XYDataset dataset=xyPlot.getDataset(datasetIndex);
		if (dataset==null||seriesIndex<0||seriesIndex>=dataset.getSeriesCount()) return null;
		Comparable<?> seriesKey=dataset.getSeriesKey(seriesIndex);
		if (seriesKey==null) return null;
		String normalizedKey=seriesKey.toString().toLowerCase(Locale.ROOT);
		for (LegendRow row : legendRows) {
			if (row.normalizedLabel.equals(normalizedKey)) {
				return row;
			}
		}
		return null;
	}

	private int resolveDatasetIndex(XYPlot xyPlot, XYDataset dataset) {
		for (int i=0; i<xyPlot.getDatasetCount(); i++) {
			if (xyPlot.getDataset(i)==dataset) return i;
		}
		return -1;
	}

	private void applyRowSelectionStyle(LegendRow row, boolean selected) {
		Color background=selected?new Color(255, 235, 59, 55):getPanelBackground();
		row.component.setBackground(background);
		row.component.setBorder(selected?BorderFactory.createLineBorder(new Color(255, 235, 59, 180))
				:BorderFactory.createEmptyBorder(4, 4, 4, 4));
		row.swatch.setSelected(selected);
	}

	private void applySelectedTraceHalo(LegendRow row) {
		clearSelectedTraceHalo();
		JFreeChart chart=chartPanel.getChart();
		if (chart==null) return;
		if (!(chart.getPlot() instanceof XYPlot xyPlot)) return;
		int datasetIndex=row.datasetIndex;
		int seriesIndex=row.seriesIndex;
		if (datasetIndex<0&&xyPlot.getDatasetCount()>0) {
			datasetIndex=0;
		}
		if (datasetIndex<0) return;
		XYDataset dataset=xyPlot.getDataset(datasetIndex);
		if (dataset==null) return;
		if (seriesIndex<0||seriesIndex>=dataset.getSeriesCount()) {
			seriesIndex=findSeriesIndexByLabel(dataset, row);
		}
		if (seriesIndex<0||seriesIndex>=dataset.getSeriesCount()) return;

		float baseStroke=2.0f;
		XYItemRenderer renderer=xyPlot.getRenderer(datasetIndex);
		if (renderer!=null) {
			Stroke stroke=renderer.getSeriesStroke(seriesIndex);
			if (stroke==null) stroke=renderer.getDefaultStroke();
			if (stroke instanceof BasicStroke bs&&Float.isFinite(bs.getLineWidth())) {
				baseStroke=Math.max(1.0f, bs.getLineWidth());
			}
		}
		BasicStroke outer=new BasicStroke(baseStroke+8.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		BasicStroke inner=new BasicStroke(baseStroke+4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

		int itemCount=dataset.getItemCount(seriesIndex);
		if (itemCount<2) return;
		for (int i=1; i<itemCount; i++) {
			double x1=dataset.getXValue(seriesIndex, i-1);
			double y1=dataset.getYValue(seriesIndex, i-1);
			double x2=dataset.getXValue(seriesIndex, i);
			double y2=dataset.getYValue(seriesIndex, i);
			if (!Double.isFinite(x1)||!Double.isFinite(y1)||!Double.isFinite(x2)||!Double.isFinite(y2)) continue;
			XYLineAnnotation outerLine=new XYLineAnnotation(x1, y1, x2, y2, outer, HALO_OUTER_COLOR);
			XYLineAnnotation innerLine=new XYLineAnnotation(x1, y1, x2, y2, inner, HALO_COLOR);
			selectedHaloAnnotations.add(outerLine);
			selectedHaloAnnotations.add(innerLine);
			xyPlot.addAnnotation(outerLine, false);
			xyPlot.addAnnotation(innerLine, false);
		}
		chartPanel.repaint();
	}

	private int findSeriesIndexByLabel(XYDataset dataset, LegendRow row) {
		String lowerLabel=row.normalizedLabel;
		for (int i=0; i<dataset.getSeriesCount(); i++) {
			Comparable<?> key=dataset.getSeriesKey(i);
			if (key==null) continue;
			String normalized=key.toString().toLowerCase(Locale.ROOT);
			if (normalized.equals(lowerLabel)) return i;
		}
		return -1;
	}

	private void clearSelectedTraceHalo() {
		JFreeChart chart=chartPanel.getChart();
		if (!(chart!=null&&chart.getPlot() instanceof XYPlot xyPlot)) {
			selectedHaloAnnotations.clear();
			return;
		}
		for (XYAnnotation annotation : selectedHaloAnnotations) {
			xyPlot.removeAnnotation(annotation);
		}
		selectedHaloAnnotations.clear();
	}

	private String buildLegendKey(LegendItem item) {
		String label=item.getLabel()==null?"":item.getLabel();
		return item.getDatasetIndex()+":"+item.getSeriesIndex()+":"+label;
	}

	private List<LegendItem> extractLegendItems() {
		ArrayList<LegendItem> items=new ArrayList<>();
		JFreeChart chart=chartPanel.getChart();
		if (chart==null) return items;

		LegendTitle legend=chart.getLegend();
		if (legend!=null) {
			LegendItemSource[] sources=legend.getSources();
			if (sources!=null) {
				for (LegendItemSource source : sources) {
					if (source==null) continue;
					appendLegendCollection(items, source.getLegendItems());
				}
			}
		}

		if (!items.isEmpty()) return items;

		Plot plot=chart.getPlot();
		if (plot instanceof XYPlot xyPlot) {
			appendLegendCollection(items, xyPlot.getLegendItems());
		} else if (plot instanceof CategoryPlot categoryPlot) {
			appendLegendCollection(items, categoryPlot.getLegendItems());
		}
		return items;
	}

	private void appendLegendCollection(List<LegendItem> target, LegendItemCollection collection) {
		if (collection==null) return;
		for (int i=0; i<collection.getItemCount(); i++) {
			LegendItem item=collection.get(i);
			if (item==null) continue;
			target.add(item);
		}
	}

	private static Color getPanelBackground() {
		Color background=UIManager.getColor("Panel.background");
		return background==null?Color.WHITE:background;
	}

	private static Color getPanelForeground() {
		Color foreground=UIManager.getColor("Panel.foreground");
		if (foreground!=null) return foreground;
		Color background=getPanelBackground();
		double brightness=0.2126*background.getRed()+0.7152*background.getGreen()+0.0722*background.getBlue();
		return brightness<128.0?Color.LIGHT_GRAY:Color.BLACK;
	}

	private static Color getMutedForeground() {
		Color foreground=getPanelForeground();
		return new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 170);
	}

	private static Color getBorderColor() {
		Color grid=UIManager.getColor("Table.gridColor");
		if (grid!=null) return grid;
		Color foreground=getPanelForeground();
		return new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 120);
	}

	private static final class LegendSwatch extends JPanel {
		private static final long serialVersionUID=1L;
		private final LegendItem item;
		private boolean selected;

		private LegendSwatch(LegendItem item) {
			this.item=item;
			setOpaque(false);
		}

		private void setSelected(boolean selected) {
			this.selected=selected;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g2=(Graphics2D)graphics.create();
			try {
				int width=getWidth();
				int height=getHeight();
				int centerY=Math.max(1, height/2);
				if (selected) {
					g2.setColor(new Color(255, 235, 59, 65));
					g2.fillRoundRect(0, 0, width, height, 8, 8);
				}
				Paint linePaint=item.getLinePaint()!=null?item.getLinePaint():item.getFillPaint();
				Paint fillPaint=item.getFillPaint()!=null?item.getFillPaint():item.getLinePaint();
				if (linePaint==null) linePaint=getPanelForeground();
				if (fillPaint==null) fillPaint=linePaint;

				if (item.isLineVisible()) {
					if (selected) {
						g2.setPaint(HALO_OUTER_COLOR);
						g2.setStroke(new BasicStroke(8.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
						g2.drawLine(2, centerY, Math.max(2, width-2), centerY);
						g2.setPaint(HALO_COLOR);
						g2.setStroke(new BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
						g2.drawLine(2, centerY, Math.max(2, width-2), centerY);
					}
					g2.setPaint(linePaint);
					Stroke stroke=item.getLineStroke();
					g2.setStroke(stroke!=null?stroke:new BasicStroke(2.0f));
					g2.drawLine(2, centerY, Math.max(2, width-2), centerY);
				}

				Shape shape=item.getShape();
				if (item.isShapeVisible()&&shape!=null) {
					Rectangle2D bounds=shape.getBounds2D();
					double scale=Math.min((width-4)/Math.max(1.0, bounds.getWidth()), (height-4)/Math.max(1.0, bounds.getHeight()));
					AffineTransform tx=new AffineTransform();
					tx.translate(width/2.0, height/2.0);
					tx.scale(scale, scale);
					tx.translate(-bounds.getCenterX(), -bounds.getCenterY());
					Shape transformed=tx.createTransformedShape(shape);
					g2.setPaint(fillPaint);
					if (item.isShapeFilled()) {
						g2.fill(transformed);
					}
					if (item.isShapeOutlineVisible()) {
						Paint outline=item.getOutlinePaint()!=null?item.getOutlinePaint():getPanelForeground();
						g2.setPaint(outline);
						Stroke outlineStroke=item.getOutlineStroke();
						g2.setStroke(outlineStroke!=null?outlineStroke:new BasicStroke(1.0f));
						g2.draw(transformed);
					}
				}
			} finally {
				g2.dispose();
			}
		}
	}
}
