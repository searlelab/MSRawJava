package org.searlelab.msrawjava.algorithms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.algorithms.demux.DemuxConfig;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.MassTolerance;
import org.searlelab.msrawjava.model.PPMMassTolerance;
import org.searlelab.msrawjava.model.Range;

class StaggeredDemultiplexerUnitTest {

	private static final double WINDOW_A_START=400.0;
	private static final double WINDOW_A_END=420.0;
	private static final double WINDOW_B_START=410.0;
	private static final double WINDOW_B_END=430.0;

	private static ArrayList<Range> buildWindows() {
		ArrayList<Range> windows=new ArrayList<>();
		windows.add(new Range(WINDOW_A_START, WINDOW_A_END));
		windows.add(new Range(WINDOW_B_START, WINDOW_B_END));
		return windows;
	}

	private static FragmentScan makeScan(String name, int index, double lower, double upper, float rt, double[] masses, float[] intensities) {
		return new FragmentScan(name, "prec", index, (lower+upper)/2.0, rt, 0, 0.0f, lower, upper, masses, intensities, null, (byte)0, lower, upper);
	}

	private static ArrayList<FragmentScan> buildCycle(float rt, double[][] massesByWindow, float[][] intensitiesByWindow) {
		ArrayList<FragmentScan> cycle=new ArrayList<>();
		cycle.add(makeScan("scanA", 0, WINDOW_A_START, WINDOW_A_END, rt, massesByWindow[0], intensitiesByWindow[0]));
		cycle.add(makeScan("scanB", 1, WINDOW_B_START, WINDOW_B_END, rt, massesByWindow[1], intensitiesByWindow[1]));
		return cycle;
	}

	@Test
	void testDemultiplexRejectsMismatchedCycleSizes() {
		ArrayList<Range> windows=buildWindows();
		MassTolerance tolerance=new PPMMassTolerance(20.0);
		StaggeredDemultiplexer demux=new StaggeredDemultiplexer(windows, tolerance);

		ArrayList<FragmentScan> cycleA=buildCycle(1.0f, new double[][] {{}, {}}, new float[][] {{}, {}});
		ArrayList<FragmentScan> cycleB=new ArrayList<>(cycleA);
		cycleB.remove(1);

		assertThrows(IllegalArgumentException.class, () -> demux.demultiplex(cycleA, cycleA, cycleB, cycleA, cycleA, 1));
	}

	@Test
	void testDemultiplexRejectsEmptyCenterCycle() {
		ArrayList<Range> windows=buildWindows();
		MassTolerance tolerance=new PPMMassTolerance(20.0);
		StaggeredDemultiplexer demux=new StaggeredDemultiplexer(windows, tolerance);

		ArrayList<FragmentScan> empty=new ArrayList<>();
		ArrayList<FragmentScan> cycle=buildCycle(1.0f, new double[][] {{}, {}}, new float[][] {{}, {}});

		assertThrows(IllegalArgumentException.class, () -> demux.demultiplex(cycle, cycle, empty, cycle, cycle, 1));
	}

	@Test
	void testEmptyTransitionsEmitEmptySubWindows() {
		ArrayList<Range> windows=buildWindows();
		MassTolerance tolerance=new PPMMassTolerance(20.0);
		StaggeredDemultiplexer demux=new StaggeredDemultiplexer(windows, tolerance);

		double[][] masses=new double[][] {{}, {}};
		float[][] intensities=new float[][] {{}, {}};
		ArrayList<FragmentScan> cycle=buildCycle(3.0f, masses, intensities);

		ArrayList<FragmentScan> outputs=demux.demultiplex(cycle, cycle, cycle, cycle, cycle, 1);

		assertEquals(4, outputs.size());
		for (FragmentScan scan : outputs) {
			assertEquals(0, scan.getMassArray().length);
			assertEquals(0, scan.getIntensityArray().length);
		}

		Map<String, Integer> counts=new HashMap<>();
		for (FragmentScan scan : outputs) {
			String key=String.format("%.1f-%.1f", scan.getIsolationWindowLower(), scan.getIsolationWindowUpper());
			counts.put(key, counts.getOrDefault(key, 0)+1);
		}
		assertEquals(1, counts.get(String.format("%.1f-%.1f", WINDOW_A_START, 410.0)));
		assertEquals(2, counts.get(String.format("%.1f-%.1f", 410.0, 420.0)));
		assertEquals(1, counts.get(String.format("%.1f-%.1f", 420.0, WINDOW_B_END)));
	}

