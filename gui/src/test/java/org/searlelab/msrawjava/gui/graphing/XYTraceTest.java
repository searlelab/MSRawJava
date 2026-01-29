package org.searlelab.msrawjava.gui.graphing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.model.Range;

class XYTraceTest {

	@Test
	void constructor_sortsPointsByXThenY() {
		double[] xs=new double[] {2.0, 1.0, 1.0};
		double[] ys=new double[] {5.0, 4.0, 3.0};
		XYTrace trace=new XYTrace(xs, ys, GraphType.line, "trace");

		Pair<double[], double[]> arrays=trace.toArrays();
		assertEquals(1.0, arrays.x[0], 1e-9);
		assertEquals(3.0, arrays.y[0], 1e-9);
		assertEquals(1.0, arrays.x[1], 1e-9);
		assertEquals(4.0, arrays.y[1], 1e-9);
		assertEquals(2.0, arrays.x[2], 1e-9);
		assertEquals(5.0, arrays.y[2], 1e-9);
	}

	@Test
	void getMaxXYInRange_returnsHighestYWithinBounds() {
		XYTrace trace=new XYTrace(new double[] {1.0, 2.0, 3.0}, new double[] {5.0, 7.0, 6.0}, GraphType.line, "range");

		XYTrace.XYPoint max=trace.getMaxXYInRange(new Range(1.5, 2.5));
		assertEquals(2.0, max.getX(), 1e-9);
		assertEquals(7.0, max.getY(), 1e-9);
	}

	@Test
	void getMaxY_acrossTracesUsesLargestValue() {
		XYTrace first=new XYTrace(new double[] {1.0}, new double[] {2.0}, GraphType.line, "a");
		XYTrace second=new XYTrace(new double[] {1.0}, new double[] {5.0}, GraphType.line, "b");

		double max=XYTrace.getMaxY(new XYTraceInterface[] {first, second});
		assertEquals(5.0, max, 1e-9);
	}

	@Test
	void compareTo_usesName() {
		XYTrace first=new XYTrace(new double[] {1.0}, new double[] {2.0}, GraphType.line, "alpha");
		XYTrace second=new XYTrace(new double[] {1.0}, new double[] {2.0}, GraphType.line, "beta");

		assertTrue(first.compareTo(second)<0);
	}
}
