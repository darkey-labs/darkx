package dev.darkx.visuals.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.darkx.visuals.modules.CapesModule;
import dev.darkx.visuals.modules.NetworkModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
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
 * Плащи DarkX. Свой выбор публикуется в базу (darkx/capes/&lt;ник&gt; = {"id":"ember"}), остальные игроки с DarkX
 * на сервере скачивают его и видят плащ. Готовые плащи лежат в ресурсах мода (textures/capes/&lt;id&gt;.png);
 * вместо id можно положить свой PNG 64x32 в поле "png" (base64) — так сможет и лаунчер.
 * Подмена идёт тем же путём, что и скины: миксины на getSkin, PlayerSkin.cape().
 */
public final class DarkCapes {
	/** Идентификаторы готовых плащей; порядок совпадает с настройкой «Плащ» (после пункта «Нет»). */
	public static final String[] IDS = {"darkx", "ember", "frost", "void", "mono"};
	public static final String[] NAMES = {"DarkX", "Ember", "Frost", "Void", "Mono"};

	private static final long OWN_MS = 20_000L, OTHER_MS = 120_000L, NONE_MS = 300_000L, ERROR_MS = 30_000L;
	private static final int MAX_BYTES = 60_000;
	private static final Pattern NICK = Pattern.compile("^[a-z0-9_]{3,16}$");

