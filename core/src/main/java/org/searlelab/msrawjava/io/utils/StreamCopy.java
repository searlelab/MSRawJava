package org.searlelab.msrawjava.io.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * StreamCopy provides bounded-memory file streaming utilities, including fast byte/character copy and targeted text
 * replacement while streaming. 
 */
public class StreamCopy {
	public static final int BUFFER_SIZE=65536;

	public static void streamCopy(Path inFile, Path outFile) throws IOException {
		try (Reader r=new BufferedReader(Files.newBufferedReader(inFile), BUFFER_SIZE);
				Writer w=new BufferedWriter(Files.newBufferedWriter(outFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING), BUFFER_SIZE)) {
			char[] buffer=new char[BUFFER_SIZE];
			int n;
			while ((n=r.read(buffer))!=-1) {
				w.write(buffer, 0, n);
			}
		}
	}

	public static void streamReplace(Path inFile, Path outFile, String find, String replace) throws IOException {
		final int findLength=(find==null?0:find.length());
		if (findLength==0) { // nothing to replace: stream copy
			streamCopy(inFile, outFile);
			return;
		}

		try (Reader r=new BufferedReader(Files.newBufferedReader(inFile), BUFFER_SIZE);
				Writer w=new BufferedWriter(Files.newBufferedWriter(outFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING), BUFFER_SIZE)) {

			char[] buffer=new char[BUFFER_SIZE];
			StringBuilder currentWindow=new StringBuilder(findLength);

			int n;
			while ((n=r.read(buffer))!=-1) {
				for (int i=0; i<n; i++) {
					currentWindow.append(buffer[i]);

					// Keep window bounded and check for matches when it reaches size L
					if (currentWindow.length()<findLength) continue;

					if (currentWindow.length()>findLength) {
						// shouldn’t really happen, but handle gracefully
						w.write(currentWindow.charAt(0));
						currentWindow.deleteCharAt(0);
						if (currentWindow.length()<findLength) continue;
					}

					// win.length() == L
					if (matches(currentWindow, find)) {
						w.write(replace);
						currentWindow.setLength(0); // reset after a full match
					} else {
						// emit the first char and slide by one
						w.write(currentWindow.charAt(0));
						currentWindow.deleteCharAt(0);
					}
				}
			}

			// Flush any trailing characters that didn’t complete a match
			if (currentWindow.length()>0) {
				w.write(currentWindow.toString());
			}
		}
	}

	private static boolean matches(StringBuilder win, String needle) {
		if (win.length()!=needle.length()) return false;
		for (int i=0; i<needle.length(); i++) {
			if (win.charAt(i)!=needle.charAt(i)) return false;
		}
		return true;
	}
}
