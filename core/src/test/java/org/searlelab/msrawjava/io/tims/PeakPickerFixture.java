package org.searlelab.msrawjava.io.tims;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;

import org.searlelab.msrawjava.model.PeakWithIMS;

/** Immutable point description; each replay owns fresh objects but preserves aliases within its input. */
final class PeakPickerFixture {

	private final long[] masses;
	private final int[] intensities;
	private final int[] mobilities;
	private final boolean[] available;
	private final int[] order;

	PeakPickerFixture(ArrayList<PeakWithIMS> points) {
		IdentityHashMap<PeakWithIMS, Integer> ids=new IdentityHashMap<>();
		ArrayList<PeakWithIMS> unique=new ArrayList<>();
		order=new int[points.size()];
		for (int i=0; i<points.size(); i++) {
			PeakWithIMS point=points.get(i);
			Integer id=ids.get(point);
			if (id==null) {
				id=unique.size();
				ids.put(point, id);
				unique.add(point);
			}
			order[i]=id;
		}
		masses=new long[unique.size()];
		intensities=new int[unique.size()];
		mobilities=new int[unique.size()];
		available=new boolean[unique.size()];
		for (int i=0; i<unique.size(); i++) {
			PeakWithIMS p=unique.get(i);
			masses[i]=Double.doubleToRawLongBits(p.mz);
			intensities[i]=Float.floatToRawIntBits(p.intensity);
			mobilities[i]=Float.floatToRawIntBits(p.ims);
			available[i]=p.isAvailable();
		}
	}

	PeakPickerFixture(DataInput in) throws IOException {
		int n=in.readInt();
		masses=new long[n];
		intensities=new int[n];
		mobilities=new int[n];
		available=new boolean[n];
		for (int i=0; i<n; i++) {
			masses[i]=in.readLong();
			intensities[i]=in.readInt();
			mobilities[i]=in.readInt();
			available[i]=in.readBoolean();
		}
		order=new int[in.readInt()];
		for (int i=0; i<order.length; i++) order[i]=in.readInt();
	}

	void write(DataOutput out) throws IOException {
		out.writeInt(masses.length);
		for (int i=0; i<masses.length; i++) {
			out.writeLong(masses[i]);
			out.writeInt(intensities[i]);
			out.writeInt(mobilities[i]);
			out.writeBoolean(available[i]);
		}
		out.writeInt(order.length);
		for (int id : order) out.writeInt(id);
	}

	ArrayList<PeakWithIMS> freshPoints() {
		PeakWithIMS[] unique=new PeakWithIMS[masses.length];
		for (int i=0; i<unique.length; i++) {
			unique[i]=new PeakWithIMS(Double.longBitsToDouble(masses[i]), Float.intBitsToFloat(intensities[i]), Float.intBitsToFloat(mobilities[i]));
			if (!available[i]) unique[i].turnOff();
		}
		ArrayList<PeakWithIMS> points=new ArrayList<>(order.length);
		for (int id : order) points.add(unique[id]);
		return points;
	}
}
