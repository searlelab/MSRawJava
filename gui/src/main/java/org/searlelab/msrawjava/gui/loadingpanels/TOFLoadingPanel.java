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
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatLightLaf;

/**
 * Teaching panel for TOF-MS with a reflectron and scan-averaged spectrum.
 * - Pulsed push; ions traverse Source -> Reflectron -> Detector.
 * - v ∝ 1/sqrt(m/z), so light ions arrive first.
 * - Stable ions appear every push; noise ions vary per push.
 * - Each push accumulates counts into the spectrum (scan averaging).
 */
/**
 * Loading panel for TOF data with progress feedback.
 */
public class TOFLoadingPanel extends LoadingPanel {
	private static final long serialVersionUID=1L;

	private final JLabel label=new JLabel("Acquiring…", SwingConstants.CENTER);
	private final JProgressBar bar=new JProgressBar();

	private final TOFCanvas canvas=new TOFCanvas();

	public TOFLoadingPanel(String text) {
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

		String base=(text==null||text.isBlank())?"Time-of-Flight (t ∝ √m/z)":text;
		label.setText(base+"...");
		Dimension fixed=label.getPreferredSize();
		label.setPreferredSize(fixed); // prevent jitter
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

	public void start() {
		canvas.start();
	}

	public void stop() {
		canvas.stop();
	}

	// ========================= Animated Canvas =========================

	private static class TOFCanvas extends JComponent {
		private static final long serialVersionUID=1L;

		// Timing & animation
		private static final int FPS_MS=33; // ~30 FPS
		private final LoadingPanel.MinPriorityAnimationLoop anim;
		private long lastNanos=0L;

		// Geometry
		private Point2D.Double src, refl, det; // Source, Reflectron apex, Detector
		private double L1, L2, Ltot; // segment lengths; total path length
		private int w, h, pad;
		private Rectangle specBox; // spectrum box
		private Line2D wireDet; // detector -> spectrum wire

		// Physics-ish model
		private static final int STABLE_IONS=5;
		private static final int NOISE_IONS=8;
		private static final int TOTAL_IONS=STABLE_IONS+NOISE_IONS;

		private final Random rng=new Random(123);
		private final List<Ion> ions=new ArrayList<>(TOTAL_IONS);

		// Mass range (a.u., pedagogical)
		private static final double MZ_MIN=400.0;
		private static final double MZ_MAX=1000.0;

		// Push cycle
		private double pushElapsed=0.0;
		private double pushPeriod=1.6; // seconds (recomputed with geometry to fit)
		private boolean flashPush=false; // show source flash briefly

		// Spectrum (scan-averaged counts)
		private static final int BINS=24;
		private final double[] accum=new double[BINS];
		private final Color[] binColor=new Color[BINS]; // last-hit color
		private int shots=0;

		// Speeds
		private double baseSpeed=600.0; // pixels/s for m/z=1 (overridden per geometry)

		TOFCanvas() {
			setOpaque(true);
			makeInitialIons(); // stable ions chosen here
			recomputeGeometry(); // set geometry & speeds
			anim=LoadingPanel.createMinPriorityAnimationLoop(FPS_MS, "tof-loading-animation", this::tick);
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
			anim.stop();
		}

		void start() {
			lastNanos=System.nanoTime();
			if (!anim.isRunning()) anim.start();
		}

		@Override
		public void addNotify() {
			super.addNotify();
			lastNanos=System.nanoTime();
			if (!anim.isRunning()) anim.start();
		}

		@Override
		public void removeNotify() {
			anim.stop();
			super.removeNotify();
		}

		// ---------- Model setup ----------

		private void makeInitialIons() {
			ions.clear();
			// Stable ions (fixed m/z across all pushes)
			for (int i=0; i<STABLE_IONS; i++) {
				double mz=MZ_MIN+(MZ_MAX-MZ_MIN)*(i+1.0)/(STABLE_IONS+1.0);
				ions.add(new Ion(mz, true, colorByMass(mz)));
			}
			// Noise ions for first push (will be regenerated each push)
			for (int i=0; i<NOISE_IONS; i++) {
				double mz=MZ_MIN+(MZ_MAX-MZ_MIN)*rng.nextDouble();
				ions.add(new Ion(mz, false, colorByMass(mz)));
			}
		}

		private void resetForNewPush() {
			shots++;
			flashPush=true;
			pushElapsed=0.0;
			// Re-roll noise ions, keep stable ions
			for (int i=0; i<ions.size(); i++) {
				Ion ion=ions.get(i);
				if (!ion.stable) {
					ion.mz=MZ_MIN+(MZ_MAX-MZ_MIN)*rng.nextDouble();
					ion.color=colorByMass(ion.mz);
				}
				ion.s=0.0;
				ion.detected=false;
				ion.v=velocityFor(ion.mz);
				ion.detTime=Ltot/ion.v;
				ion.bin=massToBin(ion.mz);
			}
		}

		private void recomputeGeometry() {
			w=getWidth();
			h=getHeight();
			pad=Math.max(8, Math.min(w, h)/22);

			// ---- Make the V much narrower (≈5x) around the center ----
			double cxMid=w*0.25;
			double topY=pad*3.0;
			double apexY=h-pad*2.0;

			double halfSpanFull=cxMid-pad*2.0; // original half-span to edges
			double halfSpanNarrow=Math.max(40.0, halfSpanFull/5.0); // ~5x narrower, but never too tiny

			// Source (left/top), Reflectron (bottom middle), Detector (right/top) — now tightly spaced in X
			src=new Point2D.Double(cxMid-halfSpanNarrow, topY);
			refl=new Point2D.Double(cxMid, apexY);
			det=new Point2D.Double(cxMid+halfSpanNarrow, topY);

			// Path lengths
			L1=src.distance(refl);
			L2=refl.distance(det);
			Ltot=L1+L2;

			// Lightest ion (m/z=1) arrival target; narrower path shortens Ltot, so re-fit base speed
			double targetLightTime=0.15; // keep your chosen feel
			baseSpeed=Ltot/targetLightTime;

			// Ensure heaviest ion arrives before next push (+ small margin)
			double tHeavy=Ltot/velocityFor(MZ_MAX);
			pushPeriod=Math.max(1.2, tHeavy+0.25);

			// Recompute ion kinematics & bins
			if (ions.isEmpty()) makeInitialIons();
			for (Ion ion : ions) {
				ion.v=velocityFor(ion.mz);
				ion.detTime=Ltot/ion.v;
				ion.bin=massToBin(ion.mz);
			}

			// ---- Give more room to the spectrum on the right ----
			int boxW=Math.max(160, (int)(w*0.38)); // a bit wider than before
			int boxH=Math.max(80, (int)(h*0.34));
			int boxX=w-boxW-2*pad;
			int boxY=h-boxH-3*pad;
			specBox=new Rectangle(boxX, boxY, boxW, boxH);

			// Wire from detector to spectrum
			wireDet=new Line2D.Double(det.x, det.y, boxX, boxY+boxH/2.0);

			// First-time seed
			if (shots==0) resetForNewPush();
		}

		// v ~ base / sqrt(m/z)
		private double velocityFor(double mz) {
			return baseSpeed/Math.sqrt(Math.max(1e-3, mz));
		}

		// map mass to spectrum bin (left=low m/z? Usually TOF: shorter time=low m/z; we show m/z increasing left→right)
		private int massToBin(double mz) {
			double f=(mz-MZ_MIN)/(MZ_MAX-MZ_MIN);
			int bin=(int)Math.round(f*(BINS-1));
			return Math.max(0, Math.min(BINS-1, bin));
			// (Heavier m/z to the right; lighter to the left)
		}

		private static Color colorByMass(double mz) {
			// Map mass within [MZ_MIN..MZ_MAX] to hue: light/fast → blue; heavy/slow → red
			double f=(mz-MZ_MIN)/(MZ_MAX-MZ_MIN);
			float hue=(float)(0.02+0.63*(1.0-f)); // invert: low m/z -> blue-ish, high -> red-ish
			return Color.getHSBColor(hue, 0.70f, 1.0f);
		}

		// ---------- Animation tick ----------

		private void tick() {
			long now=System.nanoTime();
			double dt=(lastNanos==0)?(FPS_MS/1000.0):(now-lastNanos)/1_000_000_000.0;
			dt=Math.min(dt, 0.05); // clamp hiccups
			lastNanos=now;

			pushElapsed+=dt;
			if (flashPush&&pushElapsed>0.12) flashPush=false;

			boolean allDetected=true;
			for (Ion ion : ions) {
				if (!ion.detected) {
					ion.s+=ion.v*dt;
					if (ion.s>=Ltot) {
						ion.s=Ltot;
						ion.detected=true;
						// Accumulate spectrum (stable ions stronger; noise a bit weaker)
						double weight=ion.stable?1.0:0.5;
						accum[ion.bin]+=weight;
						binColor[ion.bin]=ion.color;
					} else {
						allDetected=false;
					}
				}
			}

			// Start a new push when done or period elapsed
			if (allDetected||pushElapsed>=pushPeriod) {
				resetForNewPush();
			}

			repaint();
		}

		// ---------- Painting ----------

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2=(Graphics2D)g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

				int W=getWidth(), H=getHeight();
				g2.setColor(getBackground()!=null?getBackground():Color.white);
				g2.fillRect(0, 0, W, H);

				if (specBox==null) recomputeGeometry();

				// Flight tube (V path)
				Stroke tubeStroke=new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
				g2.setStroke(tubeStroke);
				g2.setColor(new Color(210, 214, 220));
				g2.draw(new Line2D.Double(src, refl));
				g2.draw(new Line2D.Double(refl, det));

				// Reflectron 
				drawReflectron(g2);

				// Source "pusher" plates (flash at push start)
				drawSource(g2);

				// Detector
				drawDetector(g2);

				// Wire to spectrum
				g2.setStroke(new BasicStroke(1.6f));
				g2.setColor(new Color(90, 90, 95));
				g2.draw(wireDet);

				// Spectrum box
				drawSpectrum(g2);

				// Ions
				for (Ion ion : ions)
					drawIon(g2, ion);
			} finally {
				g2.dispose();
			}
		}

