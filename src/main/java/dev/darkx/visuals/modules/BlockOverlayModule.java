package dev.darkx.visuals.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.fx.WorldFx;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

/** Цветная заливка блока, на который смотришь. */
public final class BlockOverlayModule extends Module {
	public final Setting.Num opacity = add(new Setting.Num("Прозрачность", 5, 80, 5, 30, "%"));
	public final Setting.Bool pulse = add(new Setting.Bool("Пульсация", true));
	public final Setting.Color color = add(new Setting.Color("Цвет", 0));

	public BlockOverlayModule() {
		super("BlockOverlay", "Подсвечивает блок под прицелом.", Category.VISUAL);
	}

	public void render(VertexConsumer vc, Matrix4f m) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
		BlockPos pos = hit.getBlockPos();
		BlockState state = mc.level.getBlockState(pos);
		VoxelShape shape = state.getShape(mc.level, pos);
		if (shape.isEmpty()) return;

		AABB box = shape.bounds().move(pos).inflate(0.003);
		float a = opacity.f() / 100f;
		if (pulse.value) a *= 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 300.0);
		int c = WorldFx.argb(color.color(0f), a);
		WorldFx.box(vc, m, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, c);
	}
}
