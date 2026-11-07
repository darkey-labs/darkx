package dev.darkx.visuals.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.fx.WorldFx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Шлейф за игроком: вертикальная лента, плавно исчезающая к хвосту. */
public final class TrailsModule extends Module {
	public final Setting.Num length = add(new Setting.Num("Длина", 10, 100, 5, 40, " тик."));
	public final Setting.Num height = add(new Setting.Num("Высота", 0.1, 2.0, 0.05, 0.9, ""));
	public final Setting.Num opacity = add(new Setting.Num("Прозрачность", 10, 100, 5, 70, "%"));
	public final Setting.Color color = add(new Setting.Color("Цвет", 0));

	private final List<double[]> points = new ArrayList<>();

	public TrailsModule() {
		super("Trails", "Светящийся шлейф за игроком.", Category.VISUAL);
	}

	@Override public void onTick(Minecraft mc) {
		if (mc.player == null) return;
		double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
		if (points.isEmpty() || dist2(points.get(points.size() - 1), x, y, z) > 0.0004) {
			points.add(new double[] {x, y, z});
		} else if (!points.isEmpty()) {
			points.remove(0); // стоим на месте — хвост постепенно съедается
		}
		while (points.size() > length.i()) points.remove(0);
	}

	@Override public void onDisable() {
		points.clear();
	}

	private static double dist2(double[] p, double x, double y, double z) {
		double dx = p[0] - x, dy = p[1] - y, dz = p[2] - z;
		return dx * dx + dy * dy + dz * dz;
	}

	public void render(VertexConsumer vc, Matrix4f m, float pt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || points.size() < 1) return;
		Vec3 head = mc.player.getPosition(pt);
		int n = points.size();
		float top = height.f(), a = opacity.f() / 100f;

		double px = 0, py = 0, pz = 0;
		for (int i = 0; i <= n; i++) {
			double x, y, z;
			if (i < n) {
				double[] p = points.get(i);
				x = p[0];
				y = p[1];
				z = p[2];
			} else {
				x = head.x;
				y = head.y;
				z = head.z;
			}
			if (i > 0) {
				float t0 = (i - 1) / (float) n, t1 = i / (float) n;
				int c0 = WorldFx.argb(color.color(t0), a * t0 * t0);
				int c1 = WorldFx.argb(color.color(t1), a * t1 * t1);
				int c0Top = WorldFx.argb(color.color(t0), 0f);
				int c1Top = WorldFx.argb(color.color(t1), 0f);
				WorldFx.quad(vc, m,
					px, py, pz, c0,
					px, py + top, pz, c0Top,
					x, y + top, z, c1Top,
					x, y, z, c1);
			}
			px = x;
			py = y;
			pz = z;
		}
	}
}
