package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Клиентский ночник: локальный эффект ночного зрения без иконки и частиц. */
public final class FullBrightModule extends Module {
	private boolean added;

	public FullBrightModule() {
		super("FullBright", "Максимальная яркость: видно в темноте без зелий.", Category.VISUAL);
	}

	@Override public void onTick(Minecraft mc) {
		if (mc.player == null) return;
		if (!mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
			mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, false, false, false));
			added = true;
		}
	}

	@Override public void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (added && mc.player != null) mc.player.removeEffect(MobEffects.NIGHT_VISION);
		added = false;
	}
}
