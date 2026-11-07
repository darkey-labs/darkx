package dev.darkx.visuals.ui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.Map;

/**
 * Анимации поверх {@link UI}: кривые easing и пружины с естественным «перелётом».
 * Пружина хранит своё состояние по ключу и интегрируется по реальному времени, поэтому
 * не зависит от FPS.
 */
public final class Motion {
	private Motion() {}

	// ================================================================= кривые

	public static float clamp01(float t) {
		return t < 0f ? 0f : Math.min(t, 1f);
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	public static float outCubic(float t) {
		float u = 1f - clamp01(t);
		return 1f - u * u * u;
	}

	public static float inCubic(float t) {
		t = clamp01(t);
		return t * t * t;
	}

	public static float inOutCubic(float t) {
		t = clamp01(t);
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
	}

	/** Быстрый старт, небольшой перелёт и возврат (как «поп» у iOS). */
	public static float outBack(float t) {
		t = clamp01(t);
		float c1 = 1.35f, c3 = c1 + 1f;
		float u = t - 1f;
		return 1f + c3 * u * u * u + c1 * u * u;
	}

	/** Прогресс отрезка [delay, delay + dur] для времени age (в секундах). */
	public static float seg(float age, float delay, float dur) {
		return clamp01((age - delay) / dur);
	}

	// ================================================================ пружины

	private static final class Spring {
		float x, v;
		long t = System.nanoTime();

		Spring(float x) { this.x = x; }
	}

	private static final Map<Object, Spring> SPRINGS = new HashMap<>();

	/**
	 * Пружина к target. Если ключ новый — стартует с from.
	 * k — жёсткость (~120..260), d — затухание; d = 2*sqrt(k) — без перелёта, меньше — с «пружинкой».
	 */
	public static float spring(Object key, float target, float from, float k, float d) {
		Spring s = SPRINGS.computeIfAbsent(key, kk -> new Spring(from));
		long now = System.nanoTime();
		float dt = Math.min(0.05f, (now - s.t) / 1.0e9f);
		s.t = now;
		int steps = 4;
		float h = dt / steps;
		for (int i = 0; i < steps; i++) {
			float a = k * (target - s.x) - d * s.v;
			s.v += a * h;
			s.x += s.v * h;
		}
		if (Math.abs(target - s.x) < 0.0008f && Math.abs(s.v) < 0.01f) {
			s.x = target;
			s.v = 0f;
		}
		return s.x;
	}

	/** «Поп»: появление с лёгким перелётом, исчезновение — плавное, без ухода в минус. */
	public static float pop(Object key, boolean present) {
		float target = present ? 1f : 0f;
		float k = present ? 190f : 170f;
		float d = present ? 17f : 2f * (float) Math.sqrt(k);
		float v = spring(key, target, 0f, k, d);
		return present ? v : Math.max(0f, v);
	}

	public static void forget(Object key) {
		SPRINGS.remove(key);
	}

	// ============================================================== трансформ

	/** Масштаб вокруг точки (cx, cy). Закрывать через {@code g.pose().popMatrix()}. */
	public static void scaleAround(GuiGraphics g, float cx, float cy, float s) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(s, s);
		pose.translate(-cx, -cy);
	}
}
