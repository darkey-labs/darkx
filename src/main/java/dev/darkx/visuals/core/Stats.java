package dev.darkx.visuals.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

/** Небольшая статистика для худов: скорость, CPS, сессия, пинг. */
public final class Stats {
	public static final long SESSION_START = System.currentTimeMillis();

	private static double lastX, lastZ;
	private static boolean hasLast;
	public static float speed; // блоков/сек

	private static final ArrayDeque<Long> LMB = new ArrayDeque<>();
	private static final ArrayDeque<Long> RMB = new ArrayDeque<>();
	private static boolean lmbDown, rmbDown;

	private Stats() {}

	/** Раз в клиентский тик. */
	public static void tick(Minecraft mc) {
		Player p = mc.player;
		if (p == null) {
			hasLast = false;
			speed = 0;
			return;
		}
		if (hasLast) {
			double dx = p.getX() - lastX, dz = p.getZ() - lastZ;
			float v = (float) (Math.sqrt(dx * dx + dz * dz) * 20.0);
			speed += (v - speed) * 0.35f;
		}
		lastX = p.getX();
		lastZ = p.getZ();
		hasLast = true;
	}

	/** Раз в кадр: опрос кнопок мыши. */
	public static void pollClicks(Minecraft mc) {
		long w = mc.getWindow().handle();
		boolean free = mc.screen == null;
		boolean l = free && GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean r = free && GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
		long now = System.currentTimeMillis();
		if (l && !lmbDown) LMB.addLast(now);
		if (r && !rmbDown) RMB.addLast(now);
		lmbDown = l;
		rmbDown = r;
		while (!LMB.isEmpty() && now - LMB.peekFirst() > 1000) LMB.pollFirst();
		while (!RMB.isEmpty() && now - RMB.peekFirst() > 1000) RMB.pollFirst();
	}

	public static int cpsLeft() { return LMB.size(); }

	public static int cpsRight() { return RMB.size(); }

	public static boolean lmbDown() { return lmbDown; }

	public static boolean rmbDown() { return rmbDown; }

	public static int ping(Minecraft mc) {
		if (mc.player == null || mc.getConnection() == null) return 0;
		PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return info == null ? 0 : info.getLatency();
	}

	public static String sessionTime() {
		long s = (System.currentTimeMillis() - SESSION_START) / 1000L;
		return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
	}
}
