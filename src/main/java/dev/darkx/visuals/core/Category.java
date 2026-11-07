package dev.darkx.visuals.core;

import dev.darkx.visuals.ui.Icons;

public enum Category {
	VISUAL("Visual", Icons.EYE),
	VIEW("View", Icons.CAMERA),
	MISC("Misc", Icons.SLIDERS);

	public final String title;
	public final String icon;

	Category(String title, String icon) {
		this.title = title;
		this.icon = icon;
	}
}
