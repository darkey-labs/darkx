package dev.darkx.visuals.mixin;

import dev.darkx.visuals.core.DarkCapes;
import dev.darkx.visuals.core.DarkSkins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Скины DarkX: подмена скина игрока в мире и в руке от первого лица. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void darkx$skin(CallbackInfoReturnable<PlayerSkin> cir) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		var skin = DarkSkins.apply(self.getUUID(), self.getName().getString(), cir.getReturnValue());
		cir.setReturnValue(DarkCapes.apply(self.getUUID(), self.getName().getString(), skin));
	}
}
