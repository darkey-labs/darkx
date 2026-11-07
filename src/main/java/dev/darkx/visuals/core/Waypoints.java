package dev.darkx.visuals.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Маркеры на координатах: название, цвет, иконка и мир. */
public final class Waypoints {
	public static final class Marker {
		public String name = "Маркер";
		public int color = 0xFFEF4444;
		public int icon;
		public String world = "overworld";
		public double x, y, z;
	}

	public static final String[] WORLDS = {"overworld", "nether", "end"};
	public static final String[] WORLD_NAMES = {"Верхний мир", "Незер", "Энд"};
	public static final int[] COLORS = {
		0xFFEF4444, 0xFFF97316, 0xFFFACC15, 0xFF34D399, 0xFF22D3EE, 0xFF60A5FA, 0xFF8B5CF6, 0xFFF472B6
	};

	public static final List<Marker> LIST = new ArrayList<>();

	private Waypoints() {}

	public static String currentWorld(Minecraft mc) {
		if (mc.level == null) return "overworld";
		if (mc.level.dimension() == Level.NETHER) return "nether";
		if (mc.level.dimension() == Level.END) return "end";
		return "overworld";
	}

	public static int worldIndex(String world) {
		for (int i = 0; i < WORLDS.length; i++) if (WORLDS[i].equals(world)) return i;
		return 0;
	}

	public static void save(JsonObject out) {
		JsonArray arr = new JsonArray();
		for (Marker m : LIST) {
			JsonObject o = new JsonObject();
			o.addProperty("name", m.name);
			o.addProperty("color", m.color);
			o.addProperty("icon", m.icon);
			o.addProperty("world", m.world);
			o.addProperty("x", m.x);
			o.addProperty("y", m.y);
			o.addProperty("z", m.z);
			arr.add(o);
		}
		out.add("list", arr);
	}

	public static void load(JsonObject in) {
		LIST.clear();
		if (!in.has("list") || !in.get("list").isJsonArray()) return;
		for (JsonElement e : in.getAsJsonArray("list")) {
			try {
				JsonObject o = e.getAsJsonObject();
				Marker m = new Marker();
				m.name = o.get("name").getAsString();
				m.color = o.get("color").getAsInt();
				m.icon = Math.max(0, Math.min(dev.darkx.visuals.ui.Icons.MARKER_ICONS.length - 1, o.get("icon").getAsInt()));
				m.world = o.get("world").getAsString();
				m.x = o.get("x").getAsDouble();
				m.y = o.get("y").getAsDouble();
				m.z = o.get("z").getAsDouble();
				LIST.add(m);
			} catch (Exception ignored) {
				// битый маркер пропускаем
			}
		}
	}
}
