package dev.darkx.visuals.ui;

import dev.darkx.visuals.DarkXClient;
import dev.darkx.visuals.audio.ClientSounds;
import dev.darkx.visuals.audio.Sfx;
import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Ecosystem;
import dev.darkx.visuals.core.Notifications;
import dev.darkx.visuals.core.Waypoints;
import dev.darkx.visuals.hud.HudManager;
import dev.darkx.visuals.hud.Widgets;
import dev.darkx.visuals.core.Config;
import dev.darkx.visuals.core.Friends;
import dev.darkx.visuals.core.Marks;
import dev.darkx.visuals.modules.NetworkModule;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.core.Stats;
import dev.darkx.visuals.core.Theme;
import dev.darkx.visuals.ui.UI.F;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Главное меню клиента. Открывается на правый Shift.
 * Мир под ним становится чёрно-белым (пост-эффект darkx:grayscale), по центру — OLED-панель.
 */
public class ClickGuiScreen extends Screen {
	static final Identifier GRAYSCALE = Identifier.fromNamespaceAndPath("darkx", "grayscale");

	private enum Tab {
		FUNCTIONS("Функции", Icons.GRID),
		MARKS("Метки", Icons.TAG),
		FRIENDS("Друзья", Icons.HEART),
		MARKERS("Маркеры", Icons.PIN),
		THEMES("Темы", Icons.PALETTE),
		PROFILE("Профиль", Icons.USER);

		final String title;
		final String icon;

		Tab(String title, String icon) {
			this.title = title;
			this.icon = icon;
		}
	}

	private static final int SB = 140; // ширина сайдбара

	private record Hit(int x, int y, int w, int h, IntConsumer action) {}

	private static Tab lastTab = Tab.FUNCTIONS;
	private static Category lastCategory = Category.VISUAL;

	private final List<Hit> hits = new ArrayList<>();
	private int clipX1, clipY1, clipX2, clipY2;
	private int px, py, pw, ph;

	private Tab tab = lastTab;
	private Category category = lastCategory;

	// скролл (целевые значения; отображаемые сглаживаются в UI.anim)
	private float fnScrollT, marksScrollT, popScrollT, friendsScrollT, themesScrollT;

	// всплывающие настройки модуля
	private Module popup;
	private int popX, popY, popW, popH;
	private int anchorX, anchorY, anchorW, anchorH;
	private final Set<Setting> expanded = new HashSet<>();

	// перетаскивание ползунков
	private Setting.Num dragNum;
	private boolean dragHue;
	private int dragRad; // 1 — меню, 2 — элементы, 3 — худы
	private int dragX, dragW;
	private Setting.Key listening;

	// ввод текста (поле в настройках модуля или в окне создания маркера)
	private static final class Field {
		String text;
		Field(String t) { text = t; }
	}

	private Object editing;
	private boolean editNumeric;
	private int editMax = 24;

	// вкладка «Маркеры» и окно создания
	private float markersScrollT;
	private boolean creating;
	private final Field mkName = new Field("");
	private final Field mkX = new Field("");
	private final Field mkY = new Field("");
	private final Field mkZ = new Field("");
	private int mkWorld, mkColor, mkIcon;

	private boolean applied;
	private boolean prevHideGui;

	// анимации показа: открытие, закрытие, смена вкладки, каскад плиток
	private static final float OPEN_DUR = 0.34f, CLOSE_DUR = 0.18f;
	private static final IntConsumer NOOP = b -> {};
	private long openAt = System.nanoTime();
	private long tabAt = openAt, gridAt = openAt, closeAt;
	private boolean closing, closeDone;
	private float tcx, tcy, scl = 1f, offY; // текущий трансформ панели (нужен для мыши)
	private int shiftY;                     // сдвиг контента при въезде вкладки (учитывается в хитбоксах)

	private record Dd(Setting s) {}         // ключ анимации раскрытия выпадающего списка

	public ClickGuiScreen() {
		super(Component.literal("DarkX Visuals"));
	}

	// =============================================================== жизненный цикл

	@Override
	protected void init() {
		Minecraft mc = Minecraft.getInstance();
		if (!applied) {
			applied = true;
			prevHideGui = mc.options.hideGui;
			mc.options.hideGui = true;
			((dev.darkx.visuals.mixin.GameRendererAccessor) mc.gameRenderer).darkx$setPostEffect(GRAYSCALE);
			openAt = tabAt = gridAt = System.nanoTime();
			ClientSounds.play(Sfx.OPEN);
		}
	}

	@Override
	public void removed() {
		Minecraft mc = Minecraft.getInstance();
		if (applied) {
			applied = false;
			mc.options.hideGui = prevHideGui;
			mc.gameRenderer.clearPostEffect();
		}
		editing = null;
		creating = false;
		lastTab = tab;
		lastCategory = category;
		Config.saveIfDirty();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Закрытие с анимацией: экран убирается из tick() после CLOSE_DUR. */
	@Override
	public void onClose() {
		if (closing) return;
		closing = true;
		closeAt = System.nanoTime();
		editing = null;
		listening = null;
		ClientSounds.play(Sfx.CLOSE);
	}

	@Override
	public void tick() {
		super.tick();
		if (closing && !closeDone && (System.nanoTime() - closeAt) / 1.0e9f >= CLOSE_DUR) {
			closeDone = true;
			super.onClose();
		}
	}

	// ==================================================================== утилиты

	private void hit(int x, int y, int w, int h, IntConsumer action) {
		int x1 = Math.max(x, clipX1), y1 = Math.max(y, clipY1);
		int x2 = Math.min(x + w, clipX2), y2 = Math.min(y + h, clipY2);
		if (x2 <= x1 || y2 <= y1) return;
		hits.add(new Hit(x1, y1 + shiftY, x2 - x1, y2 - y1, action));
	}

	private void scissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
		g.enableScissor(x1, y1, x2, y2);
		clipX1 = Math.max(x1, px);
		clipY1 = Math.max(y1, py);
		clipX2 = Math.min(x2, px + pw);
		clipY2 = Math.min(y2, py + ph);
	}

	private void unscissor(GuiGraphics g) {
		g.disableScissor();
		resetClip();
	}

	private void resetClip() {
		clipX1 = px;
		clipY1 = py;
		clipX2 = px + pw;
		clipY2 = py + ph;
	}

