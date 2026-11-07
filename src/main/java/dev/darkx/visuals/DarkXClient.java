package dev.darkx.visuals;

import dev.darkx.visuals.core.Config;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Stats;
import dev.darkx.visuals.fx.WorldFx;
import dev.darkx.visuals.hud.HudManager;
import dev.darkx.visuals.ui.ClickGuiScreen;
import net.fabricmc.api.ClientModInitializer;
import dev.darkx.visuals.core.Ecosystem;
import dev.darkx.visuals.modules.RwTpaModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class DarkXClient implements ClientModInitializer {
	public static final String ID = "darkx";

	private static boolean shiftWasDown;
	private static boolean shiftLatch;

	/** После закрытия меню правым Shift не открываем его заново, пока клавишу не отпустят. */
	public static void latchShift() {
		shiftLatch = true;
	}

	@Override
	public void onInitializeClient() {
		ModuleManager.init();
		Config.load();
		try {
			dev.darkx.visuals.audio.ClientSounds.init();
		} catch (Throwable ignored) {
			// звук необязателен — клиент должен запуститься и без него
		}
		HudManager.register();
		// BetterGUI — до MoreAnims: подъём хотбара под чат накладывается поверх его отступа
		dev.darkx.visuals.modules.BetterGuiModule.register();
		dev.darkx.visuals.modules.GlowHandModule.register();
		dev.darkx.visuals.modules.MoreAnimsModule.register();
		dev.darkx.visuals.modules.CrosshairModule.register();

		ClientTickEvents.END_CLIENT_TICK.register(DarkXClient::tick);
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(WorldFx::render);
		ClientReceiveMessageEvents.GAME.register(RwTpaModule::onMessage);
		ClientSendMessageEvents.ALLOW_CHAT.register(dev.darkx.visuals.core.Friends::onChat);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			// Оптимизатор возвращает настройки игры, чтобы они не сохранились «урезанными»
			dev.darkx.visuals.modules.OptimizerModule opt = ModuleManager.get(dev.darkx.visuals.modules.OptimizerModule.class);
			if (opt != null) opt.restore(client);
			Config.save();
			Ecosystem.leave();
		});
	}

	private static void tick(Minecraft mc) {
		boolean down = GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
		if (!down) shiftLatch = false;
		if (down && !shiftWasDown && !shiftLatch && mc.screen == null && mc.level != null && mc.player != null) {
			mc.setScreen(new ClickGuiScreen());
		}
		shiftWasDown = down;

		Stats.tick(mc);
		if (mc.level != null && mc.player != null) ModuleManager.tick(mc);
	}
}
