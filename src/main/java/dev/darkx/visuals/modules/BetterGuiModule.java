package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Ecosystem;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.core.Theme;
import dev.darkx.visuals.mixin.PlayerTabOverlayAccessor;
import dev.darkx.visuals.ui.UI;
import dev.darkx.visuals.ui.UI.F;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.Comparator;
import java.util.List;

/**
 * Свои хотбар, скорборд и таб вместо ванильных: скруглённые плашки (серые или OLED), шрифт Montserrat,
 * отступы от краёв экрана и плавный выезд таба сверху.
 * Ванильные элементы подменяются через HudElementRegistry.replaceElement; выключенный элемент рисуется как обычно.
 */
public final class BetterGuiModule extends Module {
	private static final int HOTBAR = 0, SCOREBOARD = 1, TAB = 2;

	public final Setting.Multi replace = add(new Setting.Multi("Заменять",
		new String[] {"Хотбар", "Скорборд", "Таб"}, new boolean[] {true, true, true}));
	public final Setting.Mode style = add(new Setting.Mode("Стиль", 0, "Как в теме", "Серый", "OLED"));
	public final Setting.Num hotbarGap = add(new Setting.Num("Отступ хотбара снизу", 2, 14, 1, 5, " px"));
	public final Setting.Num sideGap = add(new Setting.Num("Отступ скорборда справа", 0, 24, 1, 6, " px"));
	public final Setting.Bool tabSlide = add(new Setting.Bool("Выезд таба сверху", true));
	public final Setting.Bool blurTab = add(new Setting.Bool("Размытие мира при Tab", false));
	public final Setting.Num speed = add(new Setting.Num("Скорость анимации", 50, 200, 10, 100, "%"));

	public BetterGuiModule() {
		super("BetterGUI", "Свои хотбар, скорборд и таб: серый или OLED стиль, Montserrat, отступы от краёв, таб выезжает сверху.", Category.VISUAL);
	}

	// ============================================================== подключение

	/** Полоски над хотбаром — поднимаются вместе с ним, чтобы между ними не появлялась щель. */
	private static final Identifier[] RAISED = {
		VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.HEALTH_BAR,
		VanillaHudElements.FOOD_BAR,
		VanillaHudElements.AIR_BAR,
		VanillaHudElements.MOUNT_HEALTH,
		VanillaHudElements.INFO_BAR,
		VanillaHudElements.EXPERIENCE_LEVEL,
		VanillaHudElements.HELD_ITEM_TOOLTIP
	};

	/** Вешается один раз при старте — до MoreAnims, чтобы подъём под чат накладывался сверху. */
	public static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, orig -> (g, t) -> {
			BetterGuiModule m = active(HOTBAR);
			Minecraft mc = Minecraft.getInstance();
			if (m == null || mc.player == null || mc.player.isSpectator()) orig.render(g, t);
			else m.hotbar(g);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, orig -> (g, t) -> {
			BetterGuiModule m = active(SCOREBOARD);
			if (m == null) orig.render(g, t);
			else m.scoreboard(g);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST, orig -> (g, t) -> {
			BetterGuiModule m = active(TAB);
			if (m == null) orig.render(g, t);
			else m.tab(g, t, orig);
		});
		for (Identifier id : RAISED) HudElementRegistry.replaceElement(id, BetterGuiModule::wrapRaise);
	}

	private static BetterGuiModule active(int part) {
		BetterGuiModule m = ModuleManager.get(BetterGuiModule.class);
		return m != null && m.enabled && m.replace.isOn(part) ? m : null;
	}

	private static HudElement wrapRaise(HudElement orig) {
		return (g, t) -> {
			BetterGuiModule m = active(HOTBAR);
			Minecraft mc = Minecraft.getInstance();
			if (m == null || mc.player == null || mc.player.isSpectator()) {
				orig.render(g, t);
				return;
			}
			g.pose().pushMatrix();
			g.pose().translate(0f, -m.raise());
			orig.render(g, t);
			g.pose().popMatrix();
		};
	}

