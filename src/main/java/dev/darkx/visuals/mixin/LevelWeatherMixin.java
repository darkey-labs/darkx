package dev.darkx.visuals.mixin;

import dev.darkx.visuals.modules.CustomWorldModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CustomWorld: своя погода. Подменяем только клиентский мир (серверный мир одиночной игры не трогаем).
 * Лежит в опциональном конфиге: если сигнатура изменится, игра не упадёт, просто погода останется ванильной.
 */
@Mixin(Level.class)
public abstract class LevelWeatherMixin {
	@Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true, require = 0)
	private void darkx$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
		int m = CustomWorldModule.weatherMode();
		if (m == 0 || (Object) this != Minecraft.getInstance().level) return;
		cir.setReturnValue(m == 1 ? 0f : 1f);
	}

	@Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true, require = 0)
	private void darkx$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
		int m = CustomWorldModule.weatherMode();
		if (m == 0 || (Object) this != Minecraft.getInstance().level) return;
		cir.setReturnValue(m == 3 ? 1f : 0f);
	}
}
