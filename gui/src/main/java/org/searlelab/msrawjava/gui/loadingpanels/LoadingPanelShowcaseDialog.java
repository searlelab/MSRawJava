package org.searlelab.msrawjava.gui.loadingpanels;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;

import javax.swing.JFrame;

public class LoadingPanelShowcaseDialog extends JDialog {
	private static final long serialVersionUID=1L;

	private final JTabbedPane tabs=new JTabbedPane();
	private final List<LoadingPanel> loadingPanels=new ArrayList<>(4);

	private final JEditorPane quadrupoleNotes=new JEditorPane("text/html", "<html><p>"
			+ "A quadrupole mass filter is four parallel metal rods that create an electric \"corridor\" that only certain ions can travel through without crashing into the rods. "
			+ "Opposite rods are electrically paired, and the voltages alternate as a combination of a steady DC term and a rapidly oscillating RF term, which makes the ion’s sideways motion either stable or unstable depending on its mass-to-charge ratio <em>m</em>/<em>z</em>. "
			+ "Intuitively, lighter ions get “shaken” more by the RF field and heavier ions respond more sluggishly, so for any chosen settings of DC and RF (often described by a ratio like <em>U</em>/<em>V</em>), only a narrow band of <em>m</em>/<em>z</em> has a stable path down the middle. "
			+ "Ions with unstable motion grow in amplitude until they hit a rod and are removed, while the stable ions pass through to the detector. "
			+ "By scanning the voltages together (keeping the same stability condition), the quadrupole acts like a tunable sieve that selects one <em>m</em>/<em>z</em> after another.");
	private final JEditorPane tofNotes=new JEditorPane("text/html", "<html><p>"
			+ "A time-of-flight (ToF) mass analyzer separates ions by how fast they move after receiving the same push of kinetic energy in an electric field. "
			+ "If ions are accelerated through a voltage <em>V</em>, they leave with roughly "
			+ "<em>qV</em> &asymp; (1/2)<em>mv</em><sup>2</sup>, so lighter ions end up with higher speed <em>v</em> than heavier ones with the same charge <em>q</em>. "
			+ "They then drift through a field-free region of fixed length <em>L</em>, and the flight time is <em>t</em> = <em>L</em>/<em>v</em>, which means <em>t</em> &prop; &radic;(<em>m</em>/<em>z</em>). "
			+ "Ions that reach the detector earlier are lower <em>m</em>/<em>z</em>, and ions arriving later are higher <em>m</em>/<em>z</em>, so the analyzer turns arrival time into a mass spectrum. "
			+ "Since ToF analyzers operate very quickly, the analyzer can repeat many \"pushes\" per millisecond and average the resulting spectra, which boosts signal-to-noise. "
			+ "Many ToF analyzers add a reflectron, an electrostatic “mirror,” to correct small energy differences so ions of the same <em>m</em>/<em>z</em> bunch together more tightly in time and give sharper peaks, while also creating a longer flight path.</p>");
	private final JEditorPane astralNotes=new JEditorPane("text/html", "<html><p>"
			+ "An Astral analyzer is a 1D time-of-flight (ToF) folded into a 2D space where ions are injected as a tight packet and then make many controlled back-and-forth passes between two asymetric electrostatic ion mirrors before they hit the detector. "
			+ "Each pass adds path length, so small speed differences have more time to turn into measurable arrival-time differences, which boosts resolving power without needing a physically long drift tube. "
			+ "The key trick is that the mirrors and shaped electrode geometry are designed to keep the packet from spreading sideways as it reflects repeatedly (so ions do not “walk” into electrodes and get lost), and they are tuned so ions of the same <em>m</em>/<em>z</em> stay nearly synchronized in flight time even if they started with slightly different energies or angles. "
			+ "At the end, a very fast pulsed detector timestamps the packet, and the instrument converts those arrival times into <em>m</em>/<em>z</em> using the same logic of a ToF. "
			+ "The result is ToF-style timing, but with multi-reflection focusing and near-lossless transport that supports very fast, sensitive MS/MS acquisition.</p>");
	private final JEditorPane fticrNotes=new JEditorPane("text/html", "<html><p>"
			+ "In an FT-ICR analyzer, ions are trapped in a strong magnetic field and their motion becomes a clean circular “cyclotron” orbit, because the magnetic force bends their path without doing work on them. "
			+ "For a given magnetic field <em>B</em>, the cyclotron angular frequency is very simple: &omega;<sub>c</sub> = <em>qB</em>/<em>m</em>, so ions with different <em>m</em>/<em>z</em> orbit at different rates, like runners on the same track with different lap speeds. "
			+ "A short RF pulse “kicks” the ion packet so the orbits become larger and phase-coherent, which makes the ions act like a tiny rotating charge distribution. "
			+ "As that rotating packet passes near detector plates, it induces a faint alternating image current, and the instrument records the combined time-domain signal from all ions at once. "
			+ "A Fourier transform turns that signal into a set of frequencies, then the frequencies are mapped back to <em>m</em>/<em>z</em>.</p>");

