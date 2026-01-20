package org.searlelab.msrawjava.algorithms.demux;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;

import org.ejml.data.DMatrixRMaj;
import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.Range;

class DemuxDesignMatrixTest {

	@Test
	void testBasicOverlapGeometry() {
		// Create simple overlapping windows: 20 Th windows with 10 Th offset
		ArrayList<Range> windows = new ArrayList<>();
		windows.add(new Range(400, 420)); // covers sub-windows 0, 1
		windows.add(new Range(410, 430)); // covers sub-windows 1, 2
		windows.add(new Range(420, 440)); // covers sub-windows 2, 3
		windows.add(new Range(430, 450)); // covers sub-windows 3, 4

		DemuxDesignMatrix matrix = new DemuxDesignMatrix(windows);

		// Should create 5 sub-windows: 400-410, 410-420, 420-430, 430-440, 440-450
		assertEquals(5, matrix.getNumSubWindows());
		assertEquals(4, matrix.getNumAcquiredPositions());

		DemuxWindow[] subWindows = matrix.getSubWindows();
		assertEquals(400, subWindows[0].getLowerMz(), 0.01);
		assertEquals(410, subWindows[0].getUpperMz(), 0.01);
		assertEquals(440, subWindows[4].getLowerMz(), 0.01);
		assertEquals(450, subWindows[4].getUpperMz(), 0.01);
	}

	@Test
	void testDesignMatrixStructure() {
		// Create overlapping windows
		ArrayList<Range> windows = new ArrayList<>();
		windows.add(new Range(400, 420));
		windows.add(new Range(410, 430));
		windows.add(new Range(420, 440));

		DemuxDesignMatrix matrix = new DemuxDesignMatrix(windows);
		DMatrixRMaj fullMatrix = matrix.getFullMatrix();

		// Matrix should be 3x4 (3 acquired windows, 4 sub-windows)
		assertEquals(3, fullMatrix.numRows);
		assertEquals(4, fullMatrix.numCols);

		// Row 0 (400-420) should cover sub-windows 0 (400-410) and 1 (410-420)
		assertEquals(1.0, fullMatrix.get(0, 0), 0.001);
		assertEquals(1.0, fullMatrix.get(0, 1), 0.001);
		assertEquals(0.0, fullMatrix.get(0, 2), 0.001);
		assertEquals(0.0, fullMatrix.get(0, 3), 0.001);

		// Row 1 (410-430) should cover sub-windows 1 (410-420) and 2 (420-430)
		assertEquals(0.0, fullMatrix.get(1, 0), 0.001);
		assertEquals(1.0, fullMatrix.get(1, 1), 0.001);
		assertEquals(1.0, fullMatrix.get(1, 2), 0.001);
		assertEquals(0.0, fullMatrix.get(1, 3), 0.001);

		// Row 2 (420-440) should cover sub-windows 2 (420-430) and 3 (430-440)
		assertEquals(0.0, fullMatrix.get(2, 0), 0.001);
		assertEquals(0.0, fullMatrix.get(2, 1), 0.001);
		assertEquals(1.0, fullMatrix.get(2, 2), 0.001);
		assertEquals(1.0, fullMatrix.get(2, 3), 0.001);
	}

	@Test
	void testLocalMatrixExtraction() {
		ArrayList<Range> windows = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			windows.add(new Range(400 + i * 10, 420 + i * 10));
		}

		DemuxDesignMatrix matrix = new DemuxDesignMatrix(windows);

		// Extract local 7x7 matrix centered on sub-window 5
		DMatrixRMaj local = matrix.extractLocalMatrix(5, 7);

		assertEquals(7, local.numRows);
		assertEquals(7, local.numCols);
	}

	@Test
	void testSubWindowIndices() {
		ArrayList<Range> windows = new ArrayList<>();
		windows.add(new Range(400, 420));
		windows.add(new Range(410, 430));
		windows.add(new Range(420, 440));

		DemuxDesignMatrix matrix = new DemuxDesignMatrix(windows);

		// Window 400-420 should contain sub-windows with centers 405 and 415
		int[] indices = matrix.getSubWindowIndices(400, 420);
		assertEquals(2, indices.length);
	}

	@Test
	void testStaggeredWindowPattern() {
		// Simulate staggered acquisition: normal + staggered windows
		ArrayList<Range> windows = new ArrayList<>();

		// Normal windows (starting at round m/z)
		windows.add(new Range(400, 420));
		windows.add(new Range(420, 440));
		windows.add(new Range(440, 460));

		// Staggered windows (offset by 10 Th)
		windows.add(new Range(410, 430));
		windows.add(new Range(430, 450));
		windows.add(new Range(450, 470));

		DemuxDesignMatrix matrix = new DemuxDesignMatrix(windows);

		// With 50% overlap, we should get sub-windows at 10 Th intervals
		DemuxWindow[] subWindows = matrix.getSubWindows();
		assertTrue(subWindows.length > 0);

		// Check that sub-windows have approximately 10 Th width
		for (DemuxWindow sw : subWindows) {
			double width = sw.getWidth();
			assertTrue(width >= 9 && width <= 11, "Sub-window width should be ~10 Th, got: " + width);
		}
	}

	@Test
	void testExtractLocalMatrixWithIndices() {
		ArrayList<Range> windows = new ArrayList<>();
		windows.add(new Range(400, 420));
		windows.add(new Range(410, 430));
		windows.add(new Range(420, 440));

		DemuxDesignMatrix matrix = new DemuxDesignMatrix(windows);

		int[] rows = {0, 1};
		DMatrixRMaj local = matrix.extractLocalMatrix(rows, 0, 3);

		assertEquals(2, local.numRows);
		assertEquals(3, local.numCols);
		assertEquals(1.0, local.get(0, 0), 0.001);
		assertEquals(1.0, local.get(0, 1), 0.001);
	}

	@Test
	void testComputeRowMask() {
		int[] rows = {0, 2, 4, 33};
		int mask = DemuxDesignMatrix.computeRowMask(rows, 40);
		assertEquals((1 << 0) | (1 << 2) | (1 << 4), mask);
	}

	@Test
	void testFromCycleAndRowIndex() {
		ArrayList<org.searlelab.msrawjava.model.FragmentScan> cycle = new ArrayList<>();
		cycle.add(new org.searlelab.msrawjava.model.FragmentScan(
				"s1", "p1", 1, 500.0, 1.0f, 0, 0.0f,
				400.0, 420.0, new double[] {100.0}, new float[] {10.0f},
				null, (byte) 0, 400.0, 420.0));
		cycle.add(new org.searlelab.msrawjava.model.FragmentScan(
				"s2", "p2", 2, 510.0, 1.2f, 0, 0.0f,
				410.0, 430.0, new double[] {100.0}, new float[] {10.0f},
				null, (byte) 0, 410.0, 430.0));

		DemuxDesignMatrix matrix = DemuxDesignMatrix.fromCycle(cycle);
		assertEquals(2, matrix.getNumAcquiredPositions());

		int row = matrix.getRowIndex(405.0);
		assertEquals(0, row);
	}
}
