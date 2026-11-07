package dev.darkx.visuals.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Setting;
import net.minecraft.world.InteractionHand;

/** Свои анимации взмаха и настраиваемая скорость удара. */
public final class SwingAnimationModule extends Module {
	/** Строки: стиль (индекс), длительность, амплитуда. */
	private static final String[] PRESET_NAMES = {"Свой", "Быстрый", "Тяжёлый", "Вихрь", "Укол", "Плавный", "Лёгкий"};
	private static final double[][] PRESETS = {
		{0, 6, 1.0},   // Slash, быстро
		{1, 12, 1.4},  // Smash, тяжело
		{2, 10, 1.0},  // Spin
		{3, 7, 1.2},   // Stab
		{4, 14, 0.8},  // Down, плавно
		{0, 9, 0.7}    // Slash, лёгкий
	};

	public final Setting.Mode preset = add(new Setting.Mode("Пресет", 0, PRESET_NAMES));
	public final Setting.Mode style = add(new Setting.Mode("Анимация", 0, "Slash", "Smash", "Spin", "Stab", "Down"));
	public final Setting.Num ticks = add(new Setting.Num("Длительность", 2, 20, 1, 9, " тик."));
	public final Setting.Num power = add(new Setting.Num("Амплитуда", 0.5, 2, 0.1, 1, "x"));
	public final Setting.Bool mainOnly = add(new Setting.Bool("Только основная рука", true));

	private int applied = -1;
	private double[] snap;

	public SwingAnimationModule() {
		super("SwingAnimation", "Своя анимация и скорость взмаха рукой. Есть готовые пресеты.", Category.VIEW);
	}

	private double[] values() {
		return new double[] {style.index, ticks.value, power.value};
	}

	/** Применяет выбранный пресет; если потом поменять любое значение руками, пресет превращается в «Свой». */
	@Override public void onAlways(net.minecraft.client.Minecraft mc) {
		if (applied < 0) {
			applied = preset.index;
			snap = values();
			return;
		}
		if (preset.index != applied) {
			applied = preset.index;
			if (applied > 0) {
				double[] p = PRESETS[applied - 1];
				style.index = (int) p[0];
				ticks.set(p[1]);
				power.set(p[2]);
			}
			snap = values();
			dev.darkx.visuals.core.Config.dirty = true;
			return;
		}
		if (applied != 0 && !java.util.Arrays.equals(snap, values())) {
			preset.index = 0;
			applied = 0;
			dev.darkx.visuals.core.Config.dirty = true;
		}
	}

	/** Для LivingEntityMixin: длительность взмаха в тиках или null, если модуль выключен. */
	public static Integer swingDuration() {
		SwingAnimationModule m = ModuleManager.get(SwingAnimationModule.class);
		return (m != null && m.enabled) ? m.ticks.i() : null;
	}

	/** Вызывается из ItemInHandRendererMixin после ViewModel. */
	public static void apply(PoseStack ps, InteractionHand hand, float swing) {
		SwingAnimationModule m = ModuleManager.get(SwingAnimationModule.class);
		if (m == null || !m.enabled || swing <= 0f) return;
		if (m.mainOnly.value && hand != InteractionHand.MAIN_HAND) return;

		float k = m.power.f();
		float e = (float) Math.sin(swing * Math.PI) * k;
		switch (m.style.get()) {
			case "Slash" -> {
				ps.translate(-0.10f * e, 0.05f * e, 0f);
				ps.mulPose(Axis.ZP.rotationDegrees(-55f * e));
				ps.mulPose(Axis.XP.rotationDegrees(22f * e));
			}
			case "Smash" -> {
				ps.translate(0f, -0.16f * e, -0.12f * e);
				ps.mulPose(Axis.XP.rotationDegrees(-60f * e));
			}
			case "Spin" -> {
				ps.translate(0.4f, -0.4f, -0.7f);
				ps.mulPose(Axis.ZP.rotationDegrees(360f * swing * k));
				ps.translate(-0.4f, 0.4f, 0.7f);
			}
			case "Stab" -> {
				ps.translate(0f, 0f, -0.55f * e);
				ps.mulPose(Axis.XP.rotationDegrees(-15f * e));
			}
			default -> {
				ps.translate(0f, -0.30f * e, 0f);
				ps.mulPose(Axis.XP.rotationDegrees(-20f * e));
			}
		}
	}
}
