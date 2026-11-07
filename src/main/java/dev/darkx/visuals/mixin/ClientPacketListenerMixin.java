package dev.darkx.visuals.mixin;

import dev.darkx.visuals.core.TotemPops;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Тотемы: событие «сработал тотем» (статус 35) считаем для счётчика и уведомлений. Опциональный конфиг. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleEntityEvent", at = @At("TAIL"), require = 0)
	private void darkx$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		if (packet.getEventId() != 35) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		Entity e = packet.getEntity(mc.level);
		if (e != null) TotemPops.onPop(e);
	}
}
