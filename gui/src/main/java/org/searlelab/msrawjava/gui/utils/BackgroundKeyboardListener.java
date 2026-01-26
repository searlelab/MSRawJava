package org.searlelab.msrawjava.gui.utils;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.TreeSet;

import org.searlelab.msrawjava.gui.GUIPreferences;
import org.searlelab.msrawjava.logging.Logger;

public class BackgroundKeyboardListener implements KeyListener, ContainerListener {
	private final TreeSet<EasterEgg> eggs=new TreeSet<>();

	public BackgroundKeyboardListener() {
		generateEasterEggs();
	}

	@Override
	public void componentAdded(ContainerEvent e) {
		addKeyAndContainerListenerRecursively(e.getChild());
	}

	@Override
	public void componentRemoved(ContainerEvent e) {
		removeKeyAndContainerListenerRecursively(e.getChild());
	}

	@Override
	public void keyReleased(KeyEvent e) {
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (!e.isShiftDown()||!e.isControlDown()) return;
		int code=e.getKeyCode();
		for (EasterEgg egg : eggs) {
			if (code==egg.getVirtualKeyCode()) {
				egg.run();
			}
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}

	public void addKeyAndContainerListenerRecursively(Component c) {
		c.addKeyListener(this);

		if (c instanceof Container) {
			Container cont=(Container)c;
			cont.addContainerListener(this);
			Component[] children=cont.getComponents();
			for (Component child : children) {
				addKeyAndContainerListenerRecursively(child);
			}
		}
	}

	public void removeKeyAndContainerListenerRecursively(Component c) {
		c.removeKeyListener(this);

		if (c instanceof Container) {
			Container cont=(Container)c;
			cont.removeContainerListener(this);
			Component[] children=cont.getComponents();
			for (Component child : children) {
				removeKeyAndContainerListenerRecursively(child);
			}
		}
	}

	private void generateEasterEggs() {
		registerEasterEgg(new EasterEgg(KeyEvent.VK_F1, "Reset GUI preferences") {
			@Override
			void run() {
				Logger.logLine("Resetting GUI preferences to defaults.");
				GUIPreferences.resetAll();
			}
		});
	}

	private void registerEasterEgg(EasterEgg egg) {
		eggs.add(egg);
	}

	private abstract static class EasterEgg implements Comparable<EasterEgg> {
		private final int virtualKeyCode;
		private final String name;

		EasterEgg(int virtualKeyCode, String name) {
			this.virtualKeyCode=virtualKeyCode;
			this.name=name;
		}

		int getVirtualKeyCode() {
			return virtualKeyCode;
		}

		@Override
		public String toString() {
			return KeyEvent.getKeyText(virtualKeyCode)+" --> "+name;
		}

		@Override
		public int compareTo(EasterEgg o) {
			if (o==null) return 1;
			return virtualKeyCode-o.virtualKeyCode;
		}

		abstract void run();
	}
}
