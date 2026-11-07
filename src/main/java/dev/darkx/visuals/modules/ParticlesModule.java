package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/** Фоновые частицы вокруг игрока. */
public final class ParticlesModule extends Module {
	public static final String[] NAMES = {
		"Искры", "Сердца", "Электро", "Огонь", "Снежинки", "Души", "Руны", "Криты", "Пузыри", "Тотем", "Сакура", "Салют"
	};

	public final Setting.Mode type = add(new Setting.Mode("Частицы", 0, NAMES));
	public final Setting.Num amount = add(new Setting.Num("Количество", 1, 20, 1, 4, ""));
	public final Setting.Num radius = add(new Setting.Num("Радиус", 2, 16, 1, 8, " м"));

	public ParticlesModule() {
		super("Particles", "Красивые частицы вокруг тебя.", Category.VISUAL);
	}

	public static ParticleOptions option(int index) {
		return switch (index) {
			case 1 -> ParticleTypes.HEART;
			case 2 -> ParticleTypes.ELECTRIC_SPARK;
			case 3 -> ParticleTypes.FLAME;
			case 4 -> ParticleTypes.SNOWFLAKE;
			case 5 -> ParticleTypes.SOUL_FIRE_FLAME;
			case 6 -> ParticleTypes.ENCHANT;
			case 7 -> ParticleTypes.CRIT;
			case 8 -> ParticleTypes.BUBBLE;
			case 9 -> ParticleTypes.TOTEM_OF_UNDYING;
			case 10 -> ParticleTypes.CHERRY_LEAVES;
			case 11 -> ParticleTypes.FIREWORK;
			default -> ParticleTypes.END_ROD;
		};
	}

	@Override public void onTick(Minecraft mc) {
		Player p = mc.player;
		if (p == null || mc.level == null) return;
		RandomSource rnd = mc.level.getRandom();
		ParticleOptions opt = option(type.index);
		double r = radius.value;
		for (int i = 0; i < amount.i(); i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double d = Math.sqrt(rnd.nextDouble()) * r;
			double x = p.getX() + Math.cos(a) * d;
			double z = p.getZ() + Math.sin(a) * d;
			double y = p.getY() + rnd.nextDouble() * 3.5 - 0.5;
			mc.level.addParticle(opt, x, y, z,
				(rnd.nextDouble() - 0.5) * 0.02, rnd.nextDouble() * 0.03, (rnd.nextDouble() - 0.5) * 0.02);
		}
	}
}
