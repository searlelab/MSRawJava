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
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatLightLaf;
import org.searlelab.msrawjava.logging.Logger;

/**
 * Astral educational loading panel:
 * - Mimics the classic Astral / OET schematic: ions leave emitter (73) at left,
 * travel rightward through asymmetric mirror region, then return left to a detector (74) below the emitter.
 * - One packet ("push") every PACKET_PERIOD_SEC seconds (tunable). Default set to 10 seconds as requested.
 * - Ions in each packet are random m/z in [400..1000], so you can watch how different ions in the packet separate.
 * - Packets are stand-alone: spectrum resets each push (not scan-averaged).
 * - Spectrum peaks appear when ions hit the detector, peaks match detected ions.
 *
 * Trajectory model:
 * - Uses an analytic "out-and-back" drift in X with a sinusoidal bounce in Y between two long plates.
 * - Drift slows and oscillations visually compress near the far-right turning region to resemble the schematic.
 * - Ends at detector (74) below emitter (73), both on left.
 */
public class AstralLoadingPanel extends LoadingPanel {
	private static final long serialVersionUID=1L;

	private final JLabel label=new JLabel("Acquiring…", SwingConstants.CENTER);
	private final JProgressBar bar=new JProgressBar();

	private final AstralCanvas canvas=new AstralCanvas();

