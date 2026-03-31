package org.searlelab.msrawjava.gui;

interface WindowMenuActionHandler {
	void bringRawFileBrowserToFront();

	void activateVisualization(int visualizationIndex);

	void activatePreviousWindow();

	void activateNextWindow();
}
