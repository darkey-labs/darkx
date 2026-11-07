package dev.darkx.visuals.ui;

import dev.darkx.visuals.core.Battery;
import dev.darkx.visuals.core.Theme;
import dev.darkx.visuals.ui.UI.F;
import net.minecraft.client.gui.GuiGraphics;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Плашка в углу экрана при открытом меню: время и (если есть) заряд батареи. */
public final class StatusPill {
	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

	private StatusPill() {}

	public static void render(GuiGraphics g, int screenWidth) {
		Battery.poll();

		String time = LocalTime.now().format(FMT);
		int pct = Battery.percent();
		boolean hasBattery = pct >= 0;

		int h = 26, pad = 11;
		int iconW = UI.width(F.ICON, Icons.CLOCK);
		int timeW = UI.width(F.BOLD, time);
		int w = pad + iconW + 6 + timeW + pad;

		String pctText = pct + "%";
		int pctW = 0, battW = 22;
		boolean charging = Battery.charging();
		int boltW = 0;
		if (hasBattery) {
			pctW = UI.width(F.BOLD, pctText);
			if (charging) boltW = UI.width(F.ICON, Icons.BOLT) + 4;
			// 10 + разделитель 1 + 10, затем батарейка с подписью
			w += 21 + battW + 6 + pctW + boltW;
		}

		int x = screenWidth - w - 12, y = 12;
		UI.rrectBorder(g, x, y, w, h, Theme.hudR(9), 0xFF000000, 0x2AE4E4E2);

		int cy = y + h / 2;
		int cx = x + pad;
		UI.text(g, F.ICON, Icons.CLOCK, cx, cy, UI.TEXT_DIM);
		cx += iconW + 6;
		UI.text(g, F.BOLD, time, cx, cy, UI.TEXT);
		cx += timeW;

		if (!hasBattery) return;

		cx += 10;
		UI.fill(g, cx, y + 7, 1, h - 14, UI.LINE);
		cx += 1 + 10;

		int level = pct >= 50 ? 0xFF34D399 : pct >= 20 ? 0xFFFACC15 : 0xFFEF4444;
		int by = cy - 5;
		UI.rrectBorder(g, cx, by, battW, 10, 3, 0xFF000000, 0x66E4E4E2);
		int inner = Math.max(1, Math.round((battW - 4) * pct / 100f));
		UI.rrect(g, cx + 2, by + 2, inner, 6, 1, level);
		UI.fill(g, cx + battW, by + 3, 2, 4, 0x66E4E4E2);
		cx += battW + 6;

		UI.text(g, F.BOLD, pctText, cx, cy, UI.TEXT);
		cx += pctW;
		if (charging) UI.text(g, F.ICON, Icons.BOLT, cx + 4, cy, 0xFF34D399);
	}
}
