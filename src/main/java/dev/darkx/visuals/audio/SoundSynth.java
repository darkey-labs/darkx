package dev.darkx.visuals.audio;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Процедурный синтез звуков интерфейса: мягкие бипы (синус + гармоники + лёгкий хорус),
 * сглаживание и стереореверберация (комбы + all-pass по схеме Шрёдера/Freeverb).
 * Чистая Java без зависимостей от Minecraft.
 */
public final class SoundSynth {
	public static final int RATE = 44100;

	/** Готовый звук: стерео, float -1..1. */
	public record Buf(float[] l, float[] r) {
		public int frames() { return l.length; }
	}

	/** Набор гармоник: {множитель частоты, громкость, множитель затухания}. */
	private record Timbre(String name, double pitch, double decay, double[][] partials) {}

	private static final Timbre[] TIMBRES = {
		// «Кристалл»: яркий синус + немного колокольных обертонов
		new Timbre("Кристалл", 1.0, 1.0, new double[][] {{1, 1.0, 1.0}, {2, 0.20, 0.7}, {3, 0.07, 0.5}, {2.76, 0.10, 0.45}}),
		// «Мягкий»: почти чистый синус, ниже и короче
		new Timbre("Мягкий", 0.84, 0.85, new double[][] {{1, 1.0, 1.0}, {2, 0.07, 0.6}}),
		// «Дерево»: маримба — быстрые верхние гармоники
		new Timbre("Дерево", 0.5, 0.8, new double[][] {{1, 1.0, 1.0}, {4, 0.32, 0.30}, {9.2, 0.10, 0.12}}),
	};

	public static String[] timbreNames() {
		String[] n = new String[TIMBRES.length];
		for (int i = 0; i < n.length; i++) n[i] = TIMBRES[i].name;
		return n;
	}

	/** Нота: старт (с), частота от/до (Гц), громкость, постоянная затухания (с), панорама -1..1. */
	private record Note(double t, double f0, double f1, double amp, double tau, double pan) {}

	// частоты (пентатоника D-мажор: D E F# A B)
	private static final double D5 = 587.33, E5 = 659.25, FS5 = 739.99, A5 = 880.00, B5 = 987.77;
	private static final double D6 = 1174.66, E6 = 1318.51, FS6 = 1479.98, A6 = 1760.00, B6 = 1975.53;

	private static List<Note> score(Sfx s) {
		List<Note> n = new ArrayList<>();
		switch (s) {
			case OPEN -> {
				n.add(new Note(0.000, 196, 98, 0.30, 0.07, 0));          // мягкий «бум» снизу
				n.add(new Note(0.012, A5, A5, 0.40, 0.12, -0.20));
				n.add(new Note(0.070, D6, D6, 0.38, 0.13, 0.00));
				n.add(new Note(0.130, FS6, FS6, 0.34, 0.16, 0.20));
				n.add(new Note(0.190, A6, A6, 0.22, 0.20, 0.10));
			}
			case CLOSE -> {
				n.add(new Note(0.000, FS6, FS6, 0.30, 0.07, 0.20));
				n.add(new Note(0.045, D6, D6, 0.30, 0.08, 0.00));
				n.add(new Note(0.090, A5, A5, 0.30, 0.12, -0.20));
				n.add(new Note(0.090, 147, 98, 0.26, 0.07, 0));
			}
			case ON -> {
				n.add(new Note(0.000, D6, D6, 0.40, 0.07, -0.15));
				n.add(new Note(0.065, A6, A6, 0.38, 0.12, 0.15));
			}
			case OFF -> {
				n.add(new Note(0.000, A5, A5, 0.36, 0.07, 0.15));
				n.add(new Note(0.065, D5, D5, 0.36, 0.11, -0.15));
			}
			case TAB -> n.add(new Note(0.000, E6, E6, 0.34, 0.040, 0));
			case CLICK -> {
				n.add(new Note(0.000, D6 * 1.06, D6 * 0.96, 0.36, 0.030, 0));
				n.add(new Note(0.000, 300, 180, 0.12, 0.020, 0));
			}
			case POPUP_OPEN -> {
				n.add(new Note(0.000, 520, 1040, 0.30, 0.10, -0.1));
				n.add(new Note(0.085, A6, A6, 0.20, 0.10, 0.2));
			}
			case POPUP_CLOSE -> {
				n.add(new Note(0.000, 1040, 520, 0.26, 0.08, 0.1));
			}
			case SELECT -> {
				n.add(new Note(0.000, FS6, FS6, 0.30, 0.040, -0.1));
				n.add(new Note(0.032, A6, A6, 0.28, 0.060, 0.1));
			}
			case SLIDER -> n.add(new Note(0.000, D6, D6, 0.22, 0.018, 0));
			case TOAST -> {
				n.add(new Note(0.000, A5, A5, 0.36, 0.20, -0.1));
				n.add(new Note(0.090, D6, D6, 0.36, 0.26, 0.1));
			}
			case FRIEND -> {
				n.add(new Note(0.000, D6, D6, 0.34, 0.16, -0.2));
				n.add(new Note(0.080, FS6, FS6, 0.34, 0.18, 0.0));
				n.add(new Note(0.160, B6, B6, 0.34, 0.30, 0.2));
			}
		}
		return n;
	}

	public static Map<Sfx, Buf> renderAll(int timbre, float reverb01) {
		Map<Sfx, Buf> out = new EnumMap<>(Sfx.class);
		for (Sfx s : Sfx.values()) out.put(s, render(s, timbre, reverb01));
		return out;
	}

