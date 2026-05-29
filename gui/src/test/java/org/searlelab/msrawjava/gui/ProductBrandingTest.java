package org.searlelab.msrawjava.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import javax.swing.Icon;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import org.junit.jupiter.api.Test;

class ProductBrandingTest {

	@Test
	void productBranding_separatesGuiProductFromCoreLibrary() {
		assertEquals("MSForest", ProductBranding.PRODUCT_NAME);
		assertEquals("MSRawJava", ProductBranding.CORE_NAME);
		assertEquals("https://github.com/searlelab/MSRawJava", ProductBranding.REPOSITORY_URL);
	}

	@Test
	void menuUsesMsForestForVisibleGuiBranding() {
		JMenuBar bar=MenuManager.createMenuBar(null, null, WindowMenuModel.create(true, -1, java.util.List.of()), null);
		JMenuItem manual=bar.getMenu(3).getItem(0);
		JMenuItem cite=bar.getMenu(3).getItem(1);
		JMenuItem browser=bar.getMenu(2).getItem(0);

		assertEquals("Open Manual", manual.getText());
		assertTrue(manual.getToolTipText().contains("MSForest"));
		assertEquals("How to Cite", cite.getText());
		assertTrue(cite.getToolTipText().contains("MSForest"));
		assertEquals("Bring MSForest to Front", browser.getText());
	}

	@Test
	void manualFile_canBeOverriddenForInstalledManualLookup() {
		String old=System.getProperty("msforest.manual.path");
		try {
			System.setProperty("msforest.manual.path", "/tmp/MSForest-manual.pdf");
			assertEquals(new File("/tmp/MSForest-manual.pdf"), MenuManager.manualFile());
		} finally {
			if (old==null) {
				System.clearProperty("msforest.manual.path");
			} else {
				System.setProperty("msforest.manual.path", old);
			}
		}
	}

	@Test
	void citationBrandsMsForestAndKeepsMsRawJavaProvenance() {
		String about=HowToCiteDialog.aboutHtml();
		String citation=HowToCiteDialog.citationHtml();

		assertTrue(about.contains("MSForest"));
		assertTrue(about.contains("rapid mass spectrometry raw-file triage"));
		assertTrue(about.indexOf("rapid mass spectrometry raw-file triage")<about.indexOf("Searle Lab"));
		assertTrue(about.contains(ProductBranding.TAGLINE));
		assertTrue(citation.contains("MSForest"));
		assertTrue(citation.contains("MSRawJava"));
		assertTrue(citation.contains("https://github.com/searlelab/MSRawJava"));
		assertTrue(citation.contains("RawFileReader reading tool"));
		assertTrue(citation.contains("Thermo Fisher Scientific"));
	}

	@Test
	void howToCiteTopGraphicUsesSplashResource() {
		Icon splash=HowToCiteDialog.loadSplashIcon();

		assertEquals(300, splash.getIconWidth());
		assertEquals(150, splash.getIconHeight());
	}
}
