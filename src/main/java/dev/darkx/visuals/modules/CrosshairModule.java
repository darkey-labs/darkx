package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.core.Stats;
import dev.darkx.visuals.ui.UI;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Свой прицел. Ванильный подменяется через HudElementRegistry.replaceElement. */
public final class CrosshairModule extends Module {
	public final Setting.Mode style = add(new Setting.Mode("Стиль", 0, "Крест", "Крест с точкой", "Точка", "Квадрат"));
	public final Setting.Num size = add(new Setting.Num("Размер", 1, 14, 1, 5, " px"));
	public final Setting.Num thickness = add(new Setting.Num("Толщина", 1, 4, 1, 1, " px"));
	public final Setting.Num gap = add(new Setting.Num("Зазор", 0, 12, 1, 3, " px"));
	public final Setting.Bool outline = add(new Setting.Bool("Обводка", true));
	public final Setting.Bool dynamic = add(new Setting.Bool("Расширять в движении", true));
	public final Setting.Bool onTarget = add(new Setting.Bool("Красный на цели", true));
	public final Setting.Color color = add(new Setting.Color("Цвет", 0));

	private float spread;

	public CrosshairModule() {
		super("Crosshair", "Свой прицел: форма, размер, зазор, цвет.", Category.VISUAL);
	}

	/** Вешается один раз при старте клиента. */
	public static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (g, tracker) -> {
			CrosshairModule m = ModuleManager.get(CrosshairModule.class);
			if (m == null || !m.enabled) {
				original.render(g, tracker);
				return;
			}
			m.draw(g, tracker);
		});
	}

	private void draw(GuiGraphics g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;

		UI.alpha = 1f;
		int sw = g.guiWidth(), sh = g.guiHeight();
		int cx = (sw - 1) / 2, cy = (sh - 1) / 2;

		float target = 0f;
		if (dynamic.value) {
			target = Math.min(5f, Stats.speed * 0.6f);
			if (!mc.player.onGround()) target += 2f;
		}
		spread += (target - spread) * 0.2f;
		int sp = Math.round(spread);

		int col = color.color(0);
		if (onTarget.value && mc.crosshairPickEntity != null) col = 0xFFEF4444;

		int t = thickness.i(), s = size.i(), gp = gap.i() + sp;
		int x0 = cx - (t - 1) / 2, y0 = cy - (t - 1) / 2;
		boolean out = outline.value;

		if (style.is("Точка")) {
			rect(g, x0, y0, t, t, col, out, true);
			rect(g, x0, y0, t, t, col, out, false);
			return;
		}
		if (style.is("Квадрат")) {
			int half = s + gp;
			int x1 = x0 - half, y1 = y0 - half, d = half * 2 + t;
			for (int pass = 0; pass < 2; pass++) {
				boolean ol = pass == 0;
				rect(g, x1, y1, d, t, col, out, ol);
				rect(g, x1, y1 + d - t, d, t, col, out, ol);
				rect(g, x1, y1 + t, t, d - t * 2, col, out, ol);
				rect(g, x1 + d - t, y1 + t, t, d - t * 2, col, out, ol);
			}
			return;
		}

		for (int pass = 0; pass < 2; pass++) {
			boolean ol = pass == 0;
			rect(g, x0, y0 - gp - s, t, s, col, out, ol);
			rect(g, x0, y0 + t + gp, t, s, col, out, ol);
			rect(g, x0 - gp - s, y0, s, t, col, out, ol);
			rect(g, x0 + t + gp, y0, s, t, col, out, ol);
			if (style.is("Крест с точкой")) rect(g, x0, y0, t, t, col, out, ol);
		}
	}

	/** Первый проход рисует чёрную обводку, второй — сам цвет. */
	private static void rect(GuiGraphics g, int x, int y, int w, int h, int col, boolean outline, boolean outlinePass) {
		if (outlinePass) {
			if (outline) UI.fill(g, x - 1, y - 1, w + 2, h + 2, 0xCC000000);
		} else {
			UI.fill(g, x, y, w, h, col);
		}
	}
}