	public LoadingPanelShowcaseDialog(Frame parent) {
		super((Frame)null, "Loading Panels", false);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		buildUi();
		setSize(980, 720);
		if (parent!=null) {
			setLocationRelativeTo(parent);
		} else {
			setLocationByPlatform(true);
		}
	}

	public static LoadingPanelShowcaseDialog showDialog(Frame parent) {
		LoadingPanelShowcaseDialog dialog=new LoadingPanelShowcaseDialog(parent);
		SwingUtilities.invokeLater(() -> dialog.setVisible(true));
		return dialog;
	}

	public static void main(String[] args) {
		try {
			com.formdev.flatlaf.FlatLightLaf.setup();
		} catch (Throwable ignore) {
		}
		SwingUtilities.invokeLater(() -> {
			LoadingPanelShowcaseDialog dialog=new LoadingPanelShowcaseDialog(null);
			dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			dialog.setVisible(true);
		});
	}

	public JEditorPane getQuadrupoleNotes() {
		return quadrupoleNotes;
	}

	public JEditorPane getTofNotes() {
		return tofNotes;
	}

	public JEditorPane getAstralNotes() {
		return astralNotes;
	}

	public JEditorPane getFticrNotes() {
		return fticrNotes;
	}

	private void buildUi() {
		tabs.addTab("Quadrupole Mass Filter", buildTab(new QuadrupoleLoadingPanel("Quadrupole Mass Filter"), quadrupoleNotes));
		tabs.addTab("ToF Analyzer", buildTab(new TOFLoadingPanel("ToF Analyzer"), tofNotes));
		tabs.addTab("Astral Analyzer", buildTab(new AstralLoadingPanel("Astral Analyzer"), astralNotes));
		tabs.addTab("FT-ICR", buildTab(new FTICRLoadingPanel("FT-ICR"), fticrNotes));

		tabs.addChangeListener(e -> updateActivePanel());
		tabs.setSelectedIndex(0);
		updateActivePanel();

		JButton close=new JButton("Close");
		close.addActionListener(e -> {
			stopAll();
			setVisible(false);
			dispose();
		});
		JPanel buttons=new JPanel(new FlowLayout(FlowLayout.CENTER));
		buttons.add(close);

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(tabs, BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);

		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				stopAll();
			}

			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				stopAll();
			}
		});
	}

	private JPanel buildTab(LoadingPanel panel, JEditorPane notes) {
		loadingPanels.add(panel);
		panel.setPreferredSize(new Dimension(900, 350));

		notes.setEditable(false);
		JScrollPane scroll=new JScrollPane(notes);
		int width=10;
		scroll.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(width, width, width, width), BorderFactory.createTitledBorder("<html><b>Teaching notes")));
		scroll.setPreferredSize(new Dimension(0, 180));

		JPanel wrapper=new JPanel(new BorderLayout());
		wrapper.add(panel, BorderLayout.CENTER);
		wrapper.add(scroll, BorderLayout.SOUTH);
		return wrapper;
	}

	private void updateActivePanel() {
		int index=tabs.getSelectedIndex();
		for (int i=0; i<loadingPanels.size(); i++) {
			LoadingPanel panel=loadingPanels.get(i);
			if (i==index) {
				panel.start();
			} else {
				panel.stop();
			}
		}
	}

	private void stopAll() {
		for (LoadingPanel panel : loadingPanels)
			panel.stop();
	}
}