		private void drawReflectron(Graphics2D g2) {
			double plateLen=40; // extent across the tube
			double plateThk=4; // thickness along the path

			g2.setStroke(new BasicStroke(2f));
			g2.setColor(new Color(150, 150, 155));
			for (int i=-2; i<=1; i++) {
				double t=i/6.0;
				double x1=refl.x-20;
				double y1=refl.y+t*40;

				Shape plate=new RoundRectangle2D.Double(x1, y1, plateLen, plateThk, 6, 6);

				g2.setColor(new Color(200, 205, 212));
				g2.fill(plate);

				g2.setColor(new Color(130, 135, 142));
				g2.setStroke(new BasicStroke(1.2f));
				g2.draw(plate);
			}
		}

		private void drawSource(Graphics2D g2) {
			// Orientation along initial flight path (src -> refl)
			double dx=refl.x-src.x, dy=refl.y-src.y;
			double len=Math.hypot(dx, dy);
			if (len<1e-6) return;
			double ux=dx/len, uy=dy/len; // unit along path
			double theta=Math.atan2(uy, ux); // rotation angle

			// --- Single extraction plate (perpendicular to path) ---
			double plateLen=40; // extent across the tube
			double plateThk=6; // thickness along the path
			double plateOff=-10; // distance from source along the path
			double cxp=src.x+ux*plateOff;
			double cyp=src.y+uy*plateOff;

			AffineTransform at=new AffineTransform();
			at.translate(cxp, cyp);
			at.rotate(theta);
			Shape plate=new RoundRectangle2D.Double(-plateThk/2.0, -plateLen/2.0, plateThk, plateLen, 6, 6);
			Shape plateT=at.createTransformedShape(plate);

			g2.setColor(new Color(180, 185, 192));
			g2.fill(plateT);
			g2.setColor(new Color(140, 145, 150));
			g2.setStroke(new BasicStroke(1.2f));
			g2.draw(plateT);

			// --- Ring-lens cross-section: short stack just downstream of the plate ---
			int rings=4;
			double ringSpacing=7;
			double ringLen=40;
			double ringThk=4;

			for (int i=0; i<rings; i++) {
				double off=plateOff+12+i*ringSpacing;
				double cxr=src.x+ux*off;
				double cyr=src.y+uy*off;

				AffineTransform at2=new AffineTransform();
				at2.translate(cxr, cyr);
				at2.rotate(theta);

				Shape ring=new RoundRectangle2D.Double(-ringThk/2.0, -ringLen/2.0, ringThk, ringLen, 6, 6);
				Shape ringT=at2.createTransformedShape(ring);

				g2.setColor(new Color(200, 205, 212));
				g2.fill(ringT);
				g2.setColor(new Color(130, 135, 142));
				g2.draw(ringT);
			}

			// Push flash overlay (blue tint) on the extraction plate
			if (flashPush) {
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
				g2.setColor(new Color(255, 100, 100));
				g2.fill(plateT);
				g2.setComposite(AlphaComposite.SrcOver);
			}
		}

