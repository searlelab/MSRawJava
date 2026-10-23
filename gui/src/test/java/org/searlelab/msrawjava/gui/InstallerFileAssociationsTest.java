package org.searlelab.msrawjava.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class InstallerFileAssociationsTest {
	@Test
	void optionalAssociationPaneProvidesAnUncheckedChoiceForEachTypeBeforeInstallation() throws Exception {
		Document config=configuration();
		XPath xpath=XPathFactory.newInstance().newXPath();
		String screens="/install4j/installerGui/applications/application[@id='installer']/screens";
		NodeList panes=(NodeList)xpath.evaluate(screens+"/screen[@beanClass='com.install4j.runtime.beans.screens.FileAssociationsScreen']", config,
				XPathConstants.NODESET);
		assertEquals(1, panes.getLength());
		Node pane=panes.item(0);
		assertEquals(1, ((NodeList)xpath.evaluate(
				"formComponents/formComponent[@beanClass='com.install4j.runtime.beans.formcomponents.FileAssociationsComponent']", pane,
				XPathConstants.NODESET)).getLength());
		Node installation=(Node)xpath.evaluate(screens+"/screen[@beanClass='com.install4j.runtime.beans.screens.InstallationScreen']", config,
				XPathConstants.NODE);
		assertTrue((pane.compareDocumentPosition(installation)&Node.DOCUMENT_POSITION_FOLLOWING)!=0);

		// The built-in selector creates one checkbox for each association action.
		NodeList choices=(NodeList)xpath.evaluate("actions/action[@beanClass='com.install4j.runtime.beans.actions.desktop.CreateFileAssociationAction']",
				installation, XPathConstants.NODESET);
		assertEquals(4, choices.getLength());
		Set<String> extensions=new HashSet<>();
		for (int i=0; i<choices.getLength(); i++) {
			extensions.add(property(xpath, choices.item(i), "extension", "text()").toLowerCase(Locale.ROOT));
			assertEquals("false", property(xpath, choices.item(i), "selected", "@value"));
		}
		assertEquals(Set.of("raw", "dia", "mzml", "tdf"), extensions);
	}

	@Test
	void everyDesktopRegistersAllSupportedExtensionsWithTheSameSingleInstanceLauncher() throws Exception {
		Document config=configuration();
		XPath xpath=XPathFactory.newInstance().newXPath();
		String launcher="/install4j/launchers/launcher[@id='35']";
		assertEquals("true", xpath.evaluate(launcher+"/executable/@singleInstance", config));
		assertEquals("org.searlelab.msrawjava.gui.GuiMain", xpath.evaluate(launcher+"/java/@mainClass", config));
		Set<String> expected=Set.of("raw", "dia", "mzml", "tdf");
		Set<String> mac=new HashSet<>();
		NodeList associations=(NodeList)xpath.evaluate(launcher+"/macStaticAssociations/fileAssociation", config, XPathConstants.NODESET);
		for (int i=0; i<associations.getLength(); i++) {
			Element association=(Element)associations.item(i);
			assertTrue(mac.add(association.getAttribute("extension").toLowerCase(Locale.ROOT)));
			assertEquals("viewer", association.getAttribute("role"));
			assertEquals("alternate", association.getAttribute("handlerRank"));
		}
		assertEquals(expected, mac);

		Set<String> windowsAndLinux=new HashSet<>();
		Set<String> mimeTypes=new HashSet<>();
		NodeList actions=(NodeList)xpath.evaluate("//action[@beanClass='com.install4j.runtime.beans.actions.desktop.CreateFileAssociationAction']",
				config, XPathConstants.NODESET);
		for (int i=0; i<actions.getLength(); i++) {
			Node action=actions.item(i);
			assertTrue(windowsAndLinux.add(property(xpath, action, "extension", "text()").toLowerCase(Locale.ROOT)));
			assertEquals("35", property(xpath, action, "launcherId", "text()"));
			assertEquals("true", property(xpath, action, "windows", "@value"));
			assertEquals("true", property(xpath, action, "unix", "@value"));
			assertEquals("false", property(xpath, action, "selected", "@value"));
			String mime=property(xpath, action, "unixMimeType", "text()");
			assertTrue(mime.startsWith("application/"));
			assertTrue(mimeTypes.add(mime));
		}
		assertEquals(expected, windowsAndLinux);
		for (String media : Set.of("windows", "macosFolder", "unixInstaller")) {
			assertEquals(1, ((NodeList)xpath.evaluate("/install4j/mediaSets/"+media, config, XPathConstants.NODESET)).getLength());
		}
	}

	@Test
	void associationScreensAndActionsDoNotReuseOtherInstallerIds() throws Exception {
		NodeList applications=configuration().getElementsByTagName("application");
		for (int i=0; i<applications.getLength(); i++) {
			Set<String> ids=new HashSet<>();
			NodeList elements=((Element)applications.item(i)).getElementsByTagName("*");
			for (int j=0; j<elements.getLength(); j++) {
				String id=((Element)elements.item(j)).getAttribute("id");
				if (id.matches("[0-9]+")) assertTrue(ids.add(id), "Duplicate installer ID: "+id);
			}
		}
	}

	private static Document configuration() throws Exception {
		Path config=Path.of("msforest.install4j");
		if (!Files.isRegularFile(config)) config=Path.of("gui", "msforest.install4j");
		return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(config.toFile());
	}

	private static String property(XPath xpath, Node action, String name, String value) throws Exception {
		return xpath.evaluate("serializedBean/property[@name='"+name+"']/"+value, action);
	}
}
