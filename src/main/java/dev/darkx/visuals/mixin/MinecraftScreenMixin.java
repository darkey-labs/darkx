package dev.darkx.visuals.mixin;

import dev.darkx.visuals.modules.MenusModule;
import dev.darkx.visuals.ui.DarkTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Свои меню: вместо ванильного главного меню открывается DarkTitleScreen. Опциональный конфиг. */
@Mixin(Minecraft.class)
public abstract class MinecraftScreenMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
	private Screen darkx$title(Screen screen) {
		if (!MenusModule.titleOn()) return screen;
		if (screen instanceof TitleScreen) return new DarkTitleScreen();
		// setScreen(null) без мира ваниль сама превращает в новый TitleScreen — уже после нашего HEAD,
		// поэтому на старте и после «Отмены» в создании мира возвращалось ванильное меню
		if (screen == null && ((Minecraft) (Object) this).level == null) return new DarkTitleScreen();
		return screen;
	}
}
