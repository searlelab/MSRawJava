package org.searlelab.msrawjava.gui.visualization;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

final class XicInputFilter extends DocumentFilter {
	static boolean isAllowedSingleChar(char c) {
		return RawBrowserXicUtils.isAllowedXicChar(c);
	}

	@Override
	public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
		if (string==null||string.isEmpty()) return;
		if (string.length()==1) {
			char c=string.charAt(0);
			if (!isAllowedSingleChar(c)) return;
			super.insertString(fb, offset, string, attr);
			return;
		}
		super.insertString(fb, offset, RawBrowserXicUtils.sanitizeXicPasteChunk(string), attr);
	}

	@Override
	public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
		if (text==null) {
			super.replace(fb, offset, length, null, attrs);
			return;
		}
		if (text.length()==1) {
			char c=text.charAt(0);
			if (!isAllowedSingleChar(c)) return;
			super.replace(fb, offset, length, text, attrs);
			return;
		}
		super.replace(fb, offset, length, RawBrowserXicUtils.sanitizeXicPasteChunk(text), attrs);
	}
}
