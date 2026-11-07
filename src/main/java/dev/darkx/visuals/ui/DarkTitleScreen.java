package dev.darkx.visuals.ui;

import dev.darkx.visuals.core.Theme;
import dev.darkx.visuals.modules.MenusModule;
import dev.darkx.visuals.ui.UI.F;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Главное меню DarkX: чёрный фон, мягкие частицы, логотип и три кнопки. Подменяет ванильный TitleScreen
 * (см. MinecraftScreenMixin). Вёрстка идёт в «виртуальных» координатах от центра экрана и масштабируется,
 * чтобы помещаться при любом масштабе интерфейса.
 */
public final class DarkTitleScreen extends Screen {
	private static final int BLOCK_H = 312, BLOCK_W = 260;
	private static final int BW = 232, BH = 30, GAP = 8;

	private record Btn(String label, String icon, boolean primary, Runnable run) {}

	private final long openAt = System.nanoTime();
	private final List<Btn> btns = new ArrayList<>();
	private float scale = 1f;

	public DarkTitleScreen() {
		super(Component.empty());
	}

	@Override
	protected void init() {
		Minecraft mc = Minecraft.getInstance();
		btns.clear();
		btns.add(new Btn("Одиночная игра", Icons.USER, true, () -> mc.setScreen(new SelectWorldScreen(this))));
		btns.add(new Btn("Сетевая игра", Icons.WIFI, false, () -> mc.setScreen(new JoinMultiplayerScreen(this))));
		btns.add(new Btn("Настройки", Icons.SETTINGS, false, () -> mc.setScreen(new OptionsScreen(this, mc.options))));
		btns.add(new Btn("Выйти из игры", "", false, mc::stop));
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	/** Верхний левый угол кнопки i в виртуальных координатах (центр экрана = 0,0). */
	private int[] pos(int i) {
		int top = -BLOCK_H / 2;
		int y0 = top + 72 + 12 + 26 + 6 + 14 + 30; // под логотипом, названием и подписью
		if (i < 3) return new int[] {-BW / 2, y0 + i * (BH + GAP), BW, BH};
		return new int[] {-BW / 2 + 40, y0 + 3 * (BH + GAP) + 6, BW - 80, 24};
	}

	@Override
	public void render(GuiGraphics g, int rawX, int rawY, float partialTick) {
		float t = (System.nanoTime() - openAt) / 1.0e9f;
		UI.alpha = 1f;
		g.fill(0, 0, width, height, 0xFF000000);
		if (MenusModule.particlesOn()) particles(g, t);

		scale = Math.min(1f, Math.min((height - 16) / (float) BLOCK_H, (width - 16) / (float) BLOCK_W));
		float cx = width / 2f, cy = height / 2f;
		int[] m = UI.mouse();
		int mx = Math.round((m[0] - cx) / scale);
		int my = Math.round((m[1] - cy) / scale);

		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(scale, scale);

		// логотип с мягким свечением
		float lp = Motion.seg(t, 0f, 0.7f);
		float ls = Motion.lerp(0.8f, 1f, Motion.outBack(lp));
		int top = -BLOCK_H / 2;
		UI.alpha = Motion.outCubic(Motion.seg(t, 0f, 0.5f));
		Motion.scaleAround(g, 0f, top + 36f, ls);
		UI.glow(g, -36, top, 72, 72, 36, Theme.accent(), 0.6f);
		Logo.draw(g, -36, top, 72);
		pose.popMatrix();

		// название и подпись
		float a1 = Motion.outCubic(Motion.seg(t, 0.12f, 0.5f));
		UI.alpha = a1;
		UI.textC(g, F.BOLD, "DarkX", 0f, top + 72 + 12 + 9 + (1f - a1) * 8f, UI.TEXT);
		UI.textC(g, F.SMALL, "Экосистема для Minecraft", 0f, top + 72 + 12 + 26 + 6 + (1f - a1) * 8f, UI.TEXT_DIM);

		// кнопки
		for (int i = 0; i < btns.size(); i++) {
			Btn b = btns.get(i);
			int[] r = pos(i);
			float a = Motion.outCubic(Motion.seg(t, 0.22f + 0.07f * i, 0.5f));
			UI.alpha = a;
			int y = r[1] + Math.round((1f - a) * 10f);
			boolean hov = UI.inside(mx, my, r[0], r[1], r[2], r[3]);
			drawButton(g, b, r[0], y, r[2], r[3], hov, i);
		}

		// подвал
		UI.alpha = Motion.outCubic(Motion.seg(t, 0.6f, 0.6f));
		int bottom = BLOCK_H / 2;
		UI.textC(g, F.SMALL, "Правый Shift в игре — меню DarkX", 0f, bottom - 2, UI.MUTED);
		pose.popMatrix();

		// версии по углам экрана, без масштаба
		UI.alpha = Motion.outCubic(Motion.seg(t, 0.6f, 0.6f));
		String ver = FabricLoader.getInstance().getModContainer("darkx")
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		UI.text(g, F.SMALL, "DarkX Visuals " + ver, 10, height - 12, UI.MUTED);
		UI.textR(g, F.SMALL, "Minecraft " + Minecraft.getInstance().getLaunchedVersion(), width - 10, height - 12, UI.MUTED);
		UI.alpha = 1f;
	}

	private void drawButton(GuiGraphics g, Btn b, int x, int y, int w, int h, boolean hov, int idx) {
		float hv = UI.anim(this.getClass().getName() + idx, hov ? 1f : 0f, 16f);
		int r = Math.min(Theme.menuR(8), h / 2);
		if (b.primary) {
			UI.solid(g, x, y, w, h, r, hv, 0.5f);
		} else {
			UI.rrectBorder(g, x, y, w, h, r, hov ? UI.SURFACE_TOP : UI.SURFACE_HI, Theme.accent(0.12f + 0.45f * hv));
		}
		int color = b.primary ? 0xFF0A0A0A : UI.TEXT;
		float cy = y + h / 2f;
		if (b.icon.isEmpty()) {
			UI.textC(g, F.BODY, b.label, x + w / 2f, cy, hov ? UI.TEXT : UI.TEXT_DIM);
			return;
		}
		int tw = UI.width(F.BODY, b.label);
		int total = 14 + 10 + tw;
		int sx = x + (w - total) / 2;
		UI.text(g, F.ICON, b.icon, sx, cy, color);
		UI.text(g, F.BODY, b.label, sx + 24, cy, color);
	}

	/** Редкие плавающие точки: поднимаются вверх и мерцают. Позиции считаются из индекса, без состояния. */
	private void particles(GuiGraphics g, float t) {
		final int n = 46;
		for (int i = 0; i < n; i++) {
			float h1 = frac(i * 0.61803f + 0.13f);
			float h2 = frac(i * 0.38197f + 0.71f);
			float h3 = frac(i * 0.754877f + 0.37f);
			float speed = 0.008f + 0.02f * h3;
			float x = h1 * width + (float) Math.sin(t * 0.3f + i) * 6f;
			float y = frac(h2 - t * speed) * height;
			float tw = 0.5f + 0.5f * (float) Math.sin(t * (0.6f + h3) + i * 1.7f);
			float a = (0.07f + 0.22f * tw) * Math.min(1f, Math.min(y, height - y) / 40f);
			int size = h3 > 0.8f ? 2 : 1;
			int col = i % 3 == 0 ? Theme.accent() : 0xFFB8B8B6;
			UI.fill(g, Math.round(x), Math.round(y), size, size, Theme.withAlpha(col, a));
		}
	}

	private static float frac(float v) {
		return v - (float) Math.floor(v);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			int[] m = UI.mouse();
			int mx = Math.round((m[0] - width / 2f) / scale);
			int my = Math.round((m[1] - height / 2f) / scale);
			for (int i = 0; i < btns.size(); i++) {
				int[] r = pos(i);
				if (UI.inside(mx, my, r[0], r[1], r[2], r[3])) {
					Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					btns.get(i).run.run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}
}
