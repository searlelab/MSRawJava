package org.searlelab.msrawjava.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/** Keeps GUI tests from reading or writing the developer's saved preferences. */
public final class InMemoryPreferencesFactory implements PreferencesFactory {
	private final Preferences user=new MemoryPreferences(null, "");
	private final Preferences system=new MemoryPreferences(null, "");

	@Override
	public Preferences userRoot() {
		return user;
	}

	@Override
	public Preferences systemRoot() {
		return system;
	}

	static final class MemoryPreferences extends AbstractPreferences {
		private final Map<String, String> values=new HashMap<>();
		private final Map<String, MemoryPreferences> children=new HashMap<>();

		MemoryPreferences(AbstractPreferences parent, String name) {
			super(parent, name);
		}

		@Override
		protected void putSpi(String key, String value) {
			values.put(key, value);
		}

		@Override
		protected String getSpi(String key) {
			return values.get(key);
		}

		@Override
		protected void removeSpi(String key) {
			values.remove(key);
		}

		@Override
		protected void removeNodeSpi() {
			((MemoryPreferences)parent()).children.remove(name());
		}

		@Override
		protected String[] keysSpi() {
			return values.keySet().toArray(new String[0]);
		}

		@Override
		protected String[] childrenNamesSpi() {
			return children.keySet().toArray(new String[0]);
		}

		@Override
		protected AbstractPreferences childSpi(String name) {
			return children.computeIfAbsent(name, key -> new MemoryPreferences(this, key));
		}

		@Override
		protected void syncSpi() {}

		@Override
		protected void flushSpi() {}
	}
}
