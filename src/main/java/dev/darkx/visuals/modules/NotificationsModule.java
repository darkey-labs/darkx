package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;

public final class NotificationsModule extends Module {
	public final Setting.Num seconds = add(new Setting.Num("Время показа", 1, 8, 0.5, 3, " с"));

	public NotificationsModule() {
		super("Notifications", "Уведомления при включении и выключении модулей.", Category.MISC);
	}
}
