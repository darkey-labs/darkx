package dev.darkx.visuals.core;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
	public final String name;
	public final String description;
	public final Category category;
	public final List<Setting> settings = new ArrayList<>();
	public boolean enabled;

	protected Module(String name, String description, Category category) {
		this.name = name;
		this.description = description;
		this.category = category;
	}

	protected <S extends Setting> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	public void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		if (value) onEnable();
		else onDisable();
		dev.darkx.visuals.audio.ClientSounds.toggled(value);
		Notifications.moduleToggled(this);
		Config.dirty = true;
	}

	public void onEnable() {}

	public void onDisable() {}

	/** Вызывается раз в клиентский тик, пока мир загружен. */
	public void onTick(Minecraft mc) {}

	/** Раз в тик, даже когда модуль выключен (например, чтобы применить выбранный пресет). */
	public void onAlways(Minecraft mc) {}

	public void save(JsonObject out) {
		out.addProperty("enabled", enabled);
		JsonObject s = new JsonObject();
		for (Setting setting : settings) setting.save(s);
		out.add("settings", s);
	}

	public void load(JsonObject in) {
		if (in.has("enabled")) enabled = in.get("enabled").getAsBoolean();
		if (in.has("settings") && in.get("settings").isJsonObject()) {
			JsonObject s = in.getAsJsonObject("settings");
			for (Setting setting : settings) setting.load(s);
		}
	}
}
