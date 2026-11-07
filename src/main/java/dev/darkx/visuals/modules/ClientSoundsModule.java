package dev.darkx.visuals.modules;

import dev.darkx.visuals.audio.Sfx;
import dev.darkx.visuals.audio.SoundSynth;
import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import net.minecraft.client.Minecraft;

/** Звуки интерфейса клиента: мягкие бипы с реверберацией, синтезируются на лету. */
public final class ClientSoundsModule extends Module {
	public final Setting.Num volume = add(new Setting.Num("Громкость", 0, 100, 5, 60, "%"));
	public final Setting.Num reverb = add(new Setting.Num("Реверберация", 0, 100, 5, 55, "%"));
	public final Setting.Mode timbre = add(new Setting.Mode("Тембр", 0, SoundSynth.timbreNames()));
	public final Setting.Bool menu = add(new Setting.Bool("Звуки меню", true));
	public final Setting.Bool toggles = add(new Setting.Bool("Звуки функций", true));
	public final Setting.Bool toasts = add(new Setting.Bool("Звуки уведомлений", true));

	public ClientSoundsModule() {
		super("ClientSounds", "Звуки меню и функций: мягкие бипы с реверберацией.", Category.MISC);
	}

	/** Разрешён ли этот звук настройками. */
	public boolean allows(Sfx s) {
		return switch (s) {
			case ON, OFF -> toggles.value;
			case TOAST, FRIEND -> toasts.value;
			default -> menu.value;
		};
	}

	@Override
	public void onTick(Minecraft mc) {
		dev.darkx.visuals.audio.ClientSounds.tick();
	}
}
