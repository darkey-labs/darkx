package dev.darkx.visuals.ui;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Window;
import com.mojang.serialization.JsonOps;
import dev.darkx.visuals.core.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Рисовалки для всего интерфейса: шрифты Montserrat/Lucide, скруглённые прямоугольники,
 * плавные анимации. Все цвета проходят через {@link #alpha} (общая прозрачность кадра).
 */
public final class UI {
	// ---- палитра (OLED: фон чисто чёрный, текст грязно-белый, без #FFFFFF)
	public static final int BG = 0xFF000000;
	public static final int SURFACE = 0xFF0B0B0B;
	public static final int SURFACE_HI = 0xFF131313;
	public static final int SURFACE_TOP = 0xFF1B1B1B;
	public static final int LINE = 0x1FE4E4E2;
	public static final int TEXT = 0xFFE4E4E2;
	public static final int TEXT_DIM = 0xFF9A9A98;
	public static final int MUTED = 0xFF5C5C5A;

	/** Шрифт + смещение центра заглавных букв от верха строки (для вертикального выравнивания). */
	public enum F {
		BODY("mont", 3.85f),
		SMALL("mont_s", 4.55f),
		BOLD("mont_b", 3.85f),
		TITLE("mont_l", 2.8f),
		ICON("icons", 1.5f),
		ICON_S("icons_s", 3.0f);

		final String id;
		final float capCenter;
		Style style;

		F(String id, float capCenter) {
			this.id = id;
			this.capCenter = capCenter;
		}
	}

	/** Общая прозрачность текущего кадра (0..1). */
	public static float alpha = 1f;

	private static final Map<String, Component> CACHE = new HashMap<>();

	private UI() {}

	// ================================================================ цвет

	public static int c(int argb) {
		int a = (int) ((argb >>> 24) * alpha);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static int withAlpha(int argb, float mul) {
		int a = (int) ((argb >>> 24) * Math.max(0f, Math.min(1f, mul)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static float ease(float t) {
		t = Math.max(0f, Math.min(1f, t));
		float u = 1f - t;
		return 1f - u * u * u;
	}

	// ============================================================== анимации

	private static final class Anim {
		float v;
		long t = System.nanoTime();

		Anim(float v) { this.v = v; }
	}

	private static final Map<Object, Anim> ANIMS = new HashMap<>();

	/** Плавно приближает значение к target. Первый вызов сразу ставит target. */
	public static float anim(Object key, float target, float speed) {
		Anim a = ANIMS.computeIfAbsent(key, k -> new Anim(target));
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - a.t) / 1.0e9f);
		a.t = now;
		a.v += (target - a.v) * (1f - (float) Math.exp(-dt * speed));
		if (Math.abs(target - a.v) < 0.0005f) a.v = target;
		return a.v;
	}

	public static void reset(Object key, float value) {
		ANIMS.put(key, new Anim(value));
	}

	public static void forget(Object key) {
		ANIMS.remove(key);
	}

	// ================================================================== текст

	private static Style styleOf(F f) {
		if (f.style == null) {
			JsonObject o = new JsonObject();
			o.addProperty("text", "");
			o.addProperty("font", "darkx:" + f.id);
			f.style = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, o).result().map(Component::getStyle).orElse(Style.EMPTY);
		}
		return f.style;
	}

	public static Component comp(F f, String s) {
		String key = f.ordinal() + s;
		Component c = CACHE.get(key);
		if (c == null) {
			if (CACHE.size() > 4000) CACHE.clear();
			c = Component.literal(s).setStyle(styleOf(f));
			CACHE.put(key, c);
		}
		return c;
	}

	/** Оборачивает готовый компонент (ники, строки скорборда) в шрифт Montserrat — цвета и стили внутри сохраняются. */
	public static Component withFont(F f, Component c) {
		return Component.empty().setStyle(styleOf(f)).append(c);
	}

	/** Как {@link #comp(GuiGraphics, F, Component, int, float, int)}, но компонент уже обёрнут в шрифт. */
	public static void raw(GuiGraphics g, F f, Component shown, int x, float cy, int color) {
		color = c(color);
		if ((color >>> 24) < 6) return;
		g.drawString(Minecraft.getInstance().font, shown, x, Math.round(cy - f.capCenter), color, false);
	}

	/** Рисует готовый компонент: x — левый край, cy — вертикальный центр. Возвращает ширину. */
	public static int comp(GuiGraphics g, F f, Component cmp, int x, float cy, int color) {
		Component shown = withFont(f, cmp);
		int w = Minecraft.getInstance().font.width(shown);
		color = c(color);
		if ((color >>> 24) < 6) return w;
		g.drawString(Minecraft.getInstance().font, shown, x, Math.round(cy - f.capCenter), color, false);
		return w;
	}

	public static int width(F f, String s) {
		return Minecraft.getInstance().font.width(comp(f, s));
	}

	/** Рисует текст; x — левый край, cy — вертикальный центр. Возвращает ширину. */
	public static int text(GuiGraphics g, F f, String s, int x, float cy, int color) {
		Component comp = comp(f, s);
		int w = Minecraft.getInstance().font.width(comp);
		color = c(color);
		if ((color >>> 24) < 6) return w;
		g.drawString(Minecraft.getInstance().font, comp, x, Math.round(cy - f.capCenter), color, false);
		return w;
	}

	public static void textR(GuiGraphics g, F f, String s, int right, float cy, int color) {
		text(g, f, s, right - width(f, s), cy, color);
	}

	public static void textC(GuiGraphics g, F f, String s, float cx, float cy, int color) {
		text(g, f, s, Math.round(cx - width(f, s) / 2f), cy, color);
	}

	/** Обрезает строку с «…» до maxW пикселей. */
	public static String fit(F f, String s, int maxW) {
		if (width(f, s) <= maxW) return s;
		String ell = "…";
		int lo = 0, hi = s.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (width(f, s.substring(0, mid) + ell) <= maxW) lo = mid;
			else hi = mid - 1;
		}
		return s.substring(0, lo).stripTrailing() + ell;
	}

	// ================================================================== фигуры

	public static void fill(GuiGraphics g, int x, int y, int w, int h, int color) {
		color = c(color);
		if ((color >>> 24) == 0 || w <= 0 || h <= 0) return;
		g.fill(x, y, x + w, y + h, color);
	}

	private static int inset(int R, int i) {
		double dy = R - i - 0.5;
		return (int) Math.round(R - Math.sqrt(R * (double) R - dy * dy));
	}

	/** Скруглённый прямоугольник (рисуется в 2x разрешении для гладких углов). */
	public static void rrect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
		color = c(color);
		if ((color >>> 24) == 0 || w <= 0 || h <= 0) return;
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 1) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		var pose = g.pose();
		pose.pushMatrix();
		pose.scale(0.5f, 0.5f);
		int X = x * 2, Y = y * 2, W = w * 2, H = h * 2, R = r * 2;
		g.fill(X, Y + R, X + W, Y + H - R, color);
		int start = 0, prev = inset(R, 0);
		for (int i = 1; i <= R; i++) {
			int cur = i < R ? inset(R, i) : -1;
			if (cur != prev) {
				g.fill(X + prev, Y + start, X + W - prev, Y + i, color);
				g.fill(X + prev, Y + H - i, X + W - prev, Y + H - start, color);
				start = i;
				prev = cur;
			}
		}
		pose.popMatrix();
	}

	/** Прямоугольник с рамкой 1px. fill должен быть непрозрачным. */
	public static void rrectBorder(GuiGraphics g, int x, int y, int w, int h, int r, int fill, int border) {
		rrect(g, x, y, w, h, r, border);
		rrect(g, x + 1, y + 1, w - 2, h - 2, Math.max(1, r - 1), fill);
	}

	/** Серый цвет плашек стиля «Серый» — графит без синевы. */
	public static final int GRAY = 0xFF1F1F21;

	/** Серая плашка: один непрозрачный скруглённый прямоугольник — стоит столько же, сколько OLED-плашка. */
	public static void gray(GuiGraphics g, int x, int y, int w, int h, int r) {
		rrect(g, x, y, w, h, r, GRAY);
	}

	/** Мягкое свечение позади скруглённого прямоугольника (слоями, от внешнего к внутреннему). k — сила 0..1. */
	public static void glow(GuiGraphics g, int x, int y, int w, int h, int r, int color, float k) {
		k = Math.max(0f, Math.min(1f, k));
		if (k < 0.01f) return;
		final int n = 7;
		for (int i = n; i >= 1; i--) {
			float t = i / (float) n;
			int sp = i * 2;
			int a = Math.round(255f * k * 0.07f * (1.15f - t * 0.55f));
			if (a < 1) continue;
			rrect(g, x - sp, y - sp, w + sp * 2, h + sp * 2, r + sp, (a << 24) | (color & 0xFFFFFF));
		}
	}

	/**
	 * Залитая акцентом кнопка со свечением (текст на ней рисуется чёрным).
	 * hv — наведение 0..1, k — сила свечения.
	 */
	public static void solid(GuiGraphics g, int x, int y, int w, int h, int r, float hv, float k) {
		int ac = Theme.accent();
		glow(g, x, y, w, h, r, ac, k * (0.75f + 0.25f * hv));
		rrect(g, x, y, w, h, r, Theme.mix(ac, TEXT, 0.18f * hv));
	}

	public static void circle(GuiGraphics g, int cx, int cy, int d, int color) {
		rrect(g, cx - d / 2, cy - d / 2, d, d, d / 2, color);
	}

	/** Голова игрока со скруглением; углы закрашиваются цветом фона bg. */
	public static void head(GuiGraphics g, PlayerSkin skin, int x, int y, int size, int radius, int bg) {
		PlayerFaceRenderer.draw(g, skin, x, y, size, c(0xFFFFFFFF));
		bg = c(bg);
		radius = Math.min(radius, size / 2);
		if (radius <= 0 || (bg >>> 24) == 0) return;
		var pose = g.pose();
		pose.pushMatrix();
		pose.scale(0.5f, 0.5f);
		int X = x * 2, Y = y * 2, S = size * 2, R = radius * 2;
		int start = 0, prev = inset(R, 0);
		for (int i = 1; i <= R; i++) {
			int cur = i < R ? inset(R, i) : -1;
			if (cur != prev) {
				if (prev > 0) {
					g.fill(X, Y + start, X + prev, Y + i, bg);
					g.fill(X + S - prev, Y + start, X + S, Y + i, bg);
					g.fill(X, Y + S - i, X + prev, Y + S - start, bg);
					g.fill(X + S - prev, Y + S - i, X + S, Y + S - start, bg);
				}
				start = i;
				prev = cur;
			}
		}
		pose.popMatrix();
	}

	/** Переключатель (26x14). */
	public static void toggle(GuiGraphics g, Object key, int x, int y, boolean on) {
		float a = anim(key, on ? 1f : 0f, 18f);
		rrect(g, x, y, 26, 14, 7, Theme.mix(0xFF222222, Theme.accent(), a));
		circle(g, x + 7 + Math.round(12 * a), y + 7, 10, Theme.mix(0xFF7C7C7A, TEXT, a));
	}

	public static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	/** Позиция курсора в «GUI»-координатах (не зависит от сигнатур событий). */
	public static int[] mouse() {
		Window w = Minecraft.getInstance().getWindow();
		double[] xs = new double[1], ys = new double[1];
		GLFW.glfwGetCursorPos(w.handle(), xs, ys);
		return new int[] {
			(int) (xs[0] * w.getGuiScaledWidth() / Math.max(1, w.getScreenWidth())),
			(int) (ys[0] * w.getGuiScaledHeight() / Math.max(1, w.getScreenHeight()))
		};
	}
}
