package dev.darkx.visuals.mixin;

import dev.darkx.visuals.core.DarkCapes;
import dev.darkx.visuals.core.DarkSkins;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Скины DarkX в списке игроков, чате и наших худах (головы). */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void darkx$skin(CallbackInfoReturnable<PlayerSkin> cir) {
		PlayerInfo self = (PlayerInfo) (Object) this;
		var skin = DarkSkins.apply(self.getProfile().id(), self.getProfile().name(), cir.getReturnValue());
		cir.setReturnValue(DarkCapes.apply(self.getProfile().id(), self.getProfile().name(), skin));
	}
}
