package dev.darkx.visuals.core;

import dev.darkx.visuals.modules.AntiAcBotModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Что произошло с существами, по которым ты бил: попадание (существо получило урон) и убийство.
 * Только наблюдение за тем, что уже видно в игре (взмах, hurtTime, смерть) — ничего не отправляется и не меняется.
 * Модули KillEffect и HitSound вызывают {@link #tick}, а затем читают {@link #HITS} и {@link #KILLS}.
 */
public final class AttackTracker {
	/** Существа, получившие урон в этот тик после твоего удара. */
	public static final List<LivingEntity> HITS = new ArrayList<>();
	/** Существа, умершие в этот тик после твоего удара (за последние 3 секунды). */
	public static final List<LivingEntity> KILLS = new ArrayList<>();

	private static final Map<Integer, Integer> ATTACKED = new HashMap<>(); // id -> тик удара
	private static final Map<Integer, Integer> HURT = new HashMap<>();
	private static int lastTick = Integer.MIN_VALUE;
	private static boolean prevSwinging;
	private static int prevSwingTime;

	private AttackTracker() {}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) return;
		int t = mc.player.tickCount;
		if (t == lastTick) return;
		lastTick = t;
		HITS.clear();
		KILLS.clear();

		// начало взмаха: либо взмаха не было, либо счётчик взмаха сбросился
		boolean sw = mc.player.swinging;
		boolean started = sw && (!prevSwinging || mc.player.swingTime < prevSwingTime);
		prevSwinging = sw;
		prevSwingTime = mc.player.swingTime;
		if (started && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity le
			&& !AntiAcBotModule.isFake(le)) {
			ATTACKED.put(le.getId(), t);
		}

		for (Iterator<Map.Entry<Integer, Integer>> it = ATTACKED.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Integer> en = it.next();
			int id = en.getKey();
			Entity e = mc.level.getEntity(id);
			if (!(e instanceof LivingEntity le) || t - en.getValue() > 60) {
				it.remove();
				HURT.remove(id);
				continue;
			}
			Integer prev = HURT.get(id);
			int ht = le.hurtTime;
			HURT.put(id, ht);
			if (prev != null && ht > prev && ht >= 5 && t - en.getValue() <= 12) HITS.add(le);
			if (le.isDeadOrDying()) {
				KILLS.add(le);
				it.remove();
				HURT.remove(id);
			}
		}
	}
}