		private void drawDetector(Graphics2D g2) {
			// Simple MCP disk at det
			double r=14;
			g2.setColor(new Color(230, 235, 240));
			g2.fillOval((int)(det.x-r), (int)(det.y-r), (int)(2*r), (int)(2*r));
			g2.setColor(new Color(140, 145, 150));
			g2.setStroke(new BasicStroke(1.2f));
			g2.drawOval((int)(det.x-r), (int)(det.y-r), (int)(2*r), (int)(2*r));
		}

		private void drawIon(Graphics2D g2, Ion ion) {
			// Locate along the V path by arc length s
			Point2D.Double p;
			if (ion.s<=L1) {
				double u=ion.s/L1;
				p=lerp(src, refl, u);
			} else {
				double u=(ion.s-L1)/L2;
				p=lerp(refl, det, u);
			}

			double s=4.0+7.0*(ion.mz-MZ_MIN)/(MZ_MAX-MZ_MIN); // heavier -> bigger
			double glow=s*1.8;

			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
			g2.setColor(new Color(255, 255, 255, 60));
			g2.fillOval((int)(p.x-glow/2), (int)(p.y-glow/2), (int)glow, (int)glow);

			g2.setComposite(AlphaComposite.SrcOver);
			g2.setColor(ion.color);
			g2.fillOval((int)(p.x-s/2), (int)(p.y-s/2), (int)s, (int)s);

			g2.setColor(new Color(0, 0, 0, 90));
			g2.setStroke(new BasicStroke(0.8f));
			g2.drawOval((int)(p.x-s/2), (int)(p.y-s/2), (int)s, (int)s);
		}

