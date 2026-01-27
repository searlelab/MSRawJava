package org.searlelab.msrawjava.algorithms.demux;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.Range;

class DemuxWindowTest {

	@Test
	void testContainsAndOverlaps() {
		DemuxWindow window=new DemuxWindow(400.0, 410.0, 0);

		assertTrue(window.contains(400.0));
		assertTrue(window.contains(405.0));
		assertTrue(window.contains(410.0));
		assertFalse(window.contains(399.9));
		assertFalse(window.contains(410.1));

		assertTrue(window.overlaps(new Range(409.0, 420.0)));
		assertTrue(window.overlaps(new Range(395.0, 400.5)));
		assertFalse(window.overlaps(new Range(410.1, 420.0)));
	}

	@Test
	void testContainedByWithTolerance() {
		DemuxWindow window=new DemuxWindow(400.0, 410.0, 0);

		assertTrue(window.isContainedBy(400.05, 409.95));
		assertTrue(window.isContainedBy(399.95, 410.05));
		assertFalse(window.isContainedBy(400.5, 409.4));
	}

	@Test
	void testComparisonEqualityAndHash() {
		DemuxWindow a=new DemuxWindow(400.0, 410.0, 0);
		DemuxWindow b=new DemuxWindow(400.0, 410.0, 0);
		DemuxWindow c=new DemuxWindow(400.0, 410.0, 1);
		DemuxWindow d=new DemuxWindow(410.0, 420.0, 2);

		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(a, c);
		assertTrue(a.compareTo(c)<0);
		assertTrue(a.compareTo(d)<0);
	}

	@Test
	void testToStringFormat() {
		DemuxWindow window=new DemuxWindow(512.48, 520.48, 3);
		String text=window.toString();
		assertTrue(text.contains("DemuxWindow[3"));
		assertTrue(text.contains("512.48"));
		assertTrue(text.contains("520.48"));
	}
}
