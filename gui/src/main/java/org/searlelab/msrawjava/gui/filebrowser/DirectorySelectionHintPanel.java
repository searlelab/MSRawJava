package org.searlelab.msrawjava.gui.filebrowser;

import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Friendly empty-state panel shown for top-level directory selections.
 */
public class DirectorySelectionHintPanel extends JPanel {
	private static final long serialVersionUID=1L;
	private static final String MESSAGE=
			"Please use the file tree for selecting a sub-directory<br>to visualize or convert raw files in that directory.";

	public DirectorySelectionHintPanel() {
		super(new GridBagLayout());

		JPanel content=new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		content.setOpaque(false);

		ImageIcon splashIcon=loadSplashIcon();
		if (splashIcon!=null) {
			JLabel splashLabel=new JLabel(splashIcon);
			splashLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			content.add(splashLabel);
			content.add(Box.createVerticalStrut(14));
		}

		JLabel messageLabel=new JLabel("<html><div style='text-align:center; width:520px;'>"+MESSAGE+"</div></html>");
		messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
		messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		Font baseFont=messageLabel.getFont();
		if (baseFont!=null) {
			messageLabel.setFont(baseFont.deriveFont(baseFont.getSize2D()*1.3f));
		}
		content.add(messageLabel);

		add(content);
	}

	private static ImageIcon loadSplashIcon() {
		URL splashResource=DirectorySelectionHintPanel.class.getResource("/splash/splash@2x.png");
		if (splashResource==null) return null;
		ImageIcon retinaIcon=new ImageIcon(splashResource);
		int scaledWidth=Math.max(1, retinaIcon.getIconWidth()/2);
		int scaledHeight=Math.max(1, retinaIcon.getIconHeight()/2);
		Image scaledImage=retinaIcon.getImage().getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
		return new ImageIcon(scaledImage);
	}
}
