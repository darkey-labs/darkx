package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Config;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Отключает неприятные визуальные эффекты. Меняет обычные настройки игры,
 * исходные значения запоминаются (в конфиге мода) и возвращаются при выключении.
 */
public final class NoRenderModule extends Module {
	private static final String[] OPTS = {"Покачивание камеры", "Тряска при уроне", "Искажение экрана", "Изменение FOV"};

	public final Setting.Multi what = add(new Setting.Multi("Убрать", OPTS, new boolean[]{true, true, false, false}));

	// скрытые поля: исходные значения (-1 = не сохранено)
	private final Setting.Num[] saved = new Setting.Num[4];

	public NoRenderModule() {
		super("NoRender", "Убирает тряску, покачивание и искажения экрана.", Category.VIEW);
		for (int i = 0; i < saved.length; i++) {
			saved[i] = add(new Setting.Num("_orig" + i, -1, 1, 0, -1, ""));
			saved[i].showIf(() -> false);
		}
	}

	private static double read(Options o, int i) {
		return switch (i) {
			case 0 -> o.bobView().get() ? 1.0 : 0.0;
			case 1 -> o.damageTiltStrength().get();
			case 2 -> o.screenEffectScale().get();
			default -> o.fovEffectScale().get();
		};
	}

	private static void write(Options o, int i, double v) {
		switch (i) {
			case 0 -> o.bobView().set(v >= 0.5);
			case 1 -> o.damageTiltStrength().set(v);
			case 2 -> o.screenEffectScale().set(v);
			default -> o.fovEffectScale().set(v);
		}
	}

	private void restore(Options o, int i) {
		if (saved[i].value < 0) return;
		write(o, i, saved[i].value);
		saved[i].set(-1);
		Config.dirty = true;
	}

	@Override public void onTick(Minecraft mc) {
		Options o = mc.options;
		for (int i = 0; i < OPTS.length; i++) {
			if (what.isOn(i)) {
				if (saved[i].value < 0) {
					saved[i].set(read(o, i));
					Config.dirty = true;
				}
				write(o, i, 0.0);
			} else {
				restore(o, i);
			}
		}
	}

	@Override public void onDisable() {
		Options o = Minecraft.getInstance().options;
		for (int i = 0; i < OPTS.length; i++) restore(o, i);
	}
}
