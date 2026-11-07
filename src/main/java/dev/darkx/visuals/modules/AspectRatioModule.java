package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.mixin.GameRendererAccessor;
import dev.darkx.visuals.ui.ClickGuiScreen;
import dev.darkx.visuals.ui.HudEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Растянутое разрешение (как в CS): картинка сжимается по ширине до выбранного соотношения
 * и растягивается на весь экран. Работает как пост-эффект, интерфейс не затрагивается.
 * Эффект применяется только если соотношение окна шире выбранного.
 */
public final class AspectRatioModule extends Module {
	private static final String[] NAMES = {"4:3", "5:4", "3:2", "16:10", "1:1"};
	private static final String[] IDS = {"aspect_43", "aspect_54", "aspect_32", "aspect_1610", "aspect_11"};

	public final Setting.Mode ratio = add(new Setting.Mode("Соотношение", 0, NAMES));

	public AspectRatioModule() {
		super("AspectRatio", "Растянутое разрешение (4:3, 5:4 и другие).", Category.VIEW);
	}

	private static boolean menuOpen(Minecraft mc) {
		return mc.screen instanceof ClickGuiScreen || mc.screen instanceof HudEditorScreen;
	}

	@Override public void onTick(Minecraft mc) {
		// в меню DarkX работает серый пост-эффект, не перебиваем его
		if (menuOpen(mc)) return;
		((GameRendererAccessor) mc.gameRenderer).darkx$setPostEffect(
			Identifier.fromNamespaceAndPath("darkx", IDS[ratio.index]));
	}

	@Override public void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (!menuOpen(mc)) mc.gameRenderer.clearPostEffect();
	}
}
