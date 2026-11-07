package dev.darkx.visuals.mixin;

import dev.darkx.visuals.fx.HandFx;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** GlassHand: подменяем текстуру скина у руки от первого лица (у третьего лица эти методы не вызываются). */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	@ModifyVariable(method = {"renderRightHand", "renderLeftHand"}, at = @At("HEAD"), argsOnly = true)
	private Identifier darkx$handTexture(Identifier skin) {
		Identifier flat = HandFx.texture();
		return flat != null ? flat : skin;
	}
}
