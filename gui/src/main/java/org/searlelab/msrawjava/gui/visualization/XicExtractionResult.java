package org.searlelab.msrawjava.gui.visualization;

final class XicExtractionResult {
	final XicTraceData traceData;
	final float maxIntensity;

	XicExtractionResult(XicTraceData traceData, float maxIntensity) {
		this.traceData=traceData;
		this.maxIntensity=maxIntensity;
	}
}
