package dev.darkx.visuals.ui;

import dev.darkx.visuals.core.Theme;
import dev.darkx.visuals.ui.UI.F;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Общие рисовалки для своих меню: кнопки в стиле DarkX, раскладка и фон паузы, экран загрузки. */
public final class DarkMenus {
	private static Screen pauseScreen;
	private static int px, py, pw, ph;

	private DarkMenus() {}

	// ================================================================= кнопки

	/** Строка безопасна для шрифта Montserrat (латиница, кириллица, цифры, знаки). Иначе рисуем ванильным шрифтом. */
	private static boolean fontSafe(String s) {
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c > 0x024F && (c < 0x0400 || c > 0x04FF) && (c < 0x2000 || c > 0x206F)) return false;
		}
		return true;
	}

	/** Кнопка в стиле DarkX вместо ванильной. Вызывается из AbstractButtonMixin. */
	public static void drawButton(GuiGraphics g, AbstractWidget w) {
		float prev = UI.alpha;
		UI.alpha = 1f;
		int x = w.getX(), y = w.getY(), bw = w.getWidth(), bh = w.getHeight();
		boolean hov = w.active && w.isHoveredOrFocused();
		int r = Math.min(Theme.elemR(5), bh / 2);
		UI.rrectBorder(g, x, y, bw, bh, r,
			hov ? UI.SURFACE_TOP : UI.SURFACE_HI,
			hov ? Theme.accent(0.55f) : 0x2AE4E4E2);

		Minecraft mc = Minecraft.getInstance();
		Component msg = w.getMessage();
		int color = !w.active ? UI.MUTED : hov ? 0xFFEDEDEB : UI.TEXT;
		float cy = y + bh / 2f;
		if (fontSafe(msg.getString())) {
			F f = F.BODY;
			int tw = mc.font.width(UI.withFont(f, msg));
			if (tw > bw - 10) {
				f = F.SMALL;
				tw = mc.font.width(UI.withFont(f, msg));
			}
			UI.comp(g, f, msg, x + (bw - tw) / 2, cy, color);
		} else {
			g.drawCenteredString(mc.font, msg, x + bw / 2, y + (bh - 8) / 2, color);
		}
		UI.alpha = prev;
	}

	// ================================================================== пауза

	/** Перестраивает кнопки ванильной паузы в аккуратную колонку. Кнопки остаются ванильными — вся логика выхода цела. */
	public static void layoutPause(Screen s) {
		pauseScreen = null;
		List<AbstractButton> b = new ArrayList<>();
		for (GuiEventListener c : s.children()) {
			if (c instanceof AbstractButton ab) b.add(ab);
			else if (c instanceof StringWidget sw) sw.visible = false; // «Меню игры» рисуем сами
		}
		int n = b.size();
		if (n < 2) return;

		final int colW = 108, gap = 6, fullW = colW * 2 + gap, h = 24, pad = 14, head = 34;
		int mid = n - 2;
		int rows = (mid + 1) / 2;
		int midH = rows == 0 ? 0 : rows * (h + gap) - gap + 10;
		pw = fullW + pad * 2;
		ph = pad + head + h + 10 + midH + h + pad;
		px = (s.width - pw) / 2;
		py = Math.max(6, (s.height - ph) / 2);

		int x0 = px + pad;
		int y = py + pad + head;
		set(b.get(0), x0, y, fullW, h);
		y += h + 10;
		for (int i = 0; i < mid; i++) {
			int col = i % 2;
			boolean lastOdd = i == mid - 1 && mid % 2 == 1;
			set(b.get(1 + i), lastOdd ? x0 : x0 + col * (colW + gap), y, lastOdd ? fullW : colW, h);
			if (col == 1 || lastOdd) y += h + gap;
		}
		y = py + ph - pad - h;
		set(b.get(n - 1), x0, y, fullW, h);
		pauseScreen = s;
	}

	private static void set(AbstractWidget w, int x, int y, int bw, int bh) {
		w.setX(x);
		w.setY(y);
		w.setWidth(bw);
		w.setHeight(bh);
	}

	/** Фон паузы: затемнение, панель и шапка. Вызывается после ванильного фона, до кнопок. */
	public static void pauseBackdrop(GuiGraphics g, Screen s) {
		if (s != pauseScreen) return;
		float prev = UI.alpha;
		UI.alpha = 1f;
		g.fill(0, 0, s.width, s.height, 0xB0000000);
		UI.rrectBorder(g, px, py, pw, ph, Theme.menuR(12), UI.BG, 0x2AE4E4E2);
		Logo.draw(g, px + 14, py + 12, 22);
		UI.text(g, F.BOLD, "Пауза", px + 44, py + 23, UI.TEXT);
		UI.textR(g, F.SMALL, "DarkX Visuals", px + pw - 14, py + 23, UI.MUTED);
		UI.fill(g, px + 14, py + 14 + 26, pw - 28, 1, UI.LINE);
		UI.alpha = prev;
	}

	// ============================================================== загрузка

	/**
	 * Логотип и полоска прогресса вместо ванильного логотипа Mojang. Шрифтов тут нет намеренно:
	 * пока идёт загрузка ресурсов, шрифты клиента ещё не готовы.
	 */
	public static void drawLoading(GuiGraphics g, float progress, float fade) {
		float prev = UI.alpha;
		UI.alpha = Math.max(0f, Math.min(1f, fade));
		int w = g.guiWidth(), h = g.guiHeight();
		int cx = w / 2, cy = h / 2;
		int size = Math.max(48, Math.min(96, h / 5));
		int ly = cy - size / 2 - 14;
		UI.glow(g, cx - size / 2, ly, size, size, size / 2, Theme.accent(), 0.55f);
		Logo.draw(g, cx - size / 2, ly, size);

		int bw = Math.min(180, w - 40), bh = 3;
		int bx = cx - bw / 2, by = ly + size + 22;
		UI.rrect(g, bx, by, bw, bh, 1, 0xFF1B1B1B);
		int fillW = Math.round(bw * Math.max(0f, Math.min(1f, progress)));
		if (fillW > 1) UI.rrect(g, bx, by, fillW, bh, 1, Theme.accent());
		UI.alpha = prev;
	}
}
