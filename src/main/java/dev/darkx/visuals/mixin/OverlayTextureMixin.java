package dev.darkx.visuals.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.darkx.visuals.fx.OverlayHolder;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HitColor: текстура наложения (красная вспышка урона) — строки 0..7 в текстуре 16x16.
 * Лежит в опциональном конфиге darkx.optional.mixins.json: если не применится, игра не упадёт.
 */
@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin implements OverlayHolder.Applier {
	@Shadow
	@Final
	private DynamicTexture texture;

	@Inject(method = "<init>", at = @At("RETURN"), require = 0)
	private void darkx$register(CallbackInfo ci) {
		OverlayHolder.instance = (OverlayHolder.Applier) (Object) this;
	}

	@Unique
	@Override
	public void darkx$apply(int argb) {
		NativeImage img = this.texture.getPixels();
		if (img == null) return;
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) img.setPixel(x, y, argb);
		}
		this.texture.upload();
	}
}
