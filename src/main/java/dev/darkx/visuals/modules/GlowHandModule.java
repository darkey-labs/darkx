package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Свечение руки: рука и предмет всегда на полной яркости, а в углу экрана, где рука, горит мягкая аура
 * выбранного цвета. С GlassHand цвет получает и сама рука (стекло светится этим цветом).
 */
public final class GlowHandModule extends Module {
	public final Setting.Color color = add(new Setting.Color("Цвет", 0));
	public final Setting.Num strength = add(new Setting.Num("Сила свечения", 10, 100, 5, 60, "%"));
	public final Setting.Bool fullBright = add(new Setting.Bool("Полная яркость руки", true));
	public final Setting.Bool aura = add(new Setting.Bool("Аура на экране", true));

	public GlowHandModule() {
		super("GlowHand", "Рука и предмет светятся выбранным цветом или цветом темы.", Category.VIEW);
	}

	/** Аура рисуется под всем худом (худ — поверх руки, поэтому она выглядит как отсвет на экране). */
	public static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.MISC_OVERLAYS, orig -> (g, tracker) -> {
			GlowHandModule m = ModuleManager.get(GlowHandModule.class);
			if (m != null && m.enabled && m.aura.value) m.drawAura(g);
			orig.render(g, tracker);
		});
	}

	private void drawAura(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !mc.options.getCameraType().isFirstPerson() || mc.player.isSpectator()) return;

		int sw = g.guiWidth(), sh = g.guiHeight();
		boolean right = mc.player.getMainArm() == HumanoidArm.RIGHT;
		float pulse = 0.88f + 0.12f * (float) Math.sin(System.currentTimeMillis() / 700.0);
		float k = strength.f() / 100f * pulse;
		int rgb = color.color(0) & 0xFFFFFF;

		int w = Math.round(sw * 0.46f);
		int top = Math.round(sh * 0.52f);
		int hx = right ? Math.round(sw * 0.80f) : Math.round(sw * 0.20f);
		int x0 = Math.max(0, hx - w / 2), x1 = Math.min(sw, hx + w / 2);
		final int step = 12;
		for (int x = x0; x < x1; x += step) {
			float d = ((x + step / 2f) - hx) / (w / 2f);
			float bell = (float) Math.exp(-d * d * 2.2);
			int a = Math.round(255f * 0.42f * k * bell);
			if (a < 1) continue;
			g.fillGradient(x, top, Math.min(x1, x + step), sh, rgb, (a << 24) | rgb);
		}
	}
}
