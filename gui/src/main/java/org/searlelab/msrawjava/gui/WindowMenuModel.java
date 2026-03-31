package org.searlelab.msrawjava.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class WindowMenuModel {
	static final class VisualizationItem {
		private final String label;
		private final Integer acceleratorDigit;
		private final boolean active;

		private VisualizationItem(String label, Integer acceleratorDigit, boolean active) {
			this.label=Objects.requireNonNull(label);
			this.acceleratorDigit=acceleratorDigit;
			this.active=active;
		}

		String getLabel() {
			return label;
		}

		Integer getAcceleratorDigit() {
			return acceleratorDigit;
		}

		boolean isActive() {
			return active;
		}
	}

	private final boolean browserActive;
	private final List<VisualizationItem> visualizationItems;

	private WindowMenuModel(boolean browserActive, List<VisualizationItem> visualizationItems) {
		this.browserActive=browserActive;
		this.visualizationItems=List.copyOf(visualizationItems);
	}

	static WindowMenuModel create(boolean browserActive, int activeVisualizationIndex, List<String> visualizationTitles) {
		List<VisualizationItem> items=new ArrayList<>(visualizationTitles.size());
		for (int i=0; i<visualizationTitles.size(); i++) {
			Integer accelerator=i<9?Integer.valueOf(i+1):null;
			items.add(new VisualizationItem(visualizationTitles.get(i), accelerator, i==activeVisualizationIndex));
		}
		return new WindowMenuModel(browserActive, items);
	}

	boolean isBrowserActive() {
		return browserActive;
	}

	List<VisualizationItem> getVisualizationItems() {
		return visualizationItems;
	}
}
