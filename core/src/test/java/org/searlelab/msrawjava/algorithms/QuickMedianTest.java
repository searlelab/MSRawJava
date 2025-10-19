package org.searlelab.msrawjava.algorithms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuickMedianTest {

	@Test
	void median_onOddLengthArray_returnsMiddleElementByRank() {
		float[] data=new float[] {9f, 1f, 5f, 3f, 7f}; // sorted -> 1,3,5,7,9
		float m=QuickMedian.median(data);
		assertEquals(5f, m, 1e-6, "Median of 5 distinct values should be the middle element");
	}

	@Test
	void iqr_onSimpleSequence_0to9_isFour() {
		float[] data=new float[10];
		for (int i=0; i<10; i++)
			data[i]=i;
		float iq=QuickMedian.iqr(data);
		// For 0..9 and a rank-based selection with floor((n-1)*p): Q3=6, Q1=2 => IQR=4
		assertEquals(4.5f, iq, 1e-6);
	}

	@Test
	void range90_onSimpleSequence_0to9_isEight() {
		float[] data=new float[10];
		for (int i=0; i<10; i++)
			data[i]=i;
		float r90=QuickMedian.range90(data);
		// For 0..9 and floor((n-1)*p): p95=8, p05=0 => 8
		assertEquals(8.1f, r90, 1e-6);
	}

	@Test
	void iqr_and_range90_zeroOnConstantArray_andMonotoneRelationship() {
		float[] data=new float[] {5f, 5f, 5f, 5f, 5f, 5f};
		float iq=QuickMedian.iqr(data);
		float r90=QuickMedian.range90(data);
		assertEquals(0f, iq, 1e-6);
		assertEquals(0f, r90, 1e-6);
		assertTrue(r90>=iq&&iq>=0f, "Expected range90 >= iqr >= 0");
	}

	@Test
	void swap_exchangesElementsInPlace() {
		float[] a=new float[] {1f, 2f, 3f};
		QuickMedian.swap(a, 0, 2);
		assertArrayEquals(new float[] {3f, 2f, 1f}, a, 1e-6f);
	}

	@Test
	void median_onOddLengthArray_returnsMiddleElementByRankDouble() {
		double[] data=new double[] {9f, 1f, 5f, 3f, 7f}; // sorted -> 1,3,5,7,9
		double m=QuickMedian.median(data);
		assertEquals(5f, m, 1e-6, "Median of 5 distinct values should be the middle element");
	}

	@Test
	void iqr_onSimpleSequence_0to9_isFourDouble() {
		double[] data=new double[10];
		for (int i=0; i<10; i++)
			data[i]=i;
		double iq=QuickMedian.iqr(data);
		// For 0..9 and a rank-based selection with floor((n-1)*p): Q3=6, Q1=2 => IQR=4
		assertEquals(4.5f, iq, 1e-6);
	}

	@Test
	void range90_onSimpleSequence_0to9_isEightDouble() {
		double[] data=new double[10];
		for (int i=0; i<10; i++)
			data[i]=i;
		double r90=QuickMedian.range90(data);
		// For 0..9 and floor((n-1)*p): p95=8, p05=0 => 8
		assertEquals(8.1f, r90, 1e-6);
	}

	@Test
	void iqr_and_range90_zeroOnConstantArray_andMonotoneRelationshipDouble() {
		double[] data=new double[] {5f, 5f, 5f, 5f, 5f, 5f};
		double iq=QuickMedian.iqr(data);
		double r90=QuickMedian.range90(data);
		assertEquals(0f, iq, 1e-6);
		assertEquals(0f, r90, 1e-6);
		assertTrue(r90>=iq&&iq>=0f, "Expected range90 >= iqr >= 0");
	}

	@Test
	void swap_exchangesElementsInPlaceDouble() {
		double[] a=new double[] {1f, 2f, 3f};
		QuickMedian.swap(a, 0, 2);
		assertArrayEquals(new double[] {3f, 2f, 1f}, a, 1e-6f);
	}
}