	public static Buf render(Sfx sfx, int timbreIndex, float reverb01) {
		Timbre tb = TIMBRES[Math.max(0, Math.min(TIMBRES.length - 1, timbreIndex))];
		reverb01 = Math.max(0f, Math.min(1f, reverb01));
		List<Note> notes = score(sfx);

		// длина «сухого» звука
		double end = 0;
		for (Note n : notes) end = Math.max(end, n.t + n.tau * tb.decay * 5.0);
		double tail = sfx == Sfx.SLIDER ? 0.12 + 0.25 * reverb01 : 0.25 + 1.1 * reverb01;
		int frames = (int) ((end + tail) * RATE) + 1;
		float[] dl = new float[frames], dr = new float[frames];

		for (Note n : notes) {
			int start = (int) (n.t * RATE);
			double tau = n.tau * tb.decay;
			int len = Math.min(frames - start, (int) (tau * 5.0 * RATE));
			double dur = Math.max(1e-3, tau * 4.0);
			double lg = Math.sqrt(0.5 * (1 - n.pan)), rg = Math.sqrt(0.5 * (1 + n.pan)); // equal-power
			for (double[] p : tb.partials) {
				double ratio = p[0], pa = p[1] * n.amp, ptau = tau * p[2];
				double ph1 = 0, ph2 = 0;
				for (int i = 0; i < len; i++) {
					double t = i / (double) RATE;
					double glide = Math.min(1.0, t / Math.max(0.02, dur * 0.6));
					glide = 1 - Math.pow(1 - glide, 2);
					double f = (n.f0 + (n.f1 - n.f0) * glide) * tb.pitch * ratio;
					ph1 += 2 * Math.PI * f * 1.0015 / RATE;   // две слегка расстроенные копии — «хорус»
					ph2 += 2 * Math.PI * f * 0.9985 / RATE;
					double att = Math.min(1.0, t / 0.003);
					att = 0.5 - 0.5 * Math.cos(Math.PI * att);
					double env = att * Math.exp(-t / ptau);
					double v = 0.5 * (Math.sin(ph1) + Math.sin(ph2)) * env * pa;
					dl[start + i] += (float) (v * lg * 1.4142);
					dr[start + i] += (float) (v * rg * 1.4142);
				}
			}
		}

		lowpass(dl, 9500);
		lowpass(dr, 9500);

		float[][] wet = reverb(dl, dr, reverb01);
		float mix = 0.55f * reverb01;
		float[] l = new float[frames], r = new float[frames];
		float peak = 0f;
		for (int i = 0; i < frames; i++) {
			l[i] = dl[i] * (1f - 0.25f * reverb01) + wet[0][i] * mix;
			r[i] = dr[i] * (1f - 0.25f * reverb01) + wet[1][i] * mix;
			peak = Math.max(peak, Math.max(Math.abs(l[i]), Math.abs(r[i])));
		}
		float norm = peak > 0.85f ? 0.85f / peak : 1f; // только вниз, чтобы не ломать баланс звуков
		int fade = Math.min(frames, (int) (0.06 * RATE));
		for (int i = 0; i < frames; i++) {
			float g = norm;
			int left = frames - 1 - i;
			if (left < fade) {
				float u = left / (float) fade;
				g *= u * u * (3 - 2 * u);
			}
			l[i] = softClip(l[i] * g);
			r[i] = softClip(r[i] * g);
		}
		return new Buf(l, r);
	}

	private static float softClip(float x) {
		return (float) Math.tanh(x);
	}

	private static void lowpass(float[] x, double cutoff) {
		double a = 1 - Math.exp(-2 * Math.PI * cutoff / RATE);
		double y = 0;
		for (int i = 0; i < x.length; i++) {
			y += a * (x[i] - y);
			x[i] = (float) y;
		}
	}

	// ============================================================== реверберация

	private static final int[] COMB = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
	private static final int[] ALLP = {556, 441, 341, 225};
	private static final int SPREAD = 23;

	/** Моно-вход (L+R), стерео-выход; вход слегка предварительно задерживается. */
	private static float[][] reverb(float[] inL, float[] inR, float amount) {
		int n = inL.length;
		float[][] out = new float[2][n];
		if (amount <= 0.001f) return out;
		float feedback = 0.70f + 0.13f * amount;
		float damp = 0.32f;
		int pre = (int) (0.012 * RATE);
		for (int ch = 0; ch < 2; ch++) {
			float[] in = ch == 0 ? inL : inR;
			float[] o = out[ch];
			float[][] bufs = new float[COMB.length][];
			int[] pos = new int[COMB.length];
			float[] store = new float[COMB.length];
			for (int c = 0; c < COMB.length; c++) bufs[c] = new float[COMB[c] + (ch == 1 ? SPREAD : 0)];
			float[][] abuf = new float[ALLP.length][];
			int[] apos = new int[ALLP.length];
			for (int a = 0; a < ALLP.length; a++) abuf[a] = new float[ALLP[a] + (ch == 1 ? SPREAD : 0)];
			for (int i = 0; i < n; i++) {
				float x = (i >= pre ? 0.5f * (inL[i - pre] + inR[i - pre]) : 0f) * 0.2f;
				float acc = 0f;
				for (int c = 0; c < COMB.length; c++) {
					float[] b = bufs[c];
					float y = b[pos[c]];
					store[c] = y * (1 - damp) + store[c] * damp;
					b[pos[c]] = x + store[c] * feedback;
					if (++pos[c] >= b.length) pos[c] = 0;
					acc += y;
				}
				for (int a = 0; a < ALLP.length; a++) {
					float[] b = abuf[a];
					float bo = b[apos[a]];
					float y = -acc + bo;
					b[apos[a]] = acc + bo * 0.5f;
					if (++apos[a] >= b.length) apos[a] = 0;
					acc = y;
				}
				o[i] = acc;
			}
		}
		return out;
	}
}
