package org.searlelab.msrawjava.gui.visualization;

import java.util.List;

import org.searlelab.msrawjava.gui.graphing.XYTrace;

final class XicExtractionResult {
	final List<XYTrace> traces;
	final float maxIntensity;

	XicExtractionResult(List<XYTrace> traces, float maxIntensity) {
		this.traces=traces;
		this.maxIntensity=maxIntensity;
	}
}
