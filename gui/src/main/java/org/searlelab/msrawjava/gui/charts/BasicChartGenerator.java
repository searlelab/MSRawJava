package org.searlelab.msrawjava.gui.charts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.font.TextAttribute;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.text.AttributedString;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.RepaintManager;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.AbstractXYItemRenderer;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.graphics2d.svg.SVGGraphics2D;
import org.jfree.graphics2d.svg.SVGUtils;
import org.openpdf.text.Document;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfGraphics2D;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;
import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.io.utils.Triplet;
import org.searlelab.msrawjava.logging.Logger;

public class BasicChartGenerator {
	private static final DecimalFormat MASS_FORMAT=new DecimalFormat(".#");
	public static final String BASE_FONT_NAME="Arial";

	private static BasicStroke peakStroke=new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	
	public static ExtendedChartPanel getChart(String xAxis, String yAxis, boolean displayLegend, XYTraceInterface... traces) {
		return getChart(xAxis, yAxis, displayLegend, 0.0, 14, traces);
	}

	public static ExtendedChartPanel getChart(final String xAxis, String yAxis, boolean displayLegend, double maxY, int fontSize,
			final XYTraceInterface... traces) {
		if (maxY==0.0&&traces.length>0) {
			maxY=XYTrace.getMaxY(traces)*1.05;
		}

		Font font=new Font(BASE_FONT_NAME, Font.PLAIN, fontSize);
		Font font2=new Font(BASE_FONT_NAME, Font.PLAIN, fontSize);
		Font font3=new Font(BASE_FONT_NAME, Font.PLAIN, fontSize);
		Font font4=new Font(BASE_FONT_NAME, Font.PLAIN, fontSize-4);

		Pair<AttributedString, Double> axisScale=getAxisScale(yAxis, maxY, fontSize);

		double divider=axisScale.y;
		AttributedString yAxisLabel=axisScale.x;

		XYPlot plot=new XYPlot();
		NumberAxis numberaxis=new NumberAxis(xAxis);
		numberaxis.setAutoRangeIncludesZero(false);
		NumberAxis numberaxis1;
		if (divider==1) {
			numberaxis1=new NumberAxis(yAxis);
		} else {
			numberaxis1=new NumberAxis();
			numberaxis1.setAttributedLabel(yAxisLabel);
		}
		//numberaxis1.setAutoRangeIncludesZero(false);
		plot.setDomainAxis(numberaxis);
		plot.setRangeAxis(numberaxis1);

		int count=0;
		for (XYTraceInterface trace : traces) {
			Triplet<AbstractXYItemRenderer, XYSeriesCollection, ArrayList<XYTextAnnotation>> traceData=getTraceData(trace, divider);
			
			plot.setDataset(count, traceData.y);
			plot.setRenderer(count, traceData.x);
			for (XYTextAnnotation annotation : traceData.z) {
				annotation.setFont(font4);
				plot.addAnnotation(annotation);
			}

			count++;
		}

		plot.setBackgroundPaint(Color.white);
		plot.setDomainGridlinePaint(Color.white);
		plot.setDomainGridlinesVisible(false);
		plot.setRangeGridlinePaint(Color.white);
		plot.setRangeGridlinesVisible(false);
		JFreeChart chart=new JFreeChart(plot);
		chart.setBackgroundPaint(Color.white);
		chart.setPadding(new RectangleInsets(10, 10, 10, 10));

		NumberAxis rangeAxis=(NumberAxis)((XYPlot)plot).getRangeAxis();
		rangeAxis.setLabelFont(font2);
		rangeAxis.setTickLabelFont(font);

		NumberAxis domainAxis=(NumberAxis)((XYPlot)plot).getDomainAxis();
		if (domainAxis!=null) {
			domainAxis.setLabelFont(font2);
			domainAxis.setTickLabelFont(font);
		}
		
		String name;
		if (traces.length>0) {
			name=traces[0].getName();
		} else {
			name=yAxis+"_by_"+xAxis;
		}

		final ExtendedChartPanel chartPanel=new ExtendedChartPanel(chart, name, false, divider);
		if (!displayLegend) {
			chartPanel.getChart().removeLegend();
		} else {
			chartPanel.getChart().getLegend().setItemFont(font3);
		}
		addSaveMenu(xAxis, chartPanel, traces);

		chartPanel.setMinimumDrawWidth(0);
		chartPanel.setMinimumDrawHeight(0);
		chartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
		chartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);

