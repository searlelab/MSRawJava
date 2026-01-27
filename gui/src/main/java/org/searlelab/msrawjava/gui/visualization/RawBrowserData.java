package org.searlelab.msrawjava.gui.visualization;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.searlelab.msrawjava.gui.graphing.ExtendedChartPanel;
import org.searlelab.msrawjava.gui.graphing.XYTrace;
import org.searlelab.msrawjava.model.ScanSummary;

import gnu.trove.list.array.TFloatArrayList;

/**
 * Data container for raw browser views.
 */
public class RawBrowserData {
	private final List<ScanSummary> scans;
	private final XYTrace chromatogram;
	private final XYTrace basepeakTrace;
	private final XYTrace precursorIntensityHistogram;
	private final XYTrace fragmentIntensityHistogram;
	private final ExtendedChartPanel structureChart;
	private final ExtendedChartPanel globalChart;
	private final Map<Comparable<?>, TFloatArrayList> iitByRange;
	private final Map<Comparable<?>, TFloatArrayList> iitByRt;
	private final float maxTic;

	public RawBrowserData(List<ScanSummary> scans, XYTrace chromatogram, XYTrace basepeakTrace, XYTrace precursorIntensityHistogram,
			XYTrace fragmentIntensityHistogram, ExtendedChartPanel structureChart, ExtendedChartPanel globalChart,
			Map<Comparable<?>, TFloatArrayList> iitByRange, Map<Comparable<?>, TFloatArrayList> iitByRt, float maxTic) {
		this.scans=(scans==null)?new ArrayList<>():new ArrayList<>(scans);
		this.chromatogram=chromatogram;
		this.basepeakTrace=basepeakTrace;
		this.precursorIntensityHistogram=precursorIntensityHistogram;
		this.fragmentIntensityHistogram=fragmentIntensityHistogram;
		this.structureChart=structureChart;
		this.globalChart=globalChart;
		this.iitByRange=iitByRange;
		this.iitByRt=iitByRt;
		this.maxTic=maxTic;
	}

	public List<ScanSummary> getScans() {
		return scans;
	}

	public XYTrace getChromatogram() {
		return chromatogram;
	}

	public XYTrace getBasepeakTrace() {
		return basepeakTrace;
	}

	public XYTrace getPrecursorIntensityHistogram() {
		return precursorIntensityHistogram;
	}

	public XYTrace getFragmentIntensityHistogram() {
		return fragmentIntensityHistogram;
	}

	public ExtendedChartPanel getStructureChart() {
		return structureChart;
	}

	public ExtendedChartPanel getGlobalChart() {
		return globalChart;
	}

	public Map<Comparable<?>, TFloatArrayList> getIitByRange() {
		return iitByRange;
	}

	public Map<Comparable<?>, TFloatArrayList> getIitByRt() {
		return iitByRt;
	}

	public float getMaxTic() {
		return maxTic;
	}
}
