package dev.darkx.visuals.mixin;

import dev.darkx.visuals.modules.CustomWorldModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** CustomWorld: свой цвет неба (плоский, не зависит от времени суток). Опциональный конфиг. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelSkyMixin {
	@Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true, require = 0)
	private void darkx$sky(Vec3 cameraPos, float partialTick, CallbackInfoReturnable<Integer> cir) {
		if (CustomWorldModule.skyOn()) cir.setReturnValue(CustomWorldModule.skyRgb() | 0xFF000000);
	}
}
