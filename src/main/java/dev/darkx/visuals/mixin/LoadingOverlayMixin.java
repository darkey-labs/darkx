package dev.darkx.visuals.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.darkx.visuals.modules.MenusModule;
import dev.darkx.visuals.ui.DarkMenus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Свой экран загрузки: чёрный фон (прозрачность та же, что у ванильного — плавное появление меню сохраняется),
 * без логотипа Mojang, вместо него логотип DarkX и тонкая полоска прогресса. Опциональный конфиг.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow
	private float currentProgress;

	/** Был ли в этом кадре ванильный fill фона. На старте игры фон не fill, а очистка экрана красным (clear). */
	@Unique
	private boolean darkx$filled;

	@Inject(method = "render", at = @At("HEAD"), require = 0)
	private void darkx$frame(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		darkx$filled = false;
	}

	@Shadow
	protected abstract void drawProgressBar(GuiGraphics g, int x1, int y1, int x2, int y2, float alpha);

	@Redirect(method = "render",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"),
		require = 0)
	private void darkx$background(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
		darkx$filled = true;
		g.fill(x1, y1, x2, y2, MenusModule.loadingOn() ? (color & 0xFF000000) : color);
	}

	@Redirect(method = "render",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"),
		require = 0)
	private void darkx$logo(GuiGraphics g, RenderPipeline pipeline, Identifier id, int x, int y, float u, float v,
							int w, int h, int uw, int vh, int tw, int th, int color) {
		if (!MenusModule.loadingOn()) {
			g.blit(pipeline, id, x, y, u, v, w, h, uw, vh, tw, th, color);
			return;
		}
		// старт игры: красный фон — это очистка экрана без fill. Перекрываем непрозрачным чёрным, пока логотип ещё не нарисован
		if (!darkx$filled) {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xFF000000);
			darkx$filled = true;
		}
	}

	@Redirect(method = "render",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;drawProgressBar(Lnet/minecraft/client/gui/GuiGraphics;IIIIF)V"),
		require = 0)
	private void darkx$bar(LoadingOverlay self, GuiGraphics g, int x1, int y1, int x2, int y2, float alpha) {
		if (MenusModule.loadingOn()) DarkMenus.drawLoading(g, this.currentProgress, alpha);
		else this.drawProgressBar(g, x1, y1, x2, y2, alpha);
	}
}
