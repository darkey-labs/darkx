package dev.darkx.visuals.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.darkx.visuals.core.Category;
import dev.darkx.visuals.core.Module;
import dev.darkx.visuals.core.ModuleManager;
import dev.darkx.visuals.core.Setting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

/** Положение, поворот и размер предметов в руках от первого лица. */
public final class ViewModelModule extends Module {
	/** Строки: X, Y, Z, масштаб, поворот X, Y, Z. Порядок совпадает с названиями пресетов (после «Свой»). */
	private static final String[] PRESET_NAMES = {"Свой", "Ванильный", "Компакт", "Низкий", "Далеко", "Крупный", "Наклон", "Минимал"};
	private static final double[][] PRESETS = {
		{0, 0, 0, 1, 0, 0, 0},
		{0.10, -0.10, -0.10, 0.85, 0, 0, 0},
		{0.05, -0.25, -0.15, 0.90, 0, 0, 0},
		{0.15, -0.05, -0.45, 0.80, 0, 8, 0},
		{-0.05, 0, 0.10, 1.25, 0, 0, 0},
		{0.12, -0.12, -0.20, 0.90, 10, -12, 8},
		{0.20, -0.30, -0.30, 0.60, 0, 0, 0}
	};

	public final Setting.Mode preset = add(new Setting.Mode("Пресет", 0, PRESET_NAMES));
	public final Setting.Mode hands = add(new Setting.Mode("Руки", 0, "Обе", "Основная", "Вторая"));
	public final Setting.Num x = add(new Setting.Num("Позиция X", -1, 1, 0.01, 0, ""));
	public final Setting.Num y = add(new Setting.Num("Позиция Y", -1, 1, 0.01, 0, ""));
	public final Setting.Num z = add(new Setting.Num("Позиция Z", -1.5, 1, 0.01, 0, ""));
	public final Setting.Num scale = add(new Setting.Num("Масштаб", 0.3, 1.8, 0.05, 1, "x"));
	public final Setting.Num rotX = add(new Setting.Num("Поворот X", -180, 180, 1, 0, "°"));
	public final Setting.Num rotY = add(new Setting.Num("Поворот Y", -180, 180, 1, 0, "°"));
	public final Setting.Num rotZ = add(new Setting.Num("Поворот Z", -180, 180, 1, 0, "°"));

	private int applied = -1;
	private double[] snap;

	public ViewModelModule() {
		super("ViewModel", "Двигай, крути и масштабируй предметы в руках. Есть готовые пресеты.", Category.VIEW);
	}

	private Setting.Num[] nums() {
		return new Setting.Num[] {x, y, z, scale, rotX, rotY, rotZ};
	}

	private double[] values() {
		Setting.Num[] n = nums();
		double[] v = new double[n.length];
		for (int i = 0; i < n.length; i++) v[i] = n[i].value;
		return v;
	}

	/** Применяет выбранный пресет; если потом поменять любое значение руками, пресет превращается в «Свой». */
	@Override public void onAlways(net.minecraft.client.Minecraft mc) {
		if (applied < 0) {
			applied = preset.index;
			snap = values();
			return;
		}
		if (preset.index != applied) {
			applied = preset.index;
			if (applied > 0) {
				Setting.Num[] n = nums();
				for (int i = 0; i < n.length; i++) n[i].set(PRESETS[applied - 1][i]);
			}
			snap = values();
			dev.darkx.visuals.core.Config.dirty = true;
			return;
		}
		if (applied != 0 && !java.util.Arrays.equals(snap, values())) {
			preset.index = 0;
			applied = 0;
			dev.darkx.visuals.core.Config.dirty = true;
		}
	}

	/** Вызывается из ItemInHandRendererMixin перед отрисовкой руки. */
	public static void apply(PoseStack ps, AbstractClientPlayer player, InteractionHand hand) {
		ViewModelModule m = ModuleManager.get(ViewModelModule.class);
		if (m == null || !m.enabled) return;
		boolean main = hand == InteractionHand.MAIN_HAND;
		if (m.hands.is("Основная") && !main) return;
		if (m.hands.is("Вторая") && main) return;

		boolean right = main == (player.getMainArm() == HumanoidArm.RIGHT);
		float side = right ? 1f : -1f;
		ps.translate(m.x.f() * side, m.y.f(), m.z.f());
		if (m.rotX.value != 0) ps.mulPose(Axis.XP.rotationDegrees(m.rotX.f()));
		if (m.rotY.value != 0) ps.mulPose(Axis.YP.rotationDegrees(m.rotY.f() * side));
		if (m.rotZ.value != 0) ps.mulPose(Axis.ZP.rotationDegrees(m.rotZ.f() * side));
		float s = m.scale.f();
		if (s != 1f) ps.scale(s, s, s);
	}
}
