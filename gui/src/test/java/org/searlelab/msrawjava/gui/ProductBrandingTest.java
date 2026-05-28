package org.searlelab.msrawjava.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		JMenuItem cite=bar.getMenu(3).getItem(0);
		JMenuItem browser=bar.getMenu(2).getItem(0);

		assertEquals("How to Cite", cite.getText());
		assertTrue(cite.getToolTipText().contains("MSForest"));
		assertEquals("Bring MSForest to Front", browser.getText());
	}

	@Test
	void citationBrandsMsForestAndKeepsMsRawJavaProvenance() {
		String about=HowToCiteDialog.aboutHtml();
		String citation=HowToCiteDialog.citationHtml();

		assertTrue(about.contains("MSForest"));
		assertTrue(about.contains(ProductBranding.TAGLINE));
		assertTrue(citation.contains("MSForest"));
		assertTrue(citation.contains("MSRawJava"));
		assertTrue(citation.contains("https://github.com/searlelab/MSRawJava"));
	}

	@Test
	void howToCiteTopGraphicUsesSplashResource() {
		Icon splash=HowToCiteDialog.loadSplashIcon();

		assertEquals(300, splash.getIconWidth());
		assertEquals(150, splash.getIconHeight());
	}
}
