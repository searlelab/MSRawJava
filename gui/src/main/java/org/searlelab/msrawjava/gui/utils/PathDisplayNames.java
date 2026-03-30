package org.searlelab.msrawjava.gui.utils;

import java.nio.file.Path;

public final class PathDisplayNames {
	private PathDisplayNames() {
	}

	public static String displayNameFor(Path path) {
		Path fileName=(path!=null)?path.getFileName():null;
		if (fileName!=null) return fileName.toString();
		return (path!=null)?path.toString():"";
	}
}
