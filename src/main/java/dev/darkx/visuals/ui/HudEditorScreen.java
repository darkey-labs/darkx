package dev.darkx.visuals.ui;

import dev.darkx.visuals.DarkXClient;
import dev.darkx.visuals.audio.ClientSounds;
import dev.darkx.visuals.audio.Sfx;
import dev.darkx.visuals.core.Config;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Theme;
import dev.darkx.visuals.hud.HudManager;
import dev.darkx.visuals.hud.HudWidget;
import dev.darkx.visuals.modules.HudModule;
import dev.darkx.visuals.ui.UI.F;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * «Переместить ХУДы»: мир серый, все худы показаны с демо-данными.
 * ЛКМ + перетаскивание — двигать, угол справа внизу — менять размер (двойной клик по углу — 100%),
 * ПКМ — включить / выключить худ.
 */
public class HudEditorScreen extends Screen {
	private HudWidget dragging;
	private HudWidget resizing;
	private int grabX, grabY;
	private static final int PAD = 3, GRIP = 14;
	private boolean applied;
	private boolean prevHideGui;
	private boolean snapX;

	public HudEditorScreen() {
		super(Component.literal("DarkX HUD"));
	}

	@Override
	protected void init() {
		Minecraft mc = Minecraft.getInstance();
		if (!applied) {
			applied = true;
			prevHideGui = mc.options.hideGui;
			mc.options.hideGui = true;
			((dev.darkx.visuals.mixin.GameRendererAccessor) mc.gameRenderer).darkx$setPostEffect(ClickGuiScreen.GRAYSCALE);
			UI.reset(this, 0f);
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
		Config.saveIfDirty();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Назад в главное меню, а не в игру. */
	@Override
	public void onClose() {
		Config.dirty = true;
		Config.saveIfDirty();
		Minecraft.getInstance().setScreen(new ClickGuiScreen());
	}

	/** Угол изменения размера: справа внизу у рамки худа. */
	private static boolean onGrip(HudWidget w, int mx, int my) {
		int gx = w.x + w.sw() + PAD, gy = w.y + w.sh() + PAD;
		return UI.inside(mx, my, gx - GRIP / 2, gy - GRIP / 2, GRIP, GRIP);
	}

	private boolean shown(HudWidget w) {
		return HudManager.isShown(w);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float partialTick) {
		float open = UI.anim(this, 1f, 14f);
		UI.alpha = 1f;
		g.fill(0, 0, width, height, ((int) (0x66 * open)) << 24);
		UI.alpha = open;

		List<HudWidget> list = HudManager.WIDGETS;
		for (HudWidget w : list) {
			HudManager.ensurePlaced(w, width, height);
		}

		// перетаскивание
		snapX = false;
		if (dragging != null) {
			int[] m = UI.mouse();
			dragging.x = m[0] - grabX;
			dragging.y = m[1] - grabY;
			int cx = dragging.x + dragging.sw() / 2;
			if (Math.abs(cx - width / 2) <= 4) {
				dragging.x = width / 2 - dragging.sw() / 2;
				snapX = true;
			}
			HudManager.clamp(dragging, width, height);
			Config.dirty = true;
		}

		if (resizing != null) {
			int[] m = UI.mouse();
			HudWidget r = resizing;
			float bw = Math.max(r.w, 16), bh = Math.max(r.h, 16);
			// угол следует за курсором: берём среднее по обеим осям
			float k = ((m[0] - PAD - r.x) / bw + (m[1] - PAD - r.y) / bh) / 2f;
			float max = Math.min(HudWidget.MAX_SCALE, Math.min((width - r.x) / bw, (height - r.y) / bh));
			k = Math.max(HudWidget.MIN_SCALE, Math.min(Math.max(HudWidget.MIN_SCALE, max), k));
			if (Math.abs(k - 1f) < 0.04f) k = 1f;
			k = Math.round(k * 20f) / 20f;
			if (k != r.scale) {
				r.scale = k;
				Config.dirty = true;
			}
		}

		if (snapX) UI.fill(g, width / 2, 0, 1, height, Theme.accent(0.6f));

		HudWidget hover = null;
		if (dragging == null && resizing == null) {
			for (int i = list.size() - 1; i >= 0; i--) {
				if (shown(list.get(i)) && (list.get(i).contains(mx, my) || onGrip(list.get(i), mx, my))) {
					hover = list.get(i);
					break;
				}
			}
		}

		for (HudWidget w : list) {
			HudManager.clamp(w, width, height);
			boolean on = shown(w);
			float vis = UI.anim("hud.vis." + w.id, on ? 1f : 0f, 16f);
			if (vis < 0.01f) continue; // выключенный худ не мусорит экран — вернуть его можно чипом снизу
			UI.alpha = open * vis;
			w.draw(g, true);
			UI.alpha = open;

			boolean active = on && (w == dragging || w == resizing || w == hover);
			float a = UI.anim("hud.out." + w.id, active ? 1f : 0f, 20f);
			int pad = PAD;
			int ox = w.x - pad, oy = w.y - pad, ow = w.sw() + pad * 2, oh = w.sh() + pad * 2;
			int col = Theme.mix(0x33E4E4E2, Theme.accent(0.9f), a);
			UI.fill(g, ox, oy, ow, 1, col);
			UI.fill(g, ox, oy + oh - 1, ow, 1, col);
			UI.fill(g, ox, oy, 1, oh, col);
			UI.fill(g, ox + ow - 1, oy, 1, oh, col);

			// угол для изменения размера
			if (a > 0.02f) {
				boolean gh = w == resizing || onGrip(w, mx, my);
				float gv = UI.anim("hud.grip." + w.id, gh ? 1f : 0f, 22f);
				int gx = ox + ow, gy = oy + oh;
				float prevA = UI.alpha;
				UI.alpha = prevA * a;
				int gs = Math.round(8 + 2 * gv);
				UI.glow(g, gx - gs / 2, gy - gs / 2, gs, gs, 3, Theme.accent(), 0.5f * gv);
				UI.rrect(g, gx - gs / 2, gy - gs / 2, gs, gs, 3, Theme.mix(Theme.accent(0.85f), Theme.accent(), gv));
				UI.alpha = prevA;
			}

			// подпись над худом
			String label = w.title;
			if (Math.abs(w.scale - 1f) > 0.001f || w == resizing) label += "  ·  " + Math.round(w.scale * 100f) + "%";
			int lw = UI.width(F.SMALL, label) + 14;
			int ly = oy - 18 < 2 ? oy + oh + 2 : oy - 18;
			float la = a;
			if (la > 0.02f) {
				float prev = UI.alpha;
				UI.alpha = prev * la;
				UI.rrect(g, ox, ly, lw, 16, Theme.elemR(5), 0xE6000000);
				UI.text(g, F.SMALL, label, ox + 7, ly + 8, UI.TEXT);
				UI.alpha = prev;
			}
		}

		// нижняя панель
		int pw = 340, ph = 54;
		int px = (width - pw) / 2, py = height - ph - 16;
		UI.rrectBorder(g, px, py, pw, ph, Theme.menuR(12), 0xFF000000, 0x2AE4E4E2);
		UI.textC(g, F.BOLD, "Переместить ХУДы", width / 2f, py + 15, UI.TEXT);
		UI.textC(g, F.SMALL, "ЛКМ — двигать   ·   угол — размер   ·   ПКМ — вкл / выкл", width / 2f, py + 31, UI.TEXT_DIM);

		int bw = 92, bh = 22, by = py + ph - bh - 8;
		button(g, mx, my, "reset", "Сбросить", px + 14, by, bw, bh, false);
		button(g, mx, my, "done", "Готово", px + pw - 14 - bw, by, bw, bh, true);

		// выключенные худы: нажми на чип, чтобы вернуть
		List<int[]> chips = chips(py);
		if (!chips.isEmpty()) {
			int top = Integer.MAX_VALUE;
			for (int[] c : chips) top = Math.min(top, c[2]);
			UI.alpha = open;
			UI.textC(g, F.SMALL, "Выключены — нажми, чтобы вернуть", width / 2f, top - 12, UI.TEXT_DIM);
			for (int[] c : chips) {
				HudWidget w = list.get(c[0]);
				boolean hov = UI.inside(mx, my, c[1], c[2], c[3], CHIP_H);
				float hv = UI.anim("hud.chip." + w.id, hov ? 1f : 0f, 20f);
				UI.rrectBorder(g, c[1], c[2], c[3], CHIP_H, Theme.elemR(6), Theme.mix(0xFF0C0C0C, 0xFF171717, hv), Theme.mix(UI.LINE, Theme.accent(0.7f), hv));
				UI.textC(g, F.SMALL, w.title, c[1] + c[3] / 2f, c[2] + CHIP_H / 2f, Theme.mix(UI.TEXT_DIM, UI.TEXT, hv));
			}
		}
		UI.alpha = 1f;
	}

	private static final int CHIP_H = 18, CHIP_GAP = 6;

	/** Чипы выключенных худов: {индекс, x, y, ширина}. Строки растут вверх от нижней панели. */
	private List<int[]> chips(int panelY) {
		List<HudWidget> list = HudManager.WIDGETS;
		List<int[]> out = new java.util.ArrayList<>();
		List<int[]> row = new java.util.ArrayList<>();
		List<List<int[]>> rows = new java.util.ArrayList<>();
		int maxW = Math.min(width - 40, 620), rowW = 0;
		for (int i = 0; i < list.size(); i++) {
			if (shown(list.get(i))) continue;
			int cw = UI.width(F.SMALL, list.get(i).title) + 18;
			if (!row.isEmpty() && rowW + cw > maxW) {
				rows.add(row);
				row = new java.util.ArrayList<>();
				rowW = 0;
			}
			row.add(new int[]{i, 0, 0, cw});
			rowW += cw + CHIP_GAP;
		}
		if (!row.isEmpty()) rows.add(row);
		int y = panelY - 12 - CHIP_H;
		for (int r = rows.size() - 1; r >= 0; r--) {
			List<int[]> rw = rows.get(r);
			int total = -CHIP_GAP;
			for (int[] c : rw) total += c[3] + CHIP_GAP;
			int x = (width - total) / 2;
			for (int[] c : rw) {
				c[1] = x;
				c[2] = y;
				x += c[3] + CHIP_GAP;
				out.add(c);
			}
			y -= CHIP_H + CHIP_GAP;
		}
		return out;
	}

	private void button(GuiGraphics g, int mx, int my, String id, String label, int x, int y, int w, int h, boolean primary) {
		boolean hov = UI.inside(mx, my, x, y, w, h);
		float hv = UI.anim("hudbtn." + id, hov ? 1f : 0f, 20f);
		if (primary) {
			UI.solid(g, x, y, w, h, 8, hv, 1f);
			UI.textC(g, F.BODY, label, x + w / 2f, y + h / 2f, 0xFF000000);
			return;
		}
		UI.rrectBorder(g, x, y, w, h, Theme.elemR(8), Theme.mix(0xFF0C0C0C, 0xFF171717, hv), UI.LINE);
		UI.textC(g, F.BODY, label, x + w / 2f, y + h / 2f, UI.TEXT);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int[] m = UI.mouse();
		int mx = m[0], my = m[1];
		int btn = event.button();

		int pw = 340, ph = 54;
		int px = (width - pw) / 2, py = height - ph - 16;
		int bw = 92, bh = 22, by = py + ph - bh - 8;
		if (btn == 0) {
			if (UI.inside(mx, my, px + pw - 14 - bw, by, bw, bh)) {
				ClientSounds.play(Sfx.CLICK);
				onClose();
				return true;
			}
			if (UI.inside(mx, my, px + 14, by, bw, bh)) {
				ClientSounds.play(Sfx.SELECT);
				for (HudWidget w : HudManager.WIDGETS) {
					w.scale = 1f;
					w.placed = false;
					HudManager.ensurePlaced(w, width, height);
				}
				Config.dirty = true;
				return true;
			}
		}
		if (UI.inside(mx, my, px, py, pw, ph)) return true;

		List<HudWidget> list = HudManager.WIDGETS;
		if (btn == 0) {
			for (int[] c : chips(py)) {
				if (!UI.inside(mx, my, c[1], c[2], c[3], CHIP_H)) continue;
				HudModule hud = ModuleManager.get(HudModule.class);
				if (hud != null) {
					hud.show.on[c[0]] = true;
					UI.reset("hud.vis." + list.get(c[0]).id, 0f); // выплывает заново
					Config.dirty = true;
					ClientSounds.play(Sfx.ON, 1.08f);
				}
				return true;
			}
		}
		if (btn == 0) {
			for (int i = list.size() - 1; i >= 0; i--) {
				HudWidget w = list.get(i);
				if (!shown(w) || !onGrip(w, mx, my)) continue;
				ClientSounds.play(Sfx.CLICK, 1.2f);
				if (doubleClick) {
					w.scale = 1f; // двойной клик по углу — вернуть 100%
					Config.dirty = true;
				} else {
					resizing = w;
				}
				return true;
			}
		}
		for (int i = list.size() - 1; i >= 0; i--) {
			HudWidget w = list.get(i);
			if (!shown(w) || !w.contains(mx, my)) continue;
			if (btn == 0) {
				ClientSounds.play(Sfx.CLICK, 1.2f);
				dragging = w;
				grabX = mx - w.x;
				grabY = my - w.y;
			} else if (btn == 1) {
				HudModule hud = ModuleManager.get(HudModule.class);
				if (hud != null) {
					hud.show.on[i] = !hud.show.on[i];
					Config.dirty = true;
					ClientSounds.play(hud.show.on[i] ? Sfx.ON : Sfx.OFF, 1.08f);
				}
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean was = dragging != null || resizing != null;
		dragging = null;
		resizing = null;
		return was || super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			DarkXClient.latchShift();
			Minecraft.getInstance().setScreen(null);
			return true;
		}
		return super.keyPressed(event);
	}
}
