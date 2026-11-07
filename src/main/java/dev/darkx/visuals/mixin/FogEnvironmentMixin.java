package dev.darkx.visuals.mixin;

import dev.darkx.visuals.modules.CustomWorldModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CustomWorld: свой цвет и дальность обычного (атмосферного) тумана.
 * Под водой, в лаве и в снегу туман остаётся ванильным. Опциональный конфиг.
 */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class FogEnvironmentMixin {
	@Inject(method = "getBaseColor", at = @At("HEAD"), cancellable = true, require = 0)
	private void darkx$color(ClientLevel level, Camera camera, int renderDistance, float partialTick, CallbackInfoReturnable<Integer> cir) {
		if (CustomWorldModule.fogColorOn() || CustomWorldModule.skyOn()) {
			cir.setReturnValue(CustomWorldModule.fogRgb() | 0xFF000000);
		}
	}

	@Inject(method = "setupFog", at = @At("HEAD"), cancellable = true, require = 0)
	private void darkx$distance(FogData data, Camera camera, ClientLevel level, float renderDistance, DeltaTracker tracker, CallbackInfo ci) {
		if (!CustomWorldModule.fogDistanceOn()) return;
		float start = CustomWorldModule.fogStartBlocks();
		float end = CustomWorldModule.fogEndBlocks();
		data.environmentalStart = start;
		data.environmentalEnd = end;
		data.renderDistanceStart = start;
		data.renderDistanceEnd = end;
		data.skyEnd = end;
		data.cloudEnd = end;
		ci.cancel();
	}
}
