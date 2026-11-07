package dev.darkx.visuals.hud;

import dev.darkx.visuals.core.Theme;
import dev.darkx.visuals.ui.UI;
import dev.darkx.visuals.ui.UI.F;
import net.minecraft.client.gui.GuiGraphics;

/** Один худ. Координаты — в GUI-пикселях. Размер (w, h) обновляется при каждой отрисовке. */
public abstract class HudWidget {
	public final String id;
	public final String title;
	public int x, y, w, h;
	public boolean placed;
	/** Масштаб худа (меняется углом в редакторе). */
	public float scale = 1f;
	public static final float MIN_SCALE = 0.5f, MAX_SCALE = 2.5f;
	/** Иконка в шапке худа (Lucide), пусто — без иконки. */
	protected String icon = "";

	protected HudWidget(String id, String title, int w, int h) {
		this.id = id;
		this.title = title;
		this.w = w;
		this.h = h;
	}

	/** Позиция по умолчанию для экрана sw x sh. */
	public abstract void place(int sw, int sh);

	/**
	 * Рисует виджет в (x, y).
	 * @param preview true в редакторе: показывать демо-данные вместо реальных
	 */
	public abstract void render(GuiGraphics g, boolean preview);

	/** true — худ сам рисует появление/исчезновение (Таргет, Неар, Список модулей). */
	public boolean selfAnimated() {
		return false;
	}

	/** Размер на экране с учётом масштаба. */
	public int sw() {
		return Math.round(Math.max(w, 16) * scale);
	}

	public int sh() {
		return Math.round(Math.max(h, 16) * scale);
	}

	public boolean contains(int mx, int my) {
		return UI.inside(mx, my, x, y, sw(), sh());
	}

	/** Рисует худ с учётом масштаба (якорь — левый верхний угол). */
	public final void draw(GuiGraphics g, boolean preview) {
		if (Math.abs(scale - 1f) < 0.001f) {
			render(g, preview);
			return;
		}
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		pose.translate(-x, -y);
		render(g, preview);
		pose.popMatrix();
	}

	/** Высота шапки худа с заголовком. */
	protected static final int HEAD = 20;

	/** Минимальная ширина худа, чтобы влез заголовок. */
	protected int headW(String title) {
		return UI.width(F.BOLD, title) + 24 + iconW();
	}

	/** Ширина иконки шапки вместе с отступом до текста. */
	protected int iconW() {
		return icon.isEmpty() ? 0 : UI.width(F.ICON_S, icon) + 6;
	}

	/** Тёмная плашка худа без заголовка (Таргет, Профиль). */
	protected void panel(GuiGraphics g, int px, int py, int pw, int ph) {
		HudManager.drawPanel(g, px, py, pw, ph, Theme.hudR(7));
	}

	/** Плашка с заголовком сверху. Содержимое рисуется от y + HEAD. */
	protected void panel(GuiGraphics g, int px, int py, int pw, int ph, String header) {
		panel(g, px, py, pw, ph);
		int tx = px + 10;
		if (!icon.isEmpty()) {
			UI.text(g, F.ICON_S, icon, tx, py + HEAD / 2f, Theme.accent());
			tx += iconW();
		}
		UI.text(g, F.BOLD, header, tx, py + HEAD / 2f, Theme.accent());
		UI.fill(g, px + 8, py + HEAD - 1, pw - 16, 1, UI.LINE);
	}
}
