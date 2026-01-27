package org.searlelab.msrawjava.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import org.searlelab.msrawjava.logging.Logger;

public final class HowToCiteDialog {
	private HowToCiteDialog() {
	}

	public static void showDialog(java.awt.Frame parent) {
		JDialog dialog=new JDialog(parent, "How to cite", true);

		JPanel content=new JPanel(new BorderLayout());
		content.setBackground(Color.WHITE);

		JLabel graphic=new JLabel(new FlatSVGIcon("icons/icon.svg", 96, 96));
		JPanel head=new JPanel(new BorderLayout());
		head.setBackground(Color.WHITE);
		head.add(graphic, BorderLayout.NORTH);

		content.add(head, BorderLayout.NORTH);

		String message="<html><center><p style=\"font-size:12px; font-family: Helvetica, sans-serif\">"
				+"MSRawJava is a Searle Lab (searlelab.org) project at the Mayo Clinic "
				+"(https://www.mayoclinic.org) in the Department of Quantitative Health Sciences."
				+"</p></center></html>";

		JEditorPane about=new JEditorPane("text/html", message);
		about.setEditable(false);
		about.setBackground(Color.WHITE);
		content.add(about, BorderLayout.CENTER);

		String citeHtml="<html><p style=\"font-size:10px; font-family: Helvetica, sans-serif\">"
				+"Please cite the MSRawJava code repository:<br/>"
				+"<a href=\"https://github.com/searlelab/MSRawJava\">https://github.com/searlelab/MSRawJava</a>"
				+"</p></html>";

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
		main.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10),
				BorderFactory.createTitledBorder("MSRawJava")));
		main.setBackground(Color.WHITE);

		dialog.getContentPane().add(main, BorderLayout.CENTER);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.pack();
		dialog.setSize(450, 500);
		dialog.setVisible(true);
	}
}