	private static float clamp(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	// ====================================================================== render

	@Override
	public void render(GuiGraphics g, int rawX, int rawY, float partialTick) {
		hits.clear();
		long now = System.nanoTime();
		float in = Motion.clamp01((now - openAt) / 1.0e9f / OPEN_DUR);
		float out = closing ? Motion.clamp01((now - closeAt) / 1.0e9f / CLOSE_DUR) : 0f;
		float show = closing ? 1f - Motion.inCubic(out) : Motion.outCubic(Math.min(1f, in * 1.5f));
		float scale = closing ? Motion.lerp(1f, 0.94f, Motion.inCubic(out)) : Motion.lerp(0.9f, 1f, Motion.outBack(in));
		float dy = closing ? Motion.inCubic(out) * 12f : (1f - Motion.outCubic(in)) * 22f;

		// затемнение мира
		UI.alpha = 1f;
		g.fill(0, 0, width, height, ((int) (0x70 * show)) << 24);
		UI.alpha = show;

		pw = Math.min(690, width - 24);
		ph = Math.min(414, height - 24);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		resetClip();

		// трансформ панели: масштаб вокруг центра + небольшой сдвиг; мышь переводим в локальные координаты
		tcx = width / 2f;
		tcy = height / 2f;
		scl = scale;
		offY = dy;
		int mouseX = Math.round(tcx + (rawX - tcx) / scale);
		int mouseY = Math.round(tcy + (rawY - tcy - dy) / scale);
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(tcx, tcy + dy);
		pose.scale(scale, scale);
		pose.translate(-tcx, -tcy);

		UI.rrectBorder(g, px, py, pw, ph, Theme.menuR(12), UI.BG, 0x2AE4E4E2);
		renderSidebar(g, mouseX, mouseY);
		UI.fill(g, px + SB, py + 14, 1, ph - 28, UI.LINE);

		// контент вкладки въезжает снизу с проявлением
		float tabE = Motion.outCubic((now - tabAt) / 1.0e9f / 0.28f);
		float base = UI.alpha;
		UI.alpha = base * Math.min(1f, tabE * 1.4f);
		shiftY = Math.round((1f - tabE) * 10f);
		pose.pushMatrix();
		pose.translate(0f, shiftY);
		switch (tab) {
			case FUNCTIONS -> renderFunctions(g, mouseX, mouseY);
			case MARKS -> renderMarks(g, mouseX, mouseY);
			case FRIENDS -> renderFriends(g, mouseX, mouseY);
			case MARKERS -> renderMarkers(g, mouseX, mouseY);
			case THEMES -> renderThemes(g, mouseX, mouseY);
			case PROFILE -> renderProfile(g, mouseX, mouseY);
		}
		pose.popMatrix();
		shiftY = 0;
		UI.alpha = base;

		renderPopup(g, mouseX, mouseY);
		renderCreate(g, mouseX, mouseY);
		pose.popMatrix();

		UI.alpha = show;
		StatusPill.render(g, width);
		UI.alpha = 1f;
	}

	/** x мыши в локальных координатах панели (для перетаскивания ползунков). */
	private int localX() {
		return Math.round(tcx + (UI.mouse()[0] - tcx) / scl);
	}

	// ==================================================================== сайдбар

	private void sideButton(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String icon, String label,
							boolean selected, String id, IntConsumer click) {
		boolean hov = UI.inside(mx, my, x, y, w, h);
		float sel = UI.anim(id + ".sel", selected ? 1f : 0f, 16f);
		float hv = UI.anim(id + ".hov", hov ? 1f : 0f, 22f);
		int ac = Theme.accent();
		UI.glow(g, x, y, w, h, Theme.elemR(8), ac, sel);
		UI.rrect(g, x, y, w, h, Theme.elemR(8), UI.withAlpha(0xFF0E0E0E, hv * (1f - sel)));
		UI.rrect(g, x, y, w, h, Theme.elemR(8), UI.withAlpha(Theme.mix(ac, UI.TEXT, 0.12f * hv), sel));
		float cy = y + h / 2f;
		UI.text(g, F.ICON, icon, x + 13, cy, Theme.mix(UI.TEXT_DIM, 0xFF000000, sel));
		F font = UI.width(F.BODY, label) > w - 42 ? F.SMALL : F.BODY; // длинные подписи не вылезают за кнопку
		UI.text(g, font, label, x + 36, cy, Theme.mix(Theme.mix(UI.TEXT_DIM, UI.TEXT, hv), 0xFF000000, sel));
		hit(x, y, w, h, click);
	}

	private void renderSidebar(GuiGraphics g, int mx, int my) {
		Minecraft mc = Minecraft.getInstance();
		int x = px + 12, w = SB - 24;

		float age = (System.nanoTime() - openAt) / 1.0e9f;
		float baseA = UI.alpha;
		float le = Motion.outBack(Motion.seg(age, 0.02f, 0.45f));
		UI.alpha = baseA * Motion.clamp01(le * 1.2f);
		Motion.scaleAround(g, x + 4 + 17, py + 15 + 17, 0.55f + 0.45f * le);
		Logo.draw(g, x + 4, py + 15, 34);
		g.pose().popMatrix();
		float te = Motion.outCubic(Motion.seg(age, 0.10f, 0.30f));
		UI.alpha = baseA * te;
		UI.text(g, F.TITLE, "DarkX", x + 44 + Math.round((1f - te) * 8f), py + 28, UI.TEXT);
		UI.text(g, F.SMALL, "VISUALS", x + 44 + Math.round((1f - te) * 12f), py + 44, Theme.accent());
		UI.alpha = baseA;

		int ty = py + 68;
		int idx = 0;
		for (Tab t : new Tab[] {Tab.FUNCTIONS, Tab.MARKS, Tab.FRIENDS, Tab.MARKERS, Tab.THEMES}) {
			final Tab target = t;
			float e = Motion.outCubic(Motion.seg(age, 0.06f + 0.045f * idx++, 0.30f));
			UI.alpha = baseA * e;
			sideButton(g, mx, my, x - Math.round((1f - e) * 18f), ty, w, 32, t.icon, t.title, tab == t, "tab." + t.name(), b -> switchTab(target));
			ty += 36;
		}
		UI.alpha = baseA;

		// низ: «Переместить ХУДы» и профиль
		int profH = 40;
		int profY = py + ph - 12 - profH;
		int moveY = profY - 6 - 32;
		float eb = Motion.outCubic(Motion.seg(age, 0.30f, 0.30f));
		UI.alpha = baseA * eb;
		sideButton(g, mx, my, x - Math.round((1f - eb) * 18f), moveY, w, 32, Icons.MOVE, "Переместить ХУДы", false, "move",
			b -> {
				ClientSounds.play(Sfx.POPUP_OPEN, 0.9f);
				mc.setScreen(new HudEditorScreen());
			});

		float ep = Motion.outCubic(Motion.seg(age, 0.34f, 0.30f));
		UI.alpha = baseA * ep;
		x -= Math.round((1f - ep) * 18f);
		boolean sel = tab == Tab.PROFILE;
		boolean hov = UI.inside(mx, my, x, profY, w, profH);
		float s = UI.anim("tab.PROFILE.sel", sel ? 1f : 0f, 16f);
		float hv = UI.anim("tab.PROFILE.hov", hov ? 1f : 0f, 22f);
		int bg = Theme.mix(Theme.mix(0xFF080808, 0xFF0E0E0E, hv), Theme.accent(), s);
		UI.glow(g, x, profY, w, profH, 9, Theme.accent(), s);
		UI.rrectBorder(g, x, profY, w, profH, Theme.elemR(9), bg, Theme.mix(UI.LINE, Theme.accent(), s));
		if (mc.player != null) {
			UI.head(g, mc.player.getSkin(), x + 8, profY + 8, 24, 12, bg);
			String nick = UI.fit(F.BOLD, mc.player.getName().getString(), w - 8 - 24 - 10 - 8);
			UI.text(g, F.BOLD, nick, x + 40, profY + 15, Theme.mix(UI.TEXT, 0xFF000000, s));
			UI.text(g, F.SMALL, "Профиль", x + 40, profY + 28, Theme.mix(UI.TEXT_DIM, 0xFF1A1A1A, s));
		}
		hit(x, profY, w, profH, b -> switchTab(Tab.PROFILE));
		UI.alpha = baseA;
	}

	private void switchTab(Tab t) {
		if (tab == t) return;
		ClientSounds.play(Sfx.TAB);
		tab = t;
		tabAt = gridAt = System.nanoTime();
		popup = null;
		listening = null;
		editing = null;
		creating = false;
		UI.reset("pop", 0f);
		if (t == Tab.FRIENDS) Friends.refresh();
	}

	// =================================================================== функции

	private void renderFunctions(GuiGraphics g, int mx, int my) {
		int cx = px + SB + 18, cw = pw - SB - 36;
		UI.text(g, F.TITLE, "Функции", cx, py + 28, UI.TEXT);
		UI.textR(g, F.SMALL, ModuleManager.enabledCount() + " из " + ModuleManager.all().size() + " включено",
			cx + cw, py + 28, UI.MUTED);

		// подвкладки-категории
		int pillX = cx, pillY = py + 48;
		for (Category c : Category.values()) {
			String label = c.title;
			int tw = UI.width(F.BODY, label);
			int w = 12 + 8 + 6 + tw + 14;
			boolean sel = c == category;
			boolean hov = UI.inside(mx, my, pillX, pillY, w, 24);
			float a = UI.anim("cat." + c.name(), sel ? 1f : 0f, 16f);
			float hv = UI.anim("cat.h." + c.name(), hov ? 1f : 0f, 20f);
			UI.rrect(g, pillX, pillY, w, 24, Theme.elemR(12), Theme.mix(Theme.mix(0xFF0C0C0C, 0xFF131313, hv), Theme.accent(0.2f), a));
			UI.text(g, F.ICON_S, c.icon, pillX + 12, pillY + 12, Theme.mix(UI.TEXT_DIM, Theme.accent(), a));
			UI.text(g, F.BODY, label, pillX + 12 + 8 + 6, pillY + 12, Theme.mix(UI.TEXT_DIM, UI.TEXT, Math.max(a, hv)));
			final Category target = c;
			hit(pillX, pillY, w, 24, b -> {
				if (category != target) {
					ClientSounds.play(Sfx.TAB, 1.12f);
					category = target;
					fnScrollT = 0;
					popup = null;
					gridAt = System.nanoTime();
				}
			});
			pillX += w + 8;
		}

		// сетка модулей
		int vy1 = py + 84, vy2 = py + ph - 38;
		List<Module> mods = ModuleManager.byCategory(category);
		int cols = cw >= 430 ? 3 : 2, gap = 10;
		int tw = (cw - gap * (cols - 1)) / cols, th = 58;
		int rows = (mods.size() + cols - 1) / cols;
		int contentH = rows * (th + gap) - gap;
		float maxScroll = Math.max(0, contentH - (vy2 - vy1));
		fnScrollT = clamp(fnScrollT, 0, maxScroll);
		int scroll = Math.round(UI.anim("scroll.fn", fnScrollT, 18f));

		float gridAge = (System.nanoTime() - gridAt) / 1.0e9f;
		scissor(g, cx - 4, vy1, cx + cw + 4, vy2);
		for (int i = 0; i < mods.size(); i++) {
			int tx = cx + (i % cols) * (tw + gap);
			int ty = vy1 + (i / cols) * (th + gap) - scroll;
			if (ty + th < vy1 || ty > vy2) continue;
			float e = Motion.outCubic(Motion.seg(gridAge, 0.03f * Math.min(i, 14), 0.30f));
			float baseA = UI.alpha;
			UI.alpha = baseA * e;
			renderTile(g, mx, my, mods.get(i), tx, ty + Math.round((1f - e) * 14f), tw, th);
			UI.alpha = baseA;
		}
		unscissor(g);

		UI.text(g, F.SMALL, "ЛКМ — включить / выключить", cx, py + ph - 22, UI.MUTED);
		UI.text(g, F.SMALL, "ПКМ — настройки модуля", cx + UI.width(F.SMALL, "ЛКМ — включить / выключить") + 20, py + ph - 22, UI.MUTED);
	}

	private void renderTile(GuiGraphics g, int mx, int my, Module m, int x, int y, int w, int h) {
		boolean hov = UI.inside(mx, my, x, y, w, h) && UI.inside(mx, my, clipX1, clipY1, clipX2 - clipX1, clipY2 - clipY1)
			&& !(popup != null && UI.inside(mx, my, popX, popY, popW, popH));
		float on = UI.anim(m.name + "#on", m.enabled ? 1f : 0f, 16f);
		float hv = UI.anim(m.name + "#hov", hov ? 1f : 0f, 20f);
		boolean isPopup = popup == m;
		if (isPopup) {
			anchorX = x;
			anchorY = y;
			anchorW = w;
			anchorH = h;
		}
		float pulse = UI.anim(m.name + "#pulse", 0f, 6f);
		int fill = Theme.mix(Theme.mix(0xFF0A0A0A, 0xFF111111, hv), Theme.tint(0.3f), pulse * 0.8f);
		int border = Theme.mix(UI.LINE, Theme.accent(0.6f), Math.max(Math.max(on * 0.85f, isPopup ? 1f : 0f), pulse));
		UI.rrectBorder(g, x, y, w, h, Theme.elemR(9), fill, border);

		UI.text(g, F.BOLD, m.name, x + 14, y + 20, UI.TEXT);
		UI.text(g, F.SMALL, UI.fit(F.SMALL, m.description, w - 28 - 30), x + 14, y + 38, UI.TEXT_DIM);
		UI.toggle(g, m.name + "#tile", x + w - 14 - 26, y + 13, m.enabled);
		UI.text(g, F.ICON, Icons.SETTINGS, x + w - 25, y + h - 14, Theme.mix(UI.MUTED, Theme.accent(), Math.max(hv * 0.6f, isPopup ? 1f : 0f)));

		hit(x, y, w, h, b -> {
			if (b == 0) {
				UI.reset(m.name + "#pulse", 1f);
				m.toggle();
			} else if (b == 1) {
				openPopup(m);
			}
		});
	}

	private void openPopup(Module m) {
		if (popup == m) {
			popup = null;
			ClientSounds.play(Sfx.POPUP_CLOSE);
			return;
		}
		ClientSounds.play(Sfx.POPUP_OPEN);
		popup = m;
		for (Setting st : m.settings) UI.reset(new Dd(st), 0f);
		popScrollT = 0;
		listening = null;
		expanded.clear();
		UI.reset("pop", 0f);
		UI.reset("pop-scroll", 0f);
	}

	// ================================================================ всплывашка

	/** 0..1: насколько раскрыт выпадающий список (плавно). */
	private float ddAnim(Setting s) {
		float t = UI.anim(new Dd(s), expanded.contains(s) ? 1f : 0f, 17f);
		return t;
	}

	private int[] clipPush(GuiGraphics g, int x1, int y1, int x2, int y2) {
		int[] saved = {clipX1, clipY1, clipX2, clipY2};
		g.enableScissor(x1, y1, x2, y2);
		clipX1 = Math.max(clipX1, x1);
		clipY1 = Math.max(clipY1, y1);
		clipX2 = Math.min(clipX2, x2);
		clipY2 = Math.min(clipY2, y2);
		return saved;
	}

	private void clipPop(GuiGraphics g, int[] saved) {
		g.disableScissor();
		clipX1 = saved[0];
		clipY1 = saved[1];
		clipX2 = saved[2];
		clipY2 = saved[3];
	}

	private int rowHeight(Setting s) {
		if (s instanceof Setting.Bool) return 30;
		if (s instanceof Setting.Num) return 42;
		if (s instanceof Setting.Color) return 46;
		if (s instanceof Setting.Text) return 56;
		if (s instanceof Setting.Mode md) return 32 + Math.round((md.modes.length * 24 + 8) * ddAnim(s));
		if (s instanceof Setting.Multi mu) return 32 + Math.round((mu.options.length * 24 + 8) * ddAnim(s));
		return 30;
	}

	private void renderPopup(GuiGraphics g, int mx, int my) {
		if (popup == null || tab != Tab.FUNCTIONS) return;
		Module m = popup;
		float o = UI.anim("pop", 1f, 18f);

		List<Setting> vis = new ArrayList<>();
		for (Setting s : m.settings) if (s.isVisible()) vis.add(s);
		int contentH = 48;
		for (Setting s : vis) contentH += rowHeight(s);
		contentH += 8;

		int w = Math.min(Math.max(250, Math.min(anchorW, 320)), pw - SB - 28);
		int fullH = Math.min(contentH, ph - 84);
		int h = Math.max(2, Math.round(fullH * UI.ease(o)));
		int x = Math.max(px + SB + 12, Math.min(anchorX, px + pw - 12 - w));
		int y = Math.min(anchorY + anchorH + 6, py + ph - 12 - fullH);
		y = Math.max(y, py + 70);
		popX = x;
		popY = y;
		popW = w;
		popH = h;

		UI.rrect(g, x - 3, y - 3, w + 6, h + 6, Theme.elemR(13), 0x55000000);
		UI.rrectBorder(g, x, y, w, h, Theme.elemR(10), 0xFF090909, Theme.accent(0.5f));
		hit(x, y, w, h, NOOP); // блокирует клики под всплывашкой

		float maxScroll = Math.max(0, contentH - fullH);
		popScrollT = clamp(popScrollT, 0, maxScroll);
		int scroll = Math.round(UI.anim("pop-scroll", popScrollT, 18f));

		scissor(g, x + 1, y + 1, x + w - 1, y + h - 1);
		int ry = y - scroll;

		UI.text(g, F.BOLD, m.name, x + 16, ry + 22, UI.TEXT);
		UI.toggle(g, m.name + "#pop", x + w - 16 - 26, ry + 15, m.enabled);
		hit(x + w - 16 - 26 - 4, ry + 10, 34, 24, b -> m.toggle());
		UI.fill(g, x + 14, ry + 41, w - 28, 1, UI.LINE);
		ry += 48;

		for (Setting s : vis) ry = drawSetting(g, mx, my, s, x, ry, w);
		unscissor(g);

		if (maxScroll > 0) { // тонкий скролл-бар
			int trackH = h - 16;
			int thumbH = Math.max(18, Math.round(trackH * fullH / (float) contentH));
			int thumbY = y + 8 + Math.round((trackH - thumbH) * (scroll / maxScroll));
			UI.rrect(g, x + w - 5, thumbY, 2, thumbH, 1, 0x55E4E4E2);
		}
	}

	private int drawSetting(GuiGraphics g, int mx, int my, Setting s, int x, int ry, int w) {
		int rh = rowHeight(s);

		if (s instanceof Setting.Bool b) {
			UI.text(g, F.BODY, s.name, x + 16, ry + rh / 2f, UI.TEXT);
			UI.toggle(g, s, x + w - 16 - 26, ry + (rh - 14) / 2, b.value);
			hit(x + 8, ry, w - 16, rh, btn -> {
				if (btn == 0) {
					b.value = !b.value;
					Config.dirty = true;
					ClientSounds.play(b.value ? Sfx.ON : Sfx.OFF, 1.08f);
				}
			});

		} else if (s instanceof Setting.Num n) {
			UI.text(g, F.BODY, s.name, x + 16, ry + 13, UI.TEXT_DIM);
			UI.textR(g, F.SMALL, n.display(), x + w - 16, ry + 13, UI.TEXT);
			int tx = x + 16, tw = w - 32, ty = ry + 27;
			UI.rrect(g, tx, ty, tw, 4, 2, 0xFF1E1E1E);
			float r = clamp(n.ratio(), 0f, 1f);
			if (r > 0.001f) UI.rrect(g, tx, ty, Math.max(4, Math.round(tw * r)), 4, 2, Theme.accent());
			UI.circle(g, tx + Math.round(tw * r), ty + 2, 11, UI.TEXT);
			hit(x + 8, ry + 19, w - 16, 20, btn -> {
				if (btn == 0) {
					dragNum = n;
					dragX = tx;
					dragW = tw;
					applyDrag(localX());
				}
			});

		} else if (s instanceof Setting.Color c) {
			UI.text(g, F.BODY, s.name, x + 16, ry + 13, UI.TEXT_DIM);
			UI.textR(g, F.SMALL, c.get(), x + w - 16, ry + 13, UI.TEXT);
			int n = c.count(), d = 14;
			int span = w - 32;
			int step = (span - d) / (n - 1);
			for (int i = 0; i < n; i++) {
				int sx = x + 16 + d / 2 + i * step, sy = ry + 33;
				boolean sel = c.index == i;
				if (sel) UI.circle(g, sx, sy, d + 6, 0x66E4E4E2);
				UI.circle(g, sx, sy, d + (sel ? 0 : -2), 0xFF090909);
				UI.circle(g, sx, sy, d - 2 - (sel ? 0 : 2), Setting.Color.swatch(i));
				final int idx = i;
				hit(sx - step / 2, ry + 22, step, 22, btn -> {
					if (c.index != idx) ClientSounds.play(Sfx.SELECT, 0.9f + 0.05f * idx);
					c.index = idx;
					Config.dirty = true;
				});
			}

		} else if (s instanceof Setting.Mode md) {
			boolean open = expanded.contains(s);
			float dd = ddAnim(s);
			String value = md.get();
			drawDropdownHeader(g, s, value, x, ry, w, open);
			if (dd > 0.01f) {
				int[] sv = clipPush(g, x, ry + 30, x + w, ry + rh);
				float aBase = UI.alpha;
				UI.alpha = aBase * dd;
				UI.rrect(g, x + 12, ry + 32, w - 24, md.modes.length * 24 + 4, Theme.elemR(8), 0xFF0F0F0F);
				for (int i = 0; i < md.modes.length; i++) {
					int oy = ry + 34 + i * 24;
					boolean sel = md.index == i;
					boolean hov = UI.inside(mx, my, x + 14, oy, w - 28, 22);
					if (hov) UI.rrect(g, x + 14, oy, w - 28, 22, Theme.elemR(6), 0xFF171717);
					UI.text(g, F.BODY, md.modes[i], x + 26, oy + 11, sel ? Theme.accent() : UI.TEXT);
					if (sel) UI.text(g, F.ICON_S, Icons.CHECK, x + w - 38, oy + 11, Theme.accent());
					final int idx = i;
					hit(x + 14, oy, w - 28, 22, btn -> {
						if (md.index != idx) ClientSounds.play(Sfx.SELECT);
						else ClientSounds.play(Sfx.POPUP_CLOSE, 1.2f);
						md.index = idx;
						expanded.remove(s);
						Config.dirty = true;
					});
				}
				UI.alpha = aBase;
				clipPop(g, sv);
			}

		} else if (s instanceof Setting.Multi mu) {
			boolean open = expanded.contains(s);
			float dd = ddAnim(s);
			drawDropdownHeader(g, s, mu.count() + " из " + mu.options.length, x, ry, w, open);
			if (dd > 0.01f) {
				int[] sv = clipPush(g, x, ry + 30, x + w, ry + rh);
				float aBase = UI.alpha;
				UI.alpha = aBase * dd;
				UI.rrect(g, x + 12, ry + 32, w - 24, mu.options.length * 24 + 4, Theme.elemR(8), 0xFF0F0F0F);
				for (int i = 0; i < mu.options.length; i++) {
					int oy = ry + 34 + i * 24;
					boolean on = mu.isOn(i);
					boolean hov = UI.inside(mx, my, x + 14, oy, w - 28, 22);
					if (hov) UI.rrect(g, x + 14, oy, w - 28, 22, Theme.elemR(6), 0xFF171717);
					// чекбокс
					UI.rrectBorder(g, x + 24, oy + 4, 14, 14, Theme.elemR(4), on ? Theme.accent() : 0xFF0F0F0F, on ? Theme.accent() : 0xFF3A3A3A);
					if (on) UI.text(g, F.ICON_S, Icons.CHECK, x + 27, oy + 11, 0xFF0A0A0A);
					UI.text(g, F.BODY, mu.options[i], x + 46, oy + 11, on ? UI.TEXT : UI.TEXT_DIM);
					final int idx = i;
					hit(x + 14, oy, w - 28, 22, btn -> {
						if (btn == 0) {
							mu.on[idx] = !mu.on[idx];
							Config.dirty = true;
							ClientSounds.play(mu.on[idx] ? Sfx.SELECT : Sfx.TAB, mu.on[idx] ? 1.05f : 0.85f);
						}
					});
				}
				UI.alpha = aBase;
				clipPop(g, sv);
			}

		} else if (s instanceof Setting.Text t) {
			UI.text(g, F.BODY, s.name, x + 16, ry + 13, UI.TEXT_DIM);
			field(g, mx, my, t, t.value, null, x + 16, ry + 25, w - 32, 26, false, t.maxLen);

		} else if (s instanceof Setting.Key k) {
			UI.text(g, F.BODY, s.name, x + 16, ry + rh / 2f, UI.TEXT);
			boolean waiting = listening == k;
			String label = waiting ? "нажми клавишу…" : k.label();
			int pw2 = Math.max(46, UI.width(F.SMALL, label) + 22);
			int bx = x + w - 16 - pw2;
			UI.rrectBorder(g, bx, ry + 5, pw2, 20, Theme.elemR(6), 0xFF111111, waiting ? Theme.accent(0.8f) : UI.LINE);
			UI.textC(g, F.SMALL, label, bx + pw2 / 2f, ry + 15, waiting ? Theme.accent() : UI.TEXT);
			hit(bx, ry + 5, pw2, 20, btn -> listening = waiting ? null : k);
		}
		return ry + rh;
	}

	private void drawDropdownHeader(GuiGraphics g, Setting s, String value, int x, int ry, int w, boolean open) {
		UI.text(g, F.BODY, s.name, x + 16, ry + 16, UI.TEXT);
		int pillW = Math.max(74, UI.width(F.SMALL, value) + 38);
		int bx = x + w - 16 - pillW;
		UI.rrectBorder(g, bx, ry + 5, pillW, 22, Theme.elemR(7), 0xFF111111, open ? Theme.accent(0.7f) : UI.LINE);
		UI.text(g, F.SMALL, value, bx + 10, ry + 16, UI.TEXT);
		UI.text(g, F.ICON_S, open ? Icons.CHEVRON_UP : Icons.CHEVRON_DOWN, bx + pillW - 18, ry + 16, UI.TEXT_DIM);
		hit(bx, ry + 5, pillW, 22, btn -> {
			if (!expanded.remove(s)) {
				expanded.add(s);
				ClientSounds.play(Sfx.POPUP_OPEN, 1.25f);
			} else {
				ClientSounds.play(Sfx.POPUP_CLOSE, 1.25f);
			}
		});
	}

	private void applyDrag(int mouseX) {
		if (dragNum != null) {
			double r = clamp((mouseX - dragX) / (float) dragW, 0f, 1f);
			double before = dragNum.value;
			dragNum.set(dragNum.min + r * (dragNum.max - dragNum.min));
			if (dragNum.value != before) ClientSounds.play(Sfx.SLIDER, 0.75f + 0.75f * clamp(dragNum.ratio(), 0f, 1f));
			Config.dirty = true;
		} else if (dragHue) {
			float before = Theme.hue;
			Theme.hue = clamp((mouseX - dragX) / (float) dragW, 0f, 1f);
			Theme.index = Theme.customIndex();
			if (Math.abs(Theme.hue - before) > 0.004f) ClientSounds.play(Sfx.SLIDER, 0.75f + 0.75f * Theme.hue);
			Config.dirty = true;
		} else if (dragRad != 0) {
			float k = Theme.clampK(clamp((mouseX - dragX) / (float) dragW, 0f, 1f) * Theme.RADIUS_MAX);
			float before = dragRad == 1 ? Theme.menuK : dragRad == 2 ? Theme.elemK : Theme.hudK;
			if (dragRad == 1) Theme.menuK = k;
			else if (dragRad == 2) Theme.elemK = k;
			else Theme.hudK = k;
			if (k != before) ClientSounds.play(Sfx.SLIDER, 0.75f + 0.375f * k);
			Config.dirty = true;
		}
	}

	// ===================================================================== метки

	private void renderMarks(GuiGraphics g, int mx, int my) {
		Minecraft mc = Minecraft.getInstance();
		int cx = px + SB + 18, cw = pw - SB - 36;
		UI.text(g, F.TITLE, "Метки", cx, py + 28, UI.TEXT);
		UI.textR(g, F.SMALL, "Друзей " + Marks.count(Marks.Type.FRIEND) + "   ·   Врагов " + Marks.count(Marks.Type.ENEMY)
			+ "   ·   Целей " + Marks.count(Marks.Type.TARGET), cx + cw, py + 28, UI.MUTED);
		UI.text(g, F.SMALL, "Отметь игроков — метка появится в Таргет ХУДе", cx, py + 50, UI.TEXT_DIM);

		List<PlayerInfo> players = new ArrayList<>();
		if (mc.getConnection() != null) {
			for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
				if (mc.player != null && info.getProfile().id().equals(mc.player.getUUID())) continue;
				players.add(info);
			}
		}
		players.sort(Comparator.comparing((PlayerInfo i) -> i.getProfile().name().toLowerCase(Locale.ROOT)));

		int vy1 = py + 70, vy2 = py + ph - 16;
		if (players.isEmpty()) {
			UI.textC(g, F.BODY, "Здесь пока никого нет", cx + cw / 2f, (vy1 + vy2) / 2f - 8, UI.TEXT_DIM);
			UI.textC(g, F.SMALL, "Зайди на сервер — игроки онлайн появятся списком", cx + cw / 2f, (vy1 + vy2) / 2f + 10, UI.MUTED);
			return;
		}

		int rowH = 38, gap = 6;
		int contentH = players.size() * (rowH + gap) - gap;
		float maxScroll = Math.max(0, contentH - (vy2 - vy1));
		marksScrollT = clamp(marksScrollT, 0, maxScroll);
		int scroll = Math.round(UI.anim("scroll.marks", marksScrollT, 18f));

		scissor(g, cx - 4, vy1, cx + cw + 4, vy2);
		for (int i = 0; i < players.size(); i++) {
			int y = vy1 + i * (rowH + gap) - scroll;
			if (y + rowH < vy1 || y > vy2) continue;
			PlayerInfo info = players.get(i);
			UUID id = info.getProfile().id();
			UI.rrectBorder(g, cx, y, cw, rowH, Theme.elemR(9), 0xFF0A0A0A, UI.LINE);

			PlayerSkin skin = info.getSkin();
			UI.head(g, skin, cx + 9, y + 7, 24, 12, 0xFF0A0A0A);
			String shownNick = UI.fit(F.BOLD, info.getProfile().name(), cw - 300);
			UI.text(g, F.BOLD, shownNick, cx + 42, y + 15, UI.TEXT);
			if (Ecosystem.isUser(id)) Widgets.dxBadge(g, cx + 42 + UI.width(F.BOLD, shownNick) + 6, y + 15);
			UI.text(g, F.SMALL, info.getLatency() + " ms", cx + 42, y + 28, UI.MUTED);

			int bx = cx + cw - 10;
			Marks.Type[] types = Marks.Type.values();
			for (int t = types.length - 1; t >= 0; t--) {
				Marks.Type type = types[t];
				int bw = 62;
				bx -= bw;
				boolean on = Marks.get(id) == type;
				boolean hov = UI.inside(mx, my, bx, y + 9, bw, 20) && UI.inside(mx, my, cx, vy1, cw, vy2 - vy1);
				float a = UI.anim("mark." + id + type.name(), on ? 1f : 0f, 18f);
				UI.rrectBorder(g, bx, y + 9, bw, 20, Theme.elemR(10), Theme.mix(hov ? 0xFF131313 : 0xFF0D0D0D, Theme.mix(0xFF000000, type.color, 0.22f), a),
					Theme.mix(UI.LINE, Theme.withAlpha(type.color, 0.7f), a));
				UI.textC(g, F.SMALL, type.label, bx + bw / 2f, y + 19, Theme.mix(UI.TEXT_DIM, type.color, a));
				hit(bx, y + 9, bw, 20, btn -> {
					if (btn == 0) Marks.toggle(id, type);
				});
				bx -= 6;
			}
		}
		unscissor(g);
	}

