package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;

/** Рисует маркеры из вкладки «Маркеры» на экране. */
public final class MarkersModule extends Module {
	public final Setting.Bool showName = add(new Setting.Bool("Название", true));
	public final Setting.Bool showDistance = add(new Setting.Bool("Дистанция", true));
	public final Setting.Num maxDist = add(new Setting.Num("Макс. дистанция (0 — любая)", 0, 5000, 50, 0, " м"));

	public MarkersModule() {
		super("Markers", "Показывает маркеры на координатах прямо в мире.", Category.VISUAL);
	}
}
