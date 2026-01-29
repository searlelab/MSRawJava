package org.searlelab.msrawjava.gui.graphing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.utils.Pair;

class HistogramUtilsTest {

	@Test
	void histogramFromLog10_handlesEmpty() {
		XYTrace trace=HistogramUtils.histogramFromLog10(new float[0], "empty");
		Pair<double[], double[]> arrays=trace.toArrays();
		assertEquals(0, arrays.x.length);
		assertEquals(0, arrays.y.length);
	}

	@Test
	void histogramFromLog10_matchesProtectedLog10Histogram() {
		float[] values=new float[] {1.0f, 10.0f, 100.0f, 0.0f, -5.0f};
		float[] logs=new float[values.length];
		for (int i=0; i<values.length; i++) {
			logs[i]=HistogramUtils.protectedLog10(values[i]);
		}

		Pair<double[], double[]> direct=HistogramUtils.histogram(logs, "direct").toArrays();
		Pair<double[], double[]> fromLog=HistogramUtils.histogramFromLog10(values, "log10").toArrays();

		assertArrayEquals(direct.x, fromLog.x, 1e-9);
		assertArrayEquals(direct.y, fromLog.y, 1e-9);
	}

	@Test
	void histogram_countsAllValues() {
		float[] values=new float[] {1.0f, 1.0f, 2.0f, 5.5f, 7.0f};
		Pair<double[], double[]> arrays=HistogramUtils.histogram(values, "hist").toArrays();
		assertTrue(arrays.x.length>0);
		assertEquals(arrays.x.length, arrays.y.length);

		double sum=0.0;
		for (double count : arrays.y) {
			sum+=count;
		}
		assertEquals(values.length, sum, 1e-9);
	}

	@Test
	void protectedLog10_clampsNonPositive() {
		assertEquals(0.0f, HistogramUtils.protectedLog10(0.0f), 1e-6f);
		assertEquals(0.0f, HistogramUtils.protectedLog10(-1.0f), 1e-6f);
		assertEquals(2.0f, HistogramUtils.protectedLog10(100.0f), 1e-6f);
	}
}
