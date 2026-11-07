package dev.darkx.visuals.audio;

import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.modules.ClientSoundsModule;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Звуки клиента. Синтезируются при запуске (см. {@link SoundSynth}) и играют через собственный
 * микшер на javax.sound — поэтому не зависят от звукового движка Minecraft и не требуют ресурсов.
 * Любая ошибка звука тихо отключает его: клиент без звука лучше, чем крэш.
 */
public final class ClientSounds {
	private static final int CHUNK = 441;            // ~10 мс
	private static final int MAX_VOICES = 24;

	private static final class Voice {
		final SoundSynth.Buf buf;
		final float gain, rate;
		double pos;

		Voice(SoundSynth.Buf buf, float gain, float rate) {
			this.buf = buf;
			this.gain = gain;
			this.rate = rate;
		}
	}

	private static final Object LOCK = new Object();
	private static final List<Voice> VOICES = new ArrayList<>();
	private static final Map<Sfx, Long> LAST = new EnumMap<>(Sfx.class);

	private static volatile Map<Sfx, SoundSynth.Buf> bank = Map.of();
	private static volatile boolean failed;
	private static int builtTimbre = -1;
	private static float builtReverb = -1f;
	private static volatile boolean building;

	/** Счётчик сыгранных звуков: UI по нему понимает, что действие уже озвучено. */
	private static volatile int counter;

	private ClientSounds() {}

	// ================================================================== запуск

	public static void init() {
		ClientSoundsModule m = module();
		rebuild(m == null ? 0 : m.timbre.index, m == null ? 0.55f : m.reverb.f() / 100f);
		try {
			Thread t = new Thread(ClientSounds::mixLoop, "DarkX-Audio");
			t.setDaemon(true);
			t.start();
		} catch (Throwable e) {
			failed = true;
		}
	}

	private static ClientSoundsModule module() {
		return ModuleManager.get(ClientSoundsModule.class);
	}

	/** Пересобирает звуки в фоне, если поменялись тембр или реверберация. */
	public static void rebuild(int timbre, float reverb01) {
		if (building || failed) return;
		if (timbre == builtTimbre && Math.abs(reverb01 - builtReverb) < 0.001f) return;
		building = true;
		Thread t = new Thread(() -> {
			try {
				bank = SoundSynth.renderAll(timbre, reverb01);
				builtTimbre = timbre;
				builtReverb = reverb01;
			} catch (Throwable e) {
				failed = true;
			} finally {
				building = false;
			}
		}, "DarkX-SoundSynth");
		t.setDaemon(true);
		t.start();
	}

	/** Вызывается каждый тик: подхватывает смену настроек звука. */
	public static void tick() {
		ClientSoundsModule m = module();
		if (m != null && m.enabled) rebuild(m.timbre.index, m.reverb.f() / 100f);
	}

	// ================================================================= воспроизведение

	public static int counter() {
		return counter;
	}

	public static void play(Sfx sfx) {
		play(sfx, 1f);
	}

	/** pitch: 1 = как есть, 2 = октавой выше. */
	public static void play(Sfx sfx, float pitch) {
		if (failed) return;
		ClientSoundsModule m = module();
		if (m == null || !m.enabled || !m.allows(sfx)) return;
		SoundSynth.Buf buf = bank.get(sfx);
		if (buf == null) return;

		long now = System.nanoTime();
		long minGap = (sfx == Sfx.SLIDER ? 38_000_000L : 22_000_000L);
		synchronized (LOCK) {
			Long last = LAST.get(sfx);
			if (last != null && now - last < minGap) return;
			LAST.put(sfx, now);
			if (VOICES.size() >= MAX_VOICES) VOICES.remove(0);
			float vol = m.volume.f() / 100f;
			VOICES.add(new Voice(buf, vol * vol * 1.4f, Math.max(0.5f, Math.min(2f, pitch)))); // квадрат — ближе к ощущаемой громкости
			counter++;
			LOCK.notifyAll();
		}
	}

	/** Включение / выключение функции. */
	public static void toggled(boolean on) {
		play(on ? Sfx.ON : Sfx.OFF);
	}

	// ====================================================================== микшер

	private static void mixLoop() {
		LineOut line;
		try {
			line = new LineOut(SoundSynth.RATE, CHUNK * 4 * 3);
		} catch (Throwable e) {
			failed = true;
			return;
		}
		boolean started = false;
		float[] mixL = new float[CHUNK], mixR = new float[CHUNK];
		byte[] out = new byte[CHUNK * 4];
		try {
			while (true) {
				List<Voice> active = null;
				synchronized (LOCK) {
					if (!VOICES.isEmpty()) active = new ArrayList<>(VOICES);
					else if (!started) LOCK.wait();
				}
				if (active == null) {
					if (started) { // все голоса доиграли: даём допеть буферу и останавливаем линию
						line.drainAndStop();
						started = false;
					}
					continue;
				}
				if (!started) {
					line.start();
					started = true;
				}
				java.util.Arrays.fill(mixL, 0f);
				java.util.Arrays.fill(mixR, 0f);
				for (Voice v : active) {
					float[] l = v.buf.l(), r = v.buf.r();
					int n = l.length;
					for (int i = 0; i < CHUNK; i++) {
						int p = (int) v.pos;
						if (p >= n - 1) { v.pos = n; break; }
						float f = (float) (v.pos - p);
						mixL[i] += (l[p] + (l[p + 1] - l[p]) * f) * v.gain;
						mixR[i] += (r[p] + (r[p + 1] - r[p]) * f) * v.gain;
						v.pos += v.rate;
					}
				}
				synchronized (LOCK) {
					VOICES.removeIf(v -> v.pos >= v.buf.frames() - 1);
				}
				for (int i = 0; i < CHUNK; i++) {
					putSample(out, i * 4, mixL[i]);
					putSample(out, i * 4 + 2, mixR[i]);
				}
				line.write(out);
			}
		} catch (Throwable e) {
			failed = true;
			try { line.close(); } catch (Throwable ignored) {}
		}
	}

	private static void putSample(byte[] b, int at, float x) {
		x = (float) Math.tanh(x);
		int s = Math.round(x * 32767f);
		b[at] = (byte) s;
		b[at + 1] = (byte) (s >> 8);
	}
}
