package org.searlelab.msrawjava.io.thermo;

import java.io.IOException;

public final class ThermoServerPool {
	private static GrpcServerLauncher launcher;

	private static synchronized GrpcServerLauncher getLauncher() throws IOException, InterruptedException {
		if (launcher==null) launcher=new GrpcServerLauncher();
		return launcher;
	}

	public static int port() throws IOException, InterruptedException {
		return getLauncher().port();
	}

	public static synchronized void shutdown() {
		try {
			if (launcher!=null) launcher.close();
		} finally {
			launcher=null;
		}
	}
}
