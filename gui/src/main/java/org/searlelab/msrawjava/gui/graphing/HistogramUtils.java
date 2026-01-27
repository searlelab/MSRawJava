package org.searlelab.msrawjava.gui.graphing;

public final class HistogramUtils {
	private HistogramUtils() {
	}

	public static XYTrace histogramFromLog10(float[] intensities, String name) {
		if (intensities==null||intensities.length==0) {
			return new XYTrace(new double[0], new double[0], GraphType.area, name);
		}
		float[] logValues=new float[intensities.length];
		for (int i=0; i<intensities.length; i++) {
			logValues[i]=protectedLog10(intensities[i]);
		}
		return histogram(logValues, name);
	}

	public static XYTrace histogram(float[] values, String name) {
		if (values==null||values.length==0) {
			return new XYTrace(new double[0], new double[0], GraphType.area, name);
		}

		float min=Float.MAX_VALUE;
		float max=-Float.MAX_VALUE;
		for (float v : values) {
			if (v<min) min=v;
			if (v>max) max=v;
		}

		int binCount=Math.max(50, values.length/25);
		binCount=Math.min(binCount, 200);

		float range=max-min;
		float binSize=(binCount>0)?(range/binCount):1.0f;
		if (binSize<=0) binSize=1.0f;

		int numberOfBins=(int)((max-min)/binSize)+1;
		int[] counts=new int[numberOfBins];
		for (float v : values) {
			int index=(int)Math.floor((v-min)/binSize);
			if (index<0) index=0;
			if (index>=numberOfBins) index=numberOfBins-1;
			counts[index]++;
		}

		double[] xs=new double[numberOfBins];
		double[] ys=new double[numberOfBins];
		for (int i=0; i<numberOfBins; i++) {
			xs[i]=min+binSize*(i+0.5f);
			ys[i]=counts[i];
		}
		return new XYTrace(xs, ys, GraphType.area, name);
	}

	public static float protectedLog10(float v) {
		if (v<=0) return 0.0f;
		return (float)Math.log10(v);
	}
}
