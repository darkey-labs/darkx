package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import net.minecraft.client.Minecraft;

/**
 * Свой мир на клиенте: время суток, погода, туман и цвет неба. Сервер ничего не замечает —
 * меняется только то, что рисует твоя игра. Погода/туман/небо подменяются миксинами
 * (LevelWeatherMixin, FogEnvironmentMixin, ClientLevelSkyMixin) и читают статические геттеры ниже.
 */
public final class CustomWorldModule extends Module {
	private static CustomWorldModule instance;

	public final Setting.Mode mode = add(new Setting.Mode("Время", 1, "Не менять", "Фиксированное", "Ускоренное"));
	public final Setting.Num time = add(new Setting.Num("Час суток", 0, 24000, 250, 18000, ""));
	public final Setting.Num speed = add(new Setting.Num("Скорость", 1, 40, 1, 8, "x"));
	public final Setting.Mode weather = add(new Setting.Mode("Погода", 0, "Как есть", "Ясно", "Дождь", "Гроза"));
	public final Setting.Mode fog = add(new Setting.Mode("Туман", 0, "Как есть", "Свой цвет", "Цвет и дальность"));
	public final Setting.Color fogColor = add(new Setting.Color("Цвет тумана", 0));
	public final Setting.Num fogEnd = add(new Setting.Num("Дальность тумана", 16, 256, 8, 96, " бл."));
	public final Setting.Num fogStart = add(new Setting.Num("Начало тумана", 0, 90, 5, 30, "%"));
	public final Setting.Mode sky = add(new Setting.Mode("Небо", 0, "Как есть", "Свой цвет"));
	public final Setting.Color skyColor = add(new Setting.Color("Цвет неба", 7));

	private double running;

	public CustomWorldModule() {
		super("CustomWorld", "Свой мир: время суток, погода, туман и цвет неба.", Category.VISUAL);
		instance = this;
		speed.showIf(() -> mode.is("Ускоренное"));
		time.showIf(() -> mode.is("Фиксированное"));
		fogColor.showIf(() -> !fog.is("Как есть"));
		fogEnd.showIf(() -> fog.is("Цвет и дальность"));
		fogStart.showIf(() -> fog.is("Цвет и дальность"));
		skyColor.showIf(() -> sky.is("Свой цвет"));
	}

	// ---------------------------------------------------------- геттеры для миксинов

	public static boolean active() {
		return instance != null && instance.enabled;
	}

	/** 0 — как есть, 1 — ясно, 2 — дождь, 3 — гроза. */
	public static int weatherMode() {
		return active() ? instance.weather.index : 0;
	}

	public static boolean fogColorOn() {
		return active() && instance.fog.index >= 1;
	}

	public static boolean fogDistanceOn() {
		return active() && instance.fog.index == 2;
	}

	public static boolean skyOn() {
		return active() && instance.sky.index == 1;
	}

	/** Цвет тумана RGB (если туман не трогаем, но небо своё — туман под цвет неба, чтобы не было шва на горизонте). */
	public static int fogRgb() {
		if (!active()) return 0;
		if (instance.fog.index >= 1) return instance.fogColor.color(0f) & 0xFFFFFF;
		return skyRgb();
	}

	public static int skyRgb() {
		return active() ? instance.skyColor.color(0.15f) & 0xFFFFFF : 0;
	}

	/** Начало тумана в блоках. */
	public static float fogStartBlocks() {
		if (!active()) return 0f;
		return (float) (instance.fogEnd.value * instance.fogStart.value / 100.0);
	}

	public static float fogEndBlocks() {
		return active() ? instance.fogEnd.f() : 0f;
	}

	// ------------------------------------------------------------------- время

	@Override public void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		running = mc.level == null ? 0 : mc.level.getDayTime();
	}

	@Override public void onTick(Minecraft mc) {
		if (mc.level == null || mode.is("Не менять")) return;
		long t;
		if (mode.is("Фиксированное")) {
			t = time.i();
		} else {
			running += speed.value;
			t = (long) running;
		}
		mc.level.setTimeFromServer(mc.level.getGameTime(), t, false);
	}
}
