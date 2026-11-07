package dev.darkx.visuals.core;

import java.util.List;

/** Темы клиента: меняют акцентный цвет GUI, HUD и визуалов. Фон всегда чисто чёрный (OLED). */
public final class Theme {
	public record Preset(String name, int color) {}

	public static final List<Preset> PRESETS = List.of(
		new Preset("Orange", 0xFFF97316),
		new Preset("Phantom", 0xFF8B5CF6),
		new Preset("Crimson", 0xFFEF4444),
		new Preset("Ocean", 0xFF38BDF8),
		new Preset("Emerald", 0xFF34D399),
		new Preset("Amber", 0xFFF59E0B),
		new Preset("Rose", 0xFFF472B6),
		new Preset("Ash", 0xFFD4D4D4)
	);

	/** Индекс выбранной темы; PRESETS.size() = «Свой оттенок». */
	public static int index = 0;
	public static float hue = 0.62f;

	private Theme() {}

	public static int customIndex() { return PRESETS.size(); }

	public static int accent() {
		if (index >= PRESETS.size()) return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.65f, 1f) & 0xFFFFFF);
		return PRESETS.get(index).color();
	}

	public static int accent(float alpha) {
		return withAlpha(accent(), alpha);
	}

	/** Непрозрачный тёмный оттенок акцента: t = доля акцента поверх чёрного. Для заливок кнопок. */
	public static int tint(float t) {
		return mix(0xFF000000, 0xFF000000 | (accent() & 0xFFFFFF), t);
	}

	public static int withAlpha(int rgb, float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	public static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, aa = a >>> 24;
		int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255, ba = b >>> 24;
		return ((int) (aa + (ba - aa) * t) << 24)
			| ((int) (ar + (br - ar) * t) << 16)
			| ((int) (ag + (bg - ag) * t) << 8)
			| (int) (ab + (bb - ab) * t);
	}

	// ---- стиль оформления

	public enum Style {
		OLED("OLED", "Чёрные плашки без прозрачности"),
		GRAY("Серый", "Плашки графитово-серого цвета без прозрачности — лёгкие для FPS");

		public final String title, hint;

		Style(String title, String hint) {
			this.title = title;
			this.hint = hint;
		}
	}

	/** Стиль худов (BetterGUI имеет свой, отдельный). */
	public static Style style = Style.OLED;

	public static boolean gray() {
		return style == Style.GRAY;
	}

	// ---- скругления (множители 0..2, 1 = как раньше)

	public static final float RADIUS_MAX = 2f;
	/** Главное окно, диалоги. */
	public static float menuK = 1f;
	/** Карточки, кнопки, строки, поля внутри меню. */
	public static float elemK = 1f;
	/** Худы. */
	public static float hudK = 1f;

	private static int rad(int base, float k) {
		return Math.max(0, Math.round(base * k));
	}

	public static int menuR(int base) { return rad(base, menuK); }
	public static int elemR(int base) { return rad(base, elemK); }
	public static int hudR(int base) { return rad(base, hudK); }

	public static float clampK(float k) {
		return Math.max(0f, Math.min(RADIUS_MAX, Math.round(k * 20f) / 20f));
	}

	public static void resetRadius() {
		menuK = 1f;
		elemK = 1f;
		hudK = 1f;
	}
}
