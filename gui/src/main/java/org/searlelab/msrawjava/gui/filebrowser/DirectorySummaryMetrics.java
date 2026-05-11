package org.searlelab.msrawjava.gui.filebrowser;

final class DirectorySummaryMetrics {
	final Float gradientMin;
	final Float totalTIC;
	final SparkData spark;

	DirectorySummaryMetrics(Float gradientMin, Float totalTIC, SparkData spark) {
		this.gradientMin=gradientMin;
		this.totalTIC=totalTIC;
		this.spark=spark;
	}
}
