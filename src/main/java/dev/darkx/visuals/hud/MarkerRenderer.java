package dev.darkx.visuals.hud;

import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Theme;
import dev.darkx.visuals.core.Waypoints;
import dev.darkx.visuals.modules.MarkersModule;
import dev.darkx.visuals.modules.ZoomModule;
import dev.darkx.visuals.ui.ClickGuiScreen;
import dev.darkx.visuals.ui.HudEditorScreen;
import dev.darkx.visuals.ui.Icons;
import dev.darkx.visuals.ui.UI;
import dev.darkx.visuals.ui.UI.F;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/** Рисует маркеры поверх мира: проецируем координаты на экран вручную. */
public final class MarkerRenderer {
	private MarkerRenderer() {}

	public static void render(GuiGraphics g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		MarkersModule mod = ModuleManager.get(MarkersModule.class);
		if (mod == null || !mod.enabled || mc.player == null || mc.level == null) return;
		if (mc.screen instanceof ClickGuiScreen || mc.screen instanceof HudEditorScreen) return;
		if (Waypoints.LIST.isEmpty()) return;

		UI.alpha = 1f;
		String world = Waypoints.currentWorld(mc);
		float pt = dt.getGameTimeDeltaPartialTick(true);
		Vec3 eye = mc.player.getEyePosition(pt);
		double yaw = Math.toRadians(mc.player.getViewYRot(pt));
		double pitch = Math.toRadians(mc.player.getViewXRot(pt));
		double cp = Math.cos(pitch), sp = Math.sin(pitch), cy = Math.cos(yaw), sy = Math.sin(yaw);
		// базис камеры: вперёд / вправо / вверх
		double fx = -sy * cp, fy = -sp, fz = cy * cp;
		double rx = -cy, rz = -sy;
		double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;

		int sw = g.guiWidth(), sh = g.guiHeight();
		double aspect = (double) sw / Math.max(1, sh);
		double tanV = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0) / ZoomModule.divisor();
		float maxDist = mod.maxDist.f();

		for (Waypoints.Marker m : Waypoints.LIST) {
			if (!m.world.equals(world)) continue;
			double dx = m.x - eye.x, dy = m.y - eye.y, dz = m.z - eye.z;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (maxDist > 0 && dist > maxDist) continue;

			double zc = dx * fx + dy * fy + dz * fz;
			double xc = dx * rx + dz * rz;
			double yc = dx * ux + dy * uy + dz * uz;

			float px, py;
			boolean onScreen = false;
			double ax, ay;
			if (zc > 0.1) {
				px = (float) (sw / 2.0 + xc / zc / (tanV * aspect) * sw / 2.0);
				py = (float) (sh / 2.0 - yc / zc / tanV * sh / 2.0);
				onScreen = px >= 14 && px <= sw - 14 && py >= 14 && py <= sh - 14;
				ax = px - sw / 2.0;
				ay = py - sh / 2.0;
			} else {
				px = py = 0;
				ax = xc;
				ay = -yc;
			}
			if (!onScreen) {
				double len = Math.hypot(ax, ay);
				if (len < 1e-6) {
					ax = 0;
					ay = -1;
				}
				double tx = Math.abs(ax) < 1e-6 ? Double.MAX_VALUE : (sw / 2.0 - 22) / Math.abs(ax);
				double ty = Math.abs(ay) < 1e-6 ? Double.MAX_VALUE : (sh / 2.0 - 22) / Math.abs(ay);
				double t = Math.min(tx, ty);
				px = (float) (sw / 2.0 + ax * t);
				py = (float) (sh / 2.0 + ay * t);
			}
			draw(g, mod, m, Math.round(px), Math.round(py), dist, onScreen);
		}
	}

	private static void draw(GuiGraphics g, MarkersModule mod, Waypoints.Marker m, int x, int y, double dist, boolean full) {
		int color = m.color | 0xFF000000;
		UI.circle(g, x, y, 26, Theme.withAlpha(0x000000, 0.55f));
		UI.circle(g, x, y, 22, color);
		UI.textC(g, F.ICON, Icons.MARKER_ICONS[Math.max(0, Math.min(Icons.MARKER_ICONS.length - 1, m.icon))], x, y, 0xFF0A0A0A);
		if (!full) return;

		String name = mod.showName.value ? m.name : null;
		String d = mod.showDistance.value ? Math.round(dist) + " м" : null;
		if (name == null && d == null) return;
		String text = name == null ? d : (d == null ? name : name + "  ·  " + d);
		int tw = UI.width(F.SMALL, text) + 16;
		UI.rrect(g, x - tw / 2, y + 17, tw, 16, Theme.hudR(6), 0xB0000000);
		UI.textC(g, F.SMALL, text, x, y + 25, UI.TEXT);
	}
}
