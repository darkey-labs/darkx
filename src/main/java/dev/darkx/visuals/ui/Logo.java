package dev.darkx.visuals.ui;

import dev.darkx.visuals.core.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Логотип DarkX: два слоя. Основа (X с шариками) всегда грязно-белая,
 * акцентная планка перекрашивается в цвет текущей темы клиента.
 */
public final class Logo {
	private static final Identifier BASE = Identifier.fromNamespaceAndPath("darkx", "textures/gui/logo_base.png");
	private static final Identifier ACCENT = Identifier.fromNamespaceAndPath("darkx", "textures/gui/logo_accent.png");
	private static final int TEX = 96;
	private static final int INK = 0xFFE4E4E2;

	private Logo() {}

	public static void draw(GuiGraphics g, int x, int y, int size) {
		draw(g, x, y, size, Theme.accent());
	}

	/** accent — цвет планки; передай INK-цвет, чтобы получить монохромную версию. */
	public static void draw(GuiGraphics g, int x, int y, int size, int accent) {
		g.blit(RenderPipelines.GUI_TEXTURED, BASE, x, y, 0f, 0f, size, size, TEX, TEX, TEX, TEX, UI.c(INK));
		g.blit(RenderPipelines.GUI_TEXTURED, ACCENT, x, y, 0f, 0f, size, size, TEX, TEX, TEX, TEX, UI.c(accent));
	}
}
