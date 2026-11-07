package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;

/**
 * Свои меню DarkX: главное меню, пауза, кнопки и фон всех экранов, экран загрузки.
 * Сам ничего не рисует — флаги читают DarkTitleScreen, DarkMenus и миксины
 * (MinecraftScreenMixin, PauseScreenMixin, ScreenBackgroundMixin, AbstractButtonMixin, LoadingOverlayMixin).
 */
public final class MenusModule extends Module {
	private static MenusModule instance;

	public final Setting.Bool title = add(new Setting.Bool("Главное меню", true));
	public final Setting.Bool pause = add(new Setting.Bool("Меню паузы", true));
	public final Setting.Bool buttons = add(new Setting.Bool("Кнопки и чёрный фон", true));
	public final Setting.Bool loading = add(new Setting.Bool("Экран загрузки", true));
	public final Setting.Bool particles = add(new Setting.Bool("Частицы в главном меню", true));

	public MenusModule() {
		super("DarkX Menus", "Свои главное меню, пауза и экран загрузки в OLED-стиле DarkX.", Category.MISC);
		instance = this;
	}

	public static boolean titleOn() { return instance != null && instance.enabled && instance.title.value; }

	public static boolean pauseOn() { return instance != null && instance.enabled && instance.pause.value; }

	public static boolean buttonsOn() { return instance != null && instance.enabled && instance.buttons.value; }

	/** Экран загрузки работает до того, как мод загрузит конфиг модулей из мира, поэтому читаем напрямую. */
	public static boolean loadingOn() { return instance != null && instance.enabled && instance.loading.value; }

	public static boolean particlesOn() { return instance == null || instance.particles.value; }
}
