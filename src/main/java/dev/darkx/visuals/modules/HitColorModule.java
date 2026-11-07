package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.fx.OverlayHolder;
import net.minecraft.client.Minecraft;

/** Цвет и прозрачность красной вспышки при уроне (на существах и их броне). */
public final class HitColorModule extends Module {
	/** Ванильная вспышка: красный, альфа 178. */
	private static final int VANILLA = 0xB2FF0000;

	public final Setting.Color color = add(new Setting.Color("Цвет", 0));
	public final Setting.Num opacity = add(new Setting.Num("Прозрачность", 10, 100, 5, 70, "%"));

	private int lastArgb;
	private boolean hasLast;
	private Object lastInstance;

	public HitColorModule() {
		super("HitColor", "Свой цвет вспышки при получении урона.", Category.VISUAL);
	}

	private int argb() {
		int a = Math.round(opacity.f() / 100f * 255f);
		return (a << 24) | (color.color(0) & 0xFFFFFF);
	}

	@Override public void onTick(Minecraft mc) {
		if (!OverlayHolder.ready()) return;
		int v = argb();
		if (!hasLast || v != lastArgb || lastInstance != OverlayHolder.instance) {
			OverlayHolder.apply(v);
			lastArgb = v;
			hasLast = true;
			lastInstance = OverlayHolder.instance;
		}
	}

	@Override public void onDisable() {
		OverlayHolder.apply(VANILLA);
		hasLast = false;
	}
}
