package dev.darkx.visuals.mixin;

import dev.darkx.visuals.modules.MenusModule;
import dev.darkx.visuals.ui.DarkMenus;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Свои меню: пауза остаётся ванильной (кнопки и выход из мира работают как раньше),
 * но кнопки перестраиваются в колонку, а фон рисуется в стиле DarkX. Опциональный конфиг.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
	@Inject(method = "init", at = @At("TAIL"), require = 0)
	private void darkx$layout(CallbackInfo ci) {
		if (MenusModule.pauseOn()) DarkMenus.layoutPause((Screen) (Object) this);
	}
}
