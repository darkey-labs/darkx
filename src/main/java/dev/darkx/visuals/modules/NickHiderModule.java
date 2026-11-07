package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import net.minecraft.client.Minecraft;

/** Подменяет твой ник на указанный везде, где игра рисует текст (чат, таб, скорборд, худы). */
public final class NickHiderModule extends Module {
	private static NickHiderModule instance;

	public final Setting.Text nick = add(new Setting.Text("Ник", "DarkX", 16));

	public NickHiderModule() {
		super("Nick Hider", "Заменяет твой ник на другой во всём интерфейсе.", Category.MISC);
		instance = this;
	}

	/** Вызывается из миксина на каждый рисуемый кусок текста — поэтому быстрый выход. */
	public static String replace(String s) {
		NickHiderModule m = instance;
		if (m == null || !m.enabled || s == null || s.isEmpty()) return s;
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getUser() == null) return s;
		String real = mc.getUser().getName();
		String fake = m.nick.value;
		if (real == null || real.isEmpty() || fake == null || fake.isBlank()) return s;
		return s.indexOf(real) >= 0 ? s.replace(real, fake) : s;
	}
}
