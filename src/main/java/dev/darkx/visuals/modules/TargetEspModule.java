package dev.darkx.visuals.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.fx.WorldFx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Подсветка цели, на которую наведён прицел: бегающие кольца или круг под ногами. */
public final class TargetEspModule extends Module {
	public final Setting.Mode style = add(new Setting.Mode("Стиль", 0, "Кольца", "Земля"));
	public final Setting.Num size = add(new Setting.Num("Размер", 0.5, 2.0, 0.1, 1.0, "x"));
	public final Setting.Num keep = add(new Setting.Num("Держать цель", 0.5, 5, 0.5, 2, " с"));
	public final Setting.Color color = add(new Setting.Color("Цвет", 0));

	private LivingEntity target;
	private long seen;

	public TargetEspModule() {
		super("TargetESP", "Кольца вокруг цели, на которую ты смотришь.", Category.VISUAL);
	}

	@Override public void onTick(Minecraft mc) {
		long now = System.currentTimeMillis();
		Entity e = mc.crosshairPickEntity;
		if (e instanceof LivingEntity le && le != mc.player && !AntiAcBotModule.isBot(le)) {
			target = le;
			seen = now;
		}
		if (target != null && (target.isRemoved() || now - seen > (long) (keep.f() * 1000f))) target = null;
	}

	@Override public void onDisable() {
		target = null;
	}

	public void render(VertexConsumer vc, Matrix4f m, float pt) {
		if (target == null) return;
		long now = System.currentTimeMillis();
		float left = keep.f() * 1000f - (now - seen);
		float fade = Math.max(0f, Math.min(1f, left / 400f));
		if (fade <= 0f) return;

		Vec3 pos = target.getPosition(pt);
		float w = target.getBbWidth(), h = target.getBbHeight();
		float r = (w * 0.7f + 0.2f) * size.f();

		if (style.is("Земля")) {
			float pulse = 0.88f + 0.12f * (float) Math.sin(now / 220.0);
			WorldFx.ring(vc, m, pos.x, pos.y + 0.02, pos.z, r * pulse - 0.1f, r * pulse, 56, color, 0.95f * fade);
			WorldFx.disc(vc, m, pos.x, pos.y + 0.015, pos.z, r * pulse, 40, color, 0.16f * fade, 0.4f);
			return;
		}
		for (int k = 0; k < 4; k++) {
			double phase = now / 1100.0 - k * 0.035;
			float y = (float) (pos.y + h * (0.5 + 0.5 * Math.sin(phase * WorldFx.TAU)));
			float a = (1f - k * 0.27f) * fade;
			WorldFx.ring(vc, m, pos.x, y, pos.z, r - 0.07f, r, 48, color, a);
		}
	}
}
