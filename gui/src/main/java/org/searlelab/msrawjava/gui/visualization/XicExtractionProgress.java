package org.searlelab.msrawjava.gui.visualization;


final class XicExtractionProgress {
	final long token;
	final Object lock=new Object();
	final double[] xMinutes;
	final double[][] intensities;
	final double[][] observedMzs;
	final double[][] deltas;
	int extractedCount;
	int flushedCount;
	float maxIntensity;

	XicExtractionProgress(long token, double[] xMinutes, double[][] intensities, double[][] observedMzs, double[][] deltas) {
		this.token=token;
		this.xMinutes=xMinutes;
		this.intensities=intensities;
		this.observedMzs=observedMzs;
		this.deltas=deltas;
		this.extractedCount=0;
		this.flushedCount=0;
		this.maxIntensity=0.0f;
	}
}
