package org.searlelab.msrawjava.gui.filebrowser;

import org.searlelab.msrawjava.algorithms.MatrixMath;

/** Compact TIC representation for painting fast. Stores normalized y in [0..1]. */
final class SparkData {
	final float[] yNorm; // 0..1, fixed-size (e.g., 64 points)

	SparkData(float[] yNorm) {
		this.yNorm=yNorm;
	}

	static SparkData fromTIC(float[] x, float[] y, int maxPts) {
		if (y==null||y.length==0) {
			return new SparkData(new float[] {0.0f});
		}
		int n=Math.min(maxPts, y.length);

		float[] pick=new float[n];
		for (int i=0; i<y.length; i++) {
			int index=(int)Math.floor(n*i/(float)y.length);
			if (y[i]>pick[index]) {
				pick[index]=y[i];
			}
		}
		float max=MatrixMath.max(pick);
		if (max<=0) max=1.0f;

		for (int i=0; i<n; i++) {
			pick[i]=(float)(pick[i]/max);
		}
		return new SparkData(pick);
	}
}
