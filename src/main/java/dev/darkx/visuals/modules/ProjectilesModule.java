package dev.darkx.visuals.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.fx.WorldFx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Предсказание траектории стрел, трезубца, жемчуга, снежков, яиц и зелий. */
public final class ProjectilesModule extends Module {
	public final Setting.Num thick = add(new Setting.Num("Толщина линии", 1, 6, 0.5, 2, " px"));
	public final Setting.Bool landing = add(new Setting.Bool("Точка падения", true));
	public final Setting.Color color = add(new Setting.Color("Цвет", 0));

	/** speed — начальная скорость, gravity — ускорение вниз за тик, pitchOffset — поправка угла (зелья / пузырьки опыта). */
	private record Shot(float speed, float gravity, float pitchOffset) {}

	public ProjectilesModule() {
		super("Projectiles", "Траектория полёта брошенных предметов и стрел.", Category.VISUAL);
	}

	private static Shot shotFor(Player p, ItemStack s) {
		if (s.isEmpty()) return null;
		if (s.is(Items.BOW)) {
			if (!p.isUsingItem() || p.getUseItem() != s) return null;
			float f = p.getTicksUsingItem() / 20f;
			f = (f * f + f * 2f) / 3f;
			if (f < 0.1f) return null;
			return new Shot(Math.min(f, 1f) * 3.0f, 0.05f, 0f);
		}
		if (s.is(Items.CROSSBOW)) {
			return CrossbowItem.isCharged(s) ? new Shot(3.15f, 0.05f, 0f) : null;
		}
		if (s.is(Items.TRIDENT)) {
			if (!p.isUsingItem() || p.getUseItem() != s || p.getTicksUsingItem() < 10) return null;
			return new Shot(2.5f, 0.05f, 0f);
		}
		if (s.is(Items.ENDER_PEARL) || s.is(Items.SNOWBALL) || s.is(Items.EGG)) return new Shot(1.5f, 0.03f, 0f);
		if (s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)) return new Shot(0.5f, 0.05f, -20f);
		if (s.is(Items.EXPERIENCE_BOTTLE)) return new Shot(0.7f, 0.07f, -20f);
		return null;
	}

	public void render(VertexConsumer vc, Matrix4f m, float pt) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null || mc.level == null) return;

		Shot shot = shotFor(p, p.getMainHandItem());
		if (shot == null) shot = shotFor(p, p.getOffhandItem());
		if (shot == null) return;

		float xRot = p.getViewXRot(pt), yRot = p.getViewYRot(pt);
		double rx = Math.toRadians(xRot), ry = Math.toRadians(yRot), rz = Math.toRadians(xRot + shot.pitchOffset);
		double dx = -Math.sin(ry) * Math.cos(rx);
		double dy = -Math.sin(rz);
		double dz = Math.cos(ry) * Math.cos(rx);
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		Vec3 vel = new Vec3(dx / len * shot.speed, dy / len * shot.speed, dz / len * shot.speed);
		Vec3 pm = p.getDeltaMovement();
		vel = vel.add(pm.x, p.onGround() ? 0 : pm.y, pm.z);

		Vec3 pos = p.getEyePosition(pt).subtract(0, 0.1, 0);

		// живые цели рядом — чтобы заканчивать линию на попадании в моба
		List<LivingEntity> targets = new ArrayList<>();
		AABB area = p.getBoundingBox().inflate(80);
		for (Entity e : mc.level.getEntities((Entity) null, area, e -> e instanceof LivingEntity && e != p)) {
			targets.add((LivingEntity) e);
		}

		double lw = thick.value * 0.01;
		final int steps = 240;
		Vec3 end = null;
		boolean entityHit = false;

		for (int i = 0; i < steps; i++) {
			Vec3 next = pos.add(vel);

			Vec3 hit = null;
			boolean hitEntity = false;
			double best = Double.MAX_VALUE;
			for (LivingEntity le : targets) {
				Optional<Vec3> c = le.getBoundingBox().inflate(0.3).clip(pos, next);
				if (c.isPresent()) {
					double d = c.get().distanceToSqr(pos);
					if (d < best) {
						best = d;
						hit = c.get();
						hitEntity = true;
					}
				}
			}
			var bh = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (bh.getType() != HitResult.Type.MISS) {
				double d = bh.getLocation().distanceToSqr(pos);
				if (d < best) {
					hit = bh.getLocation();
					hitEntity = false;
				}
			}

			Vec3 to = hit != null ? hit : next;
			WorldFx.line(vc, m, pos.x, pos.y, pos.z, to.x, to.y, to.z, lw,
				WorldFx.argb(color.color(i / (float) steps), 0.9f));
			if (hit != null) {
				end = hit;
				entityHit = hitEntity;
				break;
			}
			pos = next;
			vel = new Vec3(vel.x * 0.99, vel.y * 0.99 - shot.gravity, vel.z * 0.99);
			if (pos.y < mc.level.getMinY() - 16) break;
		}

		if (landing.value && end != null) {
			int rgb = entityHit ? 0xEF4444 : (color.color(0) & 0xFFFFFF);
			double s = 0.14;
			WorldFx.box(vc, m, end.x - s, end.y - s, end.z - s, end.x + s, end.y + s, end.z + s, WorldFx.argb(rgb, 0.8f));
		}
	}
}
