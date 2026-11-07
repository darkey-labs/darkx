package dev.darkx.visuals.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Заряд батареи. Сначала пробуем OSHI (он идёт вместе с Minecraft, вызываем через reflection),
 * затем /sys/class/power_supply (Linux, Android / PojavLauncher). Опрос — раз в 30 секунд
 * в отдельном потоке. percent() == -1 значит, что батареи нет (обычный ПК).
 */
public final class Battery {
	private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

	private static volatile int percent = -1;
	private static volatile boolean charging;
	private static volatile boolean busy;
	private static long lastPoll;

	private Battery() {}

	public static int percent() {
		return percent;
	}

	public static boolean charging() {
		return charging;
	}

	/** Можно вызывать каждый кадр: сам ограничивает частоту. */
	public static void poll() {
		long now = System.currentTimeMillis();
		if (busy || (lastPoll != 0 && now - lastPoll < 30_000L)) return;
		lastPoll = now;
		busy = true;
		Thread t = new Thread(Battery::read, "DarkX-Battery");
		t.setDaemon(true);
		t.start();
	}

	private static void read() {
		try {
			boolean win = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
			int[] r;
			if (win) {
				// Windows: тот же источник, что и у значка батареи в системе
				// OSHI на Windows выдавал неверные 100%, поэтому его тут не используем
				r = fromWindows();
				String src = "jna";
				if (r == null) { r = fromPowerShell(); src = "powershell"; }
				if (r == null) { r = fromWmic(); src = "wmic"; }
				LOG.info("[DarkX] battery(windows): {}", r == null ? "не определена" : r[0] + "% via " + src + (r[1] == 1 ? ", charging" : ""));
			} else {
				r = fromSysfs();
				if (r == null) r = fromOshi();
				LOG.info("[DarkX] battery: {}", r == null ? "не определена" : r[0] + "%" + (r[1] == 1 ? ", charging" : ""));
			}
			if (r == null) {
				percent = -1;
			} else {
				percent = Math.max(0, Math.min(100, r[0]));
				charging = r[1] == 1;
			}
		} catch (Throwable t) {
			percent = -1;
		} finally {
			busy = false;
		}
	}

	/** GetSystemPowerStatus через JNA (идёт вместе с Minecraft). */
	private static int[] fromWindows() {
		try {
			Class<?> k32 = Class.forName("com.sun.jna.platform.win32.Kernel32");
			Object inst = k32.getField("INSTANCE").get(null);
			Class<?> st = Class.forName("com.sun.jna.platform.win32.WinBase$SYSTEM_POWER_STATUS");
			Object s = st.getConstructor().newInstance();
			boolean ok = (boolean) k32.getMethod("GetSystemPowerStatus", st).invoke(inst, s);
			if (!ok) return null;
			int pct = st.getField("BatteryLifePercent").getByte(s) & 0xFF;
			int flag = st.getField("BatteryFlag").getByte(s) & 0xFF;
			LOG.info("[DarkX] battery jna raw: percent={}, flag={}", pct, flag);
			if (pct > 100 || flag == 128) return null; // 255 = неизвестно, 128 = батареи нет
			return new int[]{pct, (flag & 8) != 0 ? 1 : 0};
		} catch (Throwable t) {
			LOG.info("[DarkX] battery jna failed: {}", t.toString());
			return null;
		}
	}

	/** Запасной вариант для Windows: WMI через PowerShell. */
	private static int[] fromPowerShell() {
		try {
			Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
				"$b = Get-CimInstance Win32_Battery | Select-Object -First 1; if ($b) { Write-Output ($b.EstimatedChargeRemaining.ToString() + ',' + $b.BatteryStatus.ToString()) }")
				.redirectErrorStream(true).start();
			if (!p.waitFor(6, java.util.concurrent.TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return null;
			}
			String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			String[] parts = out.split(",");
			if (parts.length < 1 || parts[0].isEmpty()) return null;
			int pct = Integer.parseInt(parts[0].trim());
			int status = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
			return new int[]{pct, status >= 6 && status <= 9 ? 1 : 0};
		} catch (Throwable ignored) {
			return null;
		}
	}

	/** Ещё один запасной вариант: wmic (есть в Windows 10 и ранних сборках 11). */
	private static int[] fromWmic() {
		try {
			Process p = new ProcessBuilder("cmd", "/c", "wmic path Win32_Battery get EstimatedChargeRemaining,BatteryStatus /format:csv")
				.redirectErrorStream(true).start();
			if (!p.waitFor(6, java.util.concurrent.TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return null;
			}
			String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			for (String line : out.split("\\r?\\n")) {
				String[] c = line.trim().split(",");
				// Node,BatteryStatus,EstimatedChargeRemaining
				if (c.length >= 3 && c[1].trim().matches("\\d+") && c[2].trim().matches("\\d+")) {
					int status = Integer.parseInt(c[1].trim());
					return new int[]{Integer.parseInt(c[2].trim()), status >= 6 && status <= 9 ? 1 : 0};
				}
			}
		} catch (Throwable ignored) {
			// wmic отсутствует
		}
		return null;
	}

	/** @return {процент, заряжается} или null */
	private static int[] fromOshi() {
		try {
			Class<?> sysInfo = Class.forName("oshi.SystemInfo");
			Object info = sysInfo.getConstructor().newInstance();
			Object hal = sysInfo.getMethod("getHardware").invoke(info);
			Class<?> halType = Class.forName("oshi.hardware.HardwareAbstractionLayer");
			Class<?> psType = Class.forName("oshi.hardware.PowerSource");
			Object list = halType.getMethod("getPowerSources").invoke(hal);
			if (!(list instanceof List<?> sources) || sources.isEmpty()) return null;
			for (Object ps : sources) {
				double cap = (double) psType.getMethod("getRemainingCapacityPercent").invoke(ps);
				if (cap < 0 || cap > 1.0001) continue;
				boolean ch = (boolean) psType.getMethod("isCharging").invoke(ps);
				return new int[]{(int) Math.round(cap * 100.0), ch ? 1 : 0};
			}
		} catch (Throwable ignored) {
			// OSHI недоступен — идём дальше
		}
		return null;
	}

	private static int[] fromSysfs() {
		try {
			File root = new File("/sys/class/power_supply");
			File[] dirs = root.listFiles();
			if (dirs == null) return null;
			for (File d : dirs) {
				String type = read(new File(d, "type"));
				if (type == null || !type.equalsIgnoreCase("Battery")) continue;
				String cap = read(new File(d, "capacity"));
				if (cap == null) continue;
				int pct = Integer.parseInt(cap.trim());
				String status = read(new File(d, "status"));
				boolean ch = status != null && status.trim().equalsIgnoreCase("Charging");
				return new int[]{pct, ch ? 1 : 0};
			}
		} catch (Throwable ignored) {
			// нет доступа — значит нет батареи
		}
		return null;
	}

	private static String read(File f) {
		try {
			if (!f.isFile()) return null;
			return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
		} catch (Exception e) {
			return null;
		}
	}
}