	/** Хотбар ванили стоит вплотную к низу (высота 22), наш — 24 и с отступом: полоски поднимаем на разницу. */
	private float raise() {
		return hotbarGap.f() + 2f;
	}

	// ============================================================ оформление

	private boolean gray() {
		return switch (style.index) {
			case 1 -> true;
			case 2 -> false;
			default -> Theme.gray();
		};
	}

	/** Плашка в выбранном стиле: серая или чёрная OLED с тонкой кромкой. */
	private void panel(GuiGraphics g, int x, int y, int w, int h, int r) {
		if (gray()) UI.gray(g, x, y, w, h, r);
		else UI.rrectBorder(g, x, y, w, h, r, 0xFF000000, 0x26E4E4E2);
	}

	private float anim() {
		return speed.f() / 100f;
	}

	// ================================================================= хотбар

	private void hotbar(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		UI.alpha = 1f;

		int sw = g.guiWidth(), sh = g.guiHeight();
		final int pw = 188, ph = 24;
		int px = (sw - pw) / 2, py = sh - hotbarGap.i() - ph;
		panel(g, px, py, pw, ph, Theme.hudR(8));

		int sel = p.getInventory().getSelectedSlot();
		float sx = UI.anim("bgui.slot", sel, 24f * anim());
		int cellX = px + 4 + Math.round(sx * 20f);
		UI.rrectBorder(g, cellX, py + 2, 20, 20, Theme.hudR(6), Theme.tint(0.22f), Theme.accent(0.85f));

		for (int i = 0; i < 9; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.isEmpty()) continue;
			int ix = px + 4 + i * 20 + 2, iy = py + 4;
			g.renderItem(s, ix, iy);
			g.renderItemDecorations(mc.font, s, ix, iy);
		}

