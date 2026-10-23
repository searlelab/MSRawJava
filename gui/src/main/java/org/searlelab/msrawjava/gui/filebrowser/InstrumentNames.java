package org.searlelab.msrawjava.gui.filebrowser;

import java.util.Collection;
import java.util.Map;
import java.util.TreeSet;

import org.searlelab.msrawjava.io.mzml.InstrumentId;

/** Text normalization for the optional directory-table instrument field. */
final class InstrumentNames {

	private InstrumentNames() {
	}

	static String clean(String name) {
		if (name==null||name.isBlank()||"instrument model".equalsIgnoreCase(name.trim())) return null;
		return name.trim();
	}

	static String fromConfigurations(Collection<InstrumentId> instruments) {
		TreeSet<String> names=new TreeSet<>();
		for (InstrumentId instrument : instruments) {
			String name=clean(instrument.name);
			if (name!=null) names.add(name);
		}
		return names.isEmpty()?null:String.join("; ", names);
	}

	static String fromMetadata(Map<String, String> metadata) {
		String name=clean(metadata.get("meta.InstrumentName"));
		return name!=null?name:clean(metadata.get("instrument.model"));
	}
}
