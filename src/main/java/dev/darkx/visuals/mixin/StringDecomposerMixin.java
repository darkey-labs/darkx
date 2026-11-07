package dev.darkx.visuals.mixin;

import dev.darkx.visuals.modules.NickHiderModule;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Nick Hider: весь текст игры проходит через StringDecomposer, подменяем ник на входе. */
@Mixin(StringDecomposer.class)
public class StringDecomposerMixin {
	@ModifyVariable(
		method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
		at = @At("HEAD"), argsOnly = true, require = 0)
	private static String darkx$nickFormatted(String text) {
		return NickHiderModule.replace(text);
	}

	@ModifyVariable(
		method = "iterate(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
		at = @At("HEAD"), argsOnly = true, require = 0)
	private static String darkx$nickPlain(String text) {
		return NickHiderModule.replace(text);
	}
}
