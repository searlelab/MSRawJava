package com.install4j.api.launcher;

/** Test fixture for the optional launcher API, without an install4j dependency. */
public final class StartupNotification {
	public interface Listener {
		void startupPerformed(String parameters);
	}

	public static Listener listener;

	public static void registerStartupListener(Listener listener) {
		StartupNotification.listener=listener;
		listener.startupPerformed("\"/startup folder/first.raw\"");
	}
}
