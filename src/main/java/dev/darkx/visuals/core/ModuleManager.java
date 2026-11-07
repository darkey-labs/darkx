package dev.darkx.visuals.core;

import dev.darkx.visuals.modules.*;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
	private static final List<Module> MODULES = new ArrayList<>();

	private ModuleManager() {}

	public static void init() {
		// Visual
		register(new HudModule());
		register(new JumpCirclesModule());
		register(new ChinaHatModule());
		register(new TargetEspModule());
		register(new TrailsModule());
		register(new BlockOverlayModule());
		register(new FullBrightModule());
		register(new MarkersModule());
		register(new HitColorModule());
		register(new CrosshairModule());
		register(new HitboxesModule());
		register(new ProjectilesModule());
		register(new CustomWorldModule());
		register(new ParticlesModule());
		register(new HitBubblesModule());
		register(new BetterGuiModule());
		// View
		register(new ViewModelModule());
		register(new SwingAnimationModule());
		register(new GlassHandModule());
		register(new GlowHandModule());
		register(new MoreAnimsModule());
		register(new ZoomModule());
		register(new AspectRatioModule());
		register(new NoRenderModule());
		// Misc
		register(new AntiAcBotModule());
		register(new NotificationsModule());
		register(new NickHiderModule());
		register(new RwTpaModule());
		register(new NetworkModule());
		register(new ClientSoundsModule());
		register(new CapesModule());
		register(new MenusModule());
		register(new ChatPlusModule());
		register(new TotemsModule());
		register(new OptimizerModule());
		register(new KillEffectModule());
		register(new HitSoundModule());

		// включено по умолчанию
		get(HudModule.class).enabled = true;
		get(NotificationsModule.class).enabled = true;
		get(MarkersModule.class).enabled = true;
		get(NetworkModule.class).enabled = true;
		get(ClientSoundsModule.class).enabled = true;
		get(CapesModule.class).enabled = true;
		get(MenusModule.class).enabled = true;
		get(TotemsModule.class).enabled = true;
		get(MoreAnimsModule.class).enabled = true;
	}

	private static void register(Module m) {
		MODULES.add(m);
	}

	public static List<Module> all() {
		return Collections.unmodifiableList(MODULES);
	}

	public static List<Module> byCategory(Category c) {
		List<Module> out = new ArrayList<>();
		for (Module m : MODULES) if (m.category == c) out.add(m);
		return out;
	}

	public static int enabledCount() {
		int n = 0;
		for (Module m : MODULES) if (m.enabled) n++;
		return n;
	}

	@SuppressWarnings("unchecked")
	public static <T extends Module> T get(Class<T> type) {
		for (Module m : MODULES) if (type.isInstance(m)) return (T) m;
		return null;
	}

	public static void tick(Minecraft mc) {
		for (Module m : MODULES) {
			m.onAlways(mc);
			if (m.enabled) m.onTick(mc);
		}
	}
}