	public AstralLoadingPanel(String text) {
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

		String base=(text==null||text.isBlank())?("Astral packet flight (period = "+AstralCanvas.PACKET_PERIOD_SEC+" s)"):text;
		label.setText(base+"...");
		Dimension fixed=label.getPreferredSize();
		label.setPreferredSize(fixed);

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

	private static final class AstralCanvas extends JComponent {
		private static final long serialVersionUID=1L;

		// ===== User-tunable timing =====
		// One packet ("push") every N seconds. 
		public static double PACKET_PERIOD_SEC=16.0;

		private static final int FPS_MS=40; // ~20 FPS

		private static final int IONS_PER_PACKET=18;

		private static final double MZ_MIN=400.0;
		private static final double MZ_MAX=1000.0;

		// Make the schematic look right: lots of bounces across the span
		private static final int OSCILLATIONS=10;

		// Keep ions within plate gap
		private static final double AMP_MARGIN=10.0;

		// Detector sits below emitter on left
		private static final double DETECTOR_DY_FRAC=0.095; // fraction of analyzer height

		private final LoadingPanel.MinPriorityAnimationLoop anim;
		private long lastNanos=0L;

		private int w, h, pad;
		private Rectangle analyzerBox;
		private Rectangle spectrumBox;

		// Emitter (73) and detector (74) on the left
		private Point2D.Double emitter73;
		private Point2D.Double detector74;

		// Drift extents: ions go right to turn, then come back left
		private double xLeft; // near emitter
		private double xRight; // far turning region
		private double yCenter;

		// Asymmetric "plates" (drawn as long slanted bands like the schematic)
		private QuadCurve2D topPlate;
		private QuadCurve2D botPlate;

		// Optional inner curved boundaries (to hint at shaped fields)
		private QuadCurve2D innerTop;
		private QuadCurve2D innerBot;

		private final Random rng=new Random(123);
		private final List<Ion> ions=new ArrayList<>(IONS_PER_PACKET);
		private final List<Peak> peaks=new ArrayList<>(IONS_PER_PACKET);

		private double packetElapsed=0.0;
		private boolean flashEmit=false;

		AstralCanvas() {
			setOpaque(true);
			recomputeGeometry();
			startNewPacket();

			anim=LoadingPanel.createMinPriorityAnimationLoop(FPS_MS, "astral-loading-animation", this::tick);
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

		private void recomputeGeometry() {
			w=getWidth();
			h=getHeight();
			pad=Math.max(10, Math.min(w, h)/22);

			// Put spectrum on the right, analyzer on the left
			int specW=Math.max(240, (int)(w*0.34));
			int specH=Math.max(120, (int)(h*0.32));
			int specX=w-specW-2*pad;
			int specY=h-specH-3*pad;
			spectrumBox=new Rectangle(specX, specY, specW, specH);

			int ax=pad;
			int ay=pad;
			int aw=specX-2*pad;
			int ah=h-2*pad;
			analyzerBox=new Rectangle(ax, ay, Math.max(320, aw), Math.max(260, ah));

			// Drift axis and endpoints
			xLeft=ax+analyzerBox.width*0.10;
			xRight=ax+analyzerBox.width*0.95;
			yCenter=ay+analyzerBox.height*0.48;

			// Emitter and detector on the left side, detector slightly lower
			emitter73=new Point2D.Double(xLeft-18, yCenter+0.5*analyzerBox.height*DETECTOR_DY_FRAC);
			detector74=new Point2D.Double(xLeft-18, yCenter-0.5*analyzerBox.height*DETECTOR_DY_FRAC);

			// Build plates: slanted like your schematic (top slopes downward to the right, bottom slopes upward)
			double gapLeft=analyzerBox.height*0.70;
			double gapRight=analyzerBox.height*0.46;

			double topL=yCenter-gapLeft/2.0;
			double topR=yCenter-gapRight/2.0-analyzerBox.height*0.05;

			double botL=yCenter+gapLeft/2.0;
			double botR=yCenter+gapRight/2.0+analyzerBox.height*0.05;

			double ctrlX=ax+analyzerBox.width*0.55;
			double bulge=analyzerBox.height*0.04;

			topPlate=new QuadCurve2D.Double(xLeft-10, topL, ctrlX, topL-bulge, xRight, topR);
			botPlate=new QuadCurve2D.Double(xLeft-10, botL, ctrlX, botL+bulge, xRight, botR);

			// Inner curves to mimic the concave field region in the patent-like drawing
			double innerBulge=analyzerBox.height*0.18;
			innerTop=new QuadCurve2D.Double(xLeft+analyzerBox.width*0.08, yCenter-20, ctrlX, yCenter-innerBulge, xRight-analyzerBox.width*0.03, yCenter-40);

			innerBot=new QuadCurve2D.Double(xLeft+analyzerBox.width*0.08, yCenter+20, ctrlX, yCenter+innerBulge, xRight-analyzerBox.width*0.03, yCenter+40);
		}

		private void startNewPacket() {
			// Reset packet spectrum (stand-alone)
			peaks.clear();
			ions.clear();

			for (int i=0; i<IONS_PER_PACKET; i++) {
				double mz=MZ_MIN+(MZ_MAX-MZ_MIN)*rng.nextDouble();
				Color c=colorByMass(mz);
				double inten=0.45+1.0*rng.nextDouble(); // mild spread

				Ion ion=new Ion(mz, inten, c);
				ion.detTime=detTimeFor(mz);
				ions.add(ion);
			}

			packetElapsed=0.0;
			flashEmit=true;
		}

		private double detTimeFor(double mz) {
			// Spread arrival times within the packet window: keep margin at end.
			// Heavy ions are slower: sqrt(mz).
			double ratio=mz/MZ_MIN; // normalize to 400
			double base=PACKET_PERIOD_SEC*0.30; // time for m/z=400 to finish (out-and-back)
			double t=base*Math.sqrt(Math.max(1e-6, ratio));

			double maxT=PACKET_PERIOD_SEC*0.92;
			if (t>maxT) t=maxT;

			// Also ensure very light doesn't hit instantly, give minimum
			double minT=PACKET_PERIOD_SEC*0.18;
			if (t<minT) t=minT;

			return t;
		}

		private void tick() {
			long now=System.nanoTime();
			double dt=(lastNanos==0)?(FPS_MS/1000.0):(now-lastNanos)/1_000_000_000.0;
			dt=Math.min(dt, 0.05);
			lastNanos=now;

			packetElapsed+=dt;
			if (flashEmit&&packetElapsed>0.20) flashEmit=false;

			for (Ion ion : ions) {
				if (ion.detected) continue;

				ion.phase+=dt/Math.max(1e-6, ion.detTime);
				if (ion.phase>=1.0) {
					ion.phase=1.0;
					ion.detected=true;
					peaks.add(new Peak(ion.mz, ion.intensity, ion.color));
				}
			}

			if (packetElapsed>=PACKET_PERIOD_SEC/2) {
				startNewPacket();
			}

			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2=(Graphics2D)g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

				g2.setColor(getBackground()!=null?getBackground():Color.white);
				g2.fillRect(0, 0, getWidth(), getHeight());

				drawAnalyzer(g2);
				drawSpectrum(g2);

				for (Ion ion : ions) {
					drawIon(g2, ion);
				}
			} finally {
				g2.dispose();
			}
		}

		private void drawAnalyzer(Graphics2D g2) {
			// Outer frame
			Shape body=new RoundRectangle2D.Double(analyzerBox.x, analyzerBox.y, analyzerBox.width, analyzerBox.height, 18, 18);
			g2.setColor(Color.white);
			g2.fill(body);
			g2.setColor(new Color(130, 130, 135));
			g2.setStroke(new BasicStroke(1.1f));
			g2.draw(body);

			// Draw plates as thick bands (the "asymmetric plates")
			Stroke plateStroke=new BasicStroke(9.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
			g2.setStroke(plateStroke);
			g2.setColor(Color.GRAY);
			g2.draw(topPlate);
			g2.draw(botPlate);

			// Inner curves as thin lines
			g2.setStroke(new BasicStroke(2.0f));
			g2.setColor(Color.GRAY);
			g2.draw(innerTop);
			g2.draw(innerBot);

			// Hatch-like shaded regions (very light)
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
			g2.setColor(Color.GRAY);
			Shape hatchedTop=makeHatchRegion(topPlate, innerTop, true);
			Shape hatchedBot=makeHatchRegion(botPlate, innerBot, false);
			if (hatchedTop!=null) g2.fill(hatchedTop);
			if (hatchedBot!=null) g2.fill(hatchedBot);
			g2.setComposite(AlphaComposite.SrcOver);

			// Emitter (73) and detector (74) blocks on the left side
			drawEmitterDetector(g2);
		}

		private void drawEmitterDetector(Graphics2D g2) {
			double bw=36;
			double bh=14;

			double emitDeg=-10.0;
			double detDeg=10.0;

			// Base (unrotated) rectangles centered at emitter73 / detector74
			RoundRectangle2D eBase=new RoundRectangle2D.Double(emitter73.x-bw/2.0, emitter73.y-bh/2.0, bw, bh, 6, 6);
			RoundRectangle2D dBase=new RoundRectangle2D.Double(detector74.x-bw/2.0, detector74.y-bh/2.0, bw, bh, 6, 6);

			// Rotate around each center
			Shape e=java.awt.geom.AffineTransform.getRotateInstance(Math.toRadians(emitDeg), emitter73.x, emitter73.y).createTransformedShape(eBase);

			Shape d=java.awt.geom.AffineTransform.getRotateInstance(Math.toRadians(detDeg), detector74.x, detector74.y).createTransformedShape(dBase);

			// Fill + stroke
			g2.setColor(new Color(235, 238, 242));
			g2.fill(e);
			g2.fill(d);

			g2.setColor(new Color(60, 60, 65));
			g2.setStroke(new BasicStroke(1.2f));
			g2.draw(e);
			g2.draw(d);

			// Flash on emitter when packet starts (fill the rotated shape)
			if (flashEmit) {
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
				g2.setColor(new Color(255, 110, 110));
				g2.fill(e);
				g2.setComposite(AlphaComposite.SrcOver);
			}
		}

		private static Shape makeHatchRegion(QuadCurve2D outer, QuadCurve2D inner, boolean top) {
			// Build a closed shape between two curves (outer and inner).
			// Simple sampling to build a polygon, still cheap.
			int n=28;
			Path2D p=new Path2D.Double();

			// sample outer left->right
			for (int i=0; i<=n; i++) {
				double t=i/(double)n;
				Point2D po=evalQuadPoint(outer, t);
				if (i==0) p.moveTo(po.getX(), po.getY());
				else p.lineTo(po.getX(), po.getY());
			}
			// sample inner right->left
			for (int i=n; i>=0; i--) {
				double t=i/(double)n;
				Point2D pi=evalQuadPoint(inner, t);
				p.lineTo(pi.getX(), pi.getY());
			}
			p.closePath();
			return p;
		}

		private static Point2D evalQuadPoint(QuadCurve2D q, double t) {
			double x0=q.getX1(), y0=q.getY1();
			double x1=q.getCtrlX(), y1=q.getCtrlY();
			double x2=q.getX2(), y2=q.getY2();
			double omt=1.0-t;

			double x=omt*omt*x0+2.0*omt*t*x1+t*t*x2;
			double y=omt*omt*y0+2.0*omt*t*y1+t*t*y2;
			return new Point2D.Double(x, y);
		}

		private void drawIon(Graphics2D g2, Ion ion) {
			Point2D.Double p=posOnSchematicTrack(ion);

			double s=4.0+6.5*(ion.mz-MZ_MIN)/(MZ_MAX-MZ_MIN);
			double glow=s*1.9;

			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
			g2.setColor(new Color(255, 255, 255, 80));
			g2.fillOval((int)(p.x-glow/2), (int)(p.y-glow/2), (int)glow, (int)glow);

			g2.setComposite(AlphaComposite.SrcOver);
			g2.setColor(ion.color);
			g2.fillOval((int)(p.x-s/2), (int)(p.y-s/2), (int)s, (int)s);

			g2.setColor(new Color(0, 0, 0, 85));
			g2.setStroke(new BasicStroke(0.8f));
			g2.drawOval((int)(p.x-s/2), (int)(p.y-s/2), (int)s, (int)s);
		}

		/**
		 * Mimic the schematic:
		 * - Starts at emitter73 (left).
		 * - Travels right while bouncing between plates (max amplitude immediately).
		 * - Turns and returns left.
		 * - Ends at detector74 (left) and stops there (no overshoot).
		 *
		 * Requested tweaks:
		 * 1) Emit aimed downward: force initial oscillation direction to be downward.
		 * 2) Emit/detect at largest amplitude arc: start and end on a turning point (|sin| = 1), not near the center.
		 * 3) Stop at detector: if phase would carry beyond detector, clamp to detector location.
		 */
		private Point2D.Double posOnSchematicTrack(Ion ion) {
			// If caller updated ion.phase such that it can exceed 1 due to dt, clamp.
			double pRaw=ion.phase;
			double p=clamp01(pRaw);

			// Hard stop at detector once we reach/ exceed the end (even if tick math overshoots).
			// This guarantees the drawn position does not go beyond the detector.
			if (pRaw>=0.97) {
				return new Point2D.Double(detector74.x, detector74.y);
			}

			// Out-and-back drift in x (xLeft -> xRight -> xLeft) with zero velocity at endpoints and turn.
			double drift=(1.0-Math.cos(2.0*Math.PI*p))*0.5;
			double x=xLeft+(xRight-xLeft)*drift;

			// Mirror bounds at x
			double yTop=evalQuadY(topPlate, x);
			double yBot=evalQuadY(botPlate, x);

			double yc=(yTop+yBot)/2.0;
			double amp=Math.max(6.0, (yBot-yTop)*0.48-AMP_MARGIN);

			// === Oscillation phase setup ===
			// We want:
			// - At p=0: largest amplitude and moving downward (so start near top turning point).
			// - At p=1: largest amplitude again (end on a turning point).
			//
			// Use a constant phase offset so sin(theta) = +1 at p=0 (top turning point),
			// and because OSCILLATIONS is an integer, it will also be +1 at p=1.
			//
			// Also keep a small mz-dependent offset so ions spread laterally in phase, but do NOT destroy
			// "turning point at ends". We therefore quantize the mz offset to multiples of 2π so ends remain turning points.
			// (Optional: if you prefer visible phase spread more than exact turning at ends, remove quantization.)

			// Base phase to start at top turning point (sin = +1)
			double theta0=Math.PI/2.0;

			// mz-dependent offset that preserves sin(theta) at p=0 and p=1 (adds 2π*k only)
			double phiRaw=(ion.mz*0.0019);
			double k=Math.round(phiRaw/(2.0*Math.PI));
			double phi=2.0*Math.PI*k;

			double theta=2.0*Math.PI*OSCILLATIONS*p+theta0+phi;

			// y at full amplitude immediately (no easing-to-center)
			double bounceSign=(p<=0.5)?-1.0:1.0;
			double y=yc+bounceSign*amp*Math.sin(theta);

			// Clamp inside plates
			double lo=yTop+AMP_MARGIN;
			double hi=yBot-AMP_MARGIN;
			if (y<lo) y=lo;
			if (y>hi) y=hi;

			// === Start/end positioning without shrinking amplitude ===
			// We want ions to be emitted into and detected from the large-arc region,
			// so do NOT lerp y toward emitter/detector (that was causing small oscillations near ends).
			//
			// We only guide x toward the exact emitter/detector x,y positions, while keeping y on the oscillation.
			// Additionally, we bias the very first point to originate at the emitter location, but immediately
			// depart at full amplitude.

			double easeEnd=0.08; // slightly shorter so "small oscillation zone" is minimized
			if (p<easeEnd) {
				double u=p/easeEnd;
				u=clamp01(u);

				// Keep y on the oscillation (large amplitude), but start at the emitter point at p=0.
				// This creates an immediate "launch" from the emitter into the large-amplitude track.
				x=lerp(emitter73.x, x, u);
				y=lerp(emitter73.y, y, u);
			} else if (p>1.0-easeEnd) {
				double u=(p-(1.0-easeEnd))/easeEnd;
				u=clamp01(u);

				// Keep y on the oscillation until the very end, then land on the detector.
				// This avoids a tiny centered oscillation right before detection.
				x=lerp(x, detector74.x, u);
				y=lerp(y, detector74.y, u);
			}

			// Ensure we never go "past" the detector if your geometry swap moved detector along xLeft side.
			// If p is extremely close to 1, force exact detector coordinates.
			if (p>0.9995) {
				return new Point2D.Double(detector74.x, detector74.y);
			}

			return new Point2D.Double(x, y);
		}

		private static double evalQuadY(QuadCurve2D q, double x) {
			double x0=q.getX1();
			double x2=q.getX2();
			double t=(x-x0)/Math.max(1e-6, (x2-x0));
			t=clamp01(t);

			double y0=q.getY1();
			double y1=q.getCtrlY();
			double y2=q.getY2();
			double omt=1.0-t;
			return omt*omt*y0+2.0*omt*t*y1+t*t*y2;
		}

		private void drawSpectrum(Graphics2D g2) {
			Rectangle r=spectrumBox;
			if (r==null) return;

			g2.setColor(new Color(248, 250, 252));
			g2.fillRoundRect(r.x-5, r.y, r.width+15, r.height+20, 10, 10);
			g2.setColor(new Color(150, 150, 155));
			g2.setStroke(new BasicStroke(1f));
			g2.drawRoundRect(r.x-5, r.y, r.width+15, r.height+20, 10, 10);

			g2.setFont(g2.getFont().deriveFont(Font.BOLD, Math.max(10f, h*0.035f)));
			g2.setColor(new Color(60, 60, 65));
			g2.drawString("Astral spectrum", r.x+8, r.y+16);

			g2.setFont(g2.getFont().deriveFont(Math.max(9f, h*0.030f)));
			g2.setColor(new Color(80, 80, 85));
			String t="t = "+format1(packetElapsed/2.0)+" ms / "+format1(PACKET_PERIOD_SEC/4.0)+" ms";
			g2.drawString(t, r.x+8, r.y+34);

			int px=r.x+8, py=r.y+42;
			int pw=r.width-16, ph=r.height-56;

			g2.setColor(new Color(210, 210, 215));
			g2.drawLine(px, py+ph-1, px+pw, py+ph-1);

			drawMzAxis(g2, px, py, pw, ph);

			if (peaks.isEmpty()) return;

			// Sort by m/z so peaks draw cleanly
			List<Peak> sorted=new ArrayList<>(peaks);
			Collections.sort(sorted, Comparator.comparingDouble(a -> a.mz));

			double maxI=1.0;
			for (Peak pk : sorted)
				if (pk.intensity>maxI) maxI=pk.intensity;

			for (Peak pk : sorted) {
				double frac=(pk.mz-MZ_MIN)/(MZ_MAX-MZ_MIN);
				int x=px+(int)Math.round(frac*pw);

				double a=pk.intensity/maxI;
				int yTop=py+ph-(int)Math.round((ph-4)*a);

				g2.setStroke(new BasicStroke(2.3f));
				g2.setColor(pk.color);
				g2.drawLine(x, py+ph-1, x, yTop);

				g2.setStroke(new BasicStroke(1.1f));
				g2.setColor(new Color(0, 0, 0, 70));
				g2.drawLine(x-2, yTop, x+2, yTop);
			}
		}

		private void drawMzAxis(Graphics2D g2, int px, int py, int pw, int ph) {
			g2.setFont(g2.getFont().deriveFont(Math.max(9f, h*0.03f)));
			g2.setColor(new Color(80, 80, 85));

			int[] ticks= {400, 550, 700, 850, 1000};
			for (int mz : ticks) {
				double frac=(mz-MZ_MIN)/(MZ_MAX-MZ_MIN);
				int x=px+(int)Math.round(frac*pw);
				int y0=py+ph-1, y1=y0+4;
				g2.drawLine(x, y0, x, y1);

				String lab=Integer.toString(mz);
				int tw=g2.getFontMetrics().stringWidth(lab);
				g2.drawString(lab, x-tw/2, y1+12);
			}

			String label="m/z";
			int tw=g2.getFontMetrics().stringWidth(label);
			g2.drawString(label, px+pw/2-tw/2, py+ph+30);
		}

		private static double clamp01(double v) {
			return (v<0)?0:((v>1)?1:v);
		}

		private static double lerp(double a, double b, double u) {
			u=clamp01(u);
			return a+(b-a)*u;
		}

		private static Color colorByMass(double mz) {
			double f=(mz-MZ_MIN)/(MZ_MAX-MZ_MIN);
			float hue=(float)(0.02+0.63*(1.0-f));
			return Color.getHSBColor(hue, 0.70f, 1.0f);
		}

		private static String format1(double v) {
			double x=Math.round(v*10.0)/10.0;
			return Double.toString(x);
		}

		private static final class Ion {
			final double mz;
			final double intensity;
			final Color color;

			double detTime=1.0;
			double phase=0.0; // 0..1 along the full out-and-back path to detector
			boolean detected=false;

			Ion(double mz, double intensity, Color color) {
				this.mz=mz;
				this.intensity=intensity;
				this.color=color;
			}
		}

		private static final class Peak {
			final double mz;
			final double intensity;
			final Color color;

			Peak(double mz, double intensity, Color color) {
				this.mz=mz;
				this.intensity=intensity;
				this.color=color;
			}
		}
	}

	public static void main(String[] args) {
		try {
			FlatLightLaf.setup();
		} catch (Throwable ignore) {
			Logger.errorException(ignore);
		}

		SwingUtilities.invokeLater(() -> {
			JFrame f=new JFrame("Astral Loader Demo");
			AstralLoadingPanel panel=new AstralLoadingPanel("Reading RAW/Astral…");

			f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			f.setContentPane(panel);
			f.setMinimumSize(new Dimension(980, 640));
			f.setLocationByPlatform(true);
			f.setVisible(true);

			f.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosed(WindowEvent e) {
					try {
						panel.stop();
					} catch (Throwable ignore) {
						Logger.errorException(ignore);
					}
				}
			});
		});
	}
}
