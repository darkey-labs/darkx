package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Скрывает ботов античита: «игроков» с фейковым UUID, которых нет в табе. */
public final class AntiAcBotModule extends Module {
	public final Setting.Mode mode = add(new Setting.Mode("Определять по", 0, "Нет в табе", "Версия UUID", "Оба"));

	public AntiAcBotModule() {
		super("AntiACBot", "Убирает с экрана ботов античита с фейковым UUID.", Category.MISC);
	}

	public static boolean isBot(Entity e) {
		AntiAcBotModule m = ModuleManager.get(AntiAcBotModule.class);
		if (m == null || !m.enabled || !(e instanceof Player p)) return false;
		Minecraft mc = Minecraft.getInstance();
		if (p == mc.player || mc.getConnection() == null) return false;

		boolean byTab = m.mode.is("Нет в табе") || m.mode.is("Оба");
		boolean byVersion = m.mode.is("Версия UUID") || m.mode.is("Оба");

		// У настоящих игроков есть запись в табе с тем же UUID
		if (byTab && mc.getConnection().getPlayerInfo(p.getUUID()) == null) return true;
		// Настоящие UUID: v4 (лицензия) или v3 (оффлайн-режим)
		if (byVersion) {
			int v = p.getUUID().version();
			return v != 4 && v != 3;
		}
		return false;
	}

	/**
	 * «Игрок» не настоящий (НПС, бот античита): нет в табе или UUID не версии 3/4.
	 * Работает всегда, не зависит от того, включён ли модуль. Используется в Неар ХУДе.
	 */
	public static boolean isFake(Entity e) {
		if (!(e instanceof Player p)) return false;
		Minecraft mc = Minecraft.getInstance();
		if (p == mc.player || mc.getConnection() == null) return false;
		if (mc.getConnection().getPlayerInfo(p.getUUID()) == null) return true;
		int v = p.getUUID().version();
		return v != 4 && v != 3;
	}
}
