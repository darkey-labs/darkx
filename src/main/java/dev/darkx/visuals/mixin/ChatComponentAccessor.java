package dev.darkx.visuals.mixin;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Chat+: доступ к списку сообщений чата (для схлопывания повторов). Опциональный конфиг. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("allMessages")
	List<GuiMessage> darkx$all();

	@Invoker("refreshTrimmedMessages")
	void darkx$refresh();
}
