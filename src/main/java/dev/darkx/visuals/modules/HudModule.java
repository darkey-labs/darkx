package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.hud.HudManager;

/** Один модуль на все худы: что именно показывать — в выпадающем списке «Отображать...». */
public final class HudModule extends Module {
	public final Setting.Multi show = add(new Setting.Multi("Отображать...", HudManager.titles(), HudManager.defaults()));
	public final Setting.Num opacity = add(new Setting.Num("Прозрачность фона", 0, 100, 5, 70, "%"));

	public HudModule() {
		super("HUD", "Информационные худы: таргет, профиль, эффекты, броня и другие.", Category.VISUAL);
	}
}