	@Test
	void testExcludeEdgeSubWindows() {
		ArrayList<Range> windows=buildWindows();
		MassTolerance tolerance=new PPMMassTolerance(20.0);
		DemuxConfig config=DemuxConfig.builder().excludeEdgeSubWindows().build();
		StaggeredDemultiplexer demux=new StaggeredDemultiplexer(windows, tolerance, config);

		double[][] masses=new double[][] {{}, {}};
		float[][] intensities=new float[][] {{}, {}};
		ArrayList<FragmentScan> cycle=buildCycle(3.0f, masses, intensities);

		ArrayList<FragmentScan> outputs=demux.demultiplex(cycle, cycle, cycle, cycle, cycle, 1);

		assertEquals(2, outputs.size());
		for (FragmentScan scan : outputs) {
			assertEquals(410.0, scan.getIsolationWindowLower(), 0.001);
			assertEquals(420.0, scan.getIsolationWindowUpper(), 0.001);
		}
	}

	@Test
	void testDeduplicatesAnchorTransitionsAndInterpolates() {
		ArrayList<Range> windows=buildWindows();
		MassTolerance tolerance=new PPMMassTolerance(20.0);
		DemuxConfig config=DemuxConfig.builder().excludeEdgeSubWindows().build();
		StaggeredDemultiplexer demux=new StaggeredDemultiplexer(windows, tolerance, config);

		double[][] massesA=new double[][] {{500.0000, 500.0004}, {500.0000}};
		float[][] intensitiesA=new float[][] {{6.0f, 4.0f}, {10.0f}};

		double[][] massesB=new double[][] {{500.0000}, {500.0000}};
		float[][] intensitiesB=new float[][] {{10.0f}, {10.0f}};

		ArrayList<FragmentScan> cycleM2=buildCycle(5.0f, massesB, intensitiesB);
		ArrayList<FragmentScan> cycleM1=buildCycle(4.0f, massesB, intensitiesB);
		ArrayList<FragmentScan> cycleCenter=buildCycle(3.0f, massesA, intensitiesA);
		ArrayList<FragmentScan> cycleP1=buildCycle(2.0f, massesB, intensitiesB);
		ArrayList<FragmentScan> cycleP2=buildCycle(1.0f, massesB, intensitiesB);

		ArrayList<FragmentScan> outputs=demux.demultiplex(cycleM2, cycleM1, cycleCenter, cycleP1, cycleP2, 1);

		assertEquals(2, outputs.size());
		for (FragmentScan scan : outputs) {
			assertEquals(410.0, scan.getIsolationWindowLower(), 0.001);
			assertEquals(420.0, scan.getIsolationWindowUpper(), 0.001);
			assertEquals(1, scan.getMassArray().length);
			assertEquals(1, scan.getIntensityArray().length);
			assertEquals(500.0000, scan.getMassArray()[0], 1e-4);
			assertEquals(10.0, scan.getIntensityArray()[0], 0.01);
		}
	}

	@Test
	void testLegacyGetIndices() {
		float[] centers=new float[] {100.0f, 105.0f, 110.0f};
		Range range=new Range(104.0, 111.0);
		int[] indices=StaggeredDemultiplexer.getIndicies(centers, range);
		assertEquals(2, indices.length);
	}
}
