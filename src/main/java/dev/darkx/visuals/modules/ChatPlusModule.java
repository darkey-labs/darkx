package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.mixin.ChatComponentAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Chat+: время у каждого сообщения, схлопывание одинаковых подряд идущих сообщений («(x3)») и длинная история.
 * Всё меняется только в отображении чата у тебя на экране. Хуки — ChatComponentMixin (опциональный конфиг).
 */
public final class ChatPlusModule extends Module {
	private static ChatPlusModule instance;
	private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("HH:mm:ss");

	public final Setting.Bool time = add(new Setting.Bool("Время сообщений", true));
	public final Setting.Mode timeFormat = add(new Setting.Mode("Формат времени", 0, "ЧЧ:ММ", "ЧЧ:ММ:СС"));
	public final Setting.Bool compact = add(new Setting.Bool("Схлопывать повторы", true));
	public final Setting.Num history = add(new Setting.Num("История чата", 100, 1000, 100, 500, " строк"));

	private static String lastRaw = "";
	private static int lastCount = 1;
	private static Component lastShown;

	public ChatPlusModule() {
		super("Chat+", "Время у сообщений, схлопывание повторов и длинная история чата.", Category.MISC);
		instance = this;
		timeFormat.showIf(() -> time.value);
	}

	public static boolean active() {
		return instance != null && instance.enabled;
	}

	/** Сколько строк хранить в истории (ваниль — 100). */
	public static int history(int vanilla) {
		return active() ? instance.history.i() : vanilla;
	}

	/** Превращает входящее сообщение в то, что попадёт в чат. Вызывается из ChatComponentMixin. */
	public static Component process(ChatComponent chat, Component msg) {
		if (!active() || msg == null) return msg;
		String raw = msg.getString();
		int n = 1;
		if (instance.compact.value && lastShown != null && raw.equals(lastRaw)) {
			try {
				ChatComponentAccessor acc = (ChatComponentAccessor) (Object) chat;
				List<GuiMessage> all = acc.darkx$all();
				if (!all.isEmpty() && all.get(0).content() == lastShown) {
					all.remove(0);
					acc.darkx$refresh();
					n = lastCount + 1;
				}
			} catch (Throwable ignored) {
				// миксин-аксессор не применился — просто не схлопываем
			}
		}
		MutableComponent out = Component.empty();
		if (instance.time.value) {
			DateTimeFormatter f = instance.timeFormat.index == 1 ? LONG : SHORT;
			out.append(Component.literal("[" + LocalTime.now().format(f) + "] ").withStyle(ChatFormatting.DARK_GRAY));
		}
		out.append(msg);
		if (n > 1) out.append(Component.literal(" (x" + n + ")").withStyle(ChatFormatting.GRAY));
		lastRaw = raw;
		lastCount = n;
		lastShown = out;
		return out;
	}
}
