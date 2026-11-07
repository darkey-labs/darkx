package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Setting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Дополнительные анимации ванильного интерфейса:
 *  - при открытии чата хотбар и полоски (здоровье, еда, опыт) плавно приподнимаются над строкой ввода
 *    и возвращаются при закрытии — «уступают место» чату;
 *  - новые строки чата выплывают снизу, а старые плавно уезжают вверх.
 * Работает через замену ванильных HUD-элементов в Fabric API, без миксинов.
 */
public final class MoreAnimsModule extends Module {
	public final Setting.Bool makeRoom = add(new Setting.Bool("Уступать место чату", true));
	public final Setting.Bool chat = add(new Setting.Bool("Плавный чат", true));
	public final Setting.Num speed = add(new Setting.Num("Скорость", 50, 200, 10, 100, "%"));

	public MoreAnimsModule() {
		super("MoreAnims", "Хотбар уступает место открытому чату, строки чата выплывают плавно.", Category.VIEW);
	}

	// ============================================================== подключение

	/** Элементы худа, которые приподнимаются вместе с хотбаром. */
	private static final Identifier[] LIFTED = {
		VanillaHudElements.HOTBAR,
		VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.HEALTH_BAR,
		VanillaHudElements.FOOD_BAR,
		VanillaHudElements.AIR_BAR,
		VanillaHudElements.MOUNT_HEALTH,
		VanillaHudElements.INFO_BAR,
		VanillaHudElements.EXPERIENCE_LEVEL,
		VanillaHudElements.HELD_ITEM_TOOLTIP
	};

	public static void register() {
		for (Identifier id : LIFTED) HudElementRegistry.replaceElement(id, MoreAnimsModule::wrapLift);
		HudElementRegistry.replaceElement(VanillaHudElements.CHAT, MoreAnimsModule::wrapChat);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) onChatLine(message);
		});
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> onChatLine(message));
	}

	private static MoreAnimsModule active() {
		MoreAnimsModule m = ModuleManager.get(MoreAnimsModule.class);
		return m != null && m.enabled ? m : null;
	}

	// ================================================================== хотбар

	/** Строка ввода чата занимает нижние 14 пикселей — хотбар приподнимается чуть выше неё. */
	private static final float CHAT_LIFT = 16f;

	private static float lift;                     // текущий подъём, GUI-пиксели
	private static long liftT = System.nanoTime();

	/**
	 * Плавный подъём под открытый чат. Экспоненциальное сглаживание по реальному времени:
	 * результат не зависит ни от FPS, ни от того, сколько элементов худа вызывают метод за кадр.
	 */
	private static float chatLift(MoreAnimsModule m) {
		long now = System.nanoTime();
		float dt = Math.min(0.05f, (now - liftT) / 1.0e9f);
		liftT = now;
		boolean open = m != null && m.makeRoom.value && Minecraft.getInstance().screen instanceof ChatScreen;
		float target = open ? CHAT_LIFT : 0f;
		float k = 1f - (float) Math.exp(-dt * 15f * (m == null ? 1f : m.speed.f() / 100f));
		lift += (target - lift) * k;
		if (Math.abs(target - lift) < 0.03f) lift = target;
		return lift;
	}

	private static HudElement wrapLift(HudElement orig) {
		return (g, tracker) -> {
			MoreAnimsModule m = active();
			if (m == null) {
				lift = 0f;
				orig.render(g, tracker);
				return;
			}
			float dy = -chatLift(m);
			if (Math.abs(dy) < 0.05f) {
				orig.render(g, tracker);
				return;
			}
			g.pose().pushMatrix();
			g.pose().translate(0f, dy);
			orig.render(g, tracker);
			g.pose().popMatrix();
		};
	}

	// ==================================================================== чат

	private static float chatOff; // накопленный сдвиг вниз, GUI-пиксели
	private static long chatT = System.nanoTime();

	/** Пришла строка: сдвигаем чат вниз на её высоту — дальше он плавно возвращается на место. */
	private static void onChatLine(Component message) {
		MoreAnimsModule m = active();
		if (m == null || !m.chat.value) return;
		Minecraft mc = Minecraft.getInstance();
		double scale = mc.options.chatScale().get();
		double spacing = mc.options.chatLineSpacing().get();
		int wrap = (int) Math.floor(Math.floor(mc.options.chatWidth().get() * 280.0 + 40.0) / scale);
		int lines = Math.max(1, Math.min(6, mc.font.split(message, wrap).size()));
		float lineH = (float) (9.0 * (spacing + 1.0) * scale);
		chatOff = Math.min(chatOff + lines * lineH, 6f * lineH);
	}

	private static HudElement wrapChat(HudElement orig) {
		return (g, tracker) -> {
			MoreAnimsModule m = active();
			if (m == null || !m.chat.value) {
				chatOff = 0f;
				orig.render(g, tracker);
				return;
			}
			long now = System.nanoTime();
			float dt = Math.min(0.05f, (now - chatT) / 1.0e9f);
			chatT = now;
			chatOff *= (float) Math.exp(-dt * 11f * (m.speed.f() / 100f));
			if (chatOff < 0.15f) {
				chatOff = 0f;
				orig.render(g, tracker);
				return;
			}
			// ниже нижнего края чата строка не видна — поэтому новая «выплывает» из-под него
			g.enableScissor(0, 0, g.guiWidth(), g.guiHeight() - 40 + 1);
			g.pose().pushMatrix();
			g.pose().translate(0f, chatOff);
			orig.render(g, tracker);
			g.pose().popMatrix();
			g.disableScissor();
		};
	}
}
