package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.AttackTracker;
import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;

/**
 * Эффект на месте убитого тобой существа: молния, тотем, фейерверк, души, взрыв, сердца.
 * Всё рисуется только у тебя на экране, сервер ничего не знает.
 */
public final class KillEffectModule extends Module {
	private static final String[] NAMES = {"Молния", "Тотем", "Фейерверк", "Души", "Взрыв", "Сердца"};

	public final Setting.Mode effect = add(new Setting.Mode("Эффект", 0, NAMES));
	public final Setting.Num amount = add(new Setting.Num("Количество частиц", 10, 120, 5, 50, ""));
	public final Setting.Bool sound = add(new Setting.Bool("Звук", true));
	public final Setting.Bool players = add(new Setting.Bool("Только игроки", false));

	private int boltId = -50_000;

	public KillEffectModule() {
		super("KillEffect", "Эффект на месте убитого тобой существа: молния, тотем, фейерверк и другие.", Category.VISUAL);
		amount.showIf(() -> !effect.is("Молния"));
	}

	@Override public void onTick(Minecraft mc) {
		AttackTracker.tick(mc);
		for (LivingEntity le : AttackTracker.KILLS) {
			if (players.value && !(le instanceof net.minecraft.world.entity.player.Player)) continue;
			play(mc, le);
		}
	}

	private void play(Minecraft mc, LivingEntity le) {
		double x = le.getX(), y = le.getY(), z = le.getZ();
		double cy = y + le.getBbHeight() * 0.5;
		if (effect.is("Молния")) {
			// молния только для глаз: на клиенте она не поджигает и не бьёт
			LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, mc.level);
			bolt.setPos(x, y, z);
			bolt.setVisualOnly(true);
			bolt.setId(boltId--);
			mc.level.addEntity(bolt);
			if (boltId < -90_000) boltId = -50_000;
			return;
		}
		ParticleOptions p;
		String snd;
		double speed;
		switch (effect.get()) {
			case "Тотем" -> { p = ParticleTypes.TOTEM_OF_UNDYING; snd = "item.totem.use"; speed = 0.6; }
			case "Фейерверк" -> { p = ParticleTypes.FIREWORK; snd = "entity.firework_rocket.blast"; speed = 0.5; }
			case "Души" -> { p = ParticleTypes.SOUL; snd = "particle.soul_escape"; speed = 0.15; }
			case "Взрыв" -> { p = ParticleTypes.EXPLOSION; snd = "entity.generic.explode"; speed = 0.2; }
			default -> { p = ParticleTypes.HEART; snd = "entity.experience_orb.pickup"; speed = 0.2; }
		}
		RandomSource rnd = mc.level.getRandom();
		int n = effect.is("Взрыв") ? Math.max(3, amount.i() / 10) : amount.i();
		for (int i = 0; i < n; i++) {
			double vx = (rnd.nextDouble() - 0.5) * 2 * speed;
			double vy = rnd.nextDouble() * speed * 1.2;
			double vz = (rnd.nextDouble() - 0.5) * 2 * speed;
			mc.level.addParticle(p,
				x + (rnd.nextDouble() - 0.5) * le.getBbWidth(), cy + (rnd.nextDouble() - 0.5) * 0.8,
				z + (rnd.nextDouble() - 0.5) * le.getBbWidth(), vx, vy, vz);
		}
		if (sound.value) {
			SoundEvent ev = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("minecraft", snd));
			mc.level.playLocalSound(x, cy, z, ev, SoundSource.PLAYERS, 0.8f, 1f, false);
		}
	}
}
