package org.searlelab.msrawjava.gui.loadingpanels;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.formdev.flatlaf.FlatLightLaf;

/** FT-ICR loader: curved plates with phase difference, ions mid/outer radius (big=slower), FFT with m/z ticks. */
public class FTICRLoadingPanel extends LoadingPanel {
	private static final long serialVersionUID=1L;

	private final JLabel label=new JLabel("Loading…", SwingConstants.CENTER);
	private final JProgressBar bar=new JProgressBar();

	private final AnimatedICRCanvas canvas=new AnimatedICRCanvas();

	public FTICRLoadingPanel(String text) {
		setLayout(new GridBagLayout());
		setOpaque(true);

		GridBagConstraints c=new GridBagConstraints();
		c.gridx=0;
		c.gridy=0;
		c.weightx=1;
		c.weighty=1;
		c.fill=GridBagConstraints.BOTH;
		c.insets=new Insets(8, 8, 0, 8);
		add(canvas, c);

		String base=(text==null||text.isBlank())?"Loading":text;
		label.setText(base+"...");
		Dimension fixed=label.getPreferredSize();
		label.setPreferredSize(fixed); // lock to prevent relayout jitter
		c=new GridBagConstraints();
		c.gridx=0;
		c.gridy=1;
		c.weightx=1;
		c.fill=GridBagConstraints.HORIZONTAL;
		c.insets=new Insets(6, 8, 2, 8);
		add(label, c);

		bar.setIndeterminate(true);
		c=new GridBagConstraints();
		c.gridx=0;
		c.gridy=2;
		c.weightx=1;
		c.fill=GridBagConstraints.HORIZONTAL;
		c.insets=new Insets(2, 32, 8, 32);
		add(bar, c);
	}

	public void stop() {
		canvas.stop();
	}

	// -------------------------- Animated Canvas --------------------------

	private static class AnimatedICRCanvas extends JComponent {
		private static final long serialVersionUID=1L;

		private static final int ION_COUNT=18;
		private static final int FFT_BINS=12;
		private static final int FPS_MS=33; // ~30 FPS
		private static final double TWO_PI=Math.PI*2.0;
		private static final double DETECT_WINDOW=0.08; // ±rad around E/W
		private static final double DRIVE_HZ=1.1; // plate drive frequency (visual)

		private final java.util.Random rng=new java.util.Random(42);
		private final Ion[] ions=new Ion[ION_COUNT];

		// FFT bins (amplitude + color)
		private final double[] binAmp=new double[FFT_BINS];
		private final Color[] binColor=new Color[FFT_BINS];

		// frequency range from ions (rad/s)
		private double omegaMin, omegaMax;

		private Timer anim;
		private long lastNanos=0L;

		// Geometry cache
		private double trapR=0.0;
		private int cx=0, cy=0, size=0;
		private double gap, plateThk, plateRadius;
		private Shape ring;
		private Arc2D arcN, arcS, arcE, arcW;
		private Rectangle fftBox;
		private Line2D wireE, wireW;

		// Plate phase (N/S vs E/W are 90° out of phase)
		private double drivePhase=0.0;

		AnimatedICRCanvas() {
			setOpaque(true);

			for (int i=0; i<ions.length; i++)
				ions[i]=makeIon();
			computeFreqRangeAndAssign();

			for (int i=0; i<FFT_BINS; i++)
				binColor[i]=new Color(90, 130, 210);

			anim=new Timer(FPS_MS, e -> tick());
			anim.start();

			addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					recomputeGeometry();
				}

