package org.searlelab.msrawjava.gui.filebrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.gui.filebrowser.SlowBitsLaunchPlanner.Lane;
import org.searlelab.msrawjava.gui.filebrowser.SlowBitsLaunchPlanner.Launch;
import org.searlelab.msrawjava.gui.filebrowser.SlowBitsLaunchPlanner.Plan;
import org.searlelab.msrawjava.gui.filebrowser.SlowBitsLaunchPlanner.RowState;
import org.searlelab.msrawjava.io.VendorFile;

class SlowBitsLaunchPlannerTest {

	private static final long FIVE_SECONDS_NANOS=5_000_000_000L;

	@Test
	void visibleRowsBeatOffscreenRegardlessOfVendor() {
		List<RowState> rows=List.of(pending(0, VendorFile.THERMO, false, true, 0), pending(1, VendorFile.BRUKER, false, false, 10));

		Plan plan=SlowBitsLaunchPlanner.plan(rows, 2, FIVE_SECONDS_NANOS);

		assertEquals(2, plan.launches().size());
		Launch visibleLane=plan.launches().stream().filter(l -> l.lane()==Lane.VISIBLE).findFirst().orElse(null);
		assertNotNull(visibleLane);
		assertEquals(0, visibleLane.modelIndex());
	}

	@Test
	void backfillLaneReservedWhileVisibleWorkExists() {
		List<RowState> rows=List.of(pending(0, VendorFile.THERMO, false, true, 0), pending(1, VendorFile.BRUKER, false, true, 0),
				pending(2, VendorFile.ENCYCLOPEDIA, false, false, 3));

		Plan plan=SlowBitsLaunchPlanner.plan(rows, 3, FIVE_SECONDS_NANOS);

		assertEquals(3, plan.launches().size());
		Launch backfill=plan.launches().stream().filter(l -> l.lane()==Lane.BACKFILL).findFirst().orElse(null);
		assertNotNull(backfill);
		assertEquals(2, backfill.modelIndex());
	}

	@Test
	void hiddenRowsWaitUntilAllNonHiddenFinished() {
		List<RowState> rows=List.of(pending(0, VendorFile.BRUKER, false, false, 2), pending(1, VendorFile.MZML, true, false, Integer.MAX_VALUE));

		Plan plan=SlowBitsLaunchPlanner.plan(rows, 2, FIVE_SECONDS_NANOS);

		assertEquals(1, plan.launches().size());
		assertEquals(0, plan.launches().get(0).modelIndex());
		assertFalse(plan.launches().stream().anyMatch(l -> l.modelIndex()==1));
	}

	@Test
	void hiddenRowsLaunchOnlyFromBackfillLane() {
		List<RowState> rows=List.of(ready(0, VendorFile.BRUKER, false, false, 0), ready(1, VendorFile.THERMO, false, false, 0),
				pending(2, VendorFile.MZML, true, false, Integer.MAX_VALUE), pending(3, VendorFile.ENCYCLOPEDIA, true, false, Integer.MAX_VALUE));

		Plan plan=SlowBitsLaunchPlanner.plan(rows, 4, FIVE_SECONDS_NANOS);

		assertEquals(1, plan.launches().size());
		assertEquals(Lane.BACKFILL, plan.launches().get(0).lane());
		assertTrue(plan.launches().get(0).modelIndex()==2||plan.launches().get(0).modelIndex()==3);
	}

	@Test
	void vendorTieBreakAppliesWithinSameBucket() {
		List<RowState> rows=List.of(pending(0, VendorFile.THERMO, false, false, 4), pending(1, VendorFile.MZML, false, false, 4),
				pending(2, VendorFile.ENCYCLOPEDIA, false, false, 4), pending(3, VendorFile.BRUKER, false, false, 4));

		Plan plan=SlowBitsLaunchPlanner.plan(rows, 1, FIVE_SECONDS_NANOS);

		assertEquals(1, plan.launches().size());
		assertEquals(3, plan.launches().get(0).modelIndex());
	}

