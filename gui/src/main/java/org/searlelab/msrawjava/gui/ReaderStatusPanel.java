package org.searlelab.msrawjava.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.logging.Logger;

/**
 * Panel showing reader status and progress updates.
 */
public class ReaderStatusPanel extends JPanel {
	private static final long serialVersionUID=1L;

	private static final Color GREEN=new Color(0x2e7d32);
	private static final Color RED=new Color(0xc62828);
	private static final Color YELLOW=new Color(0xf9a825);
	private static final Color OFF_GRAY=new Color(0xdddddd);

	private static final int BLINK_MS=500;

	private final StatusLine thermoLine=new StatusLine("Thermo");
	private final StatusLine brukerLine=new StatusLine("Bruker");
	private final StatusLine encyclopediaLine=new StatusLine("EncyclopeDIA");
	private final StatusLine mzmlLine=new StatusLine("mzML");
	private final Timer blinkTimer;
	private boolean blinkOn=true;

	public ReaderStatusPanel() {
		super(new BorderLayout());

		JPanel rows=new JPanel(new GridLayout(4, 1, 0, 4));
		rows.add(thermoLine);
		rows.add(brukerLine);
		rows.add(encyclopediaLine);
		rows.add(mzmlLine);

		setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		setToolTipText("Shows vendor reader availability using status lights for Thermo, Bruker, EncyclopeDIA, and mzML support.");
		add(rows, BorderLayout.CENTER);
		setPreferredSize(new Dimension(260, 100));
		setMinimumSize(new Dimension(200, 100));

		thermoLine.setState(StatusState.WAITING);
		thermoLine.setStatusText("Waiting for Thermo server...");

		brukerLine.setState(StatusState.OK);
		brukerLine.setStatusText("Available");

		encyclopediaLine.setState(StatusState.OK);
		encyclopediaLine.setStatusText("Available");

		mzmlLine.setState(StatusState.OK);
		mzmlLine.setStatusText("Available");

		blinkTimer=new Timer(BLINK_MS, e -> refreshStatuses());
		blinkTimer.setRepeats(true);
		blinkTimer.start();
		refreshStatuses();

		checkBrukerAvailability();
	}

	private void refreshStatuses() {
		boolean ready=ThermoServerPool.isReady();
		boolean starting=ThermoServerPool.isStarting();

		if (ready) {
			thermoLine.setState(StatusState.OK);
			thermoLine.setStatusText("Available");
		} else if (starting) {
			thermoLine.setState(StatusState.WAITING);
			thermoLine.setStatusText("Waiting for Thermo server...");
		} else {
			thermoLine.setState(StatusState.ERROR);
			thermoLine.setStatusText("Problem");
		}

		blinkOn=!blinkOn;
		thermoLine.setBlinkOn(blinkOn);
	}

	private void checkBrukerAvailability() {
		new SwingWorker<Boolean, Void>() {
			@Override
			protected Boolean doInBackground() {
				return isBrukerAvailable();
			}

			@Override
			protected void done() {
				boolean ok=false;
				try {
					ok=get();
				} catch (Exception ignored) {
					Logger.errorException(ignored);
					ok=false;
				}
				brukerLine.setState(ok?StatusState.OK:StatusState.ERROR);
				brukerLine.setStatusText(ok?"Available":"Problem");
			}
		}.execute();
	}

	private static boolean isBrukerAvailable() {
		try {
			Class.forName("org.searlelab.msrawjava.io.tims.TimsNative", true, ReaderStatusPanel.class.getClassLoader());
			return true;
		} catch (Throwable t) {
			Logger.errorException(t);
			return false;
		}
	}

	private enum StatusState {
		OK, WAITING, ERROR
	}

	private static final class StatusLine extends JPanel {
		private static final long serialVersionUID=1L;

		private final IndicatorLight light=new IndicatorLight();
		private final String sourceName;
		private final JLabel label;
		private final JLabel status=new JLabel();
		private StatusState state=StatusState.WAITING;

		private StatusLine(String name) {
			super(new FlowLayout(FlowLayout.LEFT, 6, 0));
			sourceName=name;
			label=new JLabel(name+":");
			Font base=label.getFont();
			label.setFont(base.deriveFont(Font.BOLD));
			status.setText("");
			add(light);
			add(label);
			add(status);
		}

		private void setStatusText(String text) {
			String resolved=text!=null?text:"";
			status.setText(resolved);
			String tooltip=sourceName+" reader status light and availability state: "+resolved+".";
			setToolTipText(tooltip);
			label.setToolTipText(tooltip);
			status.setToolTipText(tooltip);
			light.setToolTipText(tooltip);
		}

		private void setState(StatusState next) {
			state=next;
			switch (state) {
				case OK -> light.setColors(GREEN, GREEN);
				case ERROR -> light.setColors(RED, RED);
				case WAITING -> light.setColors(YELLOW, OFF_GRAY);
			}
		}

		private void setBlinkOn(boolean on) {
			light.setOn(state!=StatusState.WAITING||on);
		}
	}

	private static final class IndicatorLight extends JComponent {
		private static final long serialVersionUID=1L;
		private static final int SIZE=12;
		private Color onColor=GREEN;
		private Color offColor=OFF_GRAY;
		private boolean on=true;

		private IndicatorLight() {
			setPreferredSize(new Dimension(SIZE, SIZE));
			setMinimumSize(new Dimension(SIZE, SIZE));
			setOpaque(false);
		}

		private void setColors(Color on, Color off) {
			onColor=on;
			offColor=off;
			repaint();
		}

		private void setOn(boolean on) {
			this.on=on;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2=(Graphics2D)g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int d=Math.min(getWidth(), getHeight())-2;
				int x=(getWidth()-d)/2;
				int y=(getHeight()-d)/2;
				g2.setColor(on?onColor:offColor);
				g2.fillOval(x, y, d, d);
				g2.setColor(new Color(0, 0, 0, 60));
				g2.drawOval(x, y, d, d);
			} finally {
				g2.dispose();
			}
		}
	}
}