		ItemStack off = p.getOffhandItem();
		if (!off.isEmpty()) {
			int ox = p.getMainArm() == HumanoidArm.RIGHT ? px - 28 : px + pw + 4;
			panel(g, ox, py, 24, ph, Theme.hudR(8));
			g.renderItem(off, ox + 4, py + 4);
			g.renderItemDecorations(mc.font, off, ox + 4, py + 4);
		}
	}

	// ================================================================ скорборд

	private void scoreboard(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		Scoreboard sb = mc.level.getScoreboard();

		Objective obj = null;
		PlayerTeam team = sb.getPlayersTeam(mc.player.getScoreboardName());
		if (team != null) {
			ChatFormatting col = team.getColor();
			if (col != null && col.isColor()) {
				DisplaySlot slot = DisplaySlot.teamColorToSlot(col);
				if (slot != null) obj = sb.getDisplayObjective(slot);
			}
		}
		if (obj == null) obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (obj == null) return;

		UI.alpha = 1f;
		long now = System.currentTimeMillis();
		if (sbView == null || sbObj != obj || now - sbAt > 250L) {
			sbView = buildScoreboard(mc, sb, obj);
			sbObj = obj;
			sbAt = now;
		}
		SbView v = sbView;
		int n = v.names.length;
		Component[] names = v.names, vals = v.vals;
		int[] valW = v.valW;
		int titleW = v.titleW, content = v.content;

		final int pad = 7, rowH = 11, titleH = 14;
		int sw = g.guiWidth(), sh = g.guiHeight();
		int w = Math.max(Math.max(titleW, content) + pad * 2, 80);
		w = Math.min(w, Math.round(sw * 0.4f));
		int h = pad + titleH + (n > 0 ? 4 : 0) + n * rowH + pad - 2;
		int x = sw - w - sideGap.i();
		int y = Math.max(4, (sh - h) / 2);

		panel(g, x, y, w, h, Theme.hudR(8));

		int cy = y + pad + titleH / 2 - 1;
		g.enableScissor(x + 2, y, x + w - 2, y + h);
		UI.comp(g, F.BOLD, v.title, x + (w - Math.min(titleW, w - pad * 2)) / 2, cy, UI.TEXT);
		g.disableScissor();
		if (n > 0) UI.fill(g, x + pad, y + pad + titleH + 1, w - pad * 2, 1, 0x26E4E4E2);

		int ry = y + pad + titleH + 4;
		for (int i = 0; i < n; i++) {
			float rcy = ry + i * rowH + rowH / 2f;
			int right = x + w - pad;
			int nameMax = (right - (x + pad)) - (valW[i] > 0 ? valW[i] + 8 : 0);
			g.enableScissor(x + pad, ry + i * rowH, x + pad + Math.max(0, nameMax), ry + (i + 1) * rowH);
			UI.raw(g, F.BODY, names[i], x + pad, rcy, UI.TEXT);
			g.disableScissor();
			if (valW[i] > 0) UI.raw(g, F.BODY, vals[i], right - valW[i], rcy, UI.TEXT);
		}
	}


	// Строки скорборда пересобираем раз в 250 мс, а не каждый кадр: сортировка, обёртка шрифтом и замер ширины недёшевы.
	private record SbView(Component title, int titleW, Component[] names, Component[] vals, int[] valW, int content) {}

	private SbView sbView;
	private Objective sbObj;
	private long sbAt;

	private static SbView buildScoreboard(Minecraft mc, Scoreboard sb, Objective obj) {
		NumberFormat fmt = obj.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
		List<PlayerScoreEntry> rows = sb.listPlayerScores(obj).stream()
			.filter(e -> !e.isHidden())
			.sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed()
				.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
			.limit(15)
			.toList();
		int n = rows.size();
		Component[] names = new Component[n];
		Component[] vals = new Component[n];
		int[] valW = new int[n];
		int content = 0;
		Component title = obj.getDisplayName();
		int titleW = mc.font.width(UI.withFont(F.BOLD, title));
		for (int i = 0; i < n; i++) {
			PlayerScoreEntry e = rows.get(i);
			names[i] = UI.withFont(F.BODY, PlayerTeam.formatNameForTeam(sb.getPlayersTeam(e.owner()), e.ownerName()));
			vals[i] = UI.withFont(F.BODY, e.formatValue(fmt));
			int nw = mc.font.width(names[i]);
			valW[i] = mc.font.width(vals[i]);
			content = Math.max(content, nw + (valW[i] > 0 ? 10 + valW[i] : 0));
		}
		return new SbView(title, titleW, names, vals, valW, content);
	}

	// ===================================================================== таб

	private void tab(GuiGraphics g, DeltaTracker tracker, HudElement vanilla) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		Scoreboard sb = mc.level.getScoreboard();

		// объекты «в списке» (очки/здоровье рядом с ником) оставляем ванильному табу — они сложнее
		if (sb.getDisplayObjective(DisplaySlot.LIST) != null) {
			vanilla.render(g, tracker);
			return;
		}

		boolean down = mc.options.keyPlayerList.isDown();
		float p = UI.anim("bgui.tab", down ? 1f : 0f, tabSlide.value ? 13f * anim() : 1000f);
		if (p < 0.01f && !down) return;

		var overlay = mc.gui.getTabList();
		PlayerTabOverlayAccessor acc = (PlayerTabOverlayAccessor) (Object) overlay;
		List<PlayerInfo> infos = acc.darkx$infos();
		if (infos == null || infos.isEmpty()) return;

		if (blurTab.value && p > 0.4f && mc.screen == null) {
			try {
				g.blurBeforeThisStratum();
			} catch (Throwable ignored) {
				// размытие один раз за кадр — если его уже запросили, просто пропускаем
			}
		}

		int n = infos.size();
		int cols = 1, perCol = n;
		while (perCol > 20 && cols < 4) {
			cols++;
			perCol = (n + cols - 1) / cols;
		}

		Component[] names = new Component[n];
		int nameMax = 0;
		for (int i = 0; i < n; i++) {
			names[i] = overlay.getNameForDisplay(infos.get(i));
			nameMax = Math.max(nameMax, mc.font.width(UI.withFont(F.BODY, names[i])));
		}

		final int padX = 8, padY = 7, rowH = 12, colGap = 4, headW = 10;
		int sw = g.guiWidth(), sh = g.guiHeight();
		int colW = headW + 6 + nameMax + 10 + 14 + 8;
		colW = Math.max(120, Math.min(colW, 230));
		int maxInner = sw - 24 - padX * 2;
		if (cols * colW + (cols - 1) * colGap > maxInner) colW = Math.max(90, (maxInner - (cols - 1) * colGap) / cols);
		int inner = cols * colW + (cols - 1) * colGap;

		List<FormattedCharSequence> head = lines(acc.darkx$header(), inner);
		List<FormattedCharSequence> foot = lines(acc.darkx$footer(), inner);
		int headH = head.isEmpty() ? 0 : head.size() * 10 + 5;
		int footH = foot.isEmpty() ? 0 : foot.size() * 10 + 5;

		int pw = inner + padX * 2;
		int ph = padY * 2 + headH + perCol * rowH + footH;
		int px = (sw - pw) / 2;
		final int top = 8;

		float e = UI.ease(p);
		int py = Math.round(top - (1f - e) * (ph + top + 6));
		UI.alpha = tabSlide.value ? Math.max(0f, Math.min(1f, e * 1.25f)) : 1f;

		panel(g, px, py, pw, ph, Theme.hudR(10));

		int y0 = py + padY;
		for (int i = 0; i < head.size(); i++) {
			g.drawString(mc.font, head.get(i), px + (pw - mc.font.width(head.get(i))) / 2, y0 + i * 10 + 1, UI.c(UI.TEXT), false);
		}
		y0 += headH;

		for (int i = 0; i < n; i++) {
			PlayerInfo info = infos.get(i);
			int col = i / perCol, row = i % perCol;
			int x = px + padX + col * (colW + colGap);
			int y = y0 + row * rowH;
			float cy = y + rowH / 2f;
			boolean me = info.getProfile().id().equals(mc.player.getUUID());
			boolean spec = info.getGameMode() == GameType.SPECTATOR;

			if (me) UI.rrect(g, x - 2, y, colW + 4, rowH - 1, Theme.hudR(4), Theme.accent(0.16f));
			UI.head(g, info.getSkin(), x + 1, y + 1, headW, 0, 0);
			int tx = x + headW + 6;
			int right = x + colW - 16;
			boolean dx = Ecosystem.isUser(info.getProfile().id());
			if (dx) right -= 7;
			g.enableScissor(tx, y, Math.max(tx, right - 2), y + rowH);
			UI.comp(g, F.BODY, names[i], tx, cy, spec ? 0x80E4E4E2 : UI.TEXT);
			g.disableScissor();
			if (dx) UI.circle(g, x + colW - 22, Math.round(cy), 4, Theme.accent());
			ping(g, x + colW - 11, Math.round(cy), info.getLatency());
		}

		if (!foot.isEmpty()) {
			int fy = y0 + perCol * rowH + 5;
			for (int i = 0; i < foot.size(); i++) {
				g.drawString(mc.font, foot.get(i), px + (pw - mc.font.width(foot.get(i))) / 2, fy + i * 10, UI.c(UI.TEXT_DIM), false);
			}
		}
		UI.alpha = 1f;
	}

	private static List<FormattedCharSequence> lines(Component c, int width) {
		if (c == null || c.getString().isEmpty()) return List.of();
		return Minecraft.getInstance().font.split(UI.withFont(F.BODY, c), Math.max(40, width));
	}

	/** Четыре столбика связи: чем лучше пинг, тем больше горит. */
	private static void ping(GuiGraphics g, int x, int cy, int latency) {
		int bars = latency < 0 ? 0 : latency < 150 ? 4 : latency < 300 ? 3 : latency < 600 ? 2 : latency < 1000 ? 1 : 0;
		int on = bars >= 3 ? 0xFF34D399 : bars == 2 ? 0xFFFACC15 : 0xFFEF4444;
		for (int b = 0; b < 4; b++) {
			int h = 3 + b * 2;
			UI.fill(g, x + b * 3, cy + 4 - h, 2, h, b < bars ? on : 0x33E4E4E2);
		}
	}
}
