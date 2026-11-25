package org.searlelab.msrawjava.io.tims;

import java.util.ArrayList;
import java.util.Collections;

import org.searlelab.msrawjava.model.PeakWithIMS;

/** Independent comparator-sort oracle; deliberately does not use packed keys or picker bound helpers. */
final class TIMSSquashReference {
	static ArrayList<PeakWithIMS> squash(ArrayList<PeakWithIMS> points) {
		return squash(points, null);
	}

	/** Optional counter records bounded aggregation visits, outside timed benchmarking. */
	static ArrayList<PeakWithIMS> squash(ArrayList<PeakWithIMS> points, long[] visits) {
		points.sort(null);
		ArrayList<PeakWithIMS> candidates=new ArrayList<>(points);
		candidates.sort(new PeakWithIMS.PeakIntensityComparator());
		ArrayList<PeakWithIMS> output=new ArrayList<>();
		TIMSMassTolerance massTolerance=new TIMSMassTolerance();
		for (int c=candidates.size()-1; c>=0&&output.size()<TIMSPeakPicker.MAX_PEAKS; c--) {
			PeakWithIMS apex=candidates.get(c);
			if (!apex.isAvailable()||!(apex.intensity>0f)) continue;
			double radius=massTolerance.getToleranceInMz(apex.mz, apex.mz)/2.0;
			double low=apex.mz-radius, high=apex.mz+radius;
			int center=Collections.binarySearch(points, apex);
			int first=center, last=center+1;
			while (first>0&&points.get(first-1).mz>=low) first--;
			while (last<points.size()&&points.get(last).mz<=high) last++;
			double total=0, denominator=0, mass=0, mobility=0;
			for (int j=first; j<last; j++) {
				if (visits!=null) visits[0]++;
				PeakWithIMS member=points.get(j);
				if (!member.isAvailable()||!(member.intensity>0f)||!(member.mz>=low&&member.mz<=high)) continue;
				total+=(double)member.intensity;
				if (member.intensity>=apex.intensity*0.5f) {
					denominator+=(double)member.intensity;
					mass+=(double)member.intensity*member.mz;
					mobility+=(double)member.intensity*member.ims;
				}
				member.turnOff();
			}
			if (total>0) output.add(new PeakWithIMS(mass/denominator, (float)total, (float)(mobility/denominator)));
		}
		output.sort(null);
		return output;
	}

	static void assertSamePoints(ArrayList<PeakWithIMS> expected, ArrayList<PeakWithIMS> actual, String context) {
		if (expected.size()!=actual.size()) throw new AssertionError(context+": count "+expected.size()+" != "+actual.size());
		for (int i=0; i<expected.size(); i++) {
			PeakWithIMS a=expected.get(i), b=actual.get(i);
			if (Double.doubleToLongBits(a.mz)!=Double.doubleToLongBits(b.mz)
					||Float.floatToIntBits(a.intensity)!=Float.floatToIntBits(b.intensity)
					||Float.floatToIntBits(a.ims)!=Float.floatToIntBits(b.ims)||a.isAvailable()!=b.isAvailable()) {
				throw new AssertionError(context+" at "+i+": "+a+" available="+a.isAvailable()+" != "+b+" available="+b.isAvailable());
			}
		}
	}

	static ArrayList<PeakWithIMS> verify(PeakPickerFixture fixture, String context) {
		ArrayList<PeakWithIMS> reference=fixture.freshPoints(), actual=fixture.freshPoints();
		ArrayList<PeakWithIMS> expected=squash(reference);
		ArrayList<PeakWithIMS> result=TIMSPeakPicker.squashAcrossIMS(actual);
		assertSamePoints(expected, result, context+" outputs");
		assertSamePoints(reference, actual, context+" inputs");
		return result;
	}
}
