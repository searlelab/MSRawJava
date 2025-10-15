package org.searlelab.msrawjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

class RangeTest {

	@Test
	void normalizationAndBasics() {
		Range r1=new Range(10.0f, 5.0f); // reversed
		assertEquals(5.0f, r1.getStart(), 1e-6);
		assertEquals(10.0f, r1.getStop(), 1e-6);
		assertEquals(5.0f, r1.getRange(), 1e-6);
		assertEquals(7.5f, r1.getMiddle(), 1e-6);

		Range r2=new Range(new float[] {3f, -1f, 2f});
		assertEquals(-1f, r2.getStart(), 1e-6);
		assertEquals(3f, r2.getStop(), 1e-6);

		Range buffered=r1.addBuffer(2.0f);
		assertEquals(3.0f, buffered.getStart(), 1e-6);
		assertEquals(12.0f, buffered.getStop(), 1e-6);

		assertEquals("1.0 to 2.1", new Range(1.04f, 2.06f).toString());
	}

	@Test
	void containsValueAndRange() {
		Range r=new Range(1.0f, 3.0f);
		// value contains (inclusive boundaries)
		assertTrue(r.contains(1.0f));
		assertTrue(r.contains(2.0));
		assertTrue(r.contains(3.0f));
		assertFalse(r.contains(0.999f));
		assertFalse(r.contains(3.001));

		// range overlap/containment (method name "contains" but tests for intersection)
		assertTrue(r.contains(new Range(2.0f, 2.5f))); // fully inside
		assertTrue(r.contains(new Range(0.0f, 1.0f))); // touches at start boundary
		assertTrue(r.contains(new Range(3.0f, 4.0f))); // touches at end boundary
		assertTrue(r.contains(new Range(0.0f, 4.0f))); // r inside another
		assertFalse(r.contains(new Range(4.0f, 5.0f))); // disjoint
	}

	@Test
	void chunkIntoBinsProducesContiguousPartitions() {
		Range r=new Range(0f, 10f);
		List<Range> bins=r.chunkIntoBins(5); // 5 bins of width 2
		assertEquals(5, bins.size());
		float expectedStart=0f;
		for (int i=0; i<bins.size(); i++) {
			Range b=bins.get(i);
			assertEquals(expectedStart, b.getStart(), 1e-6, "bin "+i+" start");
			assertEquals(2f, b.getRange(), 1e-6, "bin "+i+" width");
			expectedStart+=2f;
		}
		assertEquals(10f, bins.get(bins.size()-1).getStop(), 1e-6);
	}

	@Test
	void compareToAndEqualsAndHashCode() {
		Range a=new Range(1f, 2f);
		Range b=new Range(1.0, 2.0); // same
		Range c=new Range(1f, 2.1f);
		assertEquals(0, a.compareTo(b));
		assertTrue(a.equals(b)&&b.equals(a));
		assertEquals(a.hashCode(), b.hashCode());
		assertTrue(a.compareTo(c)<0);
		assertTrue(c.compareTo(a)>0);
	}

	@Test
	void rangeContainsComparatorBehavior() {
		Range small=new Range(4f, 6f);
		Range large=new Range(3f, 10f);
		Range disjoint=new Range(11f, 12f);
		Range touchingLeft=new Range(2f, 3f);
		Range touchingRight=new Range(10f, 11f);

		Comparator<Range> cmp=Range.RANGE_CONTAINS_COMPARATOR;
		// If one range's boundary lies within the other, comparator returns 0
		assertEquals(0, cmp.compare(small, large));
		assertEquals(0, cmp.compare(large, small));
		assertEquals(0, cmp.compare(touchingLeft, large)); // 3 in large
		assertEquals(0, cmp.compare(touchingRight, large)); // 10 in large
		// Otherwise falls back to compareTo
		assertNotEquals(0, cmp.compare(disjoint, large));
		assertTrue(cmp.compare(disjoint, large)>0); // disjoint starts after large
	}

	@Test
	void linearInterpFloatAndInt_withClamping() {
		Range r=new Range(0f, 10f);
		// interior point
		assertEquals(5f, r.linearInterp(5f, 0f, 10f), 1e-6);
		// clamp below
		assertEquals(0f, r.linearInterp(-5f, 0f, 10f), 1e-6);
		// clamp above
		assertEquals(10f, r.linearInterp(15f, 0f, 10f), 1e-6);

		// integer variant rounds
		assertEquals(5, r.linearInterp(5f, 0, 10));
		assertEquals(0, r.linearInterp(-5f, 0, 10));
		assertEquals(10, r.linearInterp(15f, 0, 10));
	}

	@Test
	void linearInterp_edgeCases_deltaXorDeltaYZero() {
		// deltaX == 0 -> midpoint of Y, clamped
		Range degenerate=new Range(5f, 5f);
		assertEquals(7.5f, degenerate.linearInterp(5f, 5f, 10f), 1e-6);

		// deltaY == 0 -> return maxY
		Range r=new Range(0f, 10f);
		assertEquals(3.0f, r.linearInterp(3f, 3f, 3f), 1e-6);
	}

	@Test
	void mapBackToRange_generalAndEdgeCases() {
		Range r=new Range(0f, 10f);
		// Forward: X=2 -> Y=2 (minY=0,maxY=10); Inverse should map Y back to X
		assertEquals(2f, r.mapBackToRange(2f, 0f, 10f), 1e-6);
		// Clamp below
		assertEquals(0f, r.mapBackToRange(-5f, 0f, 10f), 1e-6);
		// Clamp above
		assertEquals(10f, r.mapBackToRange(15f, 0f, 10f), 1e-6);

		// deltaX == 0 -> returns stop
		Range degenerate=new Range(4f, 4f);
		assertEquals(4f, degenerate.mapBackToRange(123f, 0f, 10f), 1e-6);

		// deltaY == 0 -> midpoint of X, clamped
		Range r2=new Range(2f, 6f);
		float mid=(r2.getStart()+r2.getStop())/2f; // 4
		assertEquals(mid, r2.mapBackToRange(5f, 7f, 7f), 1e-6);
	}

	@Test
	void widestRangeAggregatesExtrema() {
		ArrayList<Range> list=new ArrayList<>(Arrays.asList(new Range(1f, 2f), new Range(-5f, -1f), new Range(10f, 12f)));
		Range w=Range.getWidestRange(list);
		assertEquals(-5f, w.getStart(), 1e-6);
		assertEquals(12f, w.getStop(), 1e-6);
	}

	@Test
	void sortingByCompareTo_ordersByStartThenStop() {
		List<Range> ranges=new ArrayList<>(Arrays.asList(new Range(5f, 7f), new Range(1f, 4f), new Range(1f, 3f), new Range(0f, 0f), new Range(2f, 2f)));
		Collections.sort(ranges);
		List<Range> expected=Arrays.asList(new Range(0f, 0f), new Range(1f, 3f), new Range(1f, 4f), new Range(2f, 2f), new Range(5f, 7f));
		for (int i=0; i<expected.size(); i++) {
			assertEquals(0, expected.get(i).compareTo(ranges.get(i)), "index "+i);
		}
	}
}
