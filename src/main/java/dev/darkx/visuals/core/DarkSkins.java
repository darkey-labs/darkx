package dev.darkx.visuals.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.darkx.visuals.modules.NetworkModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Скины DarkX. Лаунчер публикует PNG в базу (darkx/skins/&lt;ник&gt;), здесь он скачивается, превращается в
 * текстуру и подставляется вместо обычного скина: себе всегда, остальным — если они на этом сервере с DarkX.
 * Подмена идёт через миксины на AbstractClientPlayer.getSkin и PlayerInfo.getSkin.
 */
public final class DarkSkins {
	private static final long OWN_MS = 20_000L, OTHER_MS = 120_000L, NONE_MS = 300_000L, ERROR_MS = 30_000L;
	private static final int MAX_BYTES = 100_000;
	private static final Pattern NICK = Pattern.compile("^[a-z0-9_]{3,16}$");

	private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "darkx-skins");
		t.setDaemon(true);
		return t;
	});
	/** Старые текстуры освобождаем с задержкой, чтобы кадр, который их ещё рисует, не словил пустоту. */
	private static final List<Object[]> GRAVE = new ArrayList<>();

	private static final class Entry {
		volatile boolean busy;
		volatile long nextCheck;
		volatile long at = -1;
		volatile ClientAsset.ResourceTexture body;
		volatile PlayerModelType model = PlayerModelType.WIDE;
		Identifier tex;
		PlayerSkin lastBase, lastOut;
	}

	private DarkSkins() {}

	/** Подменяет скин, если у игрока есть скин DarkX. Вызывается из рендера, поэтому всё тяжёлое — в фоне. */
	public static PlayerSkin apply(UUID id, String name, PlayerSkin base) {
		if (base == null || id == null || name == null || !NetworkModule.skinsOn()) return base;
		Minecraft mc = Minecraft.getInstance();
		boolean own = mc.player != null && id.equals(mc.player.getUUID());
		if (!own && !Ecosystem.isUser(id)) return base;
		String key = name.toLowerCase(Locale.ROOT);
		if (!NICK.matcher(key).matches()) return base;

		sweep(mc);
		Entry e = CACHE.computeIfAbsent(key, k -> new Entry());
		long now = System.currentTimeMillis();
		if (!e.busy && now >= e.nextCheck) {
			String url = NetworkModule.baseUrl();
			if (url != null) {
				e.busy = true;
				long period = own ? OWN_MS : OTHER_MS;
				EXEC.execute(() -> fetch(e, key, url, period));
			}
		}
		ClientAsset.ResourceTexture body = e.body;
		if (body == null) return base;
		if (e.lastBase != base || e.lastOut == null || e.lastOut.body() != body) {
			e.lastBase = base;
			e.lastOut = new PlayerSkin(body, base.cape(), base.elytra(), e.model, base.secure());
		}
		return e.lastOut;
	}

	/** Сбросить кэш (смена базы / выход). Текстуры освободятся сами. */
	public static void clear() {
		CACHE.values().forEach(e -> e.nextCheck = 0);
	}

	// ------------------------------------------------------------------ фон

	private static void fetch(Entry e, String key, String base, long period) {
		long next = System.currentTimeMillis() + period;
		try {
			String atRaw = Ecosystem.request("GET", base + "/darkx/skins/" + key + "/at.json", null).trim();
			long at;
			try {
				at = Long.parseLong(atRaw);
			} catch (NumberFormatException nf) {
				at = -1; // null или ошибка доступа: скина нет
			}
			if (at < 0) {
				if (e.body != null) Minecraft.getInstance().execute(() -> drop(e));
				next = System.currentTimeMillis() + NONE_MS;
				return;
			}
			if (at == e.at && e.body != null) return;

			String body = Ecosystem.request("GET", base + "/darkx/skins/" + key + ".json", null);
			JsonObject o = JsonParser.parseString(body).getAsJsonObject();
			byte[] png = Base64.getDecoder().decode(o.get("png").getAsString());
			if (png.length > MAX_BYTES) throw new IOException("скин слишком большой");
			PlayerModelType model = o.has("model") && "slim".equals(o.get("model").getAsString()) ? PlayerModelType.SLIM : PlayerModelType.WIDE;
			long ver = at;
			Minecraft.getInstance().execute(() -> install(e, key, png, model, ver));
		} catch (Exception ex) {
			System.err.println("[DarkX] Скин «" + key + "»: не удалось загрузить: " + ex);
			next = System.currentTimeMillis() + ERROR_MS;
		} finally {
			e.nextCheck = next;
			e.busy = false;
		}
	}

	// ------------------------------------------------------------- главный поток

	private static void install(Entry e, String key, byte[] png, PlayerModelType model, long at) {
		NativeImage img = null;
		try {
			NativeImage src = NativeImage.read(png);
			try {
				img = normalize(src);
			} finally {
				if (img != src) src.close();
			}
			String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(png)).substring(0, 8);
			Identifier id = Identifier.fromNamespaceAndPath("darkx", "skins/" + key + "_" + hash);
			Minecraft mc = Minecraft.getInstance();
			DynamicTexture tex = new DynamicTexture(() -> "darkx skin " + key, img);
			img = null; // теперь принадлежит текстуре
			tex.upload();
			mc.getTextureManager().register(id, tex);

			Identifier old = e.tex;
			e.tex = id;
			e.model = model;
			e.at = at;
			e.lastBase = null;
			e.body = new ClientAsset.ResourceTexture(id, id);
			if (old != null && !old.equals(id)) retire(old);
			System.out.println("[DarkX] Скин «" + key + "» загружен (" + model.getSerializedName() + ")");
		} catch (Exception ex) {
			System.err.println("[DarkX] Скин «" + key + "»: битый PNG: " + ex);
			if (img != null) img.close();
		}
	}

	private static void drop(Entry e) {
		Identifier old = e.tex;
		e.body = null;
		e.tex = null;
		e.at = -1;
		e.lastBase = null;
		e.lastOut = null;
		if (old != null) retire(old);
	}

	private static void retire(Identifier id) {
		GRAVE.add(new Object[] {id, System.currentTimeMillis()});
	}

	private static void sweep(Minecraft mc) {
		if (GRAVE.isEmpty()) return;
		long now = System.currentTimeMillis();
		GRAVE.removeIf(g -> {
			if (now - (Long) g[1] < 5_000L) return false;
			mc.getTextureManager().release((Identifier) g[0]);
			return true;
		});
	}

	/** Приводит скин к 64x64 как ваниль: старый 64x32 достраивается зеркалом, у тела убирается прозрачность. */
	private static NativeImage normalize(NativeImage src) throws IOException {
		int w = src.getWidth(), h = src.getHeight();
		if (w != 64 || (h != 64 && h != 32)) throw new IOException("размер " + w + "x" + h + ", нужен 64x64");
		NativeImage out = src;
		if (h == 32) {
			out = new NativeImage(64, 64, true);
			out.copyFrom(src);
			out.fillRect(0, 32, 64, 32, 0);
			out.copyRect(4, 16, 16, 32, 4, 4, true, false);
			out.copyRect(8, 16, 16, 32, 4, 4, true, false);
			out.copyRect(0, 20, 24, 32, 4, 12, true, false);
			out.copyRect(4, 20, 16, 32, 4, 12, true, false);
			out.copyRect(8, 20, 8, 32, 4, 12, true, false);
			out.copyRect(12, 20, 16, 32, 4, 12, true, false);
			out.copyRect(44, 16, -8, 32, 4, 4, true, false);
			out.copyRect(48, 16, -8, 32, 4, 4, true, false);
			out.copyRect(40, 20, 0, 32, 4, 12, true, false);
			out.copyRect(44, 20, -8, 32, 4, 12, true, false);
			out.copyRect(48, 20, -16, 32, 4, 12, true, false);
			out.copyRect(52, 20, -8, 32, 4, 12, true, false);
		}
		opaque(out, 0, 0, 32, 16);
		opaque(out, 0, 16, 64, 32);
		opaque(out, 16, 48, 48, 64);
		return out;
	}

	private static void opaque(NativeImage img, int x0, int y0, int x1, int y1) {
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) img.setPixel(x, y, img.getPixel(x, y) | 0xFF000000);
		}
	}
}
