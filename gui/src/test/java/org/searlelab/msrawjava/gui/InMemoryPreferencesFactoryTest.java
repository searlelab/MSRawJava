package org.searlelab.msrawjava.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.prefs.Preferences;

import org.junit.jupiter.api.Test;

class InMemoryPreferencesFactoryTest {
	@Test
	void guiTestsUseMemoryInsteadOfTheDevelopersPreferenceStore() {
		assertTrue(Preferences.userRoot() instanceof InMemoryPreferencesFactory.MemoryPreferences);
		assertTrue(Preferences.systemRoot() instanceof InMemoryPreferencesFactory.MemoryPreferences);
		assertTrue(GUIPreferences.getPreferences() instanceof InMemoryPreferencesFactory.MemoryPreferences);
	}
}