		boolean isSpectrum=false;
		for (XYTraceInterface trace : traces) {
			if (trace.getType()==GraphType.spectrum) {
				isSpectrum=true;
				break;
			}
		}
		if (isSpectrum&&maxY>0&&divider>0) numberaxis1.setUpperBound(maxY/divider);
		return chartPanel;
	}

	public static Triplet<AbstractXYItemRenderer, XYSeriesCollection, ArrayList<XYTextAnnotation>> getTraceData(XYTraceInterface trace, double divider) {
		AbstractXYItemRenderer renderer=new XYLineAndShapeRenderer();
		XYSeriesCollection dataset=new XYSeriesCollection();
		ArrayList<XYTextAnnotation> annotations=new ArrayList<>();
		
		
		switch (trace.getType()) {
			case area:
				renderer=new XYAreaRenderer();
				renderer.setSeriesStroke(0, new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				break;

			case line:
				renderer=new XYLineAndShapeRenderer();
				renderer.setSeriesStroke(0, new BasicStroke(trace.getThickness().orElse(2.0f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				((XYLineAndShapeRenderer)renderer).setDefaultShapesVisible(false);

				break;

			case boldline:
				renderer=new XYLineAndShapeRenderer();
				renderer.setSeriesStroke(0, new BasicStroke(trace.getThickness().orElse(5.0f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				((XYLineAndShapeRenderer)renderer).setDefaultShapesVisible(false);

				break;

			case squaredline:
				renderer=new XYLineAndShapeRenderer();
				renderer.setSeriesStroke(0, new BasicStroke(trace.getThickness().orElse(5.0f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
				((XYLineAndShapeRenderer)renderer).setDefaultShapesVisible(false);

				break;

			case bolddashedline:
				renderer=new XYLineAndShapeRenderer();
				Float thickness=trace.getThickness().orElse(5.0f);
				if (thickness>5) {
					renderer.setSeriesStroke(0,
							new BasicStroke(thickness, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10.0f, new float[] {12.0f, 16.0f}, 0.0f));
				} else {
					renderer.setSeriesStroke(0,
							new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0.0f, new float[] {3.0f, 5.0f}, 0.0f));
				}
				((XYLineAndShapeRenderer)renderer).setDrawSeriesLineAsPath(true);
				((XYLineAndShapeRenderer)renderer).setDefaultShapesVisible(false);

				break;

			case dashedline:
				renderer=new XYLineAndShapeRenderer();
				thickness=trace.getThickness().orElse(2.0f);
				if (thickness>5) {
					renderer.setSeriesStroke(0,
							new BasicStroke(thickness, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10.0f, new float[] {12.0f, 16.0f}, 0.0f));
				} else {
					renderer.setSeriesStroke(0,
							new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0.0f, new float[] {3.0f, 5.0f}, 0.0f));
				}
				((XYLineAndShapeRenderer)renderer).setDrawSeriesLineAsPath(true);
				((XYLineAndShapeRenderer)renderer).setDefaultShapesVisible(false);

				break;

			case bighollowpoint:
				renderer=new XYLineAndShapeRenderer();
				renderer.setSeriesShape(0, createRingShape(0, 0, 2.5, 0.75));

				((XYLineAndShapeRenderer)renderer).setDefaultLinesVisible(false);

				break;

			case bigpoint:
				renderer=new XYLineAndShapeRenderer();
				renderer.setSeriesShape(0, new Ellipse2D.Double(-2.5, -2.5, 5, 5));

				((XYLineAndShapeRenderer)renderer).setDefaultLinesVisible(false);

				break;

			case point:
				renderer=new XYLineAndShapeRenderer();
				renderer.setSeriesShape(0, new Ellipse2D.Double(-1.5, -1.5, 3, 3));
				((XYLineAndShapeRenderer)renderer).setDefaultLinesVisible(false);

				break;

			case tinypoint:
				renderer=new XYLineAndShapeRenderer();
				renderer.setSeriesShape(0, new Ellipse2D.Double(-0.5, -0.5, 1, 1));
				((XYLineAndShapeRenderer)renderer).setDefaultLinesVisible(false);

				break;

			case uncenteredText:
			case text:
				renderer=new XYLineAndShapeRenderer();
				renderer.setSeriesShape(0, new Ellipse2D.Double(-0.5, -0.5, 1, 1));
				((XYLineAndShapeRenderer)renderer).setDefaultLinesVisible(false);

				break;

			case spectrum:
				renderer=new XYLineAndShapeRenderer();
				((XYLineAndShapeRenderer)renderer).setDefaultLinesVisible(false);
				renderer.setDefaultPaint(Color.DARK_GRAY);

				break;

			case imsspectrum:
				renderer=new XYLineAndShapeRenderer();
				renderer.setSeriesShape(0, createRingShape(0, 0, 2.5, 0.75));
				((XYLineAndShapeRenderer)renderer).setDefaultLinesVisible(false);

				break;

			default:
				throw new RuntimeException("unsupported graphing type!");
		}

		Pair<double[], double[]> values=trace.toArrays();
		double[] x=values.x;
		double[] y=MatrixMath.divide(values.y, divider);
		switch (trace.getType()) {
			case area:
			case line:
			case boldline:
			case squaredline:
			case dashedline:
			case bolddashedline:
			case bighollowpoint:
			case bigpoint:
			case point:
			case tinypoint:
				XYSeries series=new XYSeries(trace.getName(), false);
				for (int i=0; i<x.length; i++) {
					series.add(x[i], y[i]);
				}
				dataset.addSeries(series);
				break;

			case imsspectrum: {
				float[] ims=new float[x.length];
				if (trace instanceof AcquiredSpectrumWrapper) {
					if (((AcquiredSpectrumWrapper)trace).getSpectrum().getIonMobilityArray().isPresent()) {
						ims=((AcquiredSpectrumWrapper)trace).getSpectrum().getIonMobilityArray().get();
						// otherwise set it to an empty array
					}
				}

				double maxIntensity=MatrixMath.max(y);
				if (!(maxIntensity>0.0)||Double.isNaN(maxIntensity)) {
					// No signal to render; avoid NaN/Inf sizes.
					break;
				}
				int seriesCount=0;
				for (int i=0; i<x.length; i++) {
					if (!Double.isFinite(x[i])||!Double.isFinite(y[i])) {
						continue;
					}
					if (ims.length>i&&!Float.isFinite(ims[i])) {
						continue;
					}
					if (y[i]/maxIntensity<0.01) {
						// ignore peaks that are less than 1%
						continue;
					}

					XYSeries peakSeries=new XYSeries(i);
					peakSeries.add(ims[i], x[i]);
					dataset.addSeries(peakSeries);

					// dot size is related to the sqrt of intensity (area=pi*r*r)
					double intensity=Math.sqrt(y[i]/maxIntensity);

					double diameter=intensity*20.0;
					double negHalfDiameter=-diameter/2.0;

					Color color=new Color(0.0f, 0.0f, 0.0f, (float)intensity);
					renderer.setSeriesShape(seriesCount, new Ellipse2D.Double(negHalfDiameter, negHalfDiameter, diameter, diameter));
					renderer.setSeriesPaint(seriesCount, color);
					seriesCount++;
				}
			}
				break;

			case spectrum:
				// IGNORE: just annotate the top 5 ions that don't already have labels and above 20%
				//double[] intensities=y.clone();
				//Arrays.sort(intensities);
				double yThreshold=0;//intensities.length>0?intensities[Math.max(0, intensities.length-5)]:0.0;
				yThreshold=Double.MAX_VALUE;//Math.max(General.max(y)*0.1, yThreshold);

				for (int i=0; i<x.length; i++) {
					if (!Double.isNaN(x[i])&&!Double.isNaN(y[i])) {
						XYSeries peakSeries=new XYSeries(i);
						peakSeries.add(x[i], 0);
						peakSeries.add(x[i], y[i]);
						dataset.addSeries(peakSeries);

						renderer.setSeriesStroke(i, peakStroke);
						renderer.setSeriesPaint(i, Color.DARK_GRAY);

						boolean aboveThreshold=y[i]<0?y[i]<-yThreshold:y[i]>yThreshold;
						if (aboveThreshold) {
							XYTextAnnotation xytextannotation=new XYTextAnnotation(MASS_FORMAT.format(x[i]), x[i], y[i]);
							xytextannotation.setPaint(Color.DARK_GRAY);
							xytextannotation.setTextAnchor(y[i]<0?TextAnchor.TOP_CENTER:TextAnchor.BOTTOM_CENTER);
							annotations.add(xytextannotation);
						}
					}
				}

				double maxX=0.0;
				double minY=0.0;
				for (int i=0; i<x.length; i++) {
					if (x[i]>maxX) maxX=x[i];
					if (y[i]<minY) minY=y[i];
				}
				if (minY<0.0) {
					// some negative, so plot baseline (+5%)
					int index=dataset.getSeriesCount();
					XYSeries peakSeries=new XYSeries(index);
					peakSeries.add(0, 0);
					peakSeries.add(maxX*1.05, 0);
					dataset.addSeries(peakSeries);
					renderer.setSeriesStroke(index, new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					renderer.setSeriesPaint(index, Color.GRAY);
				}
				break;

			case text:
				for (int i=0; i<x.length; i++) {
					if (!Double.isNaN(x[i])&&!Double.isNaN(y[i])) {
						XYTextAnnotation annotation=new XYTextAnnotation(trace.getName(), x[i], y[i]*1.01);
						annotations.add(annotation);
					}
				}
				break;

			case uncenteredText:
				for (int i=0; i<x.length; i++) {
					if (!Double.isNaN(x[i])&&!Double.isNaN(y[i])) {
						XYTextAnnotation annotation=new XYTextAnnotation(trace.getName(), x[i], y[i]*1.01);
						annotation.setTextAnchor(TextAnchor.TOP_LEFT);
						annotations.add(annotation);
					}
				}
				break;

			default:
				throw new RuntimeException("unsupported graphing type!");
		}
		if (trace.getColor().isPresent()) {
			renderer.setSeriesPaint(0, trace.getColor().get());
			renderer.setDefaultPaint(trace.getColor().get());
		}

		Triplet<AbstractXYItemRenderer, XYSeriesCollection, ArrayList<XYTextAnnotation>> traceData=new Triplet<>(renderer, dataset, annotations);
		return traceData;
	}

	private static void addSaveMenu(final String xAxis, final ExtendedChartPanel chartPanel, final XYTraceInterface... traces) {
		JMenuItem savePDF=new JMenuItem("Save as PDF");
		chartPanel.getPopupMenu().add(savePDF, 0);
		savePDF.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				FileDialog dialog=new FileDialog((Frame)null, "Save PDF File", FileDialog.SAVE);
				dialog.setFile(chartPanel.getName()+".pdf");

				FilenameFilter pdfFilter=(dir, name) -> name.toLowerCase().endsWith(".pdf");
				dialog.setFilenameFilter(pdfFilter);

				dialog.setVisible(true);
				File[] fs=dialog.getFiles();

				if (fs.length>0) {
					File saveFile=fs[0];
					if (!saveFile.getName().toLowerCase().endsWith(".pdf")) {
						saveFile=new File(saveFile.getParentFile(), saveFile.getName()+".pdf");
					}
					Logger.logLine("Writing PDF: "+saveFile.getAbsolutePath());
					writeAsPDF(chartPanel, saveFile, chartPanel.getSize());
					Logger.logLine("Finished writing PDF.");
				}
			}
		});
		
		JMenuItem saveSVG=new JMenuItem("Save as SVG");
		chartPanel.getPopupMenu().add(saveSVG, 1);
		saveSVG.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				FileDialog dialog=new FileDialog((Frame)null, "Save SVG File", FileDialog.SAVE);
				dialog.setFile(chartPanel.getName()+".svg");

				FilenameFilter svgFilter=(dir, name) -> name.toLowerCase().endsWith(".svg");
				dialog.setFilenameFilter(svgFilter);

				dialog.setVisible(true);
				File[] fs=dialog.getFiles();

				if (fs.length>0) {
					File saveFile=fs[0];
					if (!saveFile.getName().toLowerCase().endsWith(".svg")) {
						saveFile=new File(saveFile.getParentFile(), saveFile.getName()+".svg");
					}
					Logger.logLine("Writing SVG: "+saveFile.getAbsolutePath());
					writeAsSVG(chartPanel, saveFile, chartPanel.getSize());
					Logger.logLine("Finished writing SVG.");
				}
			}
		});

		JMenuItem copyImage=new JMenuItem("Copy as image");
		chartPanel.getPopupMenu().add(copyImage, 2);
		copyImage.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				BufferedImage image=new BufferedImage(chartPanel.getWidth(), chartPanel.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics g=image.getGraphics();
				chartPanel.paint(g);
				g.dispose();
				TransferableImage trans=new TransferableImage(image);
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(trans, null);
			}
		});

		JMenuItem copyItem=new JMenuItem("Copy data values");
		chartPanel.getPopupMenu().add(copyItem, 3);
		copyItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				StringBuilder header=new StringBuilder("Row");
				int length=0;
				for (XYTraceInterface trace : traces) {
					if (trace.size()>length) length=trace.size();
				}
				StringBuilder[] rows=new StringBuilder[length];
				for (int i=0; i<rows.length; i++) {
					rows[i]=new StringBuilder(Integer.toString(i+1));
				}

				for (XYTraceInterface trace : traces) {
					header.append("\t"+xAxis+"\t"+trace.getName());

					Pair<double[], double[]> pairs=trace.toArrays();
					for (int i=0; i<rows.length; i++) {
						if (pairs.x.length>i) {
							rows[i].append("\t"+pairs.x[i]+"\t"+pairs.y[i]);
						} else {
							rows[i].append("\t\t");
						}
					}
				}
				StringBuilder sb=new StringBuilder(header.toString());
				sb.append("\n");
				for (int i=0; i<rows.length; i++) {
					sb.append(rows[i]);
					sb.append("\n");
				}
				StringSelection stringSelection=new StringSelection(sb.toString());
				Clipboard clipboard=Toolkit.getDefaultToolkit().getSystemClipboard();
				clipboard.setContents(stringSelection, null);
			}
		});
	}

	public static void writeAsSVG(JComponent panel, File f, Dimension d) {
		try {
			SVGGraphics2D g2=new SVGGraphics2D(d.width, d.height);
            Color bg = panel.getBackground() != null ? panel.getBackground() : Color.WHITE;
            g2.setColor(bg);
            g2.fillRect(0, 0, d.width, d.height);

            RepaintManager rm = RepaintManager.currentManager(panel);
            boolean oldDB = rm.isDoubleBufferingEnabled();
            Dimension oldSize = panel.getSize();
            try {
                rm.setDoubleBufferingEnabled(false);
                panel.doLayout();
                panel.printAll(g2); 
            } finally {
                panel.setSize(oldSize);
                rm.setDoubleBufferingEnabled(oldDB);
            }
            
			SVGUtils.writeToSVG(f, g2.getSVGElement());
		} catch (Exception e) {
			Logger.errorException(e);
		}
	}

	private static final double dpi=72.0;
	public static void writeAsPDF(JComponent panel, File f, Dimension d) {
	    try {
	        // Optional: embed fonts if you use text
	        FontFactory.defaultEmbedding = true;

	        // PDF sizes are in points (1/72").
	        int wPt = (int)Math.round(d.width * 72.0 / dpi);
	        int hPt = (int)Math.round(d.height * 72.0 / dpi);
	        
	        Rectangle page = new Rectangle(wPt, hPt);
	        Document document = new Document(page, 0, 0, 0, 0);

	        try (FileOutputStream os = new FileOutputStream(f)) {
	            PdfWriter writer =PdfWriter.getInstance(document, os);
	            document.open();

	            PdfContentByte cb = writer.getDirectContent();
	            PdfTemplate tpl = cb.createTemplate(wPt, hPt);

	            Graphics2D g2 = new PdfGraphics2D(tpl, wPt, hPt);

	    		if (panel instanceof ExtendedChartPanel) {
	    			((ExtendedChartPanel)panel).getChart().draw(g2, new Rectangle2D.Double(0,  0, wPt, hPt));
	    		} else {
		            Color bg = panel.getBackground() != null ? panel.getBackground() : Color.WHITE;
		            g2.setColor(bg);
		            g2.fillRect(0, 0, wPt, hPt);
	
		            // Snapshot the component at the desired size, without double buffering
		            RepaintManager rm = RepaintManager.currentManager(panel);
		            boolean oldDB = rm.isDoubleBufferingEnabled();
		            Dimension oldSize = panel.getSize();
		            try {
		                rm.setDoubleBufferingEnabled(false);
		                panel.setSize(new Dimension(wPt, hPt));
		                panel.doLayout();
		                panel.printAll(g2); 
		            } finally {
		                panel.setSize(oldSize);
		                rm.setDoubleBufferingEnabled(oldDB);
		            }
	    		}
	    		
	            g2.dispose();
	            cb.addTemplate(tpl, 0, 0);
	            document.close();
	        }
	    } catch (Exception e) {
	        Logger.errorException(e);
	    }
	}

	private static Pair<AttributedString, Double> getAxisScale(String yAxis, double maxY, int fontSize) {
		Font font2=new Font(BASE_FONT_NAME, Font.PLAIN, fontSize);
		HashMap<TextAttribute, Object> m=new HashMap<TextAttribute, Object>(font2.getAttributes());
		m.put(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUPER);
		Font font2super=new Font(m);

		AttributedString yAxisLabel;
		if (maxY>1e15) {
			yAxisLabel=new AttributedString(yAxis+" (1015)");
			yAxisLabel.addAttribute(TextAttribute.FONT, font2);
			yAxisLabel.addAttribute(TextAttribute.FONT, font2super, yAxis.length()+4, yAxis.length()+6);
			return new Pair<AttributedString, Double>(yAxisLabel, 1e15);
		} else if (maxY>1e12) {
			yAxisLabel=new AttributedString(yAxis+" (1012)");
			yAxisLabel.addAttribute(TextAttribute.FONT, font2);
			yAxisLabel.addAttribute(TextAttribute.FONT, font2super, yAxis.length()+4, yAxis.length()+6);
			return new Pair<AttributedString, Double>(yAxisLabel, 1e12);
		} else if (maxY>1e9) {
			yAxisLabel=new AttributedString(yAxis+" (109)");
			yAxisLabel.addAttribute(TextAttribute.FONT, font2);
			yAxisLabel.addAttribute(TextAttribute.FONT, font2super, yAxis.length()+4, yAxis.length()+5);
			return new Pair<AttributedString, Double>(yAxisLabel, 1e9);
		} else if (maxY>1e6) {
			yAxisLabel=new AttributedString(yAxis+" (106)");
			yAxisLabel.addAttribute(TextAttribute.FONT, font2);
			yAxisLabel.addAttribute(TextAttribute.FONT, font2super, yAxis.length()+4, yAxis.length()+5);
			return new Pair<AttributedString, Double>(yAxisLabel, 1e6);
		} else if (maxY>1e3) {
			yAxisLabel=new AttributedString(yAxis+" (103)");
			yAxisLabel.addAttribute(TextAttribute.FONT, font2);
			yAxisLabel.addAttribute(TextAttribute.FONT, font2super, yAxis.length()+4, yAxis.length()+5);
			return new Pair<AttributedString, Double>(yAxisLabel, 1e3);
		} else {
			yAxisLabel=new AttributedString(yAxis);
			yAxisLabel.addAttribute(TextAttribute.FONT, font2);
			return new Pair<AttributedString, Double>(yAxisLabel, 1.0);
		}
	}

	private static Shape createRingShape(double centerX, double centerY, double outerRadius, double thickness) {
		Ellipse2D outer=new Ellipse2D.Double(centerX-outerRadius, centerY-outerRadius, outerRadius+outerRadius, outerRadius+outerRadius);
		Ellipse2D inner=new Ellipse2D.Double(centerX-outerRadius+thickness, centerY-outerRadius+thickness, outerRadius+outerRadius-thickness-thickness,
				outerRadius+outerRadius-thickness-thickness);
		Area area=new Area(outer);
		area.subtract(new Area(inner));
		return area;
	}
}
