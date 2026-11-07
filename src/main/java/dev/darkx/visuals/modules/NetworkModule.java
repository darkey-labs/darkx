package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Ecosystem;
import dev.darkx.visuals.core.Friends;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import net.minecraft.client.Minecraft;

/** Экосистема: клиенты DarkX на одном сервере находят друг друга и показывают значок DX. */
public final class NetworkModule extends Module {
	private static NetworkModule instance;

	public final Setting.Bool badge = add(new Setting.Bool("Значок DX у игроков", true));
	public final Setting.Bool skins = add(new Setting.Bool("Скины DarkX", true));

	public NetworkModule() {
		super("DarkX Network", "Видит других игроков с DarkX Visuals на сервере.", Category.MISC);
		instance = this;
	}

	/** Модуль включён (нужен для близких друзей). */
	public static boolean active() {
		return instance != null && instance.enabled;
	}

	/** Подмена скинов DarkX включена (свой скин работает и при выключенном модуле). */
	public static boolean skinsOn() {
		return instance == null || instance.skins.value;
	}

	/** Адрес базы: всегда зашитый в клиент (менять в настройках нельзя). */
	public static String baseUrl() {
		return Ecosystem.baseUrl(null);
	}

	public static boolean showBadge() {
		return instance != null && instance.enabled && instance.badge.value;
	}

	@Override
	public void onTick(Minecraft mc) {
		Ecosystem.tick(mc, null);
		Friends.tick(mc, null);
	}

	@Override
	public void onDisable() {
		Ecosystem.leave();
		Friends.clear();
	}
}