	// ==================================================================== друзья

	/** Кнопка-«таблетка» справа налево. @return x левого края (для следующей кнопки). */
	private int friendBtn(GuiGraphics g, int mx, int my, int rightX, int y, int w, String label, int color,
						  boolean primary, int vx, int vy1, int vw, int vh, Runnable action) {
		int x = rightX - w;
		boolean hov = UI.inside(mx, my, x, y, w, 20) && UI.inside(mx, my, vx, vy1, vw, vh);
		float hv = hov ? 1f : 0f;
		int fill = primary ? Theme.mix(Theme.mix(0xFF000000, color, 0.22f), Theme.mix(0xFF000000, color, 0.34f), hv)
			: Theme.mix(0xFF0D0D0D, 0xFF161616, hv);
		int line = primary ? Theme.withAlpha(color, 0.7f) : UI.LINE;
		UI.rrectBorder(g, x, y, w, 20, Theme.elemR(10), fill, line);
		UI.textC(g, F.SMALL, label, x + w / 2f, y + 10, primary ? color : UI.TEXT_DIM);
		hit(x, y, w, 20, b -> {
			if (b == 0) action.run();
		});
		return x - 6;
	}

	private void renderFriends(GuiGraphics g, int mx, int my) {
		Minecraft mc = Minecraft.getInstance();
		int cx = px + SB + 18, cw = pw - SB - 36;
		List<Friends.Friend> fl = Friends.list();
		UI.text(g, F.TITLE, "Друзья", cx, py + 28, UI.TEXT);
		UI.textR(g, F.SMALL, "в сети " + Friends.onlineCount() + " из " + Friends.mutualCount(), cx + cw, py + 28, UI.MUTED);
		UI.text(g, F.SMALL, "Чтобы подружиться, оба пишут в чат: .dx tf add ник", cx, py + 50, UI.TEXT_DIM);

		int vy1 = py + 70, vy2 = py + ph - 16;
		float midY = (vy1 + vy2) / 2f;
		if (!NetworkModule.active()) {
			UI.textC(g, F.BODY, "Включи модуль DarkX Network", cx + cw / 2f, midY - 8, UI.TEXT_DIM);
			UI.textC(g, F.SMALL, "Функции → Разное → DarkX Network", cx + cw / 2f, midY + 10, UI.MUTED);
			return;
		}
		if (!Friends.error().isEmpty()) {
			UI.textC(g, F.SMALL, "База: " + UI.fit(F.SMALL, Friends.error(), cw - 20), cx + cw / 2f, vy1 + 6, 0xFFEF4444);
			vy1 += 16;
		}
		if (fl.isEmpty()) {
			UI.textC(g, F.BODY, "Пока никого нет", cx + cw / 2f, midY - 8, UI.TEXT_DIM);
			UI.textC(g, F.SMALL, "Напиши в чат .dx tf add ник — друг должен сделать то же самое", cx + cw / 2f, midY + 10, UI.MUTED);
			return;
		}

		int rowH = 44, gap = 6;
		int contentH = fl.size() * (rowH + gap) - gap;
		float maxScroll = Math.max(0, contentH - (vy2 - vy1));
		friendsScrollT = clamp(friendsScrollT, 0, maxScroll);
		int scroll = Math.round(UI.anim("scroll.friends", friendsScrollT, 18f));
		String here = mc.getCurrentServer() == null ? "" : mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);

