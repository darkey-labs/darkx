package dev.darkx.visuals.hud;

import com.google.gson.JsonObject;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Notifications;
import dev.darkx.visuals.core.Stats;
import dev.darkx.visuals.core.Theme;
import dev.darkx.visuals.modules.HudModule;
import dev.darkx.visuals.modules.NotificationsModule;
import dev.darkx.visuals.ui.ClickGuiScreen;
import dev.darkx.visuals.ui.HudEditorScreen;
import dev.darkx.visuals.ui.Motion;
import dev.darkx.visuals.ui.UI;
import dev.darkx.visuals.ui.UI.F;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class HudManager {
	public static final List<HudWidget> WIDGETS = List.of(
		new Widgets.Target(),
		new Widgets.Profile(),
		new Widgets.Potion(),
		new Widgets.Armor(),
		new Widgets.Coords(),
		new Widgets.Keystrokes(),
		new Widgets.Speed(),
		new Widgets.Clock(),
		new Widgets.Items4(),
		new Widgets.Cps(),
		new Widgets.ModuleList(),
		new Widgets.Near(),
		new Widgets.Reach(),
		new Widgets.Pins(),
		new Widgets.Cooldowns(),
		new Widgets.Totems()
	);

	private HudManager() {}

	public static String[] titles() {
		String[] t = new String[WIDGETS.size()];
		for (int i = 0; i < t.length; i++) t[i] = WIDGETS.get(i).title;
		return t;
	}

	/** Какие худы включены по умолчанию (Таргет, Профиль, Поушн, Армор). */
	public static boolean[] defaults() {
		boolean[] d = new boolean[WIDGETS.size()];
		for (int i = 0; i < 4 && i < d.length; i++) d[i] = true;
		return d;
	}

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("darkx", "hud"), HudManager::render);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("darkx", "markers"), MarkerRenderer::render);
	}

	// ---- настройки модуля HUD

	public static int background() {
		HudModule m = ModuleManager.get(HudModule.class);
		float a = m == null ? 0.75f : m.opacity.f() / 100f;
		return ((int) (a * 255) << 24);
	}

	/** Непрозрачность фона худов 0..1 (настройка модуля HUD). */
	public static float opacity() {
		HudModule m = ModuleManager.get(HudModule.class);
		return m == null ? 0.75f : m.opacity.f() / 100f;
	}

	/** Плашка худа в текущем стиле оформления: OLED — чёрная, «Серый» — графитовая. */
	public static void drawPanel(GuiGraphics g, int x, int y, int w, int h, int r) {
		if (Theme.gray()) UI.gray(g, x, y, w, h, r);
		else UI.rrect(g, x, y, w, h, r, background());
	}

	public static boolean isShown(HudWidget w) {
		HudModule m = ModuleManager.get(HudModule.class);
		int i = WIDGETS.indexOf(w);
		return m != null && i >= 0 && m.show.isOn(i);
	}

	public static void ensurePlaced(HudWidget w, int sw, int sh) {
		if (!w.placed) {
			w.place(sw, sh);
			w.placed = true;
		}
	}

	public static void clamp(HudWidget w, int sw, int sh) {
		w.x = Math.max(0, Math.min(Math.max(0, sw - Math.max(w.sw(), 8)), w.x));
		w.y = Math.max(0, Math.min(Math.max(0, sh - Math.max(w.sh(), 8)), w.y));
	}

	// ---- отрисовка

	private static void render(GuiGraphics g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		Stats.pollClicks(mc);
		if (mc.screen instanceof ClickGuiScreen || mc.screen instanceof HudEditorScreen) return;

		UI.alpha = 1f;
		int sw = g.guiWidth(), sh = g.guiHeight();

		HudModule hud = ModuleManager.get(HudModule.class);
		for (int i = 0; i < WIDGETS.size(); i++) {
			HudWidget w = WIDGETS.get(i);
			boolean on = hud != null && hud.enabled && hud.show.isOn(i);
			if (w.selfAnimated()) {
				if (!on) continue;
				ensurePlaced(w, sw, sh);
				clamp(w, sw, sh);
				w.draw(g, false);
				continue;
			}
			// остальные худы: «поп» при включении, сжатие при выключении
			float p = Motion.pop("hud.pop." + w.id, on);
			if (p <= 0.004f) continue;
			ensurePlaced(w, sw, sh);
			clamp(w, sw, sh);
			float prev = UI.alpha;
			UI.alpha = prev * Motion.clamp01(p * 1.5f);
			Motion.scaleAround(g, w.x + w.sw() / 2f, w.y + w.sh() / 2f, 0.82f + 0.18f * p);
			w.draw(g, false);
			g.pose().popMatrix();
			UI.alpha = prev;
		}
		renderToasts(g, sw, sh);
	}

	private static void renderToasts(GuiGraphics g, int sw, int sh) {
		NotificationsModule nm = ModuleManager.get(NotificationsModule.class);
		if (nm == null || !nm.enabled || Notifications.TOASTS.isEmpty()) return;
		long life = (long) (nm.seconds.f() * 1000f);
		Notifications.prune(life);
		long now = System.currentTimeMillis();
		int tw = 150, th = 34, gap = 6;
		int y = sh - 20 - th;
		for (int i = Notifications.TOASTS.size() - 1; i >= 0; i--) {
			Notifications.Toast t = Notifications.TOASTS.get(i);
			long age = now - t.born;
			float in = Motion.outBack(age / 380f);
			float outT = age > life - 320 ? Motion.inCubic((age - (life - 320)) / 320f) : 0f;
			float prev = UI.alpha;
			UI.alpha = prev * Motion.clamp01(age / 150f) * (1f - outT);
			float yy = UI.anim(t, y, 14f); // плавное перестроение стопки
			int x = sw - 12 - tw + Math.round((1f - in) * 70f) + Math.round(outT * 50f);
			int ty = Math.round(yy);
			if (Theme.gray()) UI.gray(g, x, ty, tw, th, Theme.hudR(7));
			else UI.rrect(g, x, ty, tw, th, Theme.hudR(7), 0xE6000000);
			UI.rrect(g, x + 1, ty + 7, 2, th - 14, 1, t.color);
			UI.text(g, F.BOLD, t.title, x + 14, ty + 12, UI.TEXT);
			UI.text(g, F.SMALL, t.text, x + 14, ty + 25, t.color == Theme.accent() ? UI.TEXT_DIM : UI.MUTED);
			UI.alpha = prev;
			y -= th + gap;
		}
	}

	// ---- конфиг

	public static void save(JsonObject out) {
		for (HudWidget w : WIDGETS) {
			if (!w.placed) continue;
			JsonObject o = new JsonObject();
			o.addProperty("x", w.x);
			o.addProperty("y", w.y);
			if (Math.abs(w.scale - 1f) > 0.001f) o.addProperty("scale", w.scale);
			out.add(w.id, o);
		}
	}

	public static void load(JsonObject in) {
		for (HudWidget w : WIDGETS) {
			if (!in.has(w.id)) continue;
			JsonObject o = in.getAsJsonObject(w.id);
			w.x = o.get("x").getAsInt();
			w.y = o.get("y").getAsInt();
			if (o.has("scale")) w.scale = Math.max(HudWidget.MIN_SCALE, Math.min(HudWidget.MAX_SCALE, o.get("scale").getAsFloat()));
			w.placed = true;
		}
	}
}
