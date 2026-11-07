package dev.darkx.visuals.mixin;

import dev.darkx.visuals.modules.ChatPlusModule;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat+: время, схлопывание повторов (через addMessage) и длина истории. Опциональный конфиг. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
		at = @At("HEAD"), argsOnly = true, require = 0)
	private Component darkx$message(Component message) {
		return ChatPlusModule.process((ChatComponent) (Object) this, message);
	}

	@ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100), require = 0)
	private int darkx$history(int vanilla) {
		return ChatPlusModule.history(vanilla);
	}
}
