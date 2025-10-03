package org.searlelab.msrawjava.io.thermo;

public final class ThermoServerPool {
	private static GrpcServerLauncher launcher;

	private static synchronized GrpcServerLauncher getLauncher() throws Exception {
		if (launcher == null) launcher = new GrpcServerLauncher();
		return launcher;
	}

	public static int port() throws Exception {
		return getLauncher().port();
	}

	public static synchronized void shutdown() {
		try {
			if (launcher != null) launcher.close();
		} finally {
			launcher = null;
		}
	}
}
