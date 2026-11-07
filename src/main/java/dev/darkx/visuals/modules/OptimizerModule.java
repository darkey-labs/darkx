package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;

/**
 * Оптимизатор: подкручивает графику Minecraft при включении и возвращает как было при выключении
 * (и при выходе из игры), плюс снижает FPS, когда окно не в фокусе. Работает только через обычные
 * настройки игры, никаких подмен рендера.
 */
public final class OptimizerModule extends Module {
	public final Setting.Bool light = add(new Setting.Bool("Без облаков и теней", true));
	public final Setting.Num distance = add(new Setting.Num("Макс. дальность прорисовки", 2, 32, 1, 12, " чанк."));
	public final Setting.Mode particles = add(new Setting.Mode("Частицы", 1, "Как есть", "Меньше", "Минимум"));
	public final Setting.Bool background = add(new Setting.Bool("Меньше FPS в фоне", true));
	public final Setting.Num backgroundFps = add(new Setting.Num("FPS в фоне", 5, 60, 5, 15, ""));

	private boolean applied;
	private boolean bgActive;
	private Integer oDistance, oFps;
	private CloudStatus oClouds;
	private Boolean oShadows;
	private ParticleStatus oParticles;

	public OptimizerModule() {
		super("Optimizer", "Больше FPS: без облаков и теней, лимит дальности и частиц, меньше FPS в фоне. Настройки возвращаются при выключении.", Category.MISC);
		backgroundFps.showIf(() -> background.value);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (!applied) apply(mc);

		// динамический FPS: окно не в фокусе — снижаем лимит кадров
		boolean away = background.value && !mc.isWindowActive();
		if (away && !bgActive) {
			oFps = mc.options.framerateLimit().get();
			mc.options.framerateLimit().set(Math.min(oFps, backgroundFps.i()));
			bgActive = true;
		} else if (!away && bgActive) {
			restoreFps(mc);
		}
	}

	@Override
	public void onDisable() {
		restore(Minecraft.getInstance());
	}

	private void apply(Minecraft mc) {
		applied = true;
		var o = mc.options;
		int cap = distance.i();
		if (o.renderDistance().get() > cap) {
			oDistance = o.renderDistance().get();
			o.renderDistance().set(cap);
		}
		if (light.value) {
			oClouds = o.cloudStatus().get();
			oShadows = o.entityShadows().get();
			o.cloudStatus().set(CloudStatus.OFF);
			o.entityShadows().set(false);
		}
		if (!particles.is("Как есть")) {
			oParticles = o.particles().get();
			o.particles().set(particles.is("Минимум") ? ParticleStatus.MINIMAL : ParticleStatus.DECREASED);
		}
	}

	private void restoreFps(Minecraft mc) {
		bgActive = false;
		if (oFps != null) mc.options.framerateLimit().set(oFps);
		oFps = null;
	}

	/** Возвращает все настройки игры, которые мы меняли. Вызывается и при выходе из игры. */
	public void restore(Minecraft mc) {
		if (mc == null || mc.options == null) return;
		if (bgActive) restoreFps(mc);
		if (!applied) return;
		applied = false;
		var o = mc.options;
		if (oDistance != null) o.renderDistance().set(oDistance);
		if (oClouds != null) o.cloudStatus().set(oClouds);
		if (oShadows != null) o.entityShadows().set(oShadows);
		if (oParticles != null) o.particles().set(oParticles);
		oDistance = null;
		oClouds = null;
		oShadows = null;
		oParticles = null;
	}
}
