package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/** Частицы в момент получения урона любым существом рядом. */
public final class HitBubblesModule extends Module {
	public final Setting.Mode type = add(new Setting.Mode("Частицы", 1, ParticlesModule.NAMES));
	public final Setting.Num amount = add(new Setting.Num("Количество", 3, 40, 1, 14, ""));
	public final Setting.Num power = add(new Setting.Num("Разлёт", 1, 10, 1, 4, ""));
	public final Setting.Bool self = add(new Setting.Bool("Включая себя", false));

	private final Map<Integer, Integer> last = new HashMap<>();

	public HitBubblesModule() {
		super("HitBubbles", "Частицы при попадании по существу.", Category.VISUAL);
	}

	@Override public void onDisable() {
		last.clear();
	}

	@Override public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) return;
		Map<Integer, Integer> now = new HashMap<>();
		RandomSource rnd = mc.level.getRandom();
		ParticleOptions opt = ParticlesModule.option(type.index);

		for (Entity e : mc.level.getEntities((Entity) null, mc.player.getBoundingBox().inflate(40), x -> x instanceof LivingEntity)) {
			LivingEntity le = (LivingEntity) e;
			if (le == mc.player && !self.value) continue;
			if (AntiAcBotModule.isFake(le)) continue;
			int id = le.getId();
			int ht = le.hurtTime;
			Integer prev = last.get(id);
			now.put(id, ht);
			if (prev == null || ht <= prev || ht < 5) continue;

			double cx = le.getX(), cy = le.getY() + le.getBbHeight() * 0.6, cz = le.getZ();
			double v = power.value * 0.04;
			for (int i = 0; i < amount.i(); i++) {
				double vx = (rnd.nextDouble() - 0.5) * 2 * v;
				double vy = rnd.nextDouble() * v * 1.2;
				double vz = (rnd.nextDouble() - 0.5) * 2 * v;
				mc.level.addParticle(opt,
					cx + (rnd.nextDouble() - 0.5) * le.getBbWidth(), cy + (rnd.nextDouble() - 0.5) * 0.6,
					cz + (rnd.nextDouble() - 0.5) * le.getBbWidth(), vx, vy, vz);
			}
		}
		last.clear();
		last.putAll(now);
	}
}