		private void drawSpectrum(Graphics2D g2) {
			Rectangle r=specBox;
			if (r==null) return;

			// Box
			g2.setColor(new Color(248, 250, 252));
			g2.fillRoundRect(r.x-5, r.y, r.width+15, r.height+20, 10, 10);
			g2.setColor(new Color(150, 150, 155));
			g2.setStroke(new BasicStroke(1f));
			g2.drawRoundRect(r.x-5, r.y, r.width+15, r.height+20, 10, 10);

			// Title + shots
			g2.setFont(g2.getFont().deriveFont(Font.BOLD, Math.max(10f, h*0.035f)));
			g2.setColor(new Color(60, 60, 65));
			g2.drawString("ToF Spectrum", r.x+8, r.y+16);
			g2.setFont(g2.getFont().deriveFont(Math.max(9f, h*0.03f)));
			g2.drawString("push: "+shots, r.x+r.width-60, r.y+16);

			// Plot area
			int px=r.x+8, py=r.y+24;
			int pw=r.width-16, ph=r.height-36;

			// Baseline
			g2.setColor(new Color(210, 210, 215));
			g2.drawLine(px, py+ph-1, px+pw, py+ph-1);

			// m/z axis ticks (left=low, right=high here)
			drawMzAxis(g2, px, py, pw, ph);

			// Find max for scaling (avoid zero-div)
			double max=0;
			for (double v : accum)
				if (v>max) max=v;
			if (max<=0) max=1;

			// Bars
			int bars=BINS;
			double gapFrac=0.25;
			int barW=Math.max(2, (int)Math.floor(pw/(bars*(1.0+gapFrac))));
			int step=(int)Math.floor(barW*(1.0+gapFrac));
			int x=px;

			for (int i=0; i<bars; i++) {
				double a=accum[i]/max;
				int bh=(int)Math.round(ph*a);
				int by=py+ph-bh;

				Color c=(binColor[i]!=null)?binColor[i]:new Color(120, 160, 220);
				float[] hsb=Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
				float sat=(float)(0.25+0.65*a);
				Color fill=Color.getHSBColor(hsb[0], sat, 1.0f);
				Color edge=new Color(0, 0, 0, 70);

				g2.setColor(fill);
				g2.fillRect(x, by, barW, bh);
				g2.setColor(edge);
				g2.drawRect(x, by, barW, bh);

				x+=step;
				if (x+barW>px+pw) break;
			}
		}

