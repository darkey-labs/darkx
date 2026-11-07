package dev.darkx.visuals.mixin;

import dev.darkx.visuals.modules.MenusModule;
import dev.darkx.visuals.ui.DarkMenus;
import dev.darkx.visuals.ui.DarkTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Свои меню: чёрный фон у экранов вне мира (настройки, выбор мира, сервера) и панель паузы.
 * Опциональный конфиг.
 */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
	@Inject(method = "renderBackground", at = @At("TAIL"), require = 0)
	private void darkx$background(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		if (self instanceof PauseScreen) {
			if (MenusModule.pauseOn()) DarkMenus.pauseBackdrop(g, self);
		} else if (MenusModule.buttonsOn() && Minecraft.getInstance().level == null && !(self instanceof DarkTitleScreen)) {
			g.fill(0, 0, self.width, self.height, 0xFF000000);
		}
	}
}