	private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
	private static final Map<String, ClientAsset.ResourceTexture> BUILTIN = new ConcurrentHashMap<>();
	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "darkx-capes");
		t.setDaemon(true);
		return t;
	});
	private static final List<Object[]> GRAVE = new ArrayList<>();

	private static final class Entry {
		volatile boolean busy;
		volatile long nextCheck;
		volatile String id;
		volatile String customHash;
		volatile ClientAsset.ResourceTexture custom;
		Identifier tex;
		PlayerSkin lastBase, lastOut;
		ClientAsset.Texture lastCape;
	}

	private DarkCapes() {}

	/** Готовый плащ по id (текстура из ресурсов мода) или null. */
	public static ClientAsset.ResourceTexture builtin(String id) {
		if (id == null) return null;
		for (String s : IDS) {
			if (s.equals(id)) {
				return BUILTIN.computeIfAbsent(id, k -> {
					Identifier path = Identifier.fromNamespaceAndPath("darkx", "textures/capes/" + k + ".png");
					return new ClientAsset.ResourceTexture(path, path);
				});
			}
		}
		return null;
	}

	/** Подменяет плащ игрока. Вызывается из рендера, поэтому всё тяжёлое делается в фоне. */
	public static PlayerSkin apply(UUID id, String name, PlayerSkin base) {
		if (base == null || id == null || name == null || !CapesModule.active()) return base;
		Minecraft mc = Minecraft.getInstance();
		boolean own = mc.player != null && id.equals(mc.player.getUUID());
		if (!own && !(CapesModule.showOthers() && Ecosystem.isUser(id))) return base;
		String key = name.toLowerCase(Locale.ROOT);
		if (!NICK.matcher(key).matches()) return base;

		sweep(mc);
		Entry e = CACHE.computeIfAbsent(key, k -> new Entry());
		ClientAsset.Texture cape = null;
		if (own) cape = builtin(CapesModule.ownId());
		if (cape == null) {
			long now = System.currentTimeMillis();
			if (!e.busy && now >= e.nextCheck) {
				String url = NetworkModule.baseUrl();
				if (url != null) {
					e.busy = true;
					long period = own ? OWN_MS : OTHER_MS;
					EXEC.execute(() -> fetch(e, key, url, period));
				}
			}
			cape = e.custom != null ? e.custom : builtin(e.id);
		}
		if (cape == null) return base;
		if (e.lastBase != base || e.lastCape != cape || e.lastOut == null) {
			e.lastBase = base;
			e.lastCape = cape;
			e.lastOut = new PlayerSkin(base.body(), cape, base.elytra(), base.model(), base.secure());
		}
		return e.lastOut;
	}

	/** Сбросить кэш (смена базы / выход). */
	public static void clear() {
		CACHE.values().forEach(e -> e.nextCheck = 0);
	}

	/** Опубликовать свой выбор: id плаща или null, чтобы убрать. Ник берётся из аккаунта. */
	public static void publish(String id) {
		String url = NetworkModule.baseUrl();
		if (url == null) return;
		String nick = Minecraft.getInstance().getUser().getName().toLowerCase(Locale.ROOT);
		if (!NICK.matcher(nick).matches()) return;
		EXEC.execute(() -> {
			try {
				String target = url + "/darkx/capes/" + nick + ".json";
				if (id == null) Ecosystem.request("DELETE", target, null);
				else Ecosystem.request("PUT", target, "{\"id\":" + Ecosystem.quote(id) + ",\"t\":{\".sv\":\"timestamp\"}}");
			} catch (Exception ex) {
				System.err.println("[DarkX] Плащ: не удалось опубликовать: " + ex);
			}
		});
	}

	// ------------------------------------------------------------------ фон

	private static void fetch(Entry e, String key, String base, long period) {
		long next = System.currentTimeMillis() + period;
		try {
			String body = Ecosystem.request("GET", base + "/darkx/capes/" + key + ".json", null).trim();
			if (!body.startsWith("{")) {
				// null или ошибка доступа: плаща нет
				if (e.id != null || e.custom != null) Minecraft.getInstance().execute(() -> drop(e));
				next = System.currentTimeMillis() + NONE_MS;
				return;
			}
			JsonObject o = JsonParser.parseString(body).getAsJsonObject();
			if (o.has("png")) {
				byte[] png = Base64.getDecoder().decode(o.get("png").getAsString());
				if (png.length > MAX_BYTES) throw new IOException("плащ слишком большой");
				String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(png)).substring(0, 8);
				if (hash.equals(e.customHash) && e.custom != null) return;
				Minecraft.getInstance().execute(() -> install(e, key, png, hash));
			} else if (o.has("id")) {
				String id = o.get("id").getAsString();
				String ok = builtin(id) == null ? null : id;
				if (e.custom != null) Minecraft.getInstance().execute(() -> drop(e));
				e.id = ok;
			}
		} catch (Exception ex) {
			System.err.println("[DarkX] Плащ «" + key + "»: не удалось загрузить: " + ex);
			next = System.currentTimeMillis() + ERROR_MS;
		} finally {
			e.nextCheck = next;
			e.busy = false;
		}
	}

	// ------------------------------------------------------------- главный поток

	private static void install(Entry e, String key, byte[] png, String hash) {
		NativeImage img = null;
		try {
			img = NativeImage.read(png);
			if (img.getWidth() != 64 || img.getHeight() != 32) throw new IOException("размер " + img.getWidth() + "x" + img.getHeight() + ", нужен 64x32");
			Identifier id = Identifier.fromNamespaceAndPath("darkx", "capes/" + key + "_" + hash);
			Minecraft mc = Minecraft.getInstance();
			DynamicTexture tex = new DynamicTexture(() -> "darkx cape " + key, img);
			img = null; // теперь принадлежит текстуре
			tex.upload();
			mc.getTextureManager().register(id, tex);
			Identifier old = e.tex;
			e.tex = id;
			e.customHash = hash;
			e.id = null;
			e.custom = new ClientAsset.ResourceTexture(id, id);
			if (old != null && !old.equals(id)) retire(old);
		} catch (Exception ex) {
			System.err.println("[DarkX] Плащ «" + key + "»: битый PNG: " + ex);
			if (img != null) img.close();
		}
	}

	private static void drop(Entry e) {
		Identifier old = e.tex;
		e.custom = null;
		e.customHash = null;
		e.tex = null;
		e.id = null;
		e.lastOut = null;
		e.lastCape = null;
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
}
