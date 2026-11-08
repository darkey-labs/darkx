package dev.darkx.visuals.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Marks;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.fx.WorldFx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/** Хитбоксы игроков / мобов / предметов: контур, заливка или оба сразу. */
public final class HitboxesModule extends Module {
	public final Setting.Multi targets = add(new Setting.Multi("Цели",
		new String[]{"Игроки", "Мобы", "Предметы"}, new boolean[]{true, false, false}));
	public final Setting.Mode style = add(new Setting.Mode("Стиль", 0, "Контур", "Заливка", "Оба"));
	public final Setting.Num range = add(new Setting.Num("Дистанция", 8, 128, 4, 48, " м"));
	public final Setting.Num grow = add(new Setting.Num("Расширение", 0, 0.3, 0.01, 0, " м"));
	public final Setting.Num thick = add(new Setting.Num("Толщина линий", 1, 6, 0.5, 2, " px"));
	public final Setting.Num fill = add(new Setting.Num("Заливка", 5, 60, 5, 20, "%"));
	public final Setting.Bool marks = add(new Setting.Bool("Цвета меток", true));
	public final Setting.Color color = add(new Setting.Color("Цвет", 0));

	public HitboxesModule() {
		super("Hitboxes", "Подсветка хитбоксов игроков, мобов и предметов.", Category.VISUAL);
	}

	public void render(VertexConsumer vc, Matrix4f m, float pt) {
		Minecraft mc = Minecraft.getInstance();
		Player me = mc.player;
		if (me == null || mc.level == null) return;

		double r = range.value;
		AABB area = me.getBoundingBox().inflate(r);
		List<Entity> list = mc.level.getEntities((Entity) null, area, e -> true);

		boolean lines = !style.is("Заливка");
		boolean fills = !style.is("Контур");
		double lw = thick.value * 0.01;
		int base = color.color(0) & 0xFFFFFF;

		for (Entity e : list) {
			if (e == me || e.isRemoved() || e.isSpectator()) continue;
			if (e instanceof LivingEntity && e.isInvisible()) continue; // невидимых (зелье) не выдаём
			boolean player = e instanceof Player;
			boolean item = e instanceof ItemEntity;
			boolean mob = e instanceof LivingEntity && !player;
			if (player) {
				if (!targets.isOn(0) || AntiAcBotModule.isFake(e)) continue;
			} else if (mob) {
				if (!targets.isOn(1)) continue;
			} else if (item) {
				if (!targets.isOn(2)) continue;
			} else {
				continue;
			}
			if (e.distanceToSqr(me) > r * r) continue;

			Vec3 off = e.getPosition(pt).subtract(e.position());
			AABB b = e.getBoundingBox().move(off).inflate(grow.value);

			int rgb = base;
			if (player && marks.value) {
				Marks.Type t = Marks.get(e.getUUID());
				if (t != null) rgb = t.color & 0xFFFFFF;
			}
			if (fills) {
				WorldFx.box(vc, m, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, WorldFx.argb(rgb, fill.f() / 100f));
			}
			if (lines) {
				WorldFx.outline(vc, m, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, lw, WorldFx.argb(rgb, 0.95f));
			}
		}
	}
}