		scissor(g, cx - 4, vy1, cx + cw + 4, vy2);
		for (int i = 0; i < fl.size(); i++) {
			int y = vy1 + i * (rowH + gap) - scroll;
			if (y + rowH < vy1 || y > vy2) continue;
			Friends.Friend f = fl.get(i);
			UI.rrectBorder(g, cx, y, cw, rowH, Theme.elemR(9), 0xFF0A0A0A, UI.LINE);

			PlayerSkin skin = null;
			if (mc.getConnection() != null) {
				for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
					if (info.getProfile().name().equalsIgnoreCase(f.name())) {
						skin = info.getSkin();
						break;
					}
				}
			}
			if (skin != null) {
				UI.head(g, skin, cx + 9, y + 10, 24, 12, 0xFF0A0A0A);
			} else {
				UI.rrect(g, cx + 9, y + 10, 24, 24, Theme.elemR(8), Theme.mix(0xFF000000, Theme.accent(), 0.3f));
				UI.textC(g, F.BOLD, f.name().substring(0, 1).toUpperCase(Locale.ROOT), cx + 21, y + 22, Theme.accent());
			}

			// кнопки справа налево
			int bx = cx + cw - 10, by = y + 12;
			final String name = f.name(), server = f.server();
			switch (f.state()) {
				case MUTUAL -> {
					bx = friendBtn(g, mx, my, bx, by, 58, "Убрать", UI.TEXT_DIM, false, cx, vy1, cw, vy2 - vy1,
						() -> Friends.remove(name));
					if (f.online() && !server.equals(Ecosystem.SINGLE) && !server.toLowerCase(Locale.ROOT).equals(here)) {
						bx = friendBtn(g, mx, my, bx, by, 64, "Зайти", Theme.accent(), true, cx, vy1, cw, vy2 - vy1,
							() -> dev.darkx.visuals.core.ServerJoin.join(server));
					}
				}
				case INCOMING -> {
					bx = friendBtn(g, mx, my, bx, by, 82, "Отклонить", UI.TEXT_DIM, false, cx, vy1, cw, vy2 - vy1,
						() -> Friends.remove(name));
					bx = friendBtn(g, mx, my, bx, by, 72, "Принять", Theme.accent(), true, cx, vy1, cw, vy2 - vy1,
						() -> Friends.add(name));
				}
				case OUTGOING -> bx = friendBtn(g, mx, my, bx, by, 64, "Отмена", UI.TEXT_DIM, false, cx, vy1, cw, vy2 - vy1,
					() -> Friends.remove(name));
			}

