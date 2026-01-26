package org.searlelab.msrawjava;

import java.util.prefs.Preferences;

import org.searlelab.msrawjava.logging.Logger;

public final class COREPreferences {
	private static final Preferences PREFS=Preferences.userNodeForPackage(COREPreferences.class);
	private static final String PREF_VERBOSE_CORE_LOGGING="core.verboseLogging";
	private static final String PREF_DEMUX_TOLERANCE_PPM="core.demuxTolerancePpm";
	private static final String PREF_MIN_MS1_INTENSITY="core.minMs1Intensity";
	private static final String PREF_MIN_MS2_INTENSITY="core.minMs2Intensity";

	private static boolean verboseCoreLogging=PREFS.getBoolean(PREF_VERBOSE_CORE_LOGGING, true);

	private COREPreferences() {
	}

	public static boolean isVerboseCoreLogging() {
		logRead(PREF_VERBOSE_CORE_LOGGING, verboseCoreLogging);
		return verboseCoreLogging;
	}

	public static void setVerboseCoreLogging(boolean enabled) {
		verboseCoreLogging=enabled;
		PREFS.putBoolean(PREF_VERBOSE_CORE_LOGGING, enabled);
		logWrite(PREF_VERBOSE_CORE_LOGGING, enabled);
	}

	public static double getDemuxTolerancePpm() {
		double value=PREFS.getDouble(PREF_DEMUX_TOLERANCE_PPM, 10.0);
		logRead(PREF_DEMUX_TOLERANCE_PPM, value);
		return value;
	}

	public static void setDemuxTolerancePpm(double value) {
		PREFS.putDouble(PREF_DEMUX_TOLERANCE_PPM, value);
		logWrite(PREF_DEMUX_TOLERANCE_PPM, value);
	}

	public static float getMinimumMS1Intensity() {
		float value=PREFS.getFloat(PREF_MIN_MS1_INTENSITY, 3.0f);
		logRead(PREF_MIN_MS1_INTENSITY, value);
		return value;
	}

	public static void setMinimumMS1Intensity(float value) {
		PREFS.putFloat(PREF_MIN_MS1_INTENSITY, value);
		logWrite(PREF_MIN_MS1_INTENSITY, value);
	}

	public static float getMinimumMS2Intensity() {
		float value=PREFS.getFloat(PREF_MIN_MS2_INTENSITY, 1.0f);
		logRead(PREF_MIN_MS2_INTENSITY, value);
		return value;
	}

	public static void setMinimumMS2Intensity(float value) {
		PREFS.putFloat(PREF_MIN_MS2_INTENSITY, value);
		logWrite(PREF_MIN_MS2_INTENSITY, value);
	}

	public static void resetAll() {
		PREFS.remove(PREF_DEMUX_TOLERANCE_PPM);
		logWrite(PREF_DEMUX_TOLERANCE_PPM, null);
		PREFS.remove(PREF_MIN_MS1_INTENSITY);
		logWrite(PREF_MIN_MS1_INTENSITY, null);
		PREFS.remove(PREF_MIN_MS2_INTENSITY);
		logWrite(PREF_MIN_MS2_INTENSITY, null);
	}

	private static void logRead(String key, Object value) {
		if (!verboseCoreLogging) return;
		Logger.logLine("COREPreferences read: "+key+"="+String.valueOf(value));
	}

	private static void logWrite(String key, Object value) {
		if (!verboseCoreLogging) return;
		Logger.logLine("COREPreferences write: "+key+"="+String.valueOf(value));
	}
}
