package dev.darkx.visuals.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.darkx.visuals.hud.HudManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Конфиг лежит в .minecraft/config/darkx-visuals.json */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("darkx-visuals.json");

	/** Ставится в true при любом изменении; GUI сохраняет при закрытии. */
	public static boolean dirty;

	private Config() {}

	public static void load() {
		if (!Files.exists(FILE)) return;
		try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(r).getAsJsonObject();

			if (root.has("theme")) {
				JsonObject t = root.getAsJsonObject("theme");
				if (t.has("index")) Theme.index = Math.max(0, Math.min(Theme.customIndex(), t.get("index").getAsInt()));
				if (t.has("hue")) Theme.hue = t.get("hue").getAsFloat();
				if (t.has("style")) {
					try {
						String sn = t.get("style").getAsString();
						Theme.style = Theme.Style.valueOf("GLASS".equals(sn) ? "GRAY" : sn); // старое имя из прошлых версий
					} catch (IllegalArgumentException ignored) {
						Theme.style = Theme.Style.OLED;
					}
				}
				if (t.has("radiusMenu")) Theme.menuK = Theme.clampK(t.get("radiusMenu").getAsFloat());
				if (t.has("radiusElem")) Theme.elemK = Theme.clampK(t.get("radiusElem").getAsFloat());
				if (t.has("radiusHud")) Theme.hudK = Theme.clampK(t.get("radiusHud").getAsFloat());
			}
			if (root.has("modules")) {
				JsonObject mods = root.getAsJsonObject("modules");
				for (Module m : ModuleManager.all()) {
					if (mods.has(m.name)) m.load(mods.getAsJsonObject(m.name));
				}
			}
			if (root.has("hud")) HudManager.load(root.getAsJsonObject("hud"));
			if (root.has("marks")) Marks.load(root.getAsJsonObject("marks"));
			if (root.has("waypoints")) Waypoints.load(root.getAsJsonObject("waypoints"));
		} catch (Exception e) {
			System.err.println("[DarkX] Не удалось прочитать конфиг, используются значения по умолчанию: " + e);
		}
	}

	public static void save() {
		JsonObject root = new JsonObject();

		JsonObject theme = new JsonObject();
		theme.addProperty("index", Theme.index);
		theme.addProperty("hue", Theme.hue);
		theme.addProperty("style", Theme.style.name());
		theme.addProperty("radiusMenu", Theme.menuK);
		theme.addProperty("radiusElem", Theme.elemK);
		theme.addProperty("radiusHud", Theme.hudK);
		root.add("theme", theme);

		JsonObject mods = new JsonObject();
		for (Module m : ModuleManager.all()) {
			JsonObject o = new JsonObject();
			m.save(o);
			mods.add(m.name, o);
		}
		root.add("modules", mods);

		JsonObject hud = new JsonObject();
		HudManager.save(hud);
		root.add("hud", hud);

		JsonObject marks = new JsonObject();
		Marks.save(marks);
		root.add("marks", marks);

		JsonObject waypoints = new JsonObject();
		Waypoints.save(waypoints);
		root.add("waypoints", waypoints);

		try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
			GSON.toJson(root, w);
			dirty = false;
		} catch (IOException e) {
			System.err.println("[DarkX] Не удалось сохранить конфиг: " + e);
		}
	}

	public static void saveIfDirty() {
		if (dirty) save();
	}
}
