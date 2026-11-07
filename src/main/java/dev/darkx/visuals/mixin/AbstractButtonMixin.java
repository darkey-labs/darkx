package dev.darkx.visuals.mixin;

import dev.darkx.visuals.modules.MenusModule;
import dev.darkx.visuals.ui.DarkMenus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Свои меню: обычные кнопки Minecraft рисуются в стиле DarkX (логика кнопок не меняется). Опциональный конфиг. */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
	private void darkx$style(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!MenusModule.buttonsOn()) return;
		// стилизуем только обычные текстовые кнопки (Button и CycleButton). Иконки, вкладки, кнопки рецептов и
		// творческого инвентаря рисуют спрайты сами — их оставляем ванильными
		Class<?> c = this.getClass();
		boolean plain = c == Button.class || c.getEnclosingClass() == Button.class || c == CycleButton.class;
		AbstractWidget self = (AbstractWidget) (Object) this;
		if (!plain || self.getWidth() < 40 || self.getMessage().getString().isEmpty()) return;
		DarkMenus.drawButton(g, self);
		ci.cancel();
	}
}