		private void drawMzAxis(Graphics2D g2, int px, int py, int pw, int ph) {
			// Label
			g2.setFont(g2.getFont().deriveFont(Math.max(9f, h*0.03f)));
			g2.setColor(new Color(80, 80, 85));

			int[] ticks= {0, 25, 50, 75, 100}; // %
			for (int t : ticks) {
				double frac=t/100.0;
				double mz=MZ_MIN+frac*(MZ_MAX-MZ_MIN);
				int x=px+(int)Math.round(frac*pw);
				int y0=py+ph-1, y1=y0+4;

				g2.drawLine(x, y0, x, y1);
				String lab=Integer.toString((int)Math.round(mz));
				int tw=g2.getFontMetrics().stringWidth(lab);
				g2.drawString(lab, x-tw/2, y1+12);
			}
			String label="m/z (light ions arrive first)";
			int tw=g2.getFontMetrics().stringWidth(label);
			g2.drawString(label, px+pw/2-tw/2, py+ph+26);
		}

		private static Point2D.Double lerp(Point2D a, Point2D b, double u) {
			u=Math.max(0, Math.min(1, u));
			return new Point2D.Double(a.getX()+(b.getX()-a.getX())*u, a.getY()+(b.getY()-a.getY())*u);
		}

		// Ion along the path param s (0..Ltot)
		private static final class Ion {
			double mz; // pedagogical mass/charge
			boolean stable; // present every push if true
			Color color; // by mass
			double s=0; // path position (0..Ltot)
			double v=0; // speed (px/s)
			@SuppressWarnings("unused")
			double detTime=0; // expected arrival as reference 
			boolean detected=false;
			int bin=0; // spectrum bin

			Ion(double mz, boolean stable, Color c) {
				this.mz=mz;
				this.stable=stable;
				this.color=c;
			}
		}
	}

	public static void main(String[] args) {
		// Nice defaults (falls back gracefully if FlatLaf isn't on classpath)
		try {
			FlatLightLaf.setup();
		} catch (Throwable ignore) {
		}

		SwingUtilities.invokeLater(() -> {
			JFrame f=new JFrame("ToF Loader Demo");
			TOFLoadingPanel panel=new TOFLoadingPanel("Reading RAW/TIMS…");

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
