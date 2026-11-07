package dev.darkx.visuals.core;

import dev.darkx.visuals.modules.TotemsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Учёт сработавших тотемов: свои и (по желанию) чужие. Данные берутся из обычного события, которое видит и ваниль. */
public final class TotemPops {
	/** Сколько раз сработал твой тотем за эту сессию. */
	public static int own;
	private static final Map<UUID, Integer> OTHERS = new HashMap<>();

	private TotemPops() {}

	public static void onPop(Entity e) {
		TotemsModule m = ModuleManager.get(TotemsModule.class);
		if (m == null || !m.enabled) return;
		Minecraft mc = Minecraft.getInstance();
		if (e == mc.player) {
			own++;
			if (m.self.value) Notifications.push("Тотем", "Сработал твой тотем (№" + own + ")", 0xFFFACC15);
		} else if (e instanceof Player p && m.others.value) {
			int n = OTHERS.merge(p.getUUID(), 1, Integer::sum);
			Notifications.push("Тотем", p.getName().getString() + " потерял тотем (" + n + ")", 0xFFF97316);
		}
	}

	public static void clear() {
		own = 0;
		OTHERS.clear();
	}

	/** Сколько тотемов в инвентаре (вместе со второй рукой). */
	public static int count(Minecraft mc) {
		if (mc.player == null) return 0;
		int n = 0;
		var inv = mc.player.getInventory();
		int size = inv.getContainerSize();
		for (int i = 0; i < size; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.TOTEM_OF_UNDYING)) n += s.getCount();
		}
		if (size < 41) {
			ItemStack off = mc.player.getOffhandItem();
			if (off.is(Items.TOTEM_OF_UNDYING)) n += off.getCount();
		}
		return n;
	}
}
