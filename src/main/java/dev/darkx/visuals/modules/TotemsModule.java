package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.core.TotemPops;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Тотемы: уведомления о сработавших тотемах (твоих и чужих) и счётчик. Сам худ «Тотемы» включается в модуле HUD. */
public final class TotemsModule extends Module {
	public final Setting.Bool self = add(new Setting.Bool("Уведомлять о своём тотеме", true));
	public final Setting.Bool others = add(new Setting.Bool("Считать тотемы других игроков", true));

	private ClientLevel lastLevel;

	public TotemsModule() {
		super("Totems", "Уведомления и счётчик сработавших тотемов. Худ «Тотемы» — в модуле HUD.", Category.VISUAL);
	}

	@Override
	public void onTick(Minecraft mc) {
		// новый мир — счётчики с нуля
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			TotemPops.clear();
		}
	}
}
