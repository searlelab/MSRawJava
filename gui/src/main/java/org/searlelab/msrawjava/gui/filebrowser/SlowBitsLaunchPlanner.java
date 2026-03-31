package org.searlelab.msrawjava.gui.filebrowser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.searlelab.msrawjava.io.VendorFile;

final class SlowBitsLaunchPlanner {

	enum Lane {
		VISIBLE, BACKFILL
	}

	record RowState(int modelIndex, VendorFile vendor, boolean hidden, boolean inViewport, boolean ready, boolean running, Lane runningLane, int distanceFromViewport,
			long runningNanos, boolean deprioritizedInBucket) {
	}

	record Launch(int modelIndex, Lane lane) {
	}

	record Plan(List<Launch> launches, List<Integer> stalledVisibleModelRows) {
	}

	private static final Comparator<RowState> ROW_PRIORITY=Comparator.<RowState>comparingInt(row -> row.deprioritizedInBucket()?1:0)
			.thenComparingInt(RowState::distanceFromViewport)
			.thenComparingInt(row -> vendorRank(row.vendor())).thenComparingInt(RowState::modelIndex);

	private SlowBitsLaunchPlanner() {
	}

	static Plan plan(List<RowState> rows, int workerCount, long stallThresholdNanos) {
		int workers=Math.max(1, workerCount);
		int visibleLaneCap=(workers>1)?(workers-1):0;
		int backfillLaneCap=1;
		int runningVisibleLanes=0;
		int runningBackfillLanes=0;

		ArrayList<Integer> stalledVisible=new ArrayList<>();
		ArrayList<RowState> bucketVisible=new ArrayList<>();
		ArrayList<RowState> bucketOffscreen=new ArrayList<>();
		ArrayList<RowState> bucketHidden=new ArrayList<>();
		boolean nonHiddenUnfinished=false;

		for (RowState row : rows) {
			if (row==null) continue;

			if (!row.hidden()&&(!row.ready()||row.running())) {
				nonHiddenUnfinished=true;
			}

			if (row.running()) {
				Lane lane=(row.runningLane()==null)?Lane.BACKFILL:row.runningLane();
				if (lane==Lane.VISIBLE) {
					runningVisibleLanes++;
				} else {
					runningBackfillLanes++;
				}
				if (row.inViewport()&&row.runningNanos()>=stallThresholdNanos) {
					stalledVisible.add(Integer.valueOf(row.modelIndex()));
				}
				continue;
			}
			if (row.ready()) continue;

			if (row.inViewport()) {
				bucketVisible.add(row);
			} else if (row.hidden()) {
				bucketHidden.add(row);
			} else {
				bucketOffscreen.add(row);
			}
		}

		bucketVisible.sort(ROW_PRIORITY);
		bucketOffscreen.sort(ROW_PRIORITY);
		bucketHidden.sort(ROW_PRIORITY);

		int visibleSlots=Math.max(0, visibleLaneCap-runningVisibleLanes);
		int backfillSlots=Math.max(0, backfillLaneCap-runningBackfillLanes);
		boolean hiddenEligible=!nonHiddenUnfinished;

		ArrayList<Launch> launches=new ArrayList<>(visibleSlots+backfillSlots);

		for (int i=0; i<visibleSlots; i++) {
			RowState next=popFirst(bucketVisible);
			if (next==null) next=popFirst(bucketOffscreen);
			if (next==null) break;
			launches.add(new Launch(next.modelIndex(), Lane.VISIBLE));
		}

		for (int i=0; i<backfillSlots; i++) {
			RowState next;
			if (workers==1) {
				next=popFirst(bucketVisible);
				if (next==null) next=popFirst(bucketOffscreen);
				if (next==null&&hiddenEligible) next=popFirst(bucketHidden);
			} else {
				next=popFirst(bucketOffscreen);
				if (next==null&&hiddenEligible) next=popFirst(bucketHidden);
			}
			if (next==null) break;
			launches.add(new Launch(next.modelIndex(), Lane.BACKFILL));
		}

		return new Plan(List.copyOf(launches), List.copyOf(stalledVisible));
	}

	private static RowState popFirst(ArrayList<RowState> rows) {
		if (rows.isEmpty()) return null;
		return rows.remove(0);
	}

	private static int vendorRank(VendorFile vendor) {
		if (vendor==VendorFile.BRUKER) return 0;
		if (vendor==VendorFile.ENCYCLOPEDIA) return 1;
		if (vendor==VendorFile.MZML) return 2;
		if (vendor==VendorFile.THERMO) return 3;
		return 4;
	}
}
