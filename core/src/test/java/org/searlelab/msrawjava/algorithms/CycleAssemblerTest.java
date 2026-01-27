package org.searlelab.msrawjava.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Range;

class CycleAssemblerTest {

	private List<Range> windows;
	private CycleAssembler assembler;

	@BeforeEach
	void setUp() {
		// Define 3 windows for a DIA cycle
		windows=Arrays.asList(new Range(400.0, 500.0), new Range(500.0, 600.0), new Range(600.0, 700.0));
		assembler=new CycleAssembler(windows);
	}

	private FragmentScan createScan(Range window, int index) {
		double[] masses= {100.0};
		float[] intensities= {100.0f};
		return new FragmentScan("scan"+index, "precursor", index, (window.getStart()+window.getStop())/2, index*1.0f, // scanStartTime
				0, null, window.getStart(), window.getStop(), masses, intensities, null, (byte)2, 100.0, 1000.0);
	}

	@Test
	void emptyAssemblerReturnsNoCycles() {
		ArrayList<ArrayList<FragmentScan>> cycles=assembler.drainCompleted();
		assertTrue(cycles.isEmpty());
	}

	@Test
	void singleScanDoesNotCompleteCycle() {
		assembler.add(createScan(windows.get(0), 1));
		ArrayList<ArrayList<FragmentScan>> cycles=assembler.drainCompleted();
		assertTrue(cycles.isEmpty());
	}

	@Test
	void allWindowsCompleteCycle() {
		assembler.add(createScan(windows.get(0), 1));
		assembler.add(createScan(windows.get(1), 2));
		assembler.add(createScan(windows.get(2), 3));

		ArrayList<ArrayList<FragmentScan>> cycles=assembler.drainCompleted();
		assertEquals(1, cycles.size());
		assertEquals(3, cycles.get(0).size());
	}

	@Test
	void cycleOrderMatchesWindowOrder() {
		// Add in different order than window definition
		assembler.add(createScan(windows.get(2), 1)); // 600-700
		assembler.add(createScan(windows.get(0), 2)); // 400-500
		assembler.add(createScan(windows.get(1), 3)); // 500-600

		ArrayList<ArrayList<FragmentScan>> cycles=assembler.drainCompleted();
		assertEquals(1, cycles.size());

		// Output should be in window order, not insertion order
		ArrayList<FragmentScan> cycle=cycles.get(0);
		assertEquals(400.0, cycle.get(0).getIsolationWindowLower(), 1e-6);
		assertEquals(500.0, cycle.get(1).getIsolationWindowLower(), 1e-6);
		assertEquals(600.0, cycle.get(2).getIsolationWindowLower(), 1e-6);
	}

	@Test
	void repeatedWindowStartsNewCycle() {
		// First cycle: windows 0, 1
		assembler.add(createScan(windows.get(0), 1));
		assembler.add(createScan(windows.get(1), 2));
		// Repeat window 0 -> triggers new cycle
		assembler.add(createScan(windows.get(0), 3));

		ArrayList<ArrayList<FragmentScan>> cycles=assembler.drainCompleted();
		// First cycle should be finalized (has 2/3 windows = 66% >= 50%)
		assertEquals(1, cycles.size());
		assertEquals(2, cycles.get(0).size());
	}

	@Test
	void multipleCyclesAreCollected() {
		// First complete cycle
		assembler.add(createScan(windows.get(0), 1));
		assembler.add(createScan(windows.get(1), 2));
		assembler.add(createScan(windows.get(2), 3));

		// Second complete cycle
		assembler.add(createScan(windows.get(0), 4));
		assembler.add(createScan(windows.get(1), 5));
		assembler.add(createScan(windows.get(2), 6));

		ArrayList<ArrayList<FragmentScan>> cycles=assembler.drainCompleted();
		assertEquals(2, cycles.size());
	}

	@Test
	void drainCompletedClearsQueue() {
		assembler.add(createScan(windows.get(0), 1));
		assembler.add(createScan(windows.get(1), 2));
		assembler.add(createScan(windows.get(2), 3));

		ArrayList<ArrayList<FragmentScan>> first=assembler.drainCompleted();
		assertEquals(1, first.size());

		ArrayList<ArrayList<FragmentScan>> second=assembler.drainCompleted();
		assertTrue(second.isEmpty());
	}

	@Test
	void unknownWindowIsIgnored() {
		Range unknownWindow=new Range(800.0, 900.0);
		assembler.add(createScan(unknownWindow, 1));
		assembler.add(createScan(windows.get(0), 2));
		assembler.add(createScan(windows.get(1), 3));
		assembler.add(createScan(windows.get(2), 4));

		ArrayList<ArrayList<FragmentScan>> cycles=assembler.drainCompleted();
		assertEquals(1, cycles.size());
		assertEquals(3, cycles.get(0).size()); // Only known windows
	}

	@Test
	void flushPartialFinalizesIncompleteCycle() {
		assembler.add(createScan(windows.get(0), 1));
		assembler.add(createScan(windows.get(1), 2));
		// Missing window 2

		// Before flush, no cycles
		assertTrue(assembler.drainCompleted().isEmpty());

		// After flush, partial cycle is finalized (2/3 >= 50%)
		assembler.flushPartial();
		ArrayList<ArrayList<FragmentScan>> cycles=assembler.drainCompleted();
		assertEquals(1, cycles.size());
		assertEquals(2, cycles.get(0).size());
	}

