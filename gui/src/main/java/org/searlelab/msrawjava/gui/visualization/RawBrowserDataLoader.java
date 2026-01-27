package org.searlelab.msrawjava.gui.visualization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.gui.graphing.GraphType;
import org.searlelab.msrawjava.gui.graphing.XYTrace;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.model.ScanSummary;

import gnu.trove.list.array.TFloatArrayList;

public final class RawBrowserDataLoader {
	private RawBrowserDataLoader() {
	}

	public static RawBrowserData build(StripeFileInterface stripe) throws Exception {
		ArrayList<ScanSummary> scans=new ArrayList<>();
		List<ScanSummary> summaries=stripe.getScanSummaries(-Float.MAX_VALUE, Float.MAX_VALUE);
		scans.addAll(summaries);
		Collections.sort(scans, new ScanSummaryComparator());

		var structure=StructureChartBuilder.buildLocalStructureChart(stripe.getRanges(), summaries);
		var global=StructureChartBuilder.buildGlobalStructureChart(stripe.getRanges());

		float maxTicLocal=0.0f;
		TFloatArrayList ticX=new TFloatArrayList();
		TFloatArrayList ticY=new TFloatArrayList();

		XYTrace chromatogramTrace;
		try {
			Pair<float[], float[]> tic=stripe.getTICTrace();
			float[] mins=MatrixMath.divide(tic.x, 60.0f);
			chromatogramTrace=new XYTrace(mins, tic.y, GraphType.area, "Precursor TIC", new java.awt.Color(0x55, 0x55, 0xF6), null);
			for (int i=0; i<mins.length; i++) {
				ticX.add(mins[i]);
				ticY.add(tic.y[i]);
				if (tic.y[i]>maxTicLocal) maxTicLocal=tic.y[i];
			}
		} catch (Exception e) {
			Logger.logException(e);
			chromatogramTrace=new XYTrace(ticX.toArray(), ticY.toArray(), GraphType.area, "Precursor TIC", new java.awt.Color(0x55, 0x55, 0xF6), null);
		}
		float minRT=Float.MAX_VALUE;
		float maxRT=0.0f;
		float threshold=maxTicLocal/20f;
		for (int i=0; i<ticX.size(); i++) {
			float y=ticY.get(i);
			float x=ticX.get(i);
			if (y>threshold) {
				if (x<minRT) minRT=x;
				if (x>maxRT) maxRT=x;
			}
		}

		Map<Comparable<?>, TFloatArrayList> iitByRange=new HashMap<>();
		Map<Comparable<?>, TFloatArrayList> iitByRt=new HashMap<>();
		for (ScanSummary summary : scans) {
			if (summary.isPrecursor()) {
				continue;
			}
			float rtMin=summary.getScanStartTime()/60f;
			if (rtMin>minRT&&rtMin<maxRT) {
				Comparable<?> rangeKey=summary.getIsolationWindowLower()==summary.getIsolationWindowUpper()
						?summary.getIsolationWindowLower()
						:new org.searlelab.msrawjava.model.Range((float)summary.getIsolationWindowLower(), (float)summary.getIsolationWindowUpper());
				TFloatArrayList list=iitByRange.get(rangeKey);
				if (list==null) {
					list=new TFloatArrayList();
					iitByRange.put(rangeKey, list);
				}
				Float iit=summary.getIonInjectionTime();
				if (iit!=null&&iit>0) list.add(iit*1000f);

				float binKey=5f*Math.round(summary.getScanStartTime()/300f);
				TFloatArrayList byRt=iitByRt.get(binKey);
				if (byRt==null) {
					byRt=new TFloatArrayList();
					iitByRt.put(binKey, byRt);
				}
				if (iit!=null&&iit>0) byRt.add(iit*1000f);
			}
		}

		XYTrace basepeakTrace=chromatogramTrace;
		XYTrace precursorIntensityHistogram=new XYTrace(new double[0], new double[0], GraphType.area, "Log10 Precursor Intensity Distribution");
		XYTrace fragmentIntensityHistogram=new XYTrace(new double[0], new double[0], GraphType.area, "Log10 Fragment Intensity Distribution");

		return new RawBrowserData(scans, chromatogramTrace, basepeakTrace, precursorIntensityHistogram, fragmentIntensityHistogram,
				structure, global, iitByRange, iitByRt, maxTicLocal);
	}

	// no helper needed

	private static final class ScanSummaryComparator implements Comparator<ScanSummary> {
		@Override
		public int compare(ScanSummary a, ScanSummary b) {
			if (a==null&&b==null) return 0;
			if (a==null) return -1;
			if (b==null) return 1;
			int c=Float.compare(a.getScanStartTime(), b.getScanStartTime());
			if (c!=0) return c;
			c=Integer.compare(a.getSpectrumIndex(), b.getSpectrumIndex());
			if (c!=0) return c;
			c=Double.compare(a.getIsolationWindowLower(), b.getIsolationWindowLower());
			if (c!=0) return c;
			return Double.compare(a.getIsolationWindowUpper(), b.getIsolationWindowUpper());
		}
	}
}
