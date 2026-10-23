package org.searlelab.msrawjava.gui.filebrowser;

import java.util.Date;

final class DirectorySummaryMetrics {
	final Float gradientMin;
	final Float totalTIC;
	final Date acquiredDate;
	final SparkData spark;
	final String instrument;

	DirectorySummaryMetrics(Float gradientMin, Float totalTIC, Date acquiredDate, SparkData spark) {
		this(gradientMin, totalTIC, acquiredDate, spark, null);
	}

	DirectorySummaryMetrics(Float gradientMin, Float totalTIC, Date acquiredDate, SparkData spark, String instrument) {
		this.instrument=instrument;
		this.gradientMin=gradientMin;
		this.totalTIC=totalTIC;
		this.acquiredDate=acquiredDate;
		this.spark=spark;
	}
}
