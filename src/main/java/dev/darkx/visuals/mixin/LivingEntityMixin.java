package dev.darkx.visuals.mixin;

import dev.darkx.visuals.modules.SwingAnimationModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SwingAnimation: своя длительность взмаха для локального игрока. */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {
	@Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
	private void darkx$swingDuration(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this != Minecraft.getInstance().player) return;
		Integer ticks = SwingAnimationModule.swingDuration();
		if (ticks != null) cir.setReturnValue(ticks);
	}
}
