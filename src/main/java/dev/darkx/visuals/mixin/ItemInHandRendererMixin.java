package dev.darkx.visuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.darkx.visuals.fx.HandFx;
import dev.darkx.visuals.modules.SwingAnimationModule;
import dev.darkx.visuals.modules.ViewModelModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ViewModel и SwingAnimation: двигаем матрицу руки до отрисовки, откатываем после. */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
	/** GlowHand: рука и предмет рисуются на полной яркости. */
	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true)
	private int darkx$light(int light) {
		return HandFx.light(light);
	}

	@Inject(method = "renderArmWithItem", at = @At("HEAD"))
	private void darkx$before(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
							  float swingProgress, ItemStack stack, float equipProgress, PoseStack poseStack,
							  SubmitNodeCollector collector, int light, CallbackInfo ci) {
		poseStack.pushPose();
		ViewModelModule.apply(poseStack, player, hand);
		SwingAnimationModule.apply(poseStack, hand, swingProgress);
	}

	@Inject(method = "renderArmWithItem", at = @At("RETURN"))
	private void darkx$after(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
							 float swingProgress, ItemStack stack, float equipProgress, PoseStack poseStack,
							 SubmitNodeCollector collector, int light, CallbackInfo ci) {
		poseStack.popPose();
	}
}