			int tw = Math.max(40, bx - (cx + 42) - 4);
			UI.text(g, F.BOLD, UI.fit(F.BOLD, f.name(), tw), cx + 42, y + 16, UI.TEXT);
			String status;
			int dot;
			switch (f.state()) {
				case MUTUAL -> {
					boolean same = f.online() && server.toLowerCase(Locale.ROOT).equals(here);
					status = f.online() ? (same ? "на этом же сервере" : Friends.where(server)) : "не в сети";
					dot = f.online() ? 0xFF34D399 : 0xFF4A4A4A;
				}
				case INCOMING -> {
					status = "хочет в близкие друзья";
					dot = Theme.accent();
				}
				default -> {
					status = "ждёт подтверждения";
					dot = 0xFFFACC15;
				}
			}
			UI.circle(g, cx + 45, y + 31, 6, dot);
			UI.text(g, F.SMALL, UI.fit(F.SMALL, status, tw - 12), cx + 53, y + 31, f.online() ? UI.TEXT_DIM : UI.MUTED);
		}
		unscissor(g);
	}

	// ====================================================================== темы

	private void renderThemes(GuiGraphics g, int mx, int my) {
		int cx = px + SB + 18, cw = pw - SB - 36;
		UI.text(g, F.TITLE, "Темы", cx, py + 28, UI.TEXT);
		UI.textR(g, F.SMALL, "акцент: " + (Theme.index >= Theme.customIndex() ? "Свой оттенок" : Theme.PRESETS.get(Theme.index).name()),
			cx + cw, py + 28, UI.MUTED);
		UI.text(g, F.SMALL, "Фон всегда чёрный (OLED) — меняется акцент и скругления", cx, py + 50, UI.TEXT_DIM);

		int vy1 = py + 68, vy2 = py + ph - 14;
		boolean wide = cw >= 470;
		int colGap = 16;
		int colL = wide ? (cw - colGap) / 2 : cw;
		int colR = wide ? cw - colGap - colL : cw;

		// высоты колонок
		int pgap = 8, pcH = 46, total = Theme.PRESETS.size() + 1, prow = (total + 1) / 2;
		int presetsH = prow * (pcH + pgap) - pgap;
		int leftH = presetsH + 14 + 18 + 8 + 14;
		int prevH = 140, radH = 18 + 3 * 34 + 28;
		int rightH = 18 + prevH + 14 + radH;
		int styleH = 18 + 44 + 16;
		int contentH = styleH + (wide ? Math.max(leftH, rightH) : leftH + 16 + rightH);

		float maxScroll = Math.max(0, contentH - (vy2 - vy1));
		themesScrollT = clamp(themesScrollT, 0, maxScroll);
		int scroll = Math.round(UI.anim("scroll.themes", themesScrollT, 18f));

		scissor(g, cx - 4, vy1, cx + cw + 4, vy2);
		int top = vy1 + 2 - scroll;

		// ---------- стиль оформления (худы)
		UI.text(g, F.BODY, "Стиль оформления", cx, top + 7, UI.TEXT);
		UI.textR(g, F.SMALL, "худы", cx + cw, top + 7, UI.MUTED);
		Theme.Style[] styles = Theme.Style.values();
		int sgap = 10, scw = (cw - sgap * (styles.length - 1)) / styles.length, sy0 = top + 18;
		for (int i = 0; i < styles.length; i++) {
			Theme.Style st = styles[i];
			int x = cx + i * (scw + sgap);
			boolean sel = Theme.style == st;
			boolean hov = UI.inside(mx, my, x, sy0, scw, 44);
			float a = UI.anim("style." + st.name(), sel ? 1f : 0f, 16f);
			float hv = UI.anim("style.h." + st.name(), hov ? 1f : 0f, 20f);
			UI.rrectBorder(g, x, sy0, scw, 44, Theme.elemR(10), Theme.mix(0xFF0A0A0A, 0xFF111111, hv), Theme.mix(UI.LINE, Theme.accent(0.85f), a));
			// образец плашки
			int sx = x + 10, sw2 = 44, sh2 = 24, sy2 = sy0 + 10;
			UI.rrect(g, sx, sy2, sw2, sh2, 3, 0xFF2A1C10);
			UI.rrect(g, sx + 18, sy2 + 8, 40, 20, 12, Theme.accent(0.55f));
			if (st == Theme.Style.GRAY) UI.gray(g, sx + 2, sy2 + 2, sw2 - 4, sh2 - 4, Theme.hudR(5));
			else UI.rrect(g, sx + 2, sy2 + 2, sw2 - 4, sh2 - 4, Theme.hudR(5), 0xFF000000);
			UI.text(g, F.BOLD, st.title, x + 64, sy0 + 17, UI.TEXT);
			UI.text(g, F.SMALL, UI.fit(F.SMALL, st.hint, scw - 64 - 28), x + 64, sy0 + 32, UI.TEXT_DIM);
			if (sel) UI.text(g, F.ICON_S, Icons.CHECK, x + scw - 16, sy0 + 14, Theme.accent());
			hit(x, sy0, scw, 44, btn -> {
				Theme.style = st;
				Config.dirty = true;
			});
		}
		top += styleH;

		// ---------- левая колонка: пресеты + свой оттенок
		int cardW = (colL - pgap) / 2;
		for (int i = 0; i < total; i++) {
			int x = cx + (i % 2) * (cardW + pgap);
			int y = top + (i / 2) * (pcH + pgap);
			if (y + pcH < vy1 || y > vy2) continue;
			boolean custom = i == Theme.customIndex();
			int color = custom ? (0xFF000000 | (java.awt.Color.HSBtoRGB(Theme.hue, 0.65f, 1f) & 0xFFFFFF)) : Theme.PRESETS.get(i).color();
			boolean sel = Theme.index == i;
			boolean hov = UI.inside(mx, my, x, y, cardW, pcH);
			float a = UI.anim("theme." + i, sel ? 1f : 0f, 16f);
			float hv = UI.anim("theme.h." + i, hov ? 1f : 0f, 20f);

			UI.rrectBorder(g, x, y, cardW, pcH, Theme.elemR(10), Theme.mix(0xFF0A0A0A, 0xFF111111, hv), Theme.mix(UI.LINE, Theme.withAlpha(color, 0.8f), a));
			UI.circle(g, x + 20, y + pcH / 2, 22, Theme.withAlpha(color, 0.22f));
			UI.circle(g, x + 20, y + pcH / 2, 14, color);
			String name = custom ? "Свой" : Theme.PRESETS.get(i).name();
			UI.text(g, F.BOLD, UI.fit(F.BOLD, name, cardW - 56), x + 40, y + 17, UI.TEXT);
			UI.text(g, F.SMALL, String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF), x + 40, y + 32, UI.TEXT_DIM);
			if (sel) UI.text(g, F.ICON_S, Icons.CHECK, x + cardW - 16, y + 14, color);
			final int idx = i;
			hit(x, y, cardW, pcH, btn -> {
				Theme.index = idx;
				Config.dirty = true;
			});
		}

		int hy = top + presetsH + 14;
		UI.text(g, F.BODY, "Свой оттенок", cx, hy + 7, UI.TEXT);
		int trackY = hy + 22, trackH = 14;
		int segs = 72;
		for (int i = 0; i < segs; i++) {
			int x0 = cx + i * colL / segs, x1 = cx + (i + 1) * colL / segs;
			int col = 0xFF000000 | (java.awt.Color.HSBtoRGB(i / (float) segs, 0.65f, 1f) & 0xFFFFFF);
			UI.fill(g, x0, trackY, x1 - x0, trackH, col);
		}
		int kx = cx + Math.round(colL * Theme.hue);
		UI.circle(g, kx, trackY + trackH / 2, 20, 0xFF000000);
		UI.circle(g, kx, trackY + trackH / 2, 16, UI.TEXT);
		UI.circle(g, kx, trackY + trackH / 2, 10, 0xFF000000 | (java.awt.Color.HSBtoRGB(Theme.hue, 0.65f, 1f) & 0xFFFFFF));
		hit(cx - 6, trackY - 6, colL + 12, trackH + 12, btn -> {
			if (btn == 0) {
				dragHue = true;
				dragX = cx;
				dragW = colL;
				applyDrag(localX());
			}
		});

		// ---------- правая колонка: предпросмотр + скругления
		int rx = wide ? cx + colL + colGap : cx;
		int ry = wide ? top : top + leftH + 16;

		UI.text(g, F.BODY, "Предпросмотр", rx, ry + 7, UI.TEXT);
		UI.textR(g, F.SMALL, "живой", rx + colR, ry + 7, UI.MUTED);
		renderThemePreview(g, rx, ry + 18, colR, prevH);

		int sy = ry + 18 + prevH + 14;
		UI.text(g, F.BODY, "Скругление углов", rx, sy + 7, UI.TEXT);
		String[] names = {"Главное меню", "Элементы меню", "Худы"};
		for (int i = 0; i < 3; i++) {
			float k = i == 0 ? Theme.menuK : i == 1 ? Theme.elemK : Theme.hudK;
			int y = sy + 18 + i * 34;
			UI.text(g, F.SMALL, names[i], rx, y + 7, UI.TEXT_DIM);
			UI.textR(g, F.SMALL, Math.round(k * 100f) + "%", rx + colR, y + 7, Theme.accent());
			int ty = y + 20;
			UI.rrect(g, rx, ty, colR, 4, 2, 0xFF1E1E1E);
			UI.fill(g, rx + colR / 2, ty - 2, 1, 8, 0x40E4E4E2); // метка «как раньше» (100%)
			int fw = Math.max(4, Math.round(colR * k / Theme.RADIUS_MAX));
			UI.rrect(g, rx, ty, fw, 4, 2, Theme.accent());
			UI.circle(g, rx + fw, ty + 2, 14, UI.TEXT);
			final int which = i + 1;
			hit(rx - 6, y + 12, colR + 12, 24, btn -> {
				if (btn == 0) {
					dragRad = which;
					dragX = rx;
					dragW = colR;
					applyDrag(localX());
				}
			});
		}

		int by = sy + 18 + 3 * 34 + 2, bh = 22;
		boolean hovR = UI.inside(mx, my, rx, by, colR, bh);
		float hr = UI.anim("theme.reset", hovR ? 1f : 0f, 20f);
		UI.rrectBorder(g, rx, by, colR, bh, Theme.elemR(8), Theme.mix(0xFF0C0C0C, 0xFF171717, hr), Theme.mix(UI.LINE, Theme.accent(0.7f), hr));
		UI.textC(g, F.SMALL, "Сбросить скругления", rx + colR / 2f, by + bh / 2f, Theme.mix(UI.TEXT_DIM, UI.TEXT, hr));
		hit(rx, by, colR, bh, btn -> {
			Theme.resetRadius();
			Config.dirty = true;
		});
		unscissor(g);

		if (maxScroll > 0) {
			int trackTop = vy1, trackLen = vy2 - vy1;
			int thumbH = Math.max(24, Math.round(trackLen * trackLen / (float) contentH));
			int thumbY = trackTop + Math.round((trackLen - thumbH) * (scroll / maxScroll));
			UI.rrect(g, cx + cw + 6, thumbY, 2, thumbH, 1, 0x55E4E4E2);
		}
	}

	/** Мини-копия интерфейса: окно меню и худ — рисуются теми же радиусами и акцентом, что и настоящие. */
	private void renderThemePreview(GuiGraphics g, int x, int y, int w, int h) {
		UI.rrectBorder(g, x, y, w, h, 6, 0xFF141414, UI.LINE);
		int ix = x + 8, iy = y + 8, iw = w - 16;

		// окно меню
		int wh = 76;
		UI.rrectBorder(g, ix, iy, iw, wh, Theme.menuR(12), UI.BG, 0x2AE4E4E2);
		int sbw = 34;
		int ac = Theme.accent();
		UI.rrect(g, ix + 6, iy + 6, sbw - 10, 24, Theme.elemR(7), Theme.mix(ac, UI.TEXT, 0.12f));
		UI.textC(g, F.ICON_S, Icons.GRID, ix + 6 + (sbw - 10) / 2f, iy + 18, 0xFF000000);
		UI.textC(g, F.ICON_S, Icons.PALETTE, ix + 6 + (sbw - 10) / 2f, iy + 44, UI.TEXT_DIM);
		UI.fill(g, ix + sbw, iy + 10, 1, wh - 20, UI.LINE);

		int cx = ix + sbw + 8, cw = iw - sbw - 14;
		UI.rrectBorder(g, cx, iy + 7, cw, 28, Theme.elemR(9), 0xFF0A0A0A, UI.LINE);
		UI.text(g, F.SMALL, "Функция", cx + 10, iy + 21, UI.TEXT);
		UI.toggle(g, "theme.preview.t", cx + cw - 36, iy + 14, true);
		int sw = Math.max(20, cw - 62);
		UI.rrect(g, cx, iy + 51, sw, 4, 2, 0xFF1E1E1E);
		UI.rrect(g, cx, iy + 51, Math.round(sw * 0.62f), 4, 2, ac);
		UI.circle(g, cx + Math.round(sw * 0.62f), iy + 53, 12, UI.TEXT);
		int bw = 48, bh = 20, bx = cx + cw - bw, byy = iy + 43;
		UI.solid(g, bx, byy, bw, bh, Theme.elemR(8), 0f, 0.5f);
		UI.textC(g, F.SMALL, "OK", bx + bw / 2f, byy + bh / 2f, 0xFF000000);

		// худ
		int hy = iy + wh + 8, hh = h - 16 - wh - 8;
		int hw = Math.min(iw, Math.max(150, UI.width(F.BOLD, "Координаты") + 60));
		UI.circle(g, ix + hw - 24, hy + hh / 2, 44, Theme.accent(0.35f)); // фон под плашкой — чтобы видна прозрачность плашки
		UI.circle(g, ix + hw - 16, hy + hh - 6, 26, Theme.accent(0.2f));
		HudManager.drawPanel(g, ix, hy, hw, hh, Theme.hudR(7));
		UI.text(g, F.ICON_S, Icons.PIN, ix + 10, hy + 10, ac);
		UI.text(g, F.BOLD, "Координаты", ix + 10 + UI.width(F.ICON_S, Icons.PIN) + 6, hy + 10, ac);
		UI.fill(g, ix + 8, hy + 19, hw - 16, 1, UI.LINE);
		UI.text(g, F.SMALL, "128  64  -32", ix + 10, hy + 30, UI.TEXT);
	}

	// ==================================================================== профиль

	private void renderProfile(GuiGraphics g, int mx, int my) {
		Minecraft mc = Minecraft.getInstance();
		int cx = px + SB + 18, cw = pw - SB - 36;
		UI.text(g, F.TITLE, "Профиль", cx, py + 28, UI.TEXT);
		if (mc.player == null) return;

		int y = py + 62;
		UI.rrectBorder(g, cx, y, cw, 88, Theme.elemR(12), 0xFF0A0A0A, UI.LINE);
		UI.head(g, mc.player.getSkin(), cx + 16, y + 16, 56, 16, 0xFF0A0A0A);
		UI.text(g, F.TITLE, mc.player.getName().getString(), cx + 88, y + 30, UI.TEXT);
		UI.text(g, F.SMALL, mc.player.getUUID().toString(), cx + 88, y + 50, UI.MUTED);
		String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Одиночная игра";
		UI.text(g, F.SMALL, server, cx + 88, y + 66, UI.TEXT_DIM);

		int gap = 10, n = 4;
		int cardW = (cw - gap * (n - 1)) / n, cardH = 64, cy = y + 88 + 14;
		String[][] stats = {
			{Icons.BOX, "Модулей включено", ModuleManager.enabledCount() + " / " + ModuleManager.all().size()},
			{Icons.GAUGE, "FPS", Integer.toString(mc.getFps())},
			{Icons.WIFI, "Пинг", Stats.ping(mc) + " ms"},
			{Icons.CLOCK, "Сессия", Stats.sessionTime()}
		};
		for (int i = 0; i < n; i++) {
			int x = cx + i * (cardW + gap);
			UI.rrectBorder(g, x, cy, cardW, cardH, Theme.elemR(10), 0xFF0A0A0A, UI.LINE);
			UI.text(g, F.ICON_S, stats[i][0], x + 14, cy + 17, Theme.accent());
			UI.text(g, F.SMALL, UI.fit(F.SMALL, stats[i][1], cardW - 40), x + 28, cy + 17, UI.TEXT_DIM);
			UI.text(g, F.TITLE, UI.fit(F.TITLE, stats[i][2], cardW - 24), x + 14, cy + 43, UI.TEXT);
		}
	}

	// ============================================================ ввод текста

	private String editGet() {
		if (editing instanceof Setting.Text t) return t.value;
		if (editing instanceof Field f) return f.text;
		return "";
	}

	private void editSet(String v) {
		if (editing instanceof Setting.Text t) {
			t.value = v;
			Config.dirty = true;
		} else if (editing instanceof Field f) {
			f.text = v;
		}
	}

	private void typeText(String in) {
		StringBuilder sb = new StringBuilder(editGet());
		for (int i = 0; i < in.length(); ) {
			int cp = in.codePointAt(i);
			i += Character.charCount(cp);
			if (editNumeric) {
				if (cp == ',') cp = '.';
				if (!((cp >= '0' && cp <= '9') || cp == '-' || cp == '.')) continue;
			} else if (cp < 32 || cp == 127) {
				continue;
			}
			if (sb.length() >= editMax) break;
			sb.appendCodePoint(cp);
		}
		editSet(sb.toString());
	}

	private static String tail(String s, int maxW) {
		while (s.length() > 1 && UI.width(F.BODY, s) > maxW) s = s.substring(1);
		return s;
	}

	private void field(GuiGraphics g, int mx, int my, Object key, String text, String prefix,
					   int x, int y, int w, int h, boolean numeric, int max) {
		boolean ed = editing == key;
		boolean hov = UI.inside(mx, my, x, y, w, h);
		UI.rrectBorder(g, x, y, w, h, Theme.elemR(7), 0xFF0D0D0D, ed ? Theme.accent(0.85f) : (hov ? 0x40E4E4E2 : UI.LINE));
		int tx = x + 10;
		if (prefix != null) {
			UI.text(g, F.SMALL, prefix, tx, y + h / 2f, UI.MUTED);
			tx += UI.width(F.SMALL, prefix) + 8;
		}
		String shown = tail(text, x + w - 12 - tx);
		UI.text(g, F.BODY, shown, tx, y + h / 2f, UI.TEXT);
		if (ed && (System.currentTimeMillis() / 500) % 2 == 0) {
			UI.fill(g, tx + UI.width(F.BODY, shown) + 1, y + 6, 1, h - 12, Theme.accent());
		}
		hit(x, y, w, h, b -> {
			if (b == 0) {
				editing = key;
				editNumeric = numeric;
				editMax = max;
			}
		});
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (editing == null) return super.charTyped(event);
		typeText(new String(Character.toChars(event.codepoint())));
		return true;
	}

	// ============================================================== маркеры

	private static double parseCoord(String text, double fallback) {
		try {
			String t = text.trim().replace(',', '.');
			double v = Double.parseDouble(t);
			// целое число = координата блока, ставим маркер в его центр
			return t.contains(".") ? v : v + 0.5;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private void openCreate() {
		Minecraft mc = Minecraft.getInstance();
		creating = true;
		popup = null;
		editing = null;
		mkName.text = "";
		if (mc.player != null) {
			mkX.text = Integer.toString((int) Math.floor(mc.player.getX()));
			mkY.text = Integer.toString((int) Math.floor(mc.player.getY()));
			mkZ.text = Integer.toString((int) Math.floor(mc.player.getZ()));
		} else {
			mkX.text = mkY.text = mkZ.text = "0";
		}
		mkWorld = Waypoints.worldIndex(Waypoints.currentWorld(mc));
		mkColor = 0;
		mkIcon = 0;
	}

	private void confirmCreate() {
		Minecraft mc = Minecraft.getInstance();
		Waypoints.Marker m = new Waypoints.Marker();
		m.name = mkName.text.isBlank() ? "Маркер " + (Waypoints.LIST.size() + 1) : mkName.text.trim();
		m.x = parseCoord(mkX.text, mc.player != null ? mc.player.getX() : 0);
		m.y = parseCoord(mkY.text, mc.player != null ? mc.player.getY() : 0);
		m.z = parseCoord(mkZ.text, mc.player != null ? mc.player.getZ() : 0);
		m.color = Waypoints.COLORS[mkColor];
		m.icon = mkIcon;
		m.world = Waypoints.WORLDS[mkWorld];
		Waypoints.LIST.add(m);
		Config.dirty = true;
		creating = false;
		editing = null;
		Notifications.push("Маркер", m.name, m.color);
	}

	private void renderMarkers(GuiGraphics g, int mx, int my) {
		Minecraft mc = Minecraft.getInstance();
		int cx = px + SB + 18, cw = pw - SB - 36;
		UI.text(g, F.TITLE, "Маркеры", cx, py + 28, UI.TEXT);

		// кнопка «Создать маркер»
		int bw = 150, bh = 26, bx = cx + cw - bw, by = py + 15;
		boolean bh0 = UI.inside(mx, my, bx, by, bw, bh) && !creating;
		float hv = UI.anim("mk.create", bh0 ? 1f : 0f, 20f);
		UI.solid(g, bx, by, bw, bh, 9, hv, 1f);
		UI.text(g, F.ICON_S, Icons.PLUS, bx + 14, by + bh / 2f, 0xFF000000);
		UI.text(g, F.BODY, "Создать маркер", bx + 34, by + bh / 2f, 0xFF000000);
		hit(bx, by, bw, bh, b -> {
			if (b == 0) openCreate();
		});

		UI.text(g, F.SMALL, "Точки на карте мира: видны на экране, пока включён модуль Markers", cx, py + 52, UI.TEXT_DIM);

		int vy1 = py + 72, vy2 = py + ph - 16;
		List<Waypoints.Marker> list = Waypoints.LIST;
		if (list.isEmpty()) {
			UI.textC(g, F.BODY, "Маркеров пока нет", cx + cw / 2f, (vy1 + vy2) / 2f - 8, UI.TEXT_DIM);
			UI.textC(g, F.SMALL, "Нажми «Создать маркер» — по умолчанию подставятся твои координаты",
				cx + cw / 2f, (vy1 + vy2) / 2f + 10, UI.MUTED);
			return;
		}

		int rowH = 46, gap = 8;
		int contentH = list.size() * (rowH + gap) - gap;
		float maxScroll = Math.max(0, contentH - (vy2 - vy1));
		markersScrollT = clamp(markersScrollT, 0, maxScroll);
		int scroll = Math.round(UI.anim("scroll.markers", markersScrollT, 18f));

		String world = Waypoints.currentWorld(mc);
		scissor(g, cx - 4, vy1, cx + cw + 4, vy2);
		for (int i = 0; i < list.size(); i++) {
			int y = vy1 + i * (rowH + gap) - scroll;
			if (y + rowH < vy1 || y > vy2) continue;
			Waypoints.Marker m = list.get(i);
			int color = m.color | 0xFF000000;
			UI.rrectBorder(g, cx, y, cw, rowH, Theme.elemR(10), 0xFF0A0A0A, UI.LINE);
			UI.circle(g, cx + 26, y + rowH / 2, 30, color);
			UI.textC(g, F.ICON, Icons.MARKER_ICONS[Math.max(0, Math.min(Icons.MARKER_ICONS.length - 1, m.icon))],
				cx + 26, y + rowH / 2f, 0xFF0A0A0A);
			UI.text(g, F.BOLD, UI.fit(F.BOLD, m.name, cw - 150), cx + 52, y + 16, UI.TEXT);

			String sub = (int) Math.floor(m.x) + "  " + (int) Math.floor(m.y) + "  " + (int) Math.floor(m.z)
				+ "   ·   " + Waypoints.WORLD_NAMES[Waypoints.worldIndex(m.world)];
			if (mc.player != null && m.world.equals(world)) {
				double dx = m.x - mc.player.getX(), dy = m.y - mc.player.getY(), dz = m.z - mc.player.getZ();
				sub += "   ·   " + Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + " м";
			}
			UI.text(g, F.SMALL, UI.fit(F.SMALL, sub, cw - 120), cx + 52, y + 32, UI.TEXT_DIM);

			// удалить
			int dxBtn = cx + cw - 14 - 26, dyBtn = y + (rowH - 26) / 2;
			boolean hov = UI.inside(mx, my, dxBtn, dyBtn, 26, 26) && !creating
				&& UI.inside(mx, my, cx, vy1, cw, vy2 - vy1);
			float a = UI.anim("mk.del." + i, hov ? 1f : 0f, 20f);
			UI.rrect(g, dxBtn, dyBtn, 26, 26, Theme.elemR(8), Theme.mix(0xFF111111, 0xFF3A1212, a));
			UI.textC(g, F.ICON_S, Icons.TRASH, dxBtn + 13, dyBtn + 13, Theme.mix(UI.TEXT_DIM, 0xFFEF4444, a));
			hit(dxBtn, dyBtn, 26, 26, b -> {
				if (b == 0) {
					Waypoints.LIST.remove(m);
					Config.dirty = true;
				}
			});
		}
		unscissor(g);
	}

	private void renderCreate(GuiGraphics g, int mx, int my) {
		if (!creating || tab != Tab.MARKERS) return;
		float o = UI.anim("mk.dialog", 1f, 18f);
		hit(px, py, pw, ph, NOOP); // блокирует всё под окном
		UI.rrect(g, px, py, pw, ph, Theme.menuR(12), ((int) (0xC0 * o)) << 24);

		int dw = Math.min(430, pw - 40), dh = 340;
		int dx = px + (pw - dw) / 2, dy = Math.max(py + 4, py + (ph - dh) / 2) + Math.round((1f - o) * 10f);
		UI.rrect(g, dx - 3, dy - 3, dw + 6, dh + 6, Theme.menuR(15), 0x66000000);
		UI.rrectBorder(g, dx, dy, dw, dh, Theme.menuR(12), 0xFF090909, Theme.accent(0.45f));
		hit(dx, dy, dw, dh, NOOP);

		int x0 = dx + 20, iw = dw - 40;
		UI.text(g, F.TITLE, "Новый маркер", x0, dy + 28, UI.TEXT);

		UI.text(g, F.SMALL, "Название", x0, dy + 56, UI.TEXT_DIM);
		field(g, mx, my, mkName, mkName.text, null, x0, dy + 66, iw, 26, false, 24);

		UI.text(g, F.SMALL, "Координаты", x0, dy + 106, UI.TEXT_DIM);
		int fw = (iw - 16) / 3;
		field(g, mx, my, mkX, mkX.text, "X", x0, dy + 116, fw, 26, true, 9);
		field(g, mx, my, mkY, mkY.text, "Y", x0 + fw + 8, dy + 116, fw, 26, true, 9);
		field(g, mx, my, mkZ, mkZ.text, "Z", x0 + 2 * (fw + 8), dy + 116, fw, 26, true, 9);

		UI.text(g, F.SMALL, "Мир", x0, dy + 156, UI.TEXT_DIM);
		for (int i = 0; i < Waypoints.WORLDS.length; i++) {
			int wx = x0 + i * (fw + 8);
			boolean sel = mkWorld == i;
			boolean hov = UI.inside(mx, my, wx, dy + 166, fw, 24);
			float a = UI.anim("mk.world." + i, sel ? 1f : 0f, 18f);
			UI.rrectBorder(g, wx, dy + 166, fw, 24, Theme.elemR(8), Theme.mix(hov ? 0xFF131313 : 0xFF0D0D0D, Theme.tint(0.2f), a),
				Theme.mix(UI.LINE, Theme.accent(0.8f), a));
			UI.textC(g, F.BODY, Waypoints.WORLD_NAMES[i], wx + fw / 2f, dy + 178, Theme.mix(UI.TEXT_DIM, Theme.accent(), a));
			final int idx = i;
			hit(wx, dy + 166, fw, 24, b -> mkWorld = idx);
		}

		UI.text(g, F.SMALL, "Цвет", x0, dy + 204, UI.TEXT_DIM);
		int n = Waypoints.COLORS.length, d = 20;
		int step = (iw - d) / (n - 1);
		for (int i = 0; i < n; i++) {
			int sx = x0 + d / 2 + i * step, sy = dy + 228;
			boolean sel = mkColor == i;
			if (sel) UI.circle(g, sx, sy, d + 8, 0x66E4E4E2);
			UI.circle(g, sx, sy, d + 2, 0xFF090909);
			UI.circle(g, sx, sy, d - 2, Waypoints.COLORS[i]);
			final int idx = i;
			hit(sx - step / 2, sy - 14, step, 28, b -> mkColor = idx);
		}

		UI.text(g, F.SMALL, "Иконка", x0, dy + 252, UI.TEXT_DIM);
		int cells = Icons.MARKER_ICONS.length, cg = 3;
		int cw2 = (iw - cg * (cells - 1)) / cells;
		int chosen = Waypoints.COLORS[mkColor];
		for (int i = 0; i < cells; i++) {
			int cx2 = x0 + i * (cw2 + cg), cy2 = dy + 262;
			boolean sel = mkIcon == i;
			boolean hov = UI.inside(mx, my, cx2, cy2, cw2, 30);
			float a = UI.anim("mk.icon." + i, sel ? 1f : 0f, 18f);
			UI.rrectBorder(g, cx2, cy2, cw2, 30, Theme.elemR(8), Theme.mix(hov ? 0xFF131313 : 0xFF0D0D0D, Theme.mix(0xFF0D0D0D, chosen, 0.22f), a),
				Theme.mix(UI.LINE, chosen, a));
			UI.textC(g, F.ICON, Icons.MARKER_ICONS[i], cx2 + cw2 / 2f, cy2 + 15, Theme.mix(UI.TEXT_DIM, chosen, a));
			final int idx = i;
			hit(cx2, cy2, cw2, 30, b -> mkIcon = idx);
		}

		// кнопки
		int bw = 110, bh = 26, by = dy + dh - 14 - bh;
		boolean h1 = UI.inside(mx, my, x0 + iw - bw * 2 - 10, by, bw, bh);
		UI.rrectBorder(g, x0 + iw - bw * 2 - 10, by, bw, bh, Theme.elemR(9), h1 ? 0xFF171717 : 0xFF0D0D0D, UI.LINE);
		UI.textC(g, F.BODY, "Отмена", x0 + iw - bw * 2 - 10 + bw / 2f, by + bh / 2f, UI.TEXT);
		hit(x0 + iw - bw * 2 - 10, by, bw, bh, b -> creating = false);

		boolean h2 = UI.inside(mx, my, x0 + iw - bw, by, bw, bh);
		UI.rrectBorder(g, x0 + iw - bw, by, bw, bh, Theme.elemR(9), Theme.mix(Theme.tint(0.2f), Theme.tint(0.34f), h2 ? 1f : 0f), Theme.accent(0.8f));
		UI.textC(g, F.BODY, "Создать", x0 + iw - bw + bw / 2f, by + bh / 2f, Theme.accent());
		hit(x0 + iw - bw, by, bw, bh, b -> {
			if (b == 0) confirmCreate();
		});
	}

	// ==================================================================== ввод

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing) return true;
		int[] m = UI.mouse();
		int mx = Math.round(tcx + (m[0] - tcx) / scl);
		int my = Math.round(tcy + (m[1] - tcy - offY) / scl);
		int btn = event.button();

		if (listening != null) listening = null;
		editing = null;

		boolean overPopup = popup != null && UI.inside(mx, my, popX, popY, popW, popH);
		if (popup != null && !overPopup && btn == 0) {
			popup = null; // клик мимо всплывашки закрывает её
			ClientSounds.play(Sfx.POPUP_CLOSE);
			return true;
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (UI.inside(mx, my, h.x(), h.y(), h.w(), h.h())) {
				int before = ClientSounds.counter();
				h.action().accept(btn);
				// действие само не озвучилось — играем обычный щелчок кнопки
				if (h.action() != NOOP && ClientSounds.counter() == before) ClientSounds.play(Sfx.CLICK);
				return true;
			}
		}
		if (popup != null && !overPopup) popup = null;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragNum != null || dragHue || dragRad != 0) {
			applyDrag(localX());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean was = dragNum != null || dragHue || dragRad != 0;
		dragNum = null;
		dragHue = false;
		dragRad = 0;
		return was || super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (closing) return true;
		float step = (float) (-scrollY * 30.0);
		int lx = Math.round(tcx + ((float) mouseX - tcx) / scl);
		int ly = Math.round(tcy + ((float) mouseY - tcy - offY) / scl);
		if (popup != null && UI.inside(lx, ly, popX, popY, popW, popH)) {
			popScrollT += step;
		} else if (tab == Tab.FUNCTIONS) {
			fnScrollT += step;
		} else if (tab == Tab.MARKS) {
			marksScrollT += step;
		} else if (tab == Tab.FRIENDS) {
			friendsScrollT += step;
		} else if (tab == Tab.MARKERS) {
			markersScrollT += step;
		} else if (tab == Tab.THEMES) {
			themesScrollT += step;
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (closing) return true;
		if (editing != null) {
			if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				editing = null;
			} else if (key == GLFW.GLFW_KEY_BACKSPACE) {
				String t = editGet();
				if (!t.isEmpty()) editSet(t.substring(0, t.length() - 1));
			} else if (key == GLFW.GLFW_KEY_V && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
				String clip = GLFW.glfwGetClipboardString(Minecraft.getInstance().getWindow().handle());
				if (clip != null) typeText(clip);
			}
			return true; // пока вводим текст, остальные клавиши (в т.ч. правый Shift) не трогаем
		}
		if (creating && key == GLFW.GLFW_KEY_ESCAPE) {
			creating = false;
			return true;
		}
		if (listening != null) {
			listening.key = (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE) ? -1 : key;
			listening = null;
			ClientSounds.play(Sfx.SELECT);
			Config.dirty = true;
			return true;
		}
		if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			DarkXClient.latchShift();
			onClose();
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE && popup != null) {
			popup = null;
			ClientSounds.play(Sfx.POPUP_CLOSE);
			return true;
		}
		return super.keyPressed(event);
	}
}
