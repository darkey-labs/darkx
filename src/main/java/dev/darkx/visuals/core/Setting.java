package dev.darkx.visuals.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.function.BooleanSupplier;

/** Базовый класс настройки модуля. Все значения (де)сериализуются в JSON по имени. */
public abstract class Setting {
	public final String name;
	private BooleanSupplier visible = () -> true;

	protected Setting(String name) {
		this.name = name;
	}

	/** Показывать настройку только при выполнении условия. */
	public Setting showIf(BooleanSupplier condition) {
		this.visible = condition;
		return this;
	}

	public boolean isVisible() {
		return visible.getAsBoolean();
	}

	public abstract void save(JsonObject out);

	public abstract void load(JsonObject in);

	protected static JsonElement get(JsonObject in, String key) {
		return in.has(key) ? in.get(key) : null;
	}

	// ------------------------------------------------------------------ Bool

	public static final class Bool extends Setting {
		public boolean value;

		public Bool(String name, boolean def) {
			super(name);
			this.value = def;
		}

		@Override public void save(JsonObject out) { out.addProperty(name, value); }

		@Override public void load(JsonObject in) {
			JsonElement e = get(in, name);
			if (e != null && e.isJsonPrimitive()) value = e.getAsBoolean();
		}
	}

	// ---------------------------------------------------------------- Number

	public static final class Num extends Setting {
		public double value;
		public final double min, max, step;
		public final String suffix;

		public Num(String name, double min, double max, double step, double def, String suffix) {
			super(name);
			this.min = min;
			this.max = max;
			this.step = step;
			this.suffix = suffix;
			set(def);
		}

		public void set(double v) {
			v = Math.max(min, Math.min(max, v));
			if (step > 0) v = Math.round((v - min) / step) * step + min;
			this.value = Math.round(v * 10000.0) / 10000.0;
		}

		public float f() { return (float) value; }

		public int i() { return (int) Math.round(value); }

		public float ratio() { return (float) ((value - min) / (max - min)); }

		public String display() {
			String s = (step >= 1) ? Integer.toString((int) Math.round(value)) : String.format(java.util.Locale.ROOT, "%.2f", value);
			return s + suffix;
		}

		@Override public void save(JsonObject out) { out.addProperty(name, value); }

		@Override public void load(JsonObject in) {
			JsonElement e = get(in, name);
			if (e != null && e.isJsonPrimitive()) set(e.getAsDouble());
		}
	}

	// ------------------------------------------------------------------ Mode

	public static class Mode extends Setting {
		public final String[] modes;
		public int index;

		public Mode(String name, int def, String... modes) {
			super(name);
			this.modes = modes;
			this.index = def;
		}

		public String get() { return modes[index]; }

		public boolean is(String mode) { return modes[index].equals(mode); }

		@Override public void save(JsonObject out) { out.addProperty(name, get()); }

		@Override public void load(JsonObject in) {
			JsonElement e = get(in, name);
			if (e == null || !e.isJsonPrimitive()) return;
			String v = e.getAsString();
			for (int i = 0; i < modes.length; i++) if (modes[i].equals(v)) { index = i; return; }
		}
	}

	// ----------------------------------------------------------------- Color

	/** Выбор цвета: «Тема» берёт акцент выбранной темы, «Радуга» плавно переливается. */
	public static final class Color extends Mode {
		private static final String[] NAMES = {
			"Тема", "Радуга", "Серый", "Красный", "Оранжевый", "Жёлтый", "Зелёный", "Голубой", "Фиолетовый", "Розовый"
		};
		private static final int[] FIXED = {
			0, 0, 0xFFD4D4D4, 0xFFEF4444, 0xFFF97316, 0xFFFACC15, 0xFF34D399, 0xFF38BDF8, 0xFF8B5CF6, 0xFFF472B6
		};

		public Color(String name, int def) {
			super(name, def, NAMES);
		}

		public int count() { return NAMES.length; }

		/** Цвет i-го пункта (для свотчей в меню). */
		public static int swatch(int i) {
			if (i == 0) return Theme.accent();
			if (i == 1) return rainbow(0f);
			return FIXED[i];
		}

		/** @param offset смещение по спектру 0..1 (для радуги/градиентов) */
		public int color(float offset) {
			if (index == 0) return Theme.accent();
			if (index == 1) return rainbow(offset);
			return FIXED[index];
		}

		public static int rainbow(float offset) {
			float hue = ((System.currentTimeMillis() % 6000L) / 6000f + offset) % 1f;
			return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.62f, 1f) & 0xFFFFFF);
		}
	}

	// ----------------------------------------------------------------- Multi

	public static final class Multi extends Setting {
		public final String[] options;
		public final boolean[] on;

		public Multi(String name, String[] options, boolean[] defaults) {
			super(name);
			this.options = options;
			this.on = defaults.clone();
		}

		public boolean isOn(int i) { return on[i]; }

		public int count() {
			int n = 0;
			for (boolean b : on) if (b) n++;
			return n;
		}

		@Override public void save(JsonObject out) {
			JsonObject o = new JsonObject();
			for (int i = 0; i < options.length; i++) o.addProperty(options[i], on[i]);
			out.add(name, o);
		}

		@Override public void load(JsonObject in) {
			JsonElement e = get(in, name);
			if (e == null || !e.isJsonObject()) return;
			JsonObject o = e.getAsJsonObject();
			for (int i = 0; i < options.length; i++) {
				if (o.has(options[i])) on[i] = o.get(options[i]).getAsBoolean();
			}
		}
	}

	// ------------------------------------------------------------------- Key

	public static final class Key extends Setting {
		public int key;

		public Key(String name, int defKey) {
			super(name);
			this.key = defKey;
		}

		public String label() {
			if (key <= 0) return "—";
			String n = org.lwjgl.glfw.GLFW.glfwGetKeyName(key, 0);
			if (n != null) return n.toUpperCase(java.util.Locale.ROOT);
			return switch (key) {
				case 32 -> "SPACE";
				case 256 -> "ESC";
				case 258 -> "TAB";
				case 340 -> "L-SHIFT";
				case 344 -> "R-SHIFT";
				case 341 -> "L-CTRL";
				case 345 -> "R-CTRL";
				case 342 -> "L-ALT";
				case 346 -> "R-ALT";
				case 262 -> "→";
				case 263 -> "←";
				case 264 -> "↓";
				case 265 -> "↑";
				default -> (key >= 290 && key <= 301) ? "F" + (key - 289) : "KEY " + key;
			};
		}

		public boolean isDown(long window) {
			return key > 0 && org.lwjgl.glfw.GLFW.glfwGetKey(window, key) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
		}

		@Override public void save(JsonObject out) { out.addProperty(name, key); }

		@Override public void load(JsonObject in) {
			JsonElement e = get(in, name);
			if (e != null && e.isJsonPrimitive()) key = e.getAsInt();
		}
	}

	// ------------------------------------------------------------------ Text

	public static final class Text extends Setting {
		public String value;
		public final int maxLen;

		public Text(String name, String def, int maxLen) {
			super(name);
			this.value = def;
			this.maxLen = maxLen;
		}

		@Override public void save(JsonObject out) { out.addProperty(name, value); }

		@Override public void load(JsonObject in) {
			JsonElement e = get(in, name);
			if (e != null && e.isJsonPrimitive()) value = e.getAsString();
		}
	}
}
