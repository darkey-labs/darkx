package dev.darkx.visuals.fx;

import com.mojang.blaze3d.platform.NativeImage;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.modules.GlassHandModule;
import dev.darkx.visuals.modules.GlowHandModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Общая часть GlassHand и GlowHand: одна плоская текстура руки (без деталей скина) и полная яркость.
 * Текстура одна на всех — при смене цвета её пиксели просто перезаливаются.
 */
public final class HandFx {
	private static final Identifier ID = Identifier.fromNamespaceAndPath("darkx", "hand/flat");
	private static final int FULL_BRIGHT = 0xF000F0;

	private static NativeImage img;
	private static DynamicTexture tex;
	private static int lastArgb;

	private HandFx() {}

	/** Текстура, которой подменяется скин руки; null — рука рисуется как обычно. */
	public static Identifier texture() {
		GlassHandModule glass = ModuleManager.get(GlassHandModule.class);
		if (glass == null || !glass.enabled) return null;
		GlowHandModule glow = ModuleManager.get(GlowHandModule.class);
		int rgb = (glow != null && glow.enabled ? glow.color.color(0) : glass.tint.color(0)) & 0xFFFFFF;
		int a = Math.round(glass.opacity.f() / 100f * 255f);
		int argb = (a << 24) | rgb;
		try {
			ensure(argb);
		} catch (Throwable t) {
			return null;
		}
		return ID;
	}

	/** Освещение руки и предмета: при включённом GlowHand они всегда «горят» на полной яркости. */
	public static int light(int light) {
		GlowHandModule glow = ModuleManager.get(GlowHandModule.class);
		return glow != null && glow.enabled && glow.fullBright.value ? FULL_BRIGHT : light;
	}

	private static void ensure(int argb) {
		if (tex == null) {
			img = new NativeImage(64, 64, true);
			tex = new DynamicTexture(() -> "darkx hand", img);
			Minecraft.getInstance().getTextureManager().register(ID, tex);
			lastArgb = ~argb;
		}
		if (differs(argb, lastArgb)) {
			img.fillRect(0, 0, 64, 64, argb);
			tex.upload();
			lastArgb = argb;
		}
	}

	/** Радуга меняет цвет каждый кадр — перезаливаем только при заметной разнице. */
	private static boolean differs(int a, int b) {
		return Math.abs((a >>> 24) - (b >>> 24)) > 1
			|| Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) > 2
			|| Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) > 2
			|| Math.abs((a & 255) - (b & 255)) > 2;
	}
}
