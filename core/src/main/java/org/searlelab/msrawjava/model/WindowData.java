package org.searlelab.msrawjava.model;

import java.util.Optional;

/**
 * WindowData aggregates statistics and annotations for a window (commonly a DIA m/z range), such as counts and timing
 * characteristics, and optionally ion-mobility span or retention-time coverage.
 */
public class WindowData {

	private final float averageDutyCycle;
	private final int numberOfMSMS;
	private final Optional<Range> ionMobilityRange;
	private final Optional<Range> rtRange;

	public WindowData(float averageDutyCycle, int numberOfMSMS) {
		this(averageDutyCycle, numberOfMSMS, Optional.empty(), Optional.empty());
	}

	public WindowData(float averageDutyCycle, int numberOfMSMS, Optional<Range> ionMobilityRange) {
		this(averageDutyCycle, numberOfMSMS, ionMobilityRange, Optional.empty());
	}

	public WindowData(float averageDutyCycle, int numberOfMSMS, Optional<Range> ionMobilityRange, Optional<Range> rtRange) {
		this.averageDutyCycle=averageDutyCycle;
		this.numberOfMSMS=numberOfMSMS;
		this.ionMobilityRange=ionMobilityRange;
		this.rtRange=rtRange;
	}

	public float getAverageDutyCycle() {
		return averageDutyCycle;
	}

	public int getNumberOfMSMS() {
		return numberOfMSMS;
	}

	public Optional<Range> getIonMobilityRange() {
		return ionMobilityRange;
	}

	public Optional<Range> getRtRange() {
		return rtRange;
	}
}
