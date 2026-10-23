package org.searlelab.msrawjava.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.BasicStroke;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Highlights the entire directory browser while an external file is dragged over it. */
final class FileNavigationDropPane extends JPanel {
	private static final long serialVersionUID=1L;
	private boolean dropActive;

	FileNavigationDropPane(JScrollPane scroll, JTree tree, FileNavigationTransferHandler handler) {
		super(new BorderLayout());
		add(scroll, BorderLayout.CENTER);
		handler.installOn(this, this::setDropActive);
		handler.installOn(scroll, this::setDropActive);
		handler.installOn(scroll.getViewport(), this::setDropActive);
		handler.installOn(tree, this::setDropActive);
	}

	void setDropActive(boolean active) {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(() -> setDropActive(active));
			return;
		}
		if (dropActive==active) return;
		dropActive=active;
		repaint();
	}

	@Override
	protected void paintChildren(Graphics graphics) {
		super.paintChildren(graphics);
		if (!dropActive) return;
		Color color=UIManager.getColor("Component.focusColor");
		if (color==null) color=new Color(0x2675BF);
		Graphics2D g=(Graphics2D)graphics.create();
		try {
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 32));
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(color);
			g.setStroke(new BasicStroke(3));
			g.drawRect(1, 1, Math.max(0, getWidth()-3), Math.max(0, getHeight()-3));
		} finally {
			g.dispose();
		}
	}
}
