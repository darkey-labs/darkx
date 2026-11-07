package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.DarkCapes;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import net.minecraft.client.Minecraft;

/** Плащи DarkX: выбираешь плащ, его видят все игроки с DarkX на сервере (и ты видишь их плащи). */
public final class CapesModule extends Module {
	private static CapesModule instance;

	public final Setting.Mode cape = add(new Setting.Mode("Плащ", 1,
		"Нет", DarkCapes.NAMES[0], DarkCapes.NAMES[1], DarkCapes.NAMES[2], DarkCapes.NAMES[3], DarkCapes.NAMES[4]));
	public final Setting.Bool others = add(new Setting.Bool("Плащи других DarkX", true));

	/** Что мы последним отправили в базу; "?" — ещё ничего. */
	private String published = "?";

	public CapesModule() {
		super("Capes", "Плащи DarkX: твой плащ видят другие игроки с DarkX Visuals на сервере.", Category.MISC);
		instance = this;
	}

	public static boolean active() {
		return instance != null && instance.enabled;
	}

	public static boolean showOthers() {
		return instance != null && instance.others.value;
	}

	/** id выбранного готового плаща или null, если «Нет». */
	public static String ownId() {
		if (instance == null || instance.cape.index <= 0) return null;
		return DarkCapes.IDS[instance.cape.index - 1];
	}

	@Override
	public void onTick(Minecraft mc) {
		String id = ownId();
		String want = id == null ? "" : id;
		if (!want.equals(published)) {
			published = want;
			DarkCapes.publish(id);
		}
	}

	@Override
	public void onDisable() {
		published = "?";
	}
}
