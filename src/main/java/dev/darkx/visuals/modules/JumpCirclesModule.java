package dev.darkx.visuals.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.fx.WorldFx;
import dev.darkx.visuals.ui.UI;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Расходящиеся кольца в месте прыжка. */
public final class JumpCirclesModule extends Module {
	public final Setting.Mode who = add(new Setting.Mode("Игроки", 0, "Только я", "Все игроки"));
	public final Setting.Num radius = add(new Setting.Num("Радиус", 0.5, 3.5, 0.1, 1.6, " бл."));
	public final Setting.Num life = add(new Setting.Num("Время жизни", 0.4, 3.0, 0.1, 1.2, " с"));
	public final Setting.Num width = add(new Setting.Num("Толщина", 0.04, 0.4, 0.01, 0.12, ""));
	public final Setting.Bool fill = add(new Setting.Bool("Заливка", true));
	public final Setting.Color color = add(new Setting.Color("Цвет", 0));

	private record Circle(double x, double y, double z, long born) {}

	private final List<Circle> circles = new ArrayList<>();
	private final Map<Integer, Boolean> ground = new HashMap<>();

	public JumpCirclesModule() {
		super("JumpCircles", "Кольца на земле в месте прыжка.", Category.VISUAL);
	}

	@Override public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) return;
		List<? extends Player> players = who.is("Все игроки") ? mc.level.players() : List.of(mc.player);
		long now = System.currentTimeMillis();
		for (Player p : players) {
			if (AntiAcBotModule.isBot(p)) continue;
			boolean onGround = p.onGround();
			Boolean was = ground.put(p.getId(), onGround);
			if (was != null && was && !onGround && p.getDeltaMovement().y > 0.1) {
				circles.add(new Circle(p.getX(), p.getY() + 0.02, p.getZ(), now));
			}
		}
		long maxAge = (long) (life.f() * 1000f) + 50;
		circles.removeIf(c -> now - c.born() > maxAge);
		while (circles.size() > 96) circles.remove(0);
		if (ground.size() > 512) ground.clear();
	}

	@Override public void onDisable() {
		circles.clear();
		ground.clear();
	}

	public void render(VertexConsumer vc, Matrix4f m) {
		long now = System.currentTimeMillis();
		float lifeMs = life.f() * 1000f;
		for (Circle c : circles) {
			float t = (now - c.born()) / lifeMs;
			if (t < 0f || t >= 1f) continue;
			float r = radius.f() * (0.15f + 0.85f * UI.ease(t));
			float alpha = (1f - t) * (1f - t);
			float inner = Math.max(0.01f, r - width.f() * (0.6f + 0.4f * (1f - t)));
			WorldFx.ring(vc, m, c.x(), c.y(), c.z(), inner, r, 56, color, alpha * 0.95f);
			if (fill.value) WorldFx.disc(vc, m, c.x(), c.y() - 0.005, c.z(), r, 40, color, alpha * 0.12f, 0.45f);
		}
	}
}
