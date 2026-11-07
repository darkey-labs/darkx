package dev.darkx.visuals.hud;

import dev.darkx.visuals.core.Ecosystem;
import dev.darkx.visuals.core.Marks;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Stats;
import dev.darkx.visuals.core.Theme;
import dev.darkx.visuals.core.Waypoints;
import dev.darkx.visuals.modules.AntiAcBotModule;
import dev.darkx.visuals.modules.NetworkModule;
import dev.darkx.visuals.ui.Icons;
import dev.darkx.visuals.ui.Logo;
import dev.darkx.visuals.ui.Motion;
import dev.darkx.visuals.ui.UI;
import dev.darkx.visuals.ui.UI.F;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Все худы клиента. Добавить новый: класс-наследник HudWidget + строка в HudManager. */
public final class Widgets {
	private Widgets() {}

	private static final int PANEL_TEXT = UI.TEXT;

	/** Ширина, которую занимает значок DX (вместе с отступом справа). */
	public static final int DX_BADGE_W = 22;

	/** Логотип DarkX рядом с ником — игрок тоже сидит с DarkX Visuals. */
	public static void dxBadge(GuiGraphics g, int x, float cy) {
		Logo.draw(g, x, Math.round(cy) - 8, 16);
	}

	private static int hpColor(float r) {
		return r < 0.5f
			? Theme.mix(0xFFEF4444, 0xFFFACC15, r * 2f)
			: Theme.mix(0xFFFACC15, 0xFF34D399, (r - 0.5f) * 2f);
	}

	// ====================================================================== TARGET

	public static final class Target extends HudWidget {
		private LivingEntity target;
		private long seen;
		private LivingEntity flashTarget;
		private int lastHurt;
		private long hitAt;

		public Target() { super("target", "Таргет ХУД", 160, 46); }

		@Override public boolean selfAnimated() { return true; }

		@Override public void place(int sw, int sh) { x = sw / 2 + 24; y = sh / 2 + 24; }

		@Override public void render(GuiGraphics g, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			long now = System.currentTimeMillis();
			Entity looked = mc.crosshairPickEntity;
			if (preview && mc.player != null) {
				target = mc.player;
				seen = now;
			} else if (looked instanceof LivingEntity le && le != mc.player && !AntiAcBotModule.isBot(le)) {
				target = le;
				seen = now;
			}
			if (target == null) return;

			// появляется «с меньшего в большее», пропадает наоборот
			boolean present = preview || (!target.isRemoved() && now - seen < 1500L);
			float pop = Motion.pop("target.pop", present);
			if (!present && pop <= 0.004f) {
				target = null;
				return;
			}
			float prevAlpha = UI.alpha;
			UI.alpha = prevAlpha * Motion.clamp01(pop * 1.6f);

			// вспышка головы при ударе: краснеет и плавно возвращается
			if (target != flashTarget) {
				flashTarget = target;
				lastHurt = target.hurtTime;
				hitAt = 0;
				UI.reset(this, target.getHealth()); // полоска здоровья не «едет» от предыдущей цели
			}
			if (target.hurtTime > lastHurt) hitAt = now;
			lastHurt = target.hurtTime;
			float flash = hitAt == 0 ? 0f : Math.max(0f, 1f - (now - hitAt) / 650f);
			if (preview) flash = (now % 2600L) < 650L ? 1f - (now % 2600L) / 650f : 0f;
			flash *= flash;

			w = 160;
			h = 46;
			Motion.scaleAround(g, x + w / 2f, y + h / 2f, 0.55f + 0.45f * pop);
			panel(g, x, y, w, h);

			int hs = 32, hx = x + 10, hy = y + 7;
			if (target instanceof AbstractClientPlayer p) {
				UI.head(g, p.getSkin(), hx, hy, hs, 8, 0xFF000000);
			} else {
				UI.rrect(g, hx, hy, hs, hs, Theme.hudR(8), 0xFF161616);
				String nm = target.getName().getString();
				UI.textC(g, F.TITLE, nm.isEmpty() ? "?" : nm.substring(0, 1).toUpperCase(Locale.ROOT), hx + hs / 2f, hy + hs / 2f, UI.TEXT_DIM);
			}
			if (flash > 0.01f) UI.rrect(g, hx, hy, hs, hs, Theme.hudR(8), Theme.withAlpha(0xFF2D2D, 0.75f * flash));

			int tx = x + 50, tw = w - 50 - 10;
			Marks.Type mark = target instanceof Player pl ? Marks.get(pl.getUUID()) : null;
			int tagW = 0;
			if (mark != null) {
				tagW = UI.width(F.SMALL, mark.label);
				UI.textR(g, F.SMALL, mark.label, x + w - 10, y + 14, mark.color);
				tagW += 8;
			}
			boolean dx = target instanceof Player pdx && Ecosystem.isUser(pdx.getUUID()) && NetworkModule.showBadge();
			int badgeW = dx ? DX_BADGE_W : 0;
			String shownName = UI.fit(F.BOLD, target.getName().getString(), tw - tagW - badgeW);
			UI.text(g, F.BOLD, shownName, tx, y + 14, PANEL_TEXT);
			if (dx) dxBadge(g, tx + UI.width(F.BOLD, shownName) + 6, y + 14);

			float hp = target.getHealth();
			float max = Math.max(1f, target.getMaxHealth());
			float abs = target.getAbsorptionAmount();
			float shown = UI.anim(this, hp, 10f);
			float ratio = Math.max(0f, Math.min(1f, shown / max));
			int by = y + 26;
			UI.rrect(g, tx, by, tw, 5, 2, 0xFF1C1C1C);
			if (ratio > 0.001f) UI.rrect(g, tx, by, Math.max(4, Math.round(tw * ratio)), 5, 2, hpColor(ratio));
			if (abs > 0f) {
				int aw = Math.max(4, Math.round(tw * Math.min(1f, abs / max)));
				UI.rrect(g, tx + tw - aw, by, aw, 5, 2, 0xFFFBBF24);
			}
			UI.text(g, F.SMALL, String.format(Locale.ROOT, "%.1f HP", hp + abs), tx, y + 38, UI.TEXT_DIM);
			if (!preview && mc.player != null) {
				UI.textR(g, F.SMALL, String.format(Locale.ROOT, "%.1f м", mc.player.distanceTo(target)), x + w - 10, y + 38, UI.MUTED);
			}
			g.pose().popMatrix();
			UI.alpha = prevAlpha;
		}
	}