				@Override
				public void componentShown(ComponentEvent e) {
					recomputeGeometry();
				}
			});
		}

		void stop() {
			if (anim!=null) anim.stop();
		}

		@Override
		public void addNotify() {
			super.addNotify();
			lastNanos=System.nanoTime();
			if (anim!=null&&!anim.isRunning()) anim.start();
		}

		@Override
		public void removeNotify() {
			if (anim!=null) anim.stop();
			super.removeNotify();
		}

		private Ion makeIon() {
			Ion ion=new Ion();
			// Visual size: 4..11 px; speed inversely proportional to size
			ion.sizePx=4.0+rng.nextDouble()*7.0;
			double omegaBase=1.8; // baseline rad/s
			ion.omega=omegaBase*(11.0/ion.sizePx); // bigger -> slower
			ion.theta=rng.nextDouble()*TWO_PI;
			// Radius halfway-ish: 0.60..0.80 of trapR
			ion.rFrac=0.60+rng.nextDouble()*0.20;
			return ion;
		}

		private void computeFreqRangeAndAssign() {
			omegaMin=Double.POSITIVE_INFINITY;
			omegaMax=Double.NEGATIVE_INFINITY;
			for (Ion ion : ions) {
				omegaMin=Math.min(omegaMin, ion.omega);
				omegaMax=Math.max(omegaMax, ion.omega);
			}
			double span=Math.max(1e-6, omegaMax-omegaMin);

			for (Ion ion : ions) {
				double f=(ion.omega-omegaMin)/span; // 0..1
				ion.bin=Math.min(FFT_BINS-1, Math.max(0, (int)Math.round(f*(FFT_BINS-1))));
				ion.color=colorByFreq(f); // slow/red → fast/blue
			}
		}

		private static Color colorByFreq(double f01) {
			// Map 0..1 to HSB: 0≈red → 0.65≈blue
			float hue=(float)(0.02+0.63*f01);
			return Color.getHSBColor(hue, 0.70f, 1.0f);
		}

		private void tick() {
			long now=System.nanoTime();
			double dt=(lastNanos==0)?(FPS_MS/1000.0):(now-lastNanos)/1_000_000_000.0;
			dt=Math.min(dt, 0.05); // clamp stalls
			lastNanos=now;

			// Drive plates with 90° phase difference (N/S vs E/W)
			drivePhase+=TWO_PI*DRIVE_HZ*dt;
			if (drivePhase>=TWO_PI) drivePhase-=TWO_PI;

			// Exponential decay of FFT bars (half-life ~0.7 s)
			double halfLife=0.7;
			double decay=Math.pow(0.5, dt/halfLife);
			for (int i=0; i<FFT_BINS; i++)
				binAmp[i]*=decay;

			// Move ions; detector events excite their frequency bin
			for (Ion ion : ions) {
				ion.theta+=ion.omega*dt;
				if (ion.theta>=TWO_PI) ion.theta-=TWO_PI;

				boolean nearE=isNearAngle(ion.theta, 0.0, DETECT_WINDOW);
				boolean nearW=isNearAngle(ion.theta, Math.PI, DETECT_WINDOW);

				if (nearE&&!ion.wasNearE) hitBin(ion.bin, ion.color);
				if (nearW&&!ion.wasNearW) hitBin(ion.bin, ion.color);

				ion.wasNearE=nearE;
				ion.wasNearW=nearW;
			}

			repaint();
		}

		private static boolean isNearAngle(double theta, double target, double window) {
			double a=theta-target;
			a%=TWO_PI;
			if (a<-Math.PI) a+=TWO_PI;
			if (a>Math.PI) a-=TWO_PI;
			return Math.abs(a)<=window;
		}

		private void hitBin(int bin, Color c) {
			binAmp[bin]=Math.min(1.0, binAmp[bin]+0.45);
			binColor[bin]=c;
		}

		private void recomputeGeometry() {
			int w=getWidth(), h=getHeight();
			int pad=Math.max(8, Math.min(w, h)/10);
			size=Math.max(0, Math.min(w, h)-pad*2);
			cx=w/3;
			cy=h/2;

			trapR=size*0.52;
			gap=Math.max(6, size*0.04);
			plateThk=Math.max(10, size*0.06);
			plateRadius=trapR+gap+plateThk*0.5;

			ring=new java.awt.geom.Ellipse2D.Double(cx-trapR, cy-trapR, trapR*2, trapR*2);

			double extent=74; // degrees of each plate
			arcN=new Arc2D.Double(cx-plateRadius, cy-plateRadius, plateRadius*2, plateRadius*2, 90-extent/2, extent, Arc2D.OPEN);
			arcS=new Arc2D.Double(cx-plateRadius, cy-plateRadius, plateRadius*2, plateRadius*2, 270-extent/2, extent, Arc2D.OPEN);
			arcE=new Arc2D.Double(cx-plateRadius, cy-plateRadius, plateRadius*2, plateRadius*2, 0-extent/2, extent, Arc2D.OPEN);
			arcW=new Arc2D.Double(cx-plateRadius, cy-plateRadius, plateRadius*2, plateRadius*2, 180-extent/2, extent, Arc2D.OPEN);

			// FFT box (right of E plate)
			int boxW=Math.max(160, (int)(size*0.36));
			int boxH=Math.max(100, (int)(size*0.24));
			int boxX=Math.min(w-boxW-pad, (int)(cx+plateRadius+gap+plateThk));
			int boxY=cy-boxH/2;
			fftBox=new Rectangle(boxX, boxY, boxW, boxH);

			// Wires from E and W to FFT
			double xE=cx+plateRadius, yE=cy;
			double xW=cx-plateRadius, yW=cy;
			double xBox=fftBox.getX(), yBox=fftBox.getCenterY();
			wireE=new Line2D.Double(xE, yE, xBox, yBox);
			wireW=new Line2D.Double(xW, yW, xBox, yBox);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2=(Graphics2D)g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

				int w=getWidth(), h=getHeight();
				g2.setColor(getBackground()!=null?getBackground():Color.white);
				g2.fillRect(0, 0, w, h);

				if (trapR<=0) recomputeGeometry();

				// Trap ring
				g2.setColor(new Color(0, 0, 0, 28));
				g2.setStroke(new BasicStroke(1.8f));
				g2.draw(ring);

				// Base plate strokes
				Stroke plateStroke=new BasicStroke((float)plateThk, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
				g2.setStroke(plateStroke);

				// N/S excitation base
				Color excBase=new Color(200, 205, 212);
				g2.setColor(excBase);
				g2.draw(arcN);
				g2.draw(arcS);

				// E/W detection base
				Color detBase=new Color(245, 193, 110);
				g2.setColor(detBase);
				g2.draw(arcE);
				g2.draw(arcW);

				// Phase-highlight overlay: N/S vs E/W 90° out of phase
				double nsAmp=0.5+0.5*Math.sin(drivePhase); // 0..1
				double ewAmp=0.5+0.5*Math.sin(drivePhase+Math.PI/2);

				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float)(0.20+0.55*nsAmp)));
				g2.setColor(new Color(120, 170, 255)); // cool glow for excitation
				g2.draw(arcN);
				g2.draw(arcS);

				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float)(0.20+0.55*ewAmp)));
				g2.setColor(new Color(255, 160, 70)); // warm glow for detection
				g2.draw(arcE);
				g2.draw(arcW);

				g2.setComposite(AlphaComposite.SrcOver);

				// Wires to FFT
				g2.setColor(new Color(80, 80, 80));
				g2.setStroke(new BasicStroke(1.6f));
				g2.draw(wireE);
				g2.draw(wireW);

				// FFT box + bars (with m/z ticks)
				drawFFT(g2);

				// Ions
				for (Ion ion : ions) {
					double r=trapR*ion.rFrac;
					double x=cx+Math.cos(ion.theta)*r;
					double y=cy+Math.sin(ion.theta)*r;

					double s=ion.sizePx;
					double glow=s*1.8;

					g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
					g2.setColor(new Color(255, 255, 255, 50));
					g2.fill(new Ellipse2D.Double(x-glow/2, y-glow/2, glow, glow));

					g2.setComposite(AlphaComposite.SrcOver);
					g2.setColor(ion.color);
					g2.fill(new Ellipse2D.Double(x-s/2, y-s/2, s, s));

					g2.setColor(new Color(0, 0, 0, 100));
					g2.setStroke(new BasicStroke(0.8f));
					g2.draw(new Ellipse2D.Double(x-s/2, y-s/2, s, s));
				}
			} finally {
				g2.dispose();
			}
		}

		private void drawFFT(Graphics2D g2) {
			if (fftBox==null) return;

			// Box
			g2.setComposite(AlphaComposite.SrcOver);
			g2.setColor(new Color(248, 250, 252));
			g2.fillRoundRect(fftBox.x, fftBox.y, fftBox.width, fftBox.height, 10, 10);
			g2.setColor(new Color(150, 150, 155));
			g2.setStroke(new BasicStroke(1f));
			g2.drawRoundRect(fftBox.x, fftBox.y, fftBox.width, fftBox.height, 10, 10);

			// Title
			g2.setFont(g2.getFont().deriveFont(Font.BOLD, Math.max(10f, size*0.035f)));
			g2.setColor(new Color(60, 60, 65));
			g2.drawString("FFT (freq ∝ 1/mass)", fftBox.x+8, fftBox.y+16);

			// Plot area
			int px=fftBox.x+13;
			int py=fftBox.y+22;
			int pw=fftBox.width-24;
			int ph=fftBox.height-40;

			// Axis baseline
			g2.setColor(new Color(210, 210, 215));
			g2.drawLine(px, py+ph-1, px+pw, py+ph-1);

			// m/z ticks (inverse to frequency; left = high m/z, right = low m/z)
			drawMzAxis(g2, px, py, pw, ph);

			// Bars
			int bars=FFT_BINS;
			double gapFrac=0.35;
			int barW=Math.max(2, (int)Math.floor(pw/(bars*(1.0+gapFrac))));
			int step=(int)Math.floor(barW*(1.0+gapFrac));
			int x=px;

			for (int i=0; i<bars; i++) {
				double a=Math.min(1.0, binAmp[i]);
				int h=(int)Math.round(ph*a);
				int y=py+ph-h;

				Color c=binColor[i];
				float[] hsb=Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
				float sat=(float)(0.25+0.65*a);
				Color fill=Color.getHSBColor(hsb[0], sat, 1.0f);
				Color edge=new Color(0, 0, 0, 70);

				g2.setColor(fill);
				g2.fillRect(x, y, barW, h);
				g2.setColor(edge);
				g2.drawRect(x, y, barW, h);

				x+=step;
				if (x+barW>px+pw) break;
			}
		}

		private void drawMzAxis(Graphics2D g2, int px, int py, int pw, int ph) {
			// We map frequency linearly across the width, then m/z ∝ 1/ω.
			if (omegaMax<=omegaMin+1e-12) return;

			// Choose a constant so that at ωmin (left) m/z is "nice". Make it ~1000 m/z.
			double k=1000.0*omegaMin;

			// Ticks at fractions across the width
			int[] ticks= {0, 25, 50, 75, 100}; // %
			g2.setFont(g2.getFont().deriveFont(Math.max(9f, size*0.03f)));
			g2.setColor(new Color(80, 80, 85));

			for (int t : ticks) {
				double frac=t/100.0;
				double omega=omegaMin+frac*(omegaMax-omegaMin);
				double mz=k/omega; // inverse mapping
				int x=px+(int)Math.round(frac*pw);
				int y0=py+ph-1, y1=y0+4;

				// tick
				g2.drawLine(x, y0, x, y1);

				// label (left high m/z → right low m/z)
				String label=(mz>=100)?String.valueOf((int)Math.round(mz)):String.format("%.0f", mz);
				int tw=g2.getFontMetrics().stringWidth(label);
				g2.drawString(label, x-tw/2, y1+12);
			}

			// Axis label
			String label="m/z";
			int tw=g2.getFontMetrics().stringWidth(label);
			g2.drawString(label, px+pw-tw+5, py+ph-3);
		}

		// -------------------- Data types --------------------

		private static final class Ion {
			double theta; // angle
			double omega; // rad/s (same direction; inversely proportional to size)
			double rFrac; // radius as fraction of trapR
			double sizePx; // visual size
			int bin; // mapped FFT bin
			boolean wasNearE, wasNearW;
			Color color; // color based on frequency (thus mass)
		}
	}

	public static void main(String[] args) {
		// Nice defaults (falls back gracefully if FlatLaf isn't on classpath)
		try {
			FlatLightLaf.setup();
		} catch (Throwable ignore) {
		}

		SwingUtilities.invokeLater(() -> {
			JFrame f=new JFrame("FT-ICR Loader Demo");
			FTICRLoadingPanel panel=new FTICRLoadingPanel("Reading RAW/TIMS…");

			f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			f.setContentPane(panel);
			f.setMinimumSize(new Dimension(640, 480));
			f.setLocationByPlatform(true);
			f.setVisible(true);

			// Ensure animation timers stop on close
			f.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosed(WindowEvent e) {
					try {
						panel.stop();
					} catch (Throwable ignore) {
					}
				}
			});
		});
	}

}