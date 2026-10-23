package org.searlelab.msrawjava.gui;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import org.searlelab.msrawjava.logging.Logger;

/** Buffers launcher events until the browser exists; each payload is one navigation request. */
final class OpenFileDispatcher {
	private Consumer<List<File>> browser;
	private List<File> pending;

	synchronized void submit(List<File> files) {
		if (files.isEmpty()) return;
		List<File> request=List.copyOf(files);
		if (browser==null) {
			// Later independent requests supersede earlier startup requests.
			pending=request;
		} else {
			Consumer<List<File>> target=browser;
			SwingUtilities.invokeLater(() -> target.accept(request));
		}
	}

	synchronized void attach(Consumer<List<File>> browser) {
		this.browser=browser;
		if (pending!=null) {
			List<File> request=pending;
			pending=null;
			submit(request);
		}
	}

	void submitArguments(String[] arguments) {
		List<File> files=new ArrayList<>();
		for (String argument : arguments) {
			if (argument!=null&&!argument.isBlank()) files.add(new File(argument));
		}
		submit(files);
	}

	void submitNotification(String parameters, boolean mac) {
		if (parameters==null||parameters.isBlank()) return;
		// macOS supplies a single filename verbatim, or a quoted list for multiple files.
		List<String> paths=mac&&!parameters.startsWith("\"")?List.of(parameters):parseParameters(parameters);
		submitArguments(paths.toArray(new String[0]));
	}

	boolean registerInstall4j(ClassLoader loader, boolean mac) {
		try {
			Class<?> notifications=Class.forName("com.install4j.api.launcher.StartupNotification", true, loader);
			Class<?> listener=Class.forName("com.install4j.api.launcher.StartupNotification$Listener", true, loader);
			Object adapter=Proxy.newProxyInstance(loader, new Class<?>[] {listener}, (proxy, method, arguments) -> {
				if ("startupPerformed".equals(method.getName())) {
					submitNotification((String)arguments[0], mac);
					return null;
				}
				if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
				if ("equals".equals(method.getName())) return proxy==arguments[0];
				return "MSForest open-file listener";
			});
			notifications.getMethod("registerStartupListener", listener).invoke(null, adapter);
			return true;
		} catch (ClassNotFoundException e) {
			return false; // Normal Maven/JAR launch without install4j.
		} catch (ReflectiveOperationException|LinkageError e) {
			Logger.errorException(e);
			return false;
		}
	}

	static List<String> parseParameters(String parameters) {
		List<String> paths=new ArrayList<>();
		StringBuilder word=new StringBuilder();
		boolean quoted=false;
		for (int i=0; i<parameters.length(); i++) {
			char c=parameters.charAt(i);
			if (c=='\\') {
				int start=i;
				while (i<parameters.length()&&parameters.charAt(i)=='\\') i++;
				int count=i-start;
				if (i<parameters.length()&&parameters.charAt(i)=='"') {
					word.append("\\".repeat(count/2));
					if (count%2==1) word.append('"');
					else quoted=!quoted;
				} else {
					word.append("\\".repeat(count));
					i--;
				}
			} else if (c=='"') {
				quoted=!quoted;
			} else if (Character.isWhitespace(c)&&!quoted) {
				if (word.length()>0) {
					paths.add(word.toString());
					word.setLength(0);
				}
			} else {
				word.append(c);
			}
		}
		if (word.length()>0) paths.add(word.toString());
		return paths;
	}
}