	// ===================================================================== PROFILE

	public static final class Profile extends HudWidget {
		public Profile() { super("profile", "Профиль ХУД", 190, 26); }

		@Override public void place(int sw, int sh) { x = 8; y = 8; }

		@Override public void render(GuiGraphics g, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			String ping = Stats.ping(mc) + " ms";
			String fps = mc.getFps() + " fps";
			int nameW = UI.width(F.BOLD, "DarkX");
			int pingW = UI.width(F.BODY, ping);
			int fpsW = UI.width(F.BODY, fps);
			int icon = 8, gap = 5;
			w = 36 + nameW + 10 + 1 + 10 + icon + gap + pingW + 10 + 1 + 10 + icon + gap + fpsW + 12;
			h = 26;
			panel(g, x, y, w, h);
			float cy = y + h / 2f;
			int cx = x + 11;
			Logo.draw(g, cx, Math.round(cy) - 9, 18);
			cx += 25;
			UI.text(g, F.BOLD, "DarkX", cx, cy, Theme.accent());
			cx += nameW + 10;
			UI.fill(g, cx, y + 7, 1, h - 14, UI.LINE);
			cx += 11;
			UI.text(g, F.ICON_S, Icons.WIFI, cx, cy, UI.MUTED);
			cx += icon + gap;
			UI.text(g, F.BODY, ping, cx, cy, PANEL_TEXT);
			cx += pingW + 10;
			UI.fill(g, cx, y + 7, 1, h - 14, UI.LINE);
			cx += 11;
			UI.text(g, F.ICON_S, Icons.GAUGE, cx, cy, UI.MUTED);
			cx += icon + gap;
			UI.text(g, F.BODY, fps, cx, cy, PANEL_TEXT);
		}
	}

	// ======================================================================= POTION

	public static final class Potion extends HudWidget {
		public Potion() { super("potion", "Поушн ХУД", 150, 30); icon = Icons.FLASK; }

		@Override public void place(int sw, int sh) { x = 8; y = sh / 2 - 40; }

		private static String roman(int n) {
			String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
			return n >= 0 && n < r.length ? r[n] : Integer.toString(n);
		}

		private static String time(MobEffectInstance e) {
			if ((e.getDuration() == -1)) return "∞";
			int s = Math.max(0, e.getDuration() / 20);
			return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
		}

		@Override public void render(GuiGraphics g, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) return;
			List<MobEffectInstance> list = new ArrayList<>();
			for (MobEffectInstance e : mc.player.getActiveEffects()) {
				if (e.showIcon()) list.add(e);
			}
			if (list.isEmpty() && preview) {
				list.add(new MobEffectInstance(MobEffects.SPEED, 1800, 1));
				list.add(new MobEffectInstance(MobEffects.STRENGTH, 720, 0));
				list.add(new MobEffectInstance(MobEffects.REGENERATION, 160, 1));
			}
			if (list.isEmpty()) {
				w = 0;
				h = 0;
				return;
			}
			list.sort(Comparator.comparingInt((MobEffectInstance e) -> (e.getDuration() == -1) ? Integer.MAX_VALUE : e.getDuration()).reversed());

			int rowH = 26, maxName = 0;
			List<String> names = new ArrayList<>();
			for (MobEffectInstance e : list) {
				String n = Component.translatable(e.getDescriptionId()).getString();
				if (e.getAmplifier() > 0) n += " " + roman(e.getAmplifier() + 1);
				names.add(n);
				maxName = Math.max(maxName, UI.width(F.BODY, n));
			}
			w = Math.max(Math.max(120, headW("Зелья")), 10 + 18 + 8 + maxName + 18);
			h = HEAD + list.size() * rowH + 6;
			panel(g, x, y, w, h, "Зелья");

