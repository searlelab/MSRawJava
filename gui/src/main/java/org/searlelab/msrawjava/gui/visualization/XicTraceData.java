package org.searlelab.msrawjava.gui.visualization;

import java.util.ArrayList;
import java.util.List;

import org.searlelab.msrawjava.gui.graphing.GraphType;
import org.searlelab.msrawjava.gui.graphing.XYTrace;

final class XicTraceData {
	final List<RawBrowserXicUtils.XicTarget> targets;
	final XicToleranceOption tolerance;
	final double[] xMinutes;
	final double[][] intensities;
	final double[][] observedMzs;
	final double[][] deltas;
	final float maxIntensity;

	XicTraceData(List<RawBrowserXicUtils.XicTarget> targets, XicToleranceOption tolerance, double[] xMinutes, double[][] intensities, double[][] observedMzs,
			double[][] deltas, float maxIntensity) {
		this.targets=targets==null?List.of():List.copyOf(targets);
		this.tolerance=tolerance==null?XicToleranceOption.DEFAULT:tolerance;
		this.xMinutes=xMinutes==null?new double[0]:xMinutes;
		this.intensities=intensities==null?new double[0][0]:intensities;
		this.observedMzs=observedMzs==null?new double[0][0]:observedMzs;
		this.deltas=deltas==null?new double[0][0]:deltas;
		this.maxIntensity=maxIntensity;
	}

	static XicTraceData empty(List<RawBrowserXicUtils.XicTarget> targets, XicToleranceOption tolerance) {
		int count=targets==null?0:targets.size();
		return new XicTraceData(targets, tolerance, new double[0], new double[count][0], new double[count][0], new double[count][0], 0.0f);
	}

	static XicTraceData fromProgress(List<RawBrowserXicUtils.XicTarget> targets, XicToleranceOption tolerance, XicExtractionProgress progress, int extractedCount,
			float maxIntensity) {
		if (progress==null) return empty(targets, tolerance);
		int targetCount=targets==null?0:targets.size();
		int count=Math.max(0, Math.min(extractedCount, progress.xMinutes.length));
		double[] xMinutes=new double[count];
		System.arraycopy(progress.xMinutes, 0, xMinutes, 0, count);
		double[][] intensities=copyRows(progress.intensities, targetCount, count);
		double[][] observedMzs=copyRows(progress.observedMzs, targetCount, count);
		double[][] deltas=copyRows(progress.deltas, targetCount, count);
		return new XicTraceData(targets, tolerance, xMinutes, intensities, observedMzs, deltas, maxIntensity);
	}

	private static double[][] copyRows(double[][] source, int targetCount, int count) {
		double[][] copy=new double[targetCount][count];
		for (int t=0; t<targetCount; t++) {
			if (source==null||t>=source.length) continue;
			int length=Math.min(count, source[t].length);
			System.arraycopy(source[t], 0, copy[t], 0, length);
		}
		return copy;
	}

	List<XYTrace> buildIntensityTraces() {
		ArrayList<XYTrace> traces=new ArrayList<>();
		for (int i=0; i<targets.size(); i++) {
			RawBrowserXicUtils.XicTarget target=targets.get(i);
			double[] y=i<intensities.length?intensities[i]:new double[0];
			traces.add(new XYTrace(xMinutes, y, GraphType.line, formatTargetLabel(target), RawBrowserScanRenderer.getXicColor(i), 3.0f));
		}
		return traces;
	}

	private static String formatTargetLabel(RawBrowserXicUtils.XicTarget target) {
		return String.format(java.util.Locale.ROOT, "%s (%.3f m/z)", target.label(), target.mz());
	}
}
