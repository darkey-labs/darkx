package dev.darkx.visuals.mixin;

import dev.darkx.visuals.modules.AntiAcBotModule;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AntiACBot: бот не попадает в список отрисовки — его не видно, ни модели, ни ника. */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void darkx$hideBots(Entity entity, Frustum frustum, double camX, double camY, double camZ,
								CallbackInfoReturnable<Boolean> cir) {
		if (AntiAcBotModule.isBot(entity)) cir.setReturnValue(false);
	}
}
