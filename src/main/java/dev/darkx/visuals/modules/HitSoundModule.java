package dev.darkx.visuals.modules;

import dev.darkx.visuals.core.AttackTracker;
import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Свой звук попадания и убийства. Играется только у тебя, когда существо, по которому ты бил, получает урон или умирает. */
public final class HitSoundModule extends Module {
	private static final String[] NAMES = {"Ding", "Pling", "Bell", "Chime", "Bit", "Hat", "Arrow", "Crit", "Totem", "Glass"};
	private static final String[] IDS = {
		"entity.experience_orb.pickup", "block.note_block.pling", "block.bell.use", "block.amethyst_block.chime",
		"block.note_block.bit", "block.note_block.hat", "entity.arrow.hit_player", "entity.player.attack.crit",
		"item.totem.use", "block.glass.break"
	};

	public final Setting.Mode hit = add(new Setting.Mode("Звук попадания", 0, withNone("Нет")));
	public final Setting.Mode kill = add(new Setting.Mode("Звук убийства", 0, withNone("Нет")));
	public final Setting.Num volume = add(new Setting.Num("Громкость", 10, 100, 5, 70, "%"));
	public final Setting.Num pitch = add(new Setting.Num("Тон", 0.5, 2, 0.05, 1, "x"));
	public final Setting.Bool players = add(new Setting.Bool("Только по игрокам", false));

	public HitSoundModule() {
		super("HitSound", "Свой звук попадания и убийства (слышишь только ты).", Category.MISC);
		hit.index = 1; // по умолчанию Ding на попадание
	}

	private static String[] withNone(String none) {
		String[] a = new String[NAMES.length + 1];
		a[0] = none;
		System.arraycopy(NAMES, 0, a, 1, NAMES.length);
		return a;
	}

	@Override public void onTick(Minecraft mc) {
		AttackTracker.tick(mc);
		for (LivingEntity le : AttackTracker.HITS) {
			if (players.value && !(le instanceof Player)) continue;
			play(mc, hit.index);
		}
		for (LivingEntity le : AttackTracker.KILLS) {
			if (players.value && !(le instanceof Player)) continue;
			play(mc, kill.index);
		}
	}

	private void play(Minecraft mc, int index) {
		if (index <= 0) return;
		SoundEvent ev = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("minecraft", IDS[index - 1]));
		mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch.f(), volume.f() / 100f));
	}
}
