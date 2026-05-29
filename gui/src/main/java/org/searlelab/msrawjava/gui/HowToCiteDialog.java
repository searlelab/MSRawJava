package org.searlelab.msrawjava.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Image;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import org.searlelab.msrawjava.Version;
import org.searlelab.msrawjava.logging.Logger;

/**
 * Dialog showing citation guidance for the project.
 */
public final class HowToCiteDialog {
	private static final int SPLASH_WIDTH=300;
	private static final int SPLASH_HEIGHT=150;
	private static final int FALLBACK_ICON_SIZE=96;

	private HowToCiteDialog() {
	}

	public static void showDialog(java.awt.Frame parent) {
		JDialog dialog=new JDialog(parent, "How to cite "+ProductBranding.PRODUCT_NAME, true);

		JPanel content=new JPanel(new BorderLayout());
		content.setBackground(Color.WHITE);

		JLabel graphic=new JLabel(loadSplashIcon());
		JPanel head=new JPanel(new BorderLayout());
		head.setBackground(Color.WHITE);
		head.add(graphic, BorderLayout.NORTH);

		content.add(head, BorderLayout.NORTH);

		String message=aboutHtml();

		JEditorPane about=new JEditorPane("text/html", message);
		about.setEditable(false);
		about.setBackground(Color.WHITE);
		content.add(about, BorderLayout.CENTER);

		String citeHtml=citationHtml();

		JEditorPane cite=new JEditorPane("text/html", citeHtml);
		cite.setEditable(false);
		cite.setBackground(Color.WHITE);
		cite.addHyperlinkListener(new HyperlinkListener() {
			@Override
			public void hyperlinkUpdate(HyperlinkEvent e) {
				if (e.getEventType()!=HyperlinkEvent.EventType.ACTIVATED) return;
				if (!Desktop.isDesktopSupported()) return;
				try {
					Desktop.getDesktop().browse(e.getURL().toURI());
				} catch (IOException|URISyntaxException ex) {
					Logger.errorException(ex);
				}
			}
		});

		JPanel citePanel=new JPanel(new BorderLayout());
		citePanel.setBackground(Color.WHITE);
		JLabel citeIcon=new JLabel(new FlatSVGIcon("icons/icon.svg", 48, 48));
		citeIcon.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		citeIcon.setOpaque(true);
		citeIcon.setBackground(Color.WHITE);
		citePanel.add(citeIcon, BorderLayout.WEST);
		citePanel.add(cite, BorderLayout.CENTER);
		content.add(citePanel, BorderLayout.SOUTH);

		JButton ok=new JButton("OK");
		ok.setToolTipText("Close this citation dialog.");
		ok.addActionListener(e -> {
			dialog.setVisible(false);
			dialog.dispose();
		});
		JPanel buttons=new JPanel(new FlowLayout(FlowLayout.CENTER));
		buttons.setBackground(Color.WHITE);
		buttons.add(ok);

		JPanel main=new JPanel(new BorderLayout());
		main.add(content, BorderLayout.CENTER);
		main.add(buttons, BorderLayout.SOUTH);
		main.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), BorderFactory.createTitledBorder(ProductBranding.PRODUCT_NAME)));
		main.setBackground(Color.WHITE);

		dialog.getContentPane().add(main, BorderLayout.CENTER);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.pack();
		dialog.setSize(450, 600);
		dialog.setVisible(true);
	}

	static String aboutHtml() {
		return "<html><center><p style=\"font-size:12px; font-family: Helvetica, sans-serif\">"
				+ProductBranding.PRODUCT_NAME+" is a desktop tool for rapid mass spectrometry raw-file triage. It helps users scan acquisition directories, "
				+"spot failed or unusual injections, inspect raw files, extract targeted chromatograms, and decide which files are ready for downstream analysis.<br/><br/>"
				+ProductBranding.PRODUCT_NAME+" is a Searle Lab (searlelab.org) project at the Mayo Clinic "
				+"(https://www.mayoclinic.org) in the Department of Quantitative Health Sciences. "
				+ProductBranding.TAGLINE+"</p></center></html>";
	}

	static String citationHtml() {
		return "<html><p style=\"font-size:10px; font-family: Helvetica, sans-serif\">"+ProductBranding.PRODUCT_NAME+" is powered by the "
				+ProductBranding.CORE_NAME+" core library. Please cite the "+ProductBranding.CORE_NAME+" code repository:<br/>"
				+"<a href=\""+ProductBranding.REPOSITORY_URL+"\">"+ProductBranding.REPOSITORY_URL+"</a><br/><br/>"
				+"Version: "+Version.getVersion()+"<br/>"
				+"Build date: "+Version.getBuildDate()+"<br/>"
				+"JVM: "+Version.getJvmName()+" ("+Version.getJvmVersion()+")<br/>"
				+"Runtime: "+Version.getRuntimeName()+" ("+Version.getRuntimeVersion()+")<br/><br/>"
				+"RawFileReader reading tool. Copyright &copy; 2016 by Thermo Fisher Scientific, Inc. All rights reserved."
				+"</p></html>";
	}

	static javax.swing.Icon loadSplashIcon() {
		URL splashResource=HowToCiteDialog.class.getResource("/splash/splash@2x.png");
		if (splashResource==null) return new FlatSVGIcon("icons/icon.svg", FALLBACK_ICON_SIZE, FALLBACK_ICON_SIZE);
		ImageIcon retinaIcon=new ImageIcon(splashResource);
		Image scaledImage=retinaIcon.getImage().getScaledInstance(SPLASH_WIDTH, SPLASH_HEIGHT, Image.SCALE_SMOOTH);
		return new ImageIcon(scaledImage);
	}
}
