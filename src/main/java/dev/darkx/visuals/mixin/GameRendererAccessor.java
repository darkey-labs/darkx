package dev.darkx.visuals.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** setPostEffect в 1.21.11 приватный — открываем его для серого фильтра меню. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Invoker("setPostEffect")
	void darkx$setPostEffect(Identifier id);
}
