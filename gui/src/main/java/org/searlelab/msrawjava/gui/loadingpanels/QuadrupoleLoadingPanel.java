package org.searlelab.msrawjava.gui.loadingpanels;

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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
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
import javax.swing.WindowConstants;

import org.searlelab.msrawjava.logging.Logger;

/** Quadrupole mass filter loader: wide, short analyzer; very wide passband; 10 peptide-like ions (m/z 400–1000). */
public class QuadrupoleLoadingPanel extends LoadingPanel {
	private static final long serialVersionUID=1L;

	private final JLabel label=new JLabel("Acquiring…", SwingConstants.CENTER);
	private final JProgressBar bar=new JProgressBar();

	private final QuadCanvas canvas=new QuadCanvas();

	public QuadrupoleLoadingPanel(String text) {
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

	public static void main(String[] args) {
		try {
			com.formdev.flatlaf.FlatLightLaf.setup();
		} catch (Throwable ignore) {
			Logger.errorException(ignore);
		}
		SwingUtilities.invokeLater(() -> {
			JFrame f=new JFrame("Quadrupole Mass Filter — peptides 400–1000 m/z");
			f.setMinimumSize(new Dimension(640, 480));
			f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			f.setContentPane(new QuadrupoleLoadingPanel("Quad demo"));
			f.pack();
			f.setLocationByPlatform(true);
			f.setVisible(true);
		});
	}

	// =================================================================

	private static final class QuadCanvas extends JComponent {
		private static final long serialVersionUID=1L;

		// ----- Physics / operating point (at reference m0) -----

		// --- Note, this tuning is off! We should fix this eventually --- 
		private static final double WINDOW_WIDTH=50.0;
		private static final double WINDOW_STEP=50.0;
		private static final double WINDOW_MIN=500.0;
		private static final double WINDOW_MAX=1300.0;

		private final int windowCount=(int)Math.floor((WINDOW_MAX-WINDOW_MIN-WINDOW_WIDTH)/WINDOW_STEP)+1;

		private int windowIndex=0;
		private double centerMz=WINDOW_MIN+WINDOW_WIDTH/2.0;

		// Optional: per-window U/V adjustment factor (U scales by this, V unchanged)
		private double uvRatio=1.0;

		// Reference and peptide range (m/z)
		private static final double REF_MZ=700.0; // m0 for a0,q0
		private static final double MZ_MIN=400.0;
		private static final double MZ_MAX=1000.0;

		// Operate mid-lobe with correct slope (≈ tip slope 0.335)
		private static final double Q0=0.52; // q at m0 when scale=1 (≈center of lobe)
		private static final double A0=0.65*Q0; // adjusts the width of the window (lower=larger window)

		// RF (unchanged unless you want a different visual speed)
		private static final double F_RF=20.0;
		private static final double OMEGA=2*Math.PI*F_RF;
		private static final double R0=1.0;
		private static final double H_RF=1.0/(F_RF*40.0); // ~40 steps per RF cycle

		// Use the same amplitude as ions (±INIT/2 on each axis)
		private static final double INIT=0.10;

		// For the pass-map and tuner: sample different RF phases and (x,y) directions
		private static final int PHASE_SAMPLES=4; // RF start phases
		private static final int AMP_DIR_SAMPLES=4; // directions on a circle at |r| = INIT/√2

		// Animation / traversal
		private static final int FPS_MS=Math.round(1000f*1f/30f); // ~30 FPS
		private static final double FLIGHT_TIME=3; // seconds across analyzer (visual)
		private final LoadingPanel.MinPriorityAnimationLoop anim;
		private long lastNanos=0L;

		// Geometry (wide + short analyzer)
		private int w, h, pad;
		private Rectangle2D quadRect;
		private Rectangle bandBox;
		private double leftX, rightX, centerY, Lz_px, pxPerUnit;
		private Shape rodTop, rodBottom;

		// Fields for scale=1 at m0 (so a(m0)=A0, q(m0)=Q0)
		private final double U0=A0*(R0*R0)*(OMEGA*OMEGA)/4.0;
		private final double V0=Q0*(R0*R0)*(OMEGA*OMEGA)/2.0;

		// Ions (10 peptide-like)
		private static final int NUM_IONS=100;
		private final List<Ion> ions=new ArrayList<>(NUM_IONS);
		private final Random rng=new Random(1234);

		// Band-pass sampling over 400..1000 m/z
		private static final int BINS=200;
		private final double[] pass=new double[BINS];

		private boolean geometryReady=false;

		QuadCanvas() {
			setOpaque(true);

			// Build ions with fixed m/z in range, random but repeatable
			for (int i=0; i<NUM_IONS; i++) {
				double mz=MZ_MIN+(MZ_MAX-MZ_MIN)*rng.nextDouble();
				ions.add(new Ion(mz, colorByMz(mz)));
			}

			anim=LoadingPanel.createMinPriorityAnimationLoop(FPS_MS, "quadrupole-loading-animation", this::tick);
			// start timer in addNotify() after geometry
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

		@Override
		public void addNotify() {
			super.addNotify();
			recomputeGeometry();
			lastNanos=System.nanoTime();
			if (!anim.isRunning()) anim.start();
		}

		@Override
		public void removeNotify() {
			anim.stop();
			super.removeNotify();
		}

		void start() {
			lastNanos=System.nanoTime();
			if (!anim.isRunning()) anim.start();
		}

		void stop() {
			anim.stop();
		}

		// ---------------- Geometry (wide + short analyzer) ----------------

		private void recomputeGeometry() {
			w=getWidth();
			h=getHeight();
			pad=Math.max(8, Math.min(w, h)/24);

			double cxMid=w*0.3; // left-centered (~25%)
			double analyzerWidth=Math.max(300, w*0.5); // wide
			double analyzerHeight=Math.max(120, h*0.25); // short

			quadRect=new Rectangle2D.Double(cxMid-analyzerWidth/2.0, (h-analyzerHeight)/2.0, analyzerWidth, analyzerHeight);

			leftX=quadRect.getX()+18;
			rightX=quadRect.getMaxX()-18;
			centerY=quadRect.getCenterY();
			Lz_px=rightX-leftX;

			// Put r0 at ~26% of half-height (tight field aperture)
			double halfGap=analyzerHeight*0.26;
			pxPerUnit=halfGap/R0;

			// Four long translucent rods spanning analyzer
			double rodLen=Lz_px;
			double rodR=pxPerUnit*0.20;
			double yTop=centerY-halfGap;
			double yBot=centerY+halfGap;

			rodTop=new RoundRectangle2D.Double(leftX, yTop-rodR, rodLen, 2*rodR, 12, 12);
			rodBottom=new RoundRectangle2D.Double(leftX, yBot-rodR, rodLen, 2*rodR, 12, 12);

			// Band-pass box on the right
			int bw=Math.max(200, (int)(w*0.40));
			int bh=Math.max(120, (int)(h*0.40));
			int bx=w-bw-pad;
			int by=(h-bh)/2;
			bandBox=new Rectangle(bx, by, bw, bh);

			// Reset all ions
			for (Ion ion : ions) {
				resetIon(ion);
			}

			computeBandpass();
			geometryReady=true;
		}

		private void resetIon(Ion ion) {
			ion.t=0;
			ion.z_px=0;
			Random r=new Random(ion.seed);
			double ang=2*Math.PI*r.nextDouble();
			double rad=INIT/Math.sqrt(2.0);
			ion.x=rad*Math.cos(ang);
			ion.y=rad*Math.sin(ang);
			ion.vx=ion.vy=0;
			ion.done=ion.hitRod=false;
			ion.path.reset();
			ion.path.moveTo((float)mapX(ion.z_px), (float)mapY(ion.x, ion.y));
		}

		// ---------------- Field scan (fixed U/V) ----------------

		// scale is set by the center of the current window
		private double currentScale() {
			return centerMz/REF_MZ;
		}

		// Adjust the slope by scaling U only (simple, fast, effective)
		private double Ueff() {
			return currentScale()*uvRatio*U0;
		}

		private double Veff() {
			return currentScale()*V0;
		}

		// ---------------- Physics (a ∝ U/m, q ∝ V/m ; r0=1, e=1) ----------------

		private static double mzToNormMass(double mz) {
			return mz/REF_MZ;
		} // m_norm = m/REF

		private double ax(double t, double x, double mNorm) {
			double field=(Ueff()+Veff()*Math.cos(OMEGA*t));
			return -(field/mNorm)*x;
		}

		private double ay(double t, double y, double mNorm) {
			double field=(Ueff()+Veff()*Math.cos(OMEGA*t));
			return +(field/mNorm)*y;
		}

		// ---------------- Animation tick ----------------

		private void tick() {
			if (!geometryReady||Lz_px<=0) return;

			long now=System.nanoTime();
			double dt=(lastNanos==0)?(FPS_MS/1000.0):(now-lastNanos)/1_000_000_000.0;
			dt=Math.min(dt, 0.05);
			lastNanos=now;

			// RF substeps
			int sub=Math.max(1, (int)Math.ceil(dt/H_RF));
			double h=dt/sub; // h ≈ H_RF (last substep may be slightly smaller)

			boolean allDone=true;
			for (Ion ion : ions) {
				if (!ion.done) {
					advanceIon(ion, h, sub);
					allDone=false;
				}
			}
			if (allDone) {
				for (Ion ion : ions) {
					advanceIon(ion, h, sub);
					resetIon(ion);
				}
				computeBandpass(); // will set centerMz and uvRatio
				windowIndex=(windowIndex+1)%windowCount;
			}

			repaint();
		}

		private void advanceIon(Ion ion, double h, int subSteps) {
			if (ion.path.getCurrentPoint()==null) {
				ion.path.moveTo((float)mapX(ion.z_px), (float)mapY(ion.x, ion.y));
			}
			double mNorm=mzToNormMass(ion.mz);

			for (int s=0; s<subSteps; s++) {
				double t0=ion.t;

				// Velocity-Verlet, time-dependent a(t)
				double ax0=ax(t0, ion.x, mNorm);
				double ay0=ay(t0, ion.y, mNorm);

				ion.vx+=0.5*h*ax0;
				ion.vy+=0.5*h*ay0;

				ion.x+=h*ion.vx;
				ion.y+=h*ion.vy;
				ion.t+=h;

				double ax1=ax(ion.t, ion.x, mNorm);
				double ay1=ay(ion.t, ion.y, mNorm);

				ion.vx+=0.5*h*ax1;
				ion.vy+=0.5*h*ay1;

				// constant axial speed (visual)
				double vz_px=Lz_px/FLIGHT_TIME;
				ion.z_px+=vz_px*h;

				if (Math.hypot(ion.x, ion.y)>=R0) {
					ion.hitRod=true;
					ion.done=true;
					break;
				}
				if (ion.z_px>=Lz_px) {
					ion.done=true;
					break;
				}
			}

			ion.path.lineTo((float)mapX(ion.z_px), (float)mapY(ion.x, ion.y));
		}

		// ---------------- Band-pass (400–1000 m/z, current scale) ----------------

		private void computeBandpass() {
			centerMz=WINDOW_MIN+WINDOW_WIDTH/2.0+windowIndex*WINDOW_STEP;

			// 1) center the window at centerMz
			tuneUvRatioForCenter(centerMz);
			// 2) make the width ~ 50 m/z
			tuneUvRatioForWidth(centerMz);

			for (int i=0; i<BINS; i++) {
				double f=i/(double)(BINS-1);
				double mz=MZ_MIN+f*(MZ_MAX-MZ_MIN);
				pass[i]=simulatePassScore(mz); // 0..1 fraction
			}
		}

		// --- Center tuning: pick uvRatio that maximizes pass at the current center mass ---
		private void tuneUvRatioForCenter(double mCenter) {
			double bestR=uvRatio, best=-1;
			// small bracket around the current ratio (fast & robust)
			for (double r=Math.max(0.5, uvRatio-0.3); r<=Math.min(1.6, uvRatio+0.3); r+=0.02) {
				uvRatio=r;
				double s=simulatePassScore(mCenter);
				if (s>best) {
					best=s;
					bestR=r;
				}
			}
			uvRatio=bestR;
		}

		// --- Width tuning: adjust uvRatio so the two edges are ~0.5 each ---
		private void tuneUvRatioForWidth(double mCenter) {
			final double half=WINDOW_WIDTH/2.0;
			final double lo=mCenter-half, hi=mCenter+half;

			double r=uvRatio;
			for (int it=0; it<8; it++) {
				uvRatio=r;
				double pL=simulatePassScore(lo);
				double pH=simulatePassScore(hi);

				// both > 0.5 ⇒ window too wide ⇒ increase slope (narrow)
				if (pL>0.5&&pH>0.5) r*=1.05;
				// both < 0.5 ⇒ window too narrow ⇒ decrease slope (widen)
				else if (pL<0.5&&pH<0.5) r*=0.95;
				else break; // one side in/one out ⇒ width about right

				r=Math.max(0.5, Math.min(1.6, r));
			}
			uvRatio=r;
		}

		private boolean simulatePassWithIC(double mz, double x0, double y0, double t0) {
			double mNorm=mzToNormMass(mz);
			double x=x0, y=y0, vx=0, vy=0, t=t0;

			double dt=1.0/(F_RF*40.0);
			double vz_px=Lz_px/FLIGHT_TIME;
			double z_px=0;
			int maxSteps=(int)Math.ceil((Lz_px/vz_px)/dt)+1;

			for (int k=0; k<maxSteps; k++) {
				double field0=(Ueff()+Veff()*Math.cos(OMEGA*t));
				double ax0=-(field0/mNorm)*x;
				double ay0=+(field0/mNorm)*y;

				vx+=0.5*dt*ax0;
				vy+=0.5*dt*ay0;
				x+=dt*vx;
				y+=dt*vy;
				t+=dt;

				double field1=(Ueff()+Veff()*Math.cos(OMEGA*t));
				double ax1=-(field1/mNorm)*x;
				double ay1=+(field1/mNorm)*y;

				vx+=0.5*dt*ax1;
				vy+=0.5*dt*ay1;

				z_px+=vz_px*dt;
				if (Math.hypot(x, y)>=R0) return false;
				if (z_px>=Lz_px) return true;
			}
			return false;
		}

		private double simulatePassScore(double mz) {
			int hits=0, trials=0;
			double r=INIT/Math.sqrt(2.0);

			for (int p=0; p<PHASE_SAMPLES; p++) {
				double t0=p*(2.0*Math.PI/OMEGA)/PHASE_SAMPLES; // spread RF phase
				for (int d=0; d<AMP_DIR_SAMPLES; d++) {
					double ang=d*(2.0*Math.PI/AMP_DIR_SAMPLES);
					double x0=r*Math.cos(ang);
					double y0=r*Math.sin(ang);
					if (simulatePassWithIC(mz, x0, y0, t0)) hits++;
					trials++;
				}
			}
			return hits/(double)trials; // 0..1
		}

		// ---------------- Mapping & painting ----------------

		private double mapX(double z_px) {
			return leftX+z_px;
		}

		private double mapY(double x, double y) {
			// 45° mix lets us see the alternate focusing/defocusing as a “wriggle”
			return centerY+(x-y)*0.70710678*pxPerUnit;
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2=(Graphics2D)g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
				g2.setColor(getBackground());
				g2.fillRect(0, 0, getWidth(), getHeight());

				if (quadRect==null) recomputeGeometry();

				// Analyzer frame
				g2.setColor(new Color(245, 246, 248));
				g2.fill(quadRect);
				g2.setColor(new Color(180, 182, 188));
				g2.setStroke(new BasicStroke(1.2f));
				g2.draw(quadRect);

				// Rods (long translucent bars)
				paintRods(g2);

				// Entry guide
				g2.setColor(new Color(160, 170, 175));
				g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] {6f, 6f}, 0));
				g2.draw(new Line2D.Double(leftX-40, centerY, leftX, centerY));

				// Detector
				paintDetector(g2);

				// Trajectories for all ions
				for (Ion ion : ions)
					paintIon(g2, ion);

				// Scan readout
				g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
				g2.setColor(new Color(70, 70, 75));

				// Band-pass box (400–1000 m/z)
				paintBandbox(g2);
			} finally {
				g2.dispose();
			}
		}

		private void paintRods(Graphics2D g2) {
			Color fill=new Color(200, 205, 212, 180);
			Color edge=new Color(130, 135, 142);
			g2.setColor(fill);
			g2.fill(rodTop);
			g2.fill(rodBottom);
			g2.setColor(edge);
			g2.setStroke(new BasicStroke(1.0f));
			g2.draw(rodTop);
			g2.draw(rodBottom);
		}

		private void paintDetector(Graphics2D g2) {
			double r=12;
			double x=rightX+8, y=centerY;
			g2.setColor(new Color(230, 235, 240));
			g2.fill(new Ellipse2D.Double(x-r, y-r, 2*r, 2*r));
			g2.setColor(new Color(140, 145, 150));
			g2.draw(new Ellipse2D.Double(x-r, y-r, 2*r, 2*r));
		}

		private void paintIon(Graphics2D g2, Ion ion) {
			// path
			g2.setStroke(new BasicStroke(2.0f));
			g2.setColor(new Color(0, 0, 0, 35));
			g2.draw(ion.path);
			g2.setColor(ion.color);
			g2.draw(ion.path);

			// head
			double x=mapX(ion.z_px);
			double y=mapY(ion.x, ion.y);
			double s=6;
			g2.setColor(new Color(255, 255, 255, 120));
			g2.fill(new Ellipse2D.Double(x-s, y-s, 2*s, 2*s));
			g2.setColor(ion.color);
			g2.setStroke(new BasicStroke(1.2f));
			g2.draw(new Ellipse2D.Double(x-s, y-s, 2*s, 2*s));

			if (ion.done&&ion.hitRod) {
				g2.setColor(ion.color);
				g2.setStroke(new BasicStroke(2f));
				g2.draw(new Line2D.Double(x-8, y-8, x+8, y+8));
				g2.draw(new Line2D.Double(x-8, y+8, x+8, y-8));
			}
		}

		private void paintBandbox(Graphics2D g2) {
			Rectangle r=bandBox;
			g2.setColor(new Color(248, 250, 252));
			g2.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
			g2.setColor(new Color(150, 150, 155));
			g2.setStroke(new BasicStroke(1f));
			g2.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10);

			// Title
			g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
			g2.setColor(new Color(60, 60, 65));
			g2.drawString("Band-pass vs m/z (current isolation)", r.x+10, r.y+18);

			// Plot area
			int px=r.x+14, py=r.y+26;
			int pw=r.width-28, ph=r.height-66;

			// Axis + ticks (400..1000)
			g2.setColor(new Color(210, 210, 215));
			g2.drawLine(px, py+ph, px+pw, py+ph);
			g2.setFont(getFont().deriveFont(11f));
			g2.setColor(new Color(80, 80, 85));
			int[] ticks= {400, 550, 700, 850, 1000};
			for (int t : ticks) {
				double frac=(t-MZ_MIN)/(MZ_MAX-MZ_MIN);
				int x=px+(int)Math.round(frac*pw);
				g2.drawLine(x, py+ph, x, py+ph+4);
				String label=String.valueOf(t);
				int tw=g2.getFontMetrics().stringWidth(label);
				g2.drawString(label, x-tw/2, py+ph+16);
			}
			g2.drawString("m/z", px+pw/2-8, py+ph+32);

			// Bars for pass-map: exact fractional mapping so last bin ends at px+pw
			Shape oldClip=g2.getClip();
			g2.setClip(new Rectangle(px, py, pw, ph)); // clip drawing to plot area

			// draws the selected range
			for (int i=0; i<BINS; i++) {
				double f0=i/(double)BINS;
				double f1=(i+1)/(double)BINS;
				int x0=px+(int)Math.round(f0*pw);
				int x1=px+(int)Math.round(f1*pw);
				int wbar=Math.max(1, x1-x0);

				double a=pass[i]>0.6?1:0;
				int hbar=(int)Math.round(ph*a);
				int y=py+ph-hbar;
				Color fill=(a>0.5)?new Color(120, 190, 120):new Color(200, 205, 212);
				Color edge=(a>0.5)?new Color(60, 120, 60):new Color(160, 165, 170);
				g2.setColor(fill);
				g2.fillRect(x0, y, wbar, hbar);
				g2.setColor(edge);
				g2.drawRect(x0, y, wbar, hbar);
			}
			g2.setClip(oldClip); // restore

			// Mark our ion m/z positions
			for (Ion ion : ions) {
				double frac=(ion.mz-MZ_MIN)/(MZ_MAX-MZ_MIN);
				frac=Math.max(0, Math.min(1, frac));
				int x=px+(int)Math.round(frac*pw);
				g2.setColor(ion.color);
				g2.setStroke(new BasicStroke(1.3f));
				g2.drawLine(x, py, x, py+ph);
			}
		}

		// ---------------- Helpers ----------------

		private static Color colorByMz(double mz) {
			// Map 400..1000 to hue: light/fast (400) → blue; heavy (1000) → red
			double f=(mz-MZ_MIN)/(MZ_MAX-MZ_MIN); // 0..1
			float hue=(float)(0.62-0.60*f); // ~blue → ~red
			return Color.getHSBColor(hue, 0.75f, 1.0f);
		}

		private static final class Ion {
			final double mz; // peptide-like m/z
			final Color color;
			final int seed; // deterministic offsets per ion
			double x, y, vx, vy;
			double z_px, t;
			boolean done, hitRod;
			final Path2D.Float path=new Path2D.Float();

			Ion(double mz, Color c) {
				this.mz=mz;
				this.color=c;
				this.seed=(int)Math.round(mz*1000);
			}
		}
	}
}
