package dev.darkx.visuals.core;

import dev.darkx.visuals.modules.NotificationsModule;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Мини-уведомления в углу экрана (включение/выключение модулей). */
public final class Notifications {
	public static final class Toast {
		public final String title;
		public final String text;
		public final int color;
		public final long born = System.currentTimeMillis();

		Toast(String title, String text, int color) {
			this.title = title;
			this.text = text;
			this.color = color;
		}
	}

	public static final List<Toast> TOASTS = new ArrayList<>();

	private Notifications() {}

	public static void moduleToggled(Module m) {
		if (m instanceof NotificationsModule) return;
		NotificationsModule n = ModuleManager.get(NotificationsModule.class);
		if (n == null || !n.enabled) return;
		add(m.name, m.enabled ? "Включён" : "Выключен", m.enabled ? Theme.accent() : 0xFF8A8A8A);
	}

	public static void push(String title, String text, int color) {
		add(title, text, color);
		dev.darkx.visuals.audio.ClientSounds.play(dev.darkx.visuals.audio.Sfx.TOAST);
	}

	private static void add(String title, String text, int color) {
		TOASTS.add(new Toast(title, text, color));
		while (TOASTS.size() > 5) TOASTS.remove(0);
	}

	public static void prune(long lifeMs) {
		long now = System.currentTimeMillis();
		for (Iterator<Toast> it = TOASTS.iterator(); it.hasNext(); ) {
			Toast t = it.next();
			if (now - t.born > lifeMs) {
				it.remove();
				dev.darkx.visuals.ui.UI.forget(t);
			}
		}
	}
}
