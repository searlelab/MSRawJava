package org.searlelab.msrawjava.gui.filebrowser;

import java.util.Date;

final class DirectorySummaryMetrics {
	final Float gradientMin;
	final Float totalTIC;
	final Date acquiredDate;
	final SparkData spark;

	DirectorySummaryMetrics(Float gradientMin, Float totalTIC, Date acquiredDate, SparkData spark) {
		this.gradientMin=gradientMin;
		this.totalTIC=totalTIC;
		this.acquiredDate=acquiredDate;
		this.spark=spark;
	}
}
