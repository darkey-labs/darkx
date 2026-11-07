package dev.darkx.visuals.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.modules.BlockOverlayModule;
import dev.darkx.visuals.modules.ChinaHatModule;
import dev.darkx.visuals.modules.HitboxesModule;
import dev.darkx.visuals.modules.ProjectilesModule;
import dev.darkx.visuals.modules.JumpCirclesModule;
import dev.darkx.visuals.modules.TargetEspModule;
import dev.darkx.visuals.modules.TrailsModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Мировые визуалы рисуются одним слоем debugFilledBox (позиция + цвет, полупрозрачный).
 * Все модули получают камеро-относительные координаты через {@link #vert}.
 * Каждая грань рисуется с двух сторон, чтобы не зависеть от отсечения.
 */
public final class WorldFx {
	public static final float TAU = (float) (Math.PI * 2.0);

	private static double camX, camY, camZ;

	private WorldFx() {}

	public static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		JumpCirclesModule jump = ModuleManager.get(JumpCirclesModule.class);
		ChinaHatModule hat = ModuleManager.get(ChinaHatModule.class);
		TargetEspModule esp = ModuleManager.get(TargetEspModule.class);
		TrailsModule trails = ModuleManager.get(TrailsModule.class);
		BlockOverlayModule block = ModuleManager.get(BlockOverlayModule.class);
		HitboxesModule boxes = ModuleManager.get(HitboxesModule.class);
		ProjectilesModule proj = ModuleManager.get(ProjectilesModule.class);
		if (!(jump.enabled || hat.enabled || esp.enabled || trails.enabled || block.enabled || boxes.enabled || proj.enabled)) return;

		Vec3 cam = ctx.worldState().cameraRenderState.pos;
		camX = cam.x;
		camY = cam.y;
		camZ = cam.z;

		VertexConsumer vc = ctx.consumers().getBuffer(RenderTypes.debugFilledBox());
		Matrix4f m = ctx.matrices().last().pose();
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

		if (jump.enabled) jump.render(vc, m);
		if (trails.enabled) trails.render(vc, m, pt);
		if (esp.enabled) esp.render(vc, m, pt);
		if (hat.enabled) hat.render(vc, m, pt);
		if (block.enabled) block.render(vc, m);
		if (boxes.enabled) boxes.render(vc, m, pt);
		if (proj.enabled) proj.render(vc, m, pt);
	}

	// ------------------------------------------------------------ примитивы

	public static int argb(int rgb, float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	public static void vert(VertexConsumer vc, Matrix4f m, double x, double y, double z, int color) {
		vc.addVertex(m, (float) (x - camX), (float) (y - camY), (float) (z - camZ)).setColor(color);
	}

	/** Четырёхугольник с обеих сторон. */
	public static void quad(VertexConsumer vc, Matrix4f m,
							double x1, double y1, double z1, int c1,
							double x2, double y2, double z2, int c2,
							double x3, double y3, double z3, int c3,
							double x4, double y4, double z4, int c4) {
		vert(vc, m, x1, y1, z1, c1);
		vert(vc, m, x2, y2, z2, c2);
		vert(vc, m, x3, y3, z3, c3);
		vert(vc, m, x4, y4, z4, c4);
		vert(vc, m, x4, y4, z4, c4);
		vert(vc, m, x3, y3, z3, c3);
		vert(vc, m, x2, y2, z2, c2);
		vert(vc, m, x1, y1, z1, c1);
	}

	/** Горизонтальное кольцо между радиусами r0 и r1. */
	public static void ring(VertexConsumer vc, Matrix4f m, double cx, double y, double cz,
							float r0, float r1, int segs, Setting.Color col, float alpha) {
		if (alpha <= 0.002f) return;
		for (int i = 0; i < segs; i++) {
			float a0 = TAU * i / segs, a1 = TAU * (i + 1) / segs;
			float s0 = (float) Math.sin(a0), c0 = (float) Math.cos(a0);
			float s1 = (float) Math.sin(a1), c1 = (float) Math.cos(a1);
			int k0 = argb(col.color(i / (float) segs), alpha);
			int k1 = argb(col.color((i + 1) / (float) segs), alpha);
			quad(vc, m,
				cx + c0 * r0, y, cz + s0 * r0, k0,
				cx + c0 * r1, y, cz + s0 * r1, k0,
				cx + c1 * r1, y, cz + s1 * r1, k1,
				cx + c1 * r0, y, cz + s1 * r0, k1);
		}
	}

	/** Диск: в центре alpha, к краю alpha * edge. */
	public static void disc(VertexConsumer vc, Matrix4f m, double cx, double y, double cz,
							float r, int segs, Setting.Color col, float alpha, float edge) {
		if (alpha <= 0.002f) return;
		for (int i = 0; i < segs; i++) {
			float a0 = TAU * i / segs, a1 = TAU * (i + 1) / segs;
			int center = argb(col.color(i / (float) segs), alpha);
			int e0 = argb(col.color(i / (float) segs), alpha * edge);
			int e1 = argb(col.color((i + 1) / (float) segs), alpha * edge);
			double x0 = cx + Math.cos(a0) * r, z0 = cz + Math.sin(a0) * r;
			double x1 = cx + Math.cos(a1) * r, z1 = cz + Math.sin(a1) * r;
			quad(vc, m, cx, y, cz, center, x0, y, z0, e0, x1, y, z1, e1, x1, y, z1, e1);
		}
	}

	/** Конус: основание на высоте y, вершина выше на height. */
	public static void cone(VertexConsumer vc, Matrix4f m, double cx, double y, double cz,
							float radius, float height, int segs, Setting.Color col, float alpha) {
		for (int i = 0; i < segs; i++) {
			float a0 = TAU * i / segs, a1 = TAU * (i + 1) / segs;
			int base0 = argb(col.color(i / (float) segs), alpha);
			int base1 = argb(col.color((i + 1) / (float) segs), alpha);
			int apex = argb(col.color(i / (float) segs), alpha * 0.55f);
			double x0 = cx + Math.cos(a0) * radius, z0 = cz + Math.sin(a0) * radius;
			double x1 = cx + Math.cos(a1) * radius, z1 = cz + Math.sin(a1) * radius;
			quad(vc, m, cx, y + height, cz, apex, x0, y, z0, base0, x1, y, z1, base1, x1, y, z1, base1);
		}
	}

	/** Линия-лента шириной width, повёрнутая к камере. */
	public static void line(VertexConsumer vc, Matrix4f m, double x0, double y0, double z0,
							double x1, double y1, double z1, double width, int color) {
		double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
		double vx = (x0 + x1) * 0.5 - camX, vy = (y0 + y1) * 0.5 - camY, vz = (z0 + z1) * 0.5 - camZ;
		// нормаль к линии и направлению взгляда
		double nx = dy * vz - dz * vy, ny = dz * vx - dx * vz, nz = dx * vy - dy * vx;
		double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1.0e-9) return;
		double k = width * 0.5 / len;
		nx *= k;
		ny *= k;
		nz *= k;
		quad(vc, m,
			x0 - nx, y0 - ny, z0 - nz, color,
			x0 + nx, y0 + ny, z0 + nz, color,
			x1 + nx, y1 + ny, z1 + nz, color,
			x1 - nx, y1 - ny, z1 - nz, color);
	}

	/** Рёбра параллелепипеда линиями. */
	public static void outline(VertexConsumer vc, Matrix4f m, double x0, double y0, double z0,
							   double x1, double y1, double z1, double width, int color) {
		line(vc, m, x0, y0, z0, x1, y0, z0, width, color);
		line(vc, m, x0, y0, z1, x1, y0, z1, width, color);
		line(vc, m, x0, y1, z0, x1, y1, z0, width, color);
		line(vc, m, x0, y1, z1, x1, y1, z1, width, color);
		line(vc, m, x0, y0, z0, x0, y1, z0, width, color);
		line(vc, m, x1, y0, z0, x1, y1, z0, width, color);
		line(vc, m, x0, y0, z1, x0, y1, z1, width, color);
		line(vc, m, x1, y0, z1, x1, y1, z1, width, color);
		line(vc, m, x0, y0, z0, x0, y0, z1, width, color);
		line(vc, m, x1, y0, z0, x1, y0, z1, width, color);
		line(vc, m, x0, y1, z0, x0, y1, z1, width, color);
		line(vc, m, x1, y1, z0, x1, y1, z1, width, color);
	}

	/** Прозрачный параллелепипед (6 граней). */
	public static void box(VertexConsumer vc, Matrix4f m, double x0, double y0, double z0,
						   double x1, double y1, double z1, int color) {
		quad(vc, m, x0, y0, z0, color, x1, y0, z0, color, x1, y1, z0, color, x0, y1, z0, color);
		quad(vc, m, x0, y0, z1, color, x1, y0, z1, color, x1, y1, z1, color, x0, y1, z1, color);
		quad(vc, m, x0, y0, z0, color, x0, y0, z1, color, x0, y1, z1, color, x0, y1, z0, color);
		quad(vc, m, x1, y0, z0, color, x1, y0, z1, color, x1, y1, z1, color, x1, y1, z0, color);
		quad(vc, m, x0, y1, z0, color, x1, y1, z0, color, x1, y1, z1, color, x0, y1, z1, color);
		quad(vc, m, x0, y0, z0, color, x1, y0, z0, color, x1, y0, z1, color, x0, y0, z1, color);
	}
}
