package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;

/**
 * Рука от первого лица теряет текстуру скина и становится матовым стеклом.
 * Саму подмену делает {@code AvatarRendererMixin} через {@link dev.darkx.visuals.fx.HandFx}.
 */
public final class GlassHandModule extends Module {
	public final Setting.Num opacity = add(new Setting.Num("Прозрачность", 15, 95, 5, 55, "%"));
	public final Setting.Color tint = add(new Setting.Color("Оттенок", 2));

	public GlassHandModule() {
		super("GlassHand", "Рука от первого лица — полупрозрачное стекло без текстуры скина.", Category.VIEW);
	}
}
