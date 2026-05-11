package org.searlelab.msrawjava.gui.visualization;


final class XicExtractionProgress {
	final long token;
	final Object lock=new Object();
	final double[] xMinutes;
	final double[][] traces;
	int extractedCount;
	int flushedCount;
	float maxIntensity;

	XicExtractionProgress(long token, double[] xMinutes, double[][] traces) {
		this.token=token;
		this.xMinutes=xMinutes;
		this.traces=traces;
		this.extractedCount=0;
		this.flushedCount=0;
		this.maxIntensity=0.0f;
	}
}