			for (int i = 0; i < list.size(); i++) {
				MobEffectInstance e = list.get(i);
				int ry = y + HEAD + 2 + i * rowH;
				Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect().value());
				if (id != null) {
					Identifier sprite = Identifier.fromNamespaceAndPath(id.getNamespace(), "mob_effect/" + id.getPath());
					g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x + 10, ry + 4, 18, 18);
				}
				UI.text(g, F.BODY, names.get(i), x + 36, ry + 8, PANEL_TEXT);
				boolean low = !(e.getDuration() == -1) && e.getDuration() < 200;
				UI.text(g, F.SMALL, time(e), x + 36, ry + 19, low ? 0xFFEF4444 : UI.TEXT_DIM);
			}
		}
	}

	// ======================================================================== ARMOR

	public static final class Armor extends HudWidget {
		private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

		public Armor() { super("armor", "Армор ХУД", 124, 48); icon = Icons.SHIELD; }

		@Override public void place(int sw, int sh) { x = sw / 2 - 62; y = sh - 96; }

		@Override public void render(GuiGraphics g, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) return;
			w = 4 * 28 + 12;
			h = 64;
			int oy = y + 16;
			panel(g, x, y, w, h, "Броня");
			for (int i = 0; i < SLOTS.length; i++) {
				ItemStack st = mc.player.getItemBySlot(SLOTS[i]);
				int sx = x + 8 + i * 28;
				if (st.isEmpty()) {
					UI.rrect(g, sx + 4, oy + 7, 16, 16, Theme.hudR(4), 0xFF161616);
					continue;
				}
				g.renderItem(st, sx + 4, oy + 7);
				if (st.isDamageableItem()) {
					float r = 1f - (float) st.getDamageValue() / Math.max(1, st.getMaxDamage());
					UI.rrect(g, sx + 2, oy + 28, 20, 3, 1, 0xFF1C1C1C);
					UI.rrect(g, sx + 2, oy + 28, Math.max(2, Math.round(20 * r)), 3, 1, hpColor(r));
					UI.textC(g, F.SMALL, Math.round(r * 100) + "%", sx + 12, oy + 39, UI.TEXT_DIM);
				} else {
					UI.textC(g, F.SMALL, "—", sx + 12, oy + 37, UI.MUTED);
				}
			}
		}
	}

	// ====================================================================== COORDS

	public static final class Coords extends HudWidget {
		public Coords() { super("coords", "Координаты", 150, 42); icon = Icons.PIN; }

		@Override public void place(int sw, int sh) { x = 8; y = sh - 54; }

		private static String dir(Player p) {
			return switch (p.getDirection().getName()) {
				case "north" -> "Север";
				case "south" -> "Юг";
				case "west" -> "Запад";
				default -> "Восток";
			};
		}

		@Override public void render(GuiGraphics g, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.level == null) return;
			Player p = mc.player;
			int px = (int) Math.floor(p.getX()), py = (int) Math.floor(p.getY()), pz = (int) Math.floor(p.getZ());
			String main = px + "  " + py + "  " + pz;
			String alt;
			if (mc.level.dimension() == Level.NETHER) alt = "Верх. мир  " + (px * 8) + "  " + (pz * 8);
			else if (mc.level.dimension() == Level.OVERWORLD) alt = "Незер  " + (px / 8) + "  " + (pz / 8);
			else alt = dir(p);
			String right = dir(p);
			w = Math.max(UI.width(F.BODY, main) + 52, UI.width(F.SMALL, alt) + 28) + 6;
			w = Math.max(w, headW("Координаты"));
			h = 58;
			int oy = y + 16;
			panel(g, x, y, w, h, "Координаты");
			UI.text(g, F.BOLD, "XYZ", x + 14, oy + 14, Theme.accent());
			UI.text(g, F.BODY, main, x + 44, oy + 14, PANEL_TEXT);
			UI.text(g, F.SMALL, alt, x + 14, oy + 29, UI.TEXT_DIM);
			if (!alt.equals(right)) UI.textR(g, F.SMALL, right, x + w - 10, oy + 29, UI.MUTED);
		}
	}

	// =================================================================== KEYSTROKES

	public static final class Keystrokes extends HudWidget {
		private static final int KEY_BG = 0xFF1A1A1A;

		public Keystrokes() { super("keys", "Клавиши", 94, 125); icon = Icons.KEYBOARD; }

		@Override public void place(int sw, int sh) { x = sw - 110; y = sh / 2 - 60; }

		private void key(GuiGraphics g, String id, String label, int kx, int ky, int kw, int kh, boolean down, String sub) {
			float a = UI.anim(this.id + id, down ? 1f : 0f, 24f);
			UI.rrect(g, kx, ky, kw, kh, Theme.hudR(5), Theme.mix(KEY_BG, Theme.accent(0.9f), a));
			int tc = Theme.mix(PANEL_TEXT, 0xFF0A0A0A, a);
			if (sub == null) {
				UI.textC(g, F.BODY, label, kx + kw / 2f, ky + kh / 2f, tc);
			} else {
				UI.textC(g, F.BODY, label, kx + kw / 2f, ky + kh / 2f - 4, tc);
				UI.textC(g, F.SMALL, sub, kx + kw / 2f, ky + kh / 2f + 7, Theme.mix(UI.TEXT_DIM, 0xFF0A0A0A, a));
			}
		}

		@Override public void render(GuiGraphics g, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			var o = mc.options;
			w = 94;
			h = 125;
			panel(g, x, y, w, h, "Клавиши");
			int ox = x + 10, oy = y + HEAD + 4;
			int k = 22, gap = 3;
			key(g, "w", "W", ox + k + gap, oy, k, k, o.keyUp.isDown(), null);
			key(g, "a", "A", ox, oy + k + gap, k, k, o.keyLeft.isDown(), null);
			key(g, "s", "S", ox + k + gap, oy + k + gap, k, k, o.keyDown.isDown(), null);
			key(g, "d", "D", ox + 2 * (k + gap), oy + k + gap, k, k, o.keyRight.isDown(), null);
			int by = oy + 2 * (k + gap);
			key(g, "l", "ЛКМ", ox, by, 35, 26, Stats.lmbDown(), Stats.cpsLeft() + " cps");
			key(g, "r", "ПКМ", ox + 39, by, 35, 26, Stats.rmbDown(), Stats.cpsRight() + " cps");
			int sy = by + 29;
			float a = UI.anim(id + "space", o.keyJump.isDown() ? 1f : 0f, 24f);
			UI.rrect(g, ox, sy, 74, 14, Theme.hudR(5), Theme.mix(KEY_BG, Theme.accent(0.9f), a));
			UI.rrect(g, ox + 22, sy + 6, 30, 2, 1, Theme.mix(UI.TEXT_DIM, 0xFF0A0A0A, a));
		}
	}

	// ======================================================================= SPEED

	public static final class Speed extends HudWidget {
		public Speed() { super("speed", "Скорость", 110, 46); icon = Icons.GAUGE; }

		@Override public void place(int sw, int sh) { x = 8; y = 42; }

		@Override public void render(GuiGraphics g, boolean preview) {
			String v = String.format(Locale.ROOT, "%.1f", Stats.speed);
			String unit = "б/с";
			int vw = UI.width(F.BOLD, v);
			w = Math.max(headW("Скорость"), 14 + 8 + 6 + vw + 5 + UI.width(F.SMALL, unit) + 12);
			h = HEAD + 26;
			panel(g, x, y, w, h, "Скорость");
			float cy = y + HEAD + 13;
			UI.text(g, F.ICON_S, Icons.ACTIVITY, x + 14, cy, Theme.accent());
			UI.text(g, F.BOLD, v, x + 28, cy, PANEL_TEXT);
			UI.text(g, F.SMALL, unit, x + 28 + vw + 5, cy + 0.5f, UI.TEXT_DIM);
		}
	}

	// ======================================================================= CLOCK

	public static final class Clock extends HudWidget {
		private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

		public Clock() { super("clock", "Часы", 104, 58); icon = Icons.CLOCK; }

		@Override public void place(int sw, int sh) { x = sw - 112; y = sh - 70; }

		@Override public void render(GuiGraphics g, boolean preview) {
			String time = LocalDateTime.now().format(FMT);
			String session = "сессия " + Stats.sessionTime();
			w = Math.max(Math.max(UI.width(F.TITLE, time) + 28, UI.width(F.SMALL, session) + 28), headW("Часы"));
			h = HEAD + 38;
			panel(g, x, y, w, h, "Часы");
			UI.text(g, F.TITLE, time, x + 14, y + HEAD + 13, PANEL_TEXT);
			UI.text(g, F.SMALL, session, x + 14, y + HEAD + 29, UI.TEXT_DIM);
		}
	}

	// ======================================================================== ITEMS

	public static final class Items4 extends HudWidget {
		private static final Item[] ITEMS = {Items.TOTEM_OF_UNDYING, Items.GOLDEN_APPLE, Items.ENDER_PEARL, Items.END_CRYSTAL};

		public Items4() { super("items", "Предметы", 130, 48); icon = Icons.BACKPACK; }

		@Override public void place(int sw, int sh) { x = sw / 2 - 65; y = sh - 150; }

		private static int count(Minecraft mc, Item item) {
			int n = 0;
			var inv = mc.player.getInventory();
			int size = inv.getContainerSize();
			for (int i = 0; i < size; i++) {
				ItemStack s = inv.getItem(i);
				if (s.is(item)) n += s.getCount();
			}
			if (size < 41) {
				ItemStack off = mc.player.getOffhandItem();
				if (off.is(item)) n += off.getCount();
			}
			return n;
		}

		@Override public void render(GuiGraphics g, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) return;
			w = ITEMS.length * 30 + 12;
			h = 62;
			int oy = y + 16;
			panel(g, x, y, w, h, "Предметы");
			for (int i = 0; i < ITEMS.length; i++) {
				int n = count(mc, ITEMS[i]);
				int sx = x + 9 + i * 30;
				g.renderItem(new ItemStack(ITEMS[i]), sx + 4, oy + 7);
				UI.textC(g, F.SMALL, Integer.toString(n), sx + 12, oy + 35, n > 0 ? PANEL_TEXT : UI.MUTED);
			}
		}
	}

	// ========================================================================= CPS

	public static final class Cps extends HudWidget {
		public Cps() { super("cps", "CPS", 90, 46); icon = Icons.CLICK; }

		@Override public void place(int sw, int sh) { x = 8; y = 92; }

		@Override public void render(GuiGraphics g, boolean preview) {
			String v = Stats.cpsLeft() + " | " + Stats.cpsRight();
			int vw = UI.width(F.BOLD, v);
			w = Math.max(headW("CPS"), 14 + vw + 14);
			h = HEAD + 26;
			panel(g, x, y, w, h, "CPS");
			UI.text(g, F.BOLD, v, x + 14, y + HEAD + 13, PANEL_TEXT);
		}
	}

	// ================================================================== REACH

	/** Дистанция последнего удара по сущности. */
	public static final class Reach extends HudWidget {
		private float last = -1f;
		private boolean prevDown;

		public Reach() { super("reach", "Дистанция", 90, 46); icon = Icons.RULER; }

		@Override public void place(int sw, int sh) { x = 8; y = 144; }

		@Override public void render(GuiGraphics g, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			boolean down = Stats.lmbDown();
			if (!preview && down && !prevDown && mc.player != null && mc.hitResult instanceof EntityHitResult hit) {
				last = (float) mc.player.getEyePosition().distanceTo(hit.getLocation());
			}
			prevDown = down;

			String v;
			if (preview) v = "3.12 м";
			else v = last < 0 ? "—" : String.format(Locale.ROOT, "%.2f м", last);
			int vw = UI.width(F.BOLD, v);
			w = Math.max(headW("Дистанция"), 14 + vw + 14);
			h = HEAD + 26;
			panel(g, x, y, w, h, "Дистанция");
			UI.text(g, F.BOLD, v, x + 14, y + HEAD + 13, PANEL_TEXT);
		}
	}

	// ================================================================== MODULE LIST

	public static final class ModuleList extends HudWidget {
		private static final String TITLE = "Модули";

		private final Set<String> known = new LinkedHashSet<>();

		public ModuleList() { super("modules", "Список модулей", 110, 60); icon = Icons.LIST; }

		@Override public boolean selfAnimated() { return true; }

		@Override public void place(int sw, int sh) { x = sw - 118; y = 8; }

		@Override public void render(GuiGraphics g, boolean preview) {
			List<String> on = new ArrayList<>();
			for (Module m : ModuleManager.all()) if (m.enabled) on.add(m.name);
			if (on.isEmpty() && preview) {
				on.add("JumpCircles");
				on.add("ChinaHat");
				on.add("ViewModel");
			}
			for (String n : on) if (known.add(n)) UI.reset("ml.p." + n, 0f); // новый модуль въезжает справа

			Map<String, Float> pm = new HashMap<>();
			List<String> names = new ArrayList<>();
			for (Iterator<String> it = known.iterator(); it.hasNext(); ) {
				String n = it.next();
				boolean want = on.contains(n);
				float p = UI.anim("ml.p." + n, want ? 1f : 0f, 11f);
				if (!want && p < 0.01f) {
					it.remove();
					UI.forget("ml.p." + n);
					UI.forget("ml.y." + n);
					continue;
				}
				pm.put(n, p);
				names.add(n);
			}
			if (names.isEmpty()) {
				w = 0;
				h = 0;
				return;
			}
			names.sort(Comparator.comparingInt((String s) -> UI.width(F.BODY, s)).reversed().thenComparing(Comparator.naturalOrder()));

			int rowH = 17, maxW = UI.width(F.BOLD, TITLE) + iconW();
			float sum = 0f;
			for (String n : names) {
				maxW = Math.max(maxW, UI.width(F.BODY, n));
				sum += pm.get(n);
			}
			w = Math.round(UI.anim("ml.w", maxW + 24, 10f));
			h = Math.round(rowH * (1f + sum));

			// заголовок списка
			int hw = UI.width(F.BOLD, TITLE) + 20 + iconW();
			HudManager.drawPanel(g, x + w - hw, y, hw, rowH - 2, Theme.hudR(4));
			int htx = x + w - hw + 10;
			UI.text(g, F.ICON_S, icon, htx, y + (rowH - 2) / 2f, Theme.accent());
			UI.text(g, F.BOLD, TITLE, htx + iconW(), y + (rowH - 2) / 2f, Theme.accent());

			int accent = Theme.accent();
			float prev = UI.alpha;
			for (int i = 0; i < names.size(); i++) {
				String n = names.get(i);
				float p = pm.get(n);
				int rw = UI.width(F.BODY, n) + 20;
				int rx = x + w - rw + Math.round((1f - p) * 26f);
				int ry = y + Math.round(UI.anim("ml.y." + n, (i + 1) * rowH, 12f));
				UI.alpha = prev * Motion.clamp01(p * 1.4f);
				HudManager.drawPanel(g, rx, ry, rw, rowH - 2, Theme.hudR(4));
				UI.rrect(g, rx + rw - 3, ry + 3, 2, rowH - 8, 1, accent);
				UI.text(g, F.BODY, n, rx + 8, ry + (rowH - 2) / 2f, PANEL_TEXT);
			}
			UI.alpha = prev;
		}
	}

	// ======================================================================== NEAR

	/** Игроки в радиусе 50 блоков: голова, ник, дистанция и стрелка в их сторону. */
	public static final class Near extends HudWidget {
		private static final float RANGE = 50f;
		private static final int MAX_ROWS = 8;

		private record Row(UUID id, PlayerSkin skin, String name, float dist, float rel, int color, boolean dx) {}

		/** Ключ анимации строки: kind 0 — позиция, 1 — появление. */
		private record RowKey(UUID id, int kind) {}

		/** Последние данные каждой строки: уходящий игрок плавно тает, а не пропадает. */
		private final Map<UUID, Row> known = new LinkedHashMap<>();
		private final Map<UUID, Integer> slots = new HashMap<>();
		private int lastRows = 1;

		public Near() { super("near", "Неар ХУД", 160, 50); icon = Icons.RADAR; }

		@Override public boolean selfAnimated() { return true; }

		@Override public void place(int sw, int sh) { x = 8; y = sh / 2 + 70; }

		private static final Identifier ARROW = Identifier.fromNamespaceAndPath("darkx", "textures/gui/arrow.png");

		/** Гладкая стрелка (текстура с билинейной фильтрацией), повёрнутая по часовой на relDeg градусов. */
		static void arrow(GuiGraphics g, float cx, float cy, float relDeg, int color) {
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(cx, cy);
			pose.rotate((float) Math.toRadians(relDeg));
			g.blit(RenderPipelines.GUI_TEXTURED, ARROW, -7, -7, 0f, 0f, 14, 14, 48, 48, 48, 48, UI.c(color));
			pose.popMatrix();
		}

		private static final UUID DEMO_A = UUID.nameUUIDFromBytes("darkx-demo-a".getBytes());
		private static final UUID DEMO_B = UUID.nameUUIDFromBytes("darkx-demo-b".getBytes());

		@Override public void render(GuiGraphics g, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.level == null) return;

			List<Row> rows = new ArrayList<>();
			for (AbstractClientPlayer p : mc.level.players()) {
				if (p == mc.player || p.isRemoved() || p.isSpectator() || AntiAcBotModule.isFake(p) || AntiAcBotModule.isBot(p)) continue;
				float d = mc.player.distanceTo(p);
				if (d > RANGE) continue;
				double dx = p.getX() - mc.player.getX(), dz = p.getZ() - mc.player.getZ();
				float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				float rel = Mth.wrapDegrees(yaw - mc.player.getYRot());
				Marks.Type mk = Marks.get(p.getUUID());
				rows.add(new Row(p.getUUID(), p.getSkin(), p.getName().getString(), d, rel, mk != null ? (mk.color | 0xFF000000) : Theme.accent(),
					Ecosystem.isUser(p.getUUID()) && NetworkModule.showBadge()));
			}
			rows.sort(Comparator.comparingDouble(Row::dist));
			if (rows.size() > MAX_ROWS) rows = new ArrayList<>(rows.subList(0, MAX_ROWS));

			if (rows.isEmpty() && preview) {
				PlayerSkin skin = mc.player.getSkin();
				rows.add(new Row(DEMO_A, skin, "Steve", 12.4f, 40f, Theme.accent(), true));
				rows.add(new Row(DEMO_B, skin, "Alex", 31.0f, -125f, Theme.accent(), false));
			}

			boolean fresh = known.isEmpty(); // панель только что появилась: размеры сразу, без «дыхания»

			// кто сейчас в списке, а кто уже уходит
			Set<UUID> present = new LinkedHashSet<>();
			for (int i = 0; i < rows.size(); i++) {
				Row r = rows.get(i);
				present.add(r.id());
				known.put(r.id(), r);
				slots.put(r.id(), i);
			}
			boolean any = !rows.isEmpty();
			float panelPop = Motion.pop("near.panel", any);
			for (Iterator<Map.Entry<UUID, Row>> it = known.entrySet().iterator(); it.hasNext(); ) {
				UUID id = it.next().getKey();
				if (!present.contains(id) && (!any ? panelPop <= 0.004f : Motion.pop(new RowKey(id, 1), false) <= 0.004f)) {
					it.remove();
					slots.remove(id);
					Motion.forget(new RowKey(id, 1));
					UI.forget(new RowKey(id, 0));
				}
			}
			if (known.isEmpty() && panelPop <= 0.004f) {
				w = 0;
				h = 0;
				return;
			}

			int rowH = 24;
			if (any) lastRows = rows.size();
			int targetRows = any ? rows.size() : lastRows;

			// ширина считается по актуальным игрокам (или по последним, пока панель гаснет)
			Collection<Row> forWidth = any ? rows : known.values();
			int maxName = 0, maxDist = 0;
			for (Row r : forWidth) {
				String n = UI.fit(F.BODY, r.name(), r.dx() ? 84 : 110);
				maxName = Math.max(maxName, UI.width(F.BODY, n) + (r.dx() ? DX_BADGE_W : 0));
				maxDist = Math.max(maxDist, UI.width(F.SMALL, Math.round(r.dist()) + " м"));
			}
			float tw = Math.max(headW("Рядом"), 10 + 18 + 8 + maxName + 10 + maxDist + 10 + 12 + 10);
			float th = HEAD + targetRows * rowH + 6;
			if (fresh) {
				UI.reset("near.w", tw);
				UI.reset("near.h", th);
			}
			w = Math.round(UI.anim("near.w", tw, 12f));
			h = Math.round(UI.anim("near.h", th, 12f)); // панель плавно расширяется, когда появляется новый игрок

			float prevAlpha = UI.alpha;
			UI.alpha = prevAlpha * Motion.clamp01(panelPop * 1.6f);
			Motion.scaleAround(g, x + w / 2f, y + h / 2f, 0.6f + 0.4f * panelPop);
			panel(g, x, y, w, h, "Рядом");

			g.enableScissor(x, y + HEAD, x + w, y + h - 3);
			float rowAlpha = UI.alpha;
			for (Map.Entry<UUID, Row> en : known.entrySet()) {
				UUID id = en.getKey();
				Row r = en.getValue();
				boolean live = present.contains(id);
				float p = Motion.pop(new RowKey(id, 1), live && any);
				if (p <= 0.004f) continue;
				float slot = UI.anim(new RowKey(id, 0), slots.getOrDefault(id, 0) * rowH, 13f);
				int ry = y + HEAD + 2 + Math.round(slot);
				UI.alpha = rowAlpha * Motion.clamp01(p * 1.8f);
				Motion.scaleAround(g, x + w / 2f, ry + rowH / 2f, 0.55f + 0.45f * p);
				String name = UI.fit(F.BODY, r.name(), r.dx() ? 84 : 110);
				UI.head(g, r.skin(), x + 10, ry + 3, 18, 5, 0xFF000000);
				UI.text(g, F.BODY, name, x + 36, ry + rowH / 2f, PANEL_TEXT);
				if (r.dx()) dxBadge(g, x + 36 + UI.width(F.BODY, name) + 6, ry + rowH / 2f);
				arrow(g, x + w - 16, ry + rowH / 2f, r.rel(), r.color());
				UI.textR(g, F.SMALL, Math.round(r.dist()) + " м", x + w - 32, ry + rowH / 2f, UI.TEXT_DIM);
				g.pose().popMatrix();
			}
			g.disableScissor();
			g.pose().popMatrix();
			UI.alpha = prevAlpha;
		}
	}

	// ======================================================================= PINS

	/** ХУД «Маркеры»: ближайшие маркеры текущего мира — иконка, название, дистанция и стрелка направления. */
	public static final class Pins extends HudWidget {
		private static final int MAX_ROWS = 6;

		private record Row(Waypoints.Marker m, double dist, float rel) {}

		public Pins() { super("pins", "Маркеры", 160, 50); icon = Icons.FLAG; }

		@Override public void place(int sw, int sh) { x = sw - 178; y = sh / 2 - 40; }

		private static String distText(double d) {
			return d < 1000 ? Math.round(d) + " м" : String.format(Locale.ROOT, "%.1f км", d / 1000.0);
		}

		private static Waypoints.Marker demo(String name, int color, int icon) {
			Waypoints.Marker m = new Waypoints.Marker();
			m.name = name;
			m.color = color;
			m.icon = icon;
			return m;
		}

		@Override public void render(GuiGraphics g, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.level == null) return;

			String world = Waypoints.currentWorld(mc);
			double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
			List<Row> rows = new ArrayList<>();
			int otherWorlds = 0;
			for (Waypoints.Marker m : Waypoints.LIST) {
				if (!m.world.equals(world)) {
					otherWorlds++;
					continue;
				}
				double dx = m.x - px, dy = m.y - py, dz = m.z - pz;
				float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				rows.add(new Row(m, Math.sqrt(dx * dx + dy * dy + dz * dz), Mth.wrapDegrees(yaw - mc.player.getYRot())));
			}
			rows.sort(Comparator.comparingDouble(Row::dist));
			int hidden = Math.max(0, rows.size() - MAX_ROWS);
			if (hidden > 0) rows = new ArrayList<>(rows.subList(0, MAX_ROWS));

			if (rows.isEmpty() && preview) {
				rows.add(new Row(demo("База", 0xFF34D399, 3), 48.0, 25f));
				rows.add(new Row(demo("Шахта", 0xFFF97316, 8), 212.0, -110f));
				rows.add(new Row(demo("Портал", 0xFF8B5CF6, 10), 1480.0, 160f));
			}
			if (rows.isEmpty()) {
				w = 0;
				h = 0;
				return;
			}

			// нижняя строка: сколько не поместилось и сколько лежит в других мирах
			String footer = null;
			if (hidden > 0 && otherWorlds > 0) footer = "ещё " + hidden + " · в др. мирах " + otherWorlds;
			else if (hidden > 0) footer = "ещё " + hidden;
			else if (otherWorlds > 0) footer = "в др. мирах: " + otherWorlds;

			int rowH = 24;
			int maxName = 0, maxDist = 0;
			for (Row r : rows) {
				maxName = Math.max(maxName, UI.width(F.BODY, UI.fit(F.BODY, r.m().name, 110)));
				maxDist = Math.max(maxDist, UI.width(F.SMALL, distText(r.dist())));
			}
			w = Math.max(headW("Маркеры"), 10 + 18 + 8 + maxName + 10 + maxDist + 10 + 12 + 10);
			if (footer != null) w = Math.max(w, UI.width(F.SMALL, footer) + 28);
			h = HEAD + rows.size() * rowH + (footer != null ? 16 : 0) + 6;
			panel(g, x, y, w, h, "Маркеры");

			for (int i = 0; i < rows.size(); i++) {
				Row r = rows.get(i);
				int ry = y + HEAD + 2 + i * rowH;
				float cy = ry + rowH / 2f;
				int color = r.m().color | 0xFF000000;
				UI.circle(g, x + 19, Math.round(cy), 18, color);
				UI.textC(g, F.ICON_S, Icons.MARKER_ICONS[Math.max(0, Math.min(Icons.MARKER_ICONS.length - 1, r.m().icon))], x + 19, cy, 0xFF0A0A0A);
				UI.text(g, F.BODY, UI.fit(F.BODY, r.m().name, 110), x + 36, cy, PANEL_TEXT);
				Near.arrow(g, x + w - 16, cy, r.rel(), color);
				UI.textR(g, F.SMALL, distText(r.dist()), x + w - 32, cy, UI.TEXT_DIM);
			}
			if (footer != null) UI.text(g, F.SMALL, footer, x + 14, y + HEAD + 2 + rows.size() * rowH + 8, UI.MUTED);
		}
	}

	// ==================================================================== КУЛДАУНЫ

	/**
	 * Таймеры предметов, которые сейчас «на перезарядке» (жемчуг, хорус, щит и т.п.). Данные — обычные кулдауны игры,
	 * длительность определяется по скорости убывания, поэтому работает и с серверными кулдауны.
	 */
	public static final class Cooldowns extends HudWidget {
		private static final class Cd {
			ItemStack stack;
			float last, total = -1f, pct;
		}

		private record Row(ItemStack stack, float pct, float sec) {}

		private final Map<Item, Cd> map = new LinkedHashMap<>();
		private int lastTick = Integer.MIN_VALUE;

		public Cooldowns() { super("cooldowns", "Кулдауны", 150, 60); icon = Icons.CLOCK; }

		@Override public void place(int sw, int sh) { x = sw - 160; y = sh / 2 - 40; }

		private void sample(Minecraft mc) {
			int t = mc.player.tickCount;
			if (t == lastTick) return;
			lastTick = t;
			var cds = mc.player.getCooldowns();
			Set<Item> active = new LinkedHashSet<>();
			var inv = mc.player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (s.isEmpty() || active.contains(s.getItem())) continue;
				float p = cds.getCooldownPercent(s, 0f);
				if (p <= 0f) continue;
				active.add(s.getItem());
				Cd c = map.computeIfAbsent(s.getItem(), k -> new Cd());
				c.stack = s;
				// процент убывает линейно: два замера дают полную длительность в тиках
				if (c.total < 0f && c.last > p) c.total = 1f / (c.last - p);
				c.last = p;
				c.pct = p;
			}
			map.keySet().retainAll(active);
		}

		@Override public void render(GuiGraphics g, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			List<Row> rows = new ArrayList<>();
			if (preview) {
				rows.add(new Row(new ItemStack(Items.ENDER_PEARL), 0.6f, 12.0f));
				rows.add(new Row(new ItemStack(Items.CHORUS_FRUIT), 0.25f, 1.0f));
			} else {
				if (mc.player == null) return;
				sample(mc);
				for (Cd c : map.values()) {
					if (c.total > 0f) rows.add(new Row(c.stack, c.pct, c.pct * c.total / 20f));
				}
			}
			if (rows.isEmpty()) return;
			final int rowH = 24;
			w = Math.max(headW("Кулдауны"), 150);
			h = HEAD + 4 + rows.size() * rowH + 2;
			panel(g, x, y, w, h, "Кулдауны");
			for (int i = 0; i < rows.size(); i++) {
				Row r = rows.get(i);
				int ry = y + HEAD + 3 + i * rowH;
				float cy = ry + rowH / 2f;
				g.renderItem(r.stack(), x + 9, ry + 4);
				String t = String.format(Locale.ROOT, "%.1fс", r.sec());
				UI.textR(g, F.SMALL, t, x + w - 10, cy, PANEL_TEXT);
				int bx = x + 32, bw = w - 32 - 52;
				UI.rrect(g, bx, Math.round(cy) - 1, bw, 3, 1, 0xFF1B1B1B);
				int fw = Math.round(bw * Math.max(0f, Math.min(1f, r.pct())));
				if (fw > 1) UI.rrect(g, bx, Math.round(cy) - 1, fw, 3, 1, Theme.accent());
			}
		}
	}

	// ====================================================================== ТОТЕМЫ

	/** Количество тотемов в инвентаре и сколько раз сработал твой. */
	public static final class Totems extends HudWidget {
		public Totems() { super("totems", "Тотемы", 96, 30); }

		@Override public void place(int sw, int sh) { x = sw / 2 - 48; y = sh - 100; }

		@Override public void render(GuiGraphics g, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			int n = preview ? 3 : dev.darkx.visuals.core.TotemPops.count(mc);
			int pops = preview ? 1 : dev.darkx.visuals.core.TotemPops.own;
			w = 96;
			h = 30;
			panel(g, x, y, w, h);
			g.renderItem(new ItemStack(Items.TOTEM_OF_UNDYING), x + 8, y + 7);
			UI.text(g, F.BOLD, "x" + n, x + 30, y + h / 2f, n > 0 ? PANEL_TEXT : 0xFFEF4444);
			UI.textR(g, F.SMALL, "поп " + pops, x + w - 8, y + h / 2f, UI.TEXT_DIM);
		}
	}
}
