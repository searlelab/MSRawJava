package org.searlelab.msrawjava.gui.filebrowser;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ExtensionFilenameFilter implements FilenameFilter {
	private final Set<String> extensionSet;

	public ExtensionFilenameFilter(String... extensions) {
		extensionSet=new HashSet<>();
		for (String e : extensions) {
			if (e!=null&&!e.isBlank()) {
				String s=e.startsWith(".")?e.toLowerCase(Locale.ROOT):"."+e.toLowerCase(Locale.ROOT);
				extensionSet.add(s);
			}
		}
	}

	@Override
	public boolean accept(File dir, String name) {
		File f=new File(dir, name);
		if (f.isDirectory()) return true;
		String lower=name.toLowerCase(Locale.ROOT);
		for (String ex : extensionSet) {
			if (lower.endsWith(ex)) return true;
		}
		return false; // hide non-matching files
	}
}
