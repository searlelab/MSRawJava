package org.searlelab.msrawjava.gui.filebrowser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.searlelab.msrawjava.io.VendorFile;

final class SlowBitsLaunchPlanner {

	enum Lane {
		VISIBLE, BACKFILL
	}

	static final class RowState {
		private final int modelIndex;
		private final VendorFile vendor;
		private final boolean hidden;
		private final boolean inViewport;
		private final boolean ready;
		private final boolean running;
		private final Lane runningLane;
		private final int distanceFromViewport;
		private final long runningNanos;
		private final boolean deprioritizedInBucket;
		private final boolean launchEligible;

		RowState(int modelIndex, VendorFile vendor, boolean hidden, boolean inViewport, boolean ready, boolean running, Lane runningLane,
				int distanceFromViewport, long runningNanos, boolean deprioritizedInBucket, boolean launchEligible) {
			this.modelIndex=modelIndex;
			this.vendor=vendor;
			this.hidden=hidden;
			this.inViewport=inViewport;
			this.ready=ready;
			this.running=running;
			this.runningLane=runningLane;
			this.distanceFromViewport=distanceFromViewport;
			this.runningNanos=runningNanos;
			this.deprioritizedInBucket=deprioritizedInBucket;
			this.launchEligible=launchEligible;
		}

		int modelIndex() {
			return modelIndex;
		}

		VendorFile vendor() {
			return vendor;
		}

		boolean hidden() {
			return hidden;
		}

		boolean inViewport() {
			return inViewport;
		}

		boolean ready() {
			return ready;
		}

		boolean running() {
			return running;
		}

		Lane runningLane() {
			return runningLane;
		}

		int distanceFromViewport() {
			return distanceFromViewport;
		}

		long runningNanos() {
			return runningNanos;
		}

		boolean deprioritizedInBucket() {
			return deprioritizedInBucket;
		}

		boolean launchEligible() {
			return launchEligible;
		}

		@Override
		public boolean equals(Object obj) {
			if (this==obj) return true;
			if (!(obj instanceof RowState)) return false;
			RowState other=(RowState)obj;
			return modelIndex==other.modelIndex&&hidden==other.hidden&&inViewport==other.inViewport&&ready==other.ready&&running==other.running
					&&distanceFromViewport==other.distanceFromViewport&&runningNanos==other.runningNanos
					&&deprioritizedInBucket==other.deprioritizedInBucket&&launchEligible==other.launchEligible&&vendor==other.vendor
					&&runningLane==other.runningLane;
		}

		@Override
		public int hashCode() {
			return Objects.hash(Integer.valueOf(modelIndex), vendor, Boolean.valueOf(hidden), Boolean.valueOf(inViewport), Boolean.valueOf(ready),
					Boolean.valueOf(running), runningLane, Integer.valueOf(distanceFromViewport), Long.valueOf(runningNanos),
					Boolean.valueOf(deprioritizedInBucket), Boolean.valueOf(launchEligible));
		}

		@Override
		public String toString() {
			return "RowState[modelIndex="+modelIndex+", vendor="+vendor+", hidden="+hidden+", inViewport="+inViewport+", ready="+ready+", running="+running
					+", runningLane="+runningLane+", distanceFromViewport="+distanceFromViewport+", runningNanos="+runningNanos
					+", deprioritizedInBucket="+deprioritizedInBucket+", launchEligible="+launchEligible+"]";
		}
	}

	static final class Launch {
		private final int modelIndex;
		private final Lane lane;

		Launch(int modelIndex, Lane lane) {
			this.modelIndex=modelIndex;
			this.lane=lane;
		}

		int modelIndex() {
			return modelIndex;
		}

		Lane lane() {
			return lane;
		}

		@Override
		public boolean equals(Object obj) {
			if (this==obj) return true;
			if (!(obj instanceof Launch)) return false;
			Launch other=(Launch)obj;
			return modelIndex==other.modelIndex&&lane==other.lane;
		}

		@Override
		public int hashCode() {
			return Objects.hash(Integer.valueOf(modelIndex), lane);
		}

		@Override
		public String toString() {
			return "Launch[modelIndex="+modelIndex+", lane="+lane+"]";
		}
	}

	static final class Plan {
		private final List<Launch> launches;
		private final List<Integer> stalledVisibleModelRows;

		Plan(List<Launch> launches, List<Integer> stalledVisibleModelRows) {
			this.launches=launches;
			this.stalledVisibleModelRows=stalledVisibleModelRows;
		}

		List<Launch> launches() {
			return launches;
		}

		List<Integer> stalledVisibleModelRows() {
			return stalledVisibleModelRows;
		}

		@Override
		public boolean equals(Object obj) {
			if (this==obj) return true;
			if (!(obj instanceof Plan)) return false;
			Plan other=(Plan)obj;
			return Objects.equals(launches, other.launches)&&Objects.equals(stalledVisibleModelRows, other.stalledVisibleModelRows);
		}

		@Override
		public int hashCode() {
			return Objects.hash(launches, stalledVisibleModelRows);
		}

		@Override
		public String toString() {
			return "Plan[launches="+launches+", stalledVisibleModelRows="+stalledVisibleModelRows+"]";
		}
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
			if (!row.launchEligible()) continue;

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
