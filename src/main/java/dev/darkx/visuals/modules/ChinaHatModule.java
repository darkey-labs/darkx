package dev.darkx.visuals.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.Setting;
import dev.darkx.visuals.fx.WorldFx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Китайская шляпа над головой (конус с градиентом). */
public final class ChinaHatModule extends Module {
	public final Setting.Num radius = add(new Setting.Num("Радиус", 0.3, 1.0, 0.01, 0.62, ""));
	public final Setting.Num height = add(new Setting.Num("Высота", 0.1, 0.8, 0.01, 0.34, ""));
	public final Setting.Num opacity = add(new Setting.Num("Прозрачность", 10, 100, 5, 60, "%"));
	public final Setting.Bool outline = add(new Setting.Bool("Контур", true));
	public final Setting.Bool others = add(new Setting.Bool("На других игроках", false));
	public final Setting.Color color = add(new Setting.Color("Цвет", 0));

	public ChinaHatModule() {
		super("ChinaHat", "Китайская шляпа над головой игрока.", Category.VISUAL);
	}

	public void render(VertexConsumer vc, Matrix4f m, float pt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		List<Player> list = new ArrayList<>();
		if (!mc.options.getCameraType().isFirstPerson()) list.add(mc.player);
		if (others.value) {
			for (Player p : mc.level.players()) {
				if (p != mc.player && !p.isInvisible() && !AntiAcBotModule.isBot(p)) list.add(p);
			}
		}

		float a = opacity.f() / 100f;
		for (Player p : list) {
			Vec3 pos = p.getPosition(pt);
			double base = pos.y + p.getEyeHeight() + 0.2;
			WorldFx.cone(vc, m, pos.x, base, pos.z, radius.f(), height.f(), 48, color, a);
			if (outline.value) {
				WorldFx.ring(vc, m, pos.x, base + 0.001, pos.z, radius.f() - 0.03f, radius.f() + 0.01f, 48, color, Math.min(1f, a + 0.3f));
			}
		}
	}
}
