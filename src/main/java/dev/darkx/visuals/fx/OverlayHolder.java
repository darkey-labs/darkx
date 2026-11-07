package dev.darkx.visuals.fx;

/**
 * Связка между модулем HitColor и миксином OverlayTexture (он лежит в опциональном конфиге).
 * Если миксин не применился — instance остаётся null, и модуль просто ничего не делает.
 */
public final class OverlayHolder {
	public interface Applier {
		/** Заливает красную «вспышку урона» цветом argb. */
		void darkx$apply(int argb);
	}

	public static volatile Applier instance;

	private OverlayHolder() {}

	public static boolean ready() {
		return instance != null;
	}

	public static void apply(int argb) {
		Applier a = instance;
		if (a != null) a.darkx$apply(argb);
	}
}
