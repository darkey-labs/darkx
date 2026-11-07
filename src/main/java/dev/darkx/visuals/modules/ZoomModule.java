package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Зум на удержание клавиши. Само изменение FOV делает GameRendererMixin. */
public final class ZoomModule extends Module {
	public final Setting.Key key = add(new Setting.Key("Клавиша", GLFW.GLFW_KEY_C));
	public final Setting.Num factor = add(new Setting.Num("Кратность", 1.5, 10, 0.5, 4, "x"));
	public final Setting.Bool smooth = add(new Setting.Bool("Плавно", true));

	private float current = 1f;
	private long last = System.nanoTime();

	public ZoomModule() {
		super("Zoom", "Приближение камеры по удержанию клавиши.", Category.VIEW);
	}

	/** Во сколько раз уменьшить FOV в этом кадре (1 = без зума). */
	public static float divisor() {
		ZoomModule m = ModuleManager.get(ZoomModule.class);
		if (m == null) return 1f;
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - m.last) / 1.0e9f);
		m.last = now;

		boolean active = m.enabled && mc.screen == null && m.key.isDown(mc.getWindow().handle());
		float target = active ? m.factor.f() : 1f;
		if (m.smooth.value) m.current += (target - m.current) * (1f - (float) Math.exp(-dt * 14f));
		else m.current = target;
		if (Math.abs(m.current - 1f) < 0.003f && !active) m.current = 1f;
		return m.current;
	}
}
