package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Marks;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Notifications;
import dev.darkx.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ReallyWorld: сам принимает /tpaccept, если телепорт просит игрок с меткой «Друг». */
public final class RwTpaModule extends Module {
	private static final Pattern FIRST_LINE = Pattern.compile("^(\\S{1,16}) просит телепортироваться к Вам\\.");

	public final Setting.Bool onlyRw = add(new Setting.Bool("Только на ReallyWorld", true));
	public final Setting.Bool checkColors = add(new Setting.Bool("Проверять цвета строки", true));
	public final Setting.Num delay = add(new Setting.Num("Задержка", 0, 2000, 50, 300, " мс"));

	private long acceptAt;
	private long lastAccept;

	public RwTpaModule() {
		super("RW TPA", "На ReallyWorld автоматически принимает телепорт от друзей.", Category.MISC);
	}

	/** Подписано на ClientReceiveMessageEvents.GAME. */
	public static void onMessage(Component message, boolean overlay) {
		RwTpaModule m = ModuleManager.get(RwTpaModule.class);
		if (m == null || !m.enabled || overlay) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null) return;
		if (m.onlyRw.value) {
			if (mc.getCurrentServer() == null) return;
			if (!mc.getCurrentServer().ip.toLowerCase(Locale.ROOT).contains("reallyworld")) return;
		}

		String first = message.getString().split("\n", 2)[0];
		Matcher mt = FIRST_LINE.matcher(first);
		if (!mt.find()) return;
		String nick = mt.group(1);

		if (m.checkColors.value && !colorsOk(message, nick.length())) return;

		UUID id = null;
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			if (info.getProfile().name().equalsIgnoreCase(nick)) {
				id = info.getProfile().id();
				break;
			}
		}
		if (id == null || Marks.get(id) != Marks.Type.FRIEND) return;

		long now = System.currentTimeMillis();
		if (now - m.lastAccept < 3000) return;
		m.acceptAt = now + (long) m.delay.f();
	}

	@Override
	public void onTick(Minecraft mc) {
		if (acceptAt == 0 || System.currentTimeMillis() < acceptAt) return;
		acceptAt = 0;
		lastAccept = System.currentTimeMillis();
		if (mc.getConnection() != null) {
			mc.getConnection().sendCommand("tpaccept");
			Notifications.push("RW TPA", "Принят телепорт от друга", 0xFF34D399);
		}
	}

	/** Ник — оранжевый, дальше текст — серый. */
	private static boolean colorsOk(Component message, int nickLen) {
		int[] nickColor = {-1};
		int[] textColor = {-1};
		int[] pos = {0};
		message.visit((Style style, String s) -> {
			int start = pos[0], end = start + s.length();
			TextColor c = style.getColor();
			int v = c == null ? -1 : c.getValue();
			if (nickColor[0] == -1 && start <= 0 && end > 0) nickColor[0] = v;
			int probe = nickLen + 2; // первая буква слова «просит»
			if (textColor[0] == -1 && start <= probe && end > probe) textColor[0] = v;
			pos[0] = end;
			return Optional.empty();
		}, Style.EMPTY);
		return isOrange(nickColor[0]) && isGray(textColor[0]);
	}

	private static boolean isOrange(int rgb) {
		if (rgb < 0) return false;
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		return r >= 0xD0 && g >= 0x60 && g <= 0xC8 && b <= 0x70 && r > g;
	}

	private static boolean isGray(int rgb) {
		if (rgb < 0) return false;
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		return max - min <= 24 && max >= 0x60 && max <= 0xD8;
	}
}