	@Test
	void flushPartialRejectsTooSmallCycle() {
		// With 3 windows, threshold is max(1, 3/2) = max(1, 1) = 1 (integer division)
		// So 1 window IS accepted with 3 windows. Need more windows to test rejection.
		// Use 6 windows -> threshold = max(1, 6/2) = 3, so 2 windows < 3 will be rejected
		List<Range> sixWindows=Arrays.asList(new Range(400.0, 450.0), new Range(450.0, 500.0), new Range(500.0, 550.0), new Range(550.0, 600.0),
				new Range(600.0, 650.0), new Range(650.0, 700.0));
		CycleAssembler sixAssembler=new CycleAssembler(sixWindows);

		// Add only 2 windows (2 < 3 threshold)
		sixAssembler.add(createScan(sixWindows.get(0), 1));
		sixAssembler.add(createScan(sixWindows.get(1), 2));

		sixAssembler.flushPartial();
		ArrayList<ArrayList<FragmentScan>> cycles=sixAssembler.drainCompleted();
		assertTrue(cycles.isEmpty());
	}

	@Test
	void flushPartialClearsCurrentState() {
		assembler.add(createScan(windows.get(0), 1));
		assembler.add(createScan(windows.get(1), 2));
		assembler.flushPartial();
		assembler.drainCompleted();

		// Adding more scans should start fresh
		assembler.add(createScan(windows.get(0), 3));
		assembler.add(createScan(windows.get(1), 4));
		assembler.add(createScan(windows.get(2), 5));

		ArrayList<ArrayList<FragmentScan>> cycles=assembler.drainCompleted();
		assertEquals(1, cycles.size());
		assertEquals(3, cycles.get(0).size());
	}

	@Test
	void singleWindowCycleWorks() {
		List<Range> singleWindow=Collections.singletonList(new Range(400.0, 500.0));
		CycleAssembler singleAssembler=new CycleAssembler(singleWindow);

		singleAssembler.add(createScan(singleWindow.get(0), 1));
		ArrayList<ArrayList<FragmentScan>> cycles=singleAssembler.drainCompleted();
		assertEquals(1, cycles.size());
		assertEquals(1, cycles.get(0).size());
	}

	@Test
	void manyWindowsCycleRequiresHalf() {
		// 6 windows -> need at least 3
		List<Range> manyWindows=Arrays.asList(new Range(400.0, 450.0), new Range(450.0, 500.0), new Range(500.0, 550.0), new Range(550.0, 600.0),
				new Range(600.0, 650.0), new Range(650.0, 700.0));
		CycleAssembler manyAssembler=new CycleAssembler(manyWindows);

		// Add only 2 windows (< 50%)
		manyAssembler.add(createScan(manyWindows.get(0), 1));
		manyAssembler.add(createScan(manyWindows.get(1), 2));
		manyAssembler.flushPartial();

		assertTrue(manyAssembler.drainCompleted().isEmpty());

		// Add 3 windows (= 50%)
		manyAssembler.add(createScan(manyWindows.get(0), 3));
		manyAssembler.add(createScan(manyWindows.get(1), 4));
		manyAssembler.add(createScan(manyWindows.get(2), 5));
		manyAssembler.flushPartial();

		ArrayList<ArrayList<FragmentScan>> cycles=manyAssembler.drainCompleted();
		assertEquals(1, cycles.size());
		assertEquals(3, cycles.get(0).size());
	}

	@Test
	void emptyWindowListHandled() {
		CycleAssembler emptyAssembler=new CycleAssembler(Collections.emptyList());
		emptyAssembler.add(createScan(new Range(400.0, 500.0), 1));
		emptyAssembler.flushPartial();
		assertTrue(emptyAssembler.drainCompleted().isEmpty());
	}

	@Test
	void duplicateWindowInSameCycleTriggersBoundary() {
		// If the same window appears twice in sequence, it should start a new cycle
		assembler.add(createScan(windows.get(0), 1));
		assembler.add(createScan(windows.get(0), 2)); // Duplicate triggers boundary

		// With 3 windows, threshold is max(1, 3/2) = 1, so even 1 window cycle is accepted
		ArrayList<ArrayList<FragmentScan>> cycles=assembler.drainCompleted();
		assertEquals(1, cycles.size()); // The first single-window cycle is accepted
		assertEquals(1, cycles.get(0).size());
	}

	@Test
	void cycleCompletionAndRepeatInSameSequence() {
		// Complete cycle, then immediately repeat first window
		assembler.add(createScan(windows.get(0), 1));
		assembler.add(createScan(windows.get(1), 2));
		assembler.add(createScan(windows.get(2), 3)); // Completes cycle
		// At this point cycle is finalized and current is cleared

		assembler.add(createScan(windows.get(0), 4)); // Starts new cycle

		ArrayList<ArrayList<FragmentScan>> cycles=assembler.drainCompleted();
		assertEquals(1, cycles.size()); // Only the first complete cycle

		// Finish second cycle
		assembler.add(createScan(windows.get(1), 5));
		assembler.add(createScan(windows.get(2), 6));

		cycles=assembler.drainCompleted();
		assertEquals(1, cycles.size());
	}
}