	@Test
	void softStallDetectionFlagsVisibleRunningRowsWithoutPreemption() {
		List<RowState> rows=List.of(running(0, VendorFile.THERMO, false, true, 0, Lane.VISIBLE, 6_000_000_000L),
				pending(1, VendorFile.BRUKER, false, true, 0), pending(2, VendorFile.ENCYCLOPEDIA, false, false, 5));

		Plan plan=SlowBitsLaunchPlanner.plan(rows, 3, FIVE_SECONDS_NANOS);

		assertEquals(List.of(Integer.valueOf(0)), plan.stalledVisibleModelRows());
		assertTrue(plan.launches().stream().noneMatch(l -> l.modelIndex()==0));
	}

	@Test
	void singleWorkerStillTreatsHiddenRowsAsReservedBackfillOnly() {
		List<RowState> rows=List.of(pending(0, VendorFile.MZML, false, true, 0), pending(1, VendorFile.THERMO, true, false, Integer.MAX_VALUE));

		Plan firstPlan=SlowBitsLaunchPlanner.plan(rows, 1, FIVE_SECONDS_NANOS);
		assertEquals(1, firstPlan.launches().size());
		assertEquals(0, firstPlan.launches().get(0).modelIndex());
		assertEquals(Lane.BACKFILL, firstPlan.launches().get(0).lane());

		List<RowState> finishedVisible=List.of(ready(0, VendorFile.MZML, false, true, 0), pending(1, VendorFile.THERMO, true, false, Integer.MAX_VALUE));
		Plan secondPlan=SlowBitsLaunchPlanner.plan(finishedVisible, 1, FIVE_SECONDS_NANOS);
		assertEquals(1, secondPlan.launches().size());
		assertEquals(1, secondPlan.launches().get(0).modelIndex());
		assertEquals(Lane.BACKFILL, secondPlan.launches().get(0).lane());
	}

	@Test
	void deprioritizedRowsMoveToBottomOfTheirCurrentBucket() {
		List<RowState> rows=List.of(pending(0, VendorFile.BRUKER, false, true, 0, true), pending(1, VendorFile.THERMO, false, true, 0, false));

		Plan plan=SlowBitsLaunchPlanner.plan(rows, 1, FIVE_SECONDS_NANOS);

		assertEquals(1, plan.launches().size());
		assertEquals(1, plan.launches().get(0).modelIndex());
	}

	@Test
	void launchIneligibleRowsAreSkippedButStillBlockHiddenRows() {
		List<RowState> rows=List.of(pending(0, VendorFile.THERMO, false, true, 0, false, false), pending(1, VendorFile.MZML, true, false, Integer.MAX_VALUE));

		Plan plan=SlowBitsLaunchPlanner.plan(rows, 2, FIVE_SECONDS_NANOS);

		assertTrue(plan.launches().isEmpty());
	}

	private static RowState pending(int modelIndex, VendorFile vendor, boolean hidden, boolean inViewport, int distance) {
		return pending(modelIndex, vendor, hidden, inViewport, distance, false, true);
	}

	private static RowState pending(int modelIndex, VendorFile vendor, boolean hidden, boolean inViewport, int distance, boolean deprioritizedInBucket) {
		return pending(modelIndex, vendor, hidden, inViewport, distance, deprioritizedInBucket, true);
	}

	private static RowState pending(int modelIndex, VendorFile vendor, boolean hidden, boolean inViewport, int distance, boolean deprioritizedInBucket,
			boolean launchEligible) {
		return new RowState(modelIndex, vendor, hidden, inViewport, false, false, null, distance, 0L, deprioritizedInBucket, launchEligible);
	}

	private static RowState ready(int modelIndex, VendorFile vendor, boolean hidden, boolean inViewport, int distance) {
		return new RowState(modelIndex, vendor, hidden, inViewport, true, false, null, distance, 0L, false, true);
	}

	private static RowState running(int modelIndex, VendorFile vendor, boolean hidden, boolean inViewport, int distance, Lane runningLane, long runningNanos) {
		return new RowState(modelIndex, vendor, hidden, inViewport, false, true, runningLane, distance, runningNanos, false, false);
	}
}
