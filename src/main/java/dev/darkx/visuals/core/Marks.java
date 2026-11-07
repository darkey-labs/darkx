package dev.darkx.visuals.core;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Метки игроков (друг / враг / цель). Отображаются в Таргет ХУДе и в списке на вкладке «Метки». */
public final class Marks {
	public enum Type {
		FRIEND("Друг", 0xFF34D399),
		ENEMY("Враг", 0xFFEF4444),
		TARGET("Цель", 0xFFF59E0B);

		public final String label;
		public final int color;

		Type(String label, int color) {
			this.label = label;
			this.color = color;
		}
	}

	private static final Map<UUID, Type> MAP = new LinkedHashMap<>();

	private Marks() {}

	public static Type get(UUID id) {
		return MAP.get(id);
	}

	/** Повторный клик по той же метке снимает её. */
	public static void toggle(UUID id, Type type) {
		if (MAP.get(id) == type) MAP.remove(id);
		else MAP.put(id, type);
		Config.dirty = true;
	}

	public static int count(Type type) {
		int n = 0;
		for (Type t : MAP.values()) if (t == type) n++;
		return n;
	}

	public static void save(JsonObject out) {
		for (Map.Entry<UUID, Type> e : MAP.entrySet()) out.addProperty(e.getKey().toString(), e.getValue().name());
	}

	public static void load(JsonObject in) {
		MAP.clear();
		for (Map.Entry<String, com.google.gson.JsonElement> e : in.entrySet()) {
			try {
				MAP.put(UUID.fromString(e.getKey()), Type.valueOf(e.getValue().getAsString()));
			} catch (IllegalArgumentException ignored) {
				// битая запись — пропускаем
			}
		}
	}
}
