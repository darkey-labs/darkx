package dev.darkx.visuals.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Выйти с текущего сервера и зайти на другой по адресу. Вся работа с Mojang-классами — только здесь. */
public final class ServerJoin {
	private ServerJoin() {}

	public static void join(String ip) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			Screen parent = new JoinMultiplayerScreen(new TitleScreen());
			if (mc.level != null) mc.disconnect(parent, false);
			ServerData data = new ServerData("DarkX", ip, ServerData.Type.OTHER);
			ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(ip), data, false, null);
		});
	}
}
