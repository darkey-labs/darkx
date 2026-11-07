package dev.darkx.visuals.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** BetterGUI: список игроков, шапка и подвал таба — для собственной отрисовки. */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
	@Invoker("getPlayerInfos")
	List<PlayerInfo> darkx$infos();

	@Accessor("header")
	Component darkx$header();

	@Accessor("footer")
	Component darkx$footer();
}
