package dev.darkx.visuals.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Экосистема DarkX: каждый клиент раз в 20 секунд пишет в общую Firebase Realtime Database
 * «я на сервере X» и читает, кто ещё из DarkX сидит на этом же сервере.
 * Отправляются только: UUID, ник и адрес сервера. Если адрес базы пустой — ничего не делает.
 */
public final class Ecosystem {
	/** Адрес базы для всех клиентов. Зашит в клиент, в настройках не меняется. */
	public static final String DEFAULT_URL = "https://darkx-b0d38-default-rtdb.europe-west1.firebasedatabase.app";

	/** Значение «сервера» для одиночной игры и хоста LAN: другу видно «в одиночной игре». */
	public static final String SINGLE = "singleplayer";

	private static final long PERIOD_MS = 20_000L;
	private static final long FRESH_MS = 75_000L;

	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "darkx-network");
		t.setDaemon(true);
		return t;
	});

	private static volatile Set<UUID> users = Set.of();
	private static volatile boolean busy;
	private static long lastSync;
	private static String lastServer = "";
	private static volatile String lastBase;
	private static volatile UUID lastUuid;

	private Ecosystem() {}

	public static boolean isUser(UUID id) {
		return users.contains(id);
	}

	public static int count() {
		return users.size();
	}

	/** Разница между часами Firebase и локальными (из заголовка Date), мс. */
	private static volatile long skew;

	/** Текущее время по часам сервера Firebase. */
	public static long serverNow() {
		return System.currentTimeMillis() + skew;
	}

	/** Адрес базы с учётом значения по умолчанию; null, если адрес некорректный. */
	public static String baseUrl(String raw) {
		return normalize(raw == null || raw.isBlank() ? DEFAULT_URL : raw);
	}

	private static String normalize(String url) {
		if (url == null) return null;
		url = url.trim();
		while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
		return url.startsWith("https://") ? url : null;
	}

	public static void tick(Minecraft mc, String rawUrl) {
		String base = normalize(rawUrl == null || rawUrl.isBlank() ? DEFAULT_URL : rawUrl);
		if (base == null || mc.player == null) {
			users = Set.of();
			return;
		}
		String server;
		if (mc.getCurrentServer() != null) server = mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
		else if (mc.getSingleplayerServer() != null) server = SINGLE;
		else {
			users = Set.of();
			return;
		}
		if (!server.equals(lastServer)) {
			lastServer = server;
			users = Set.of();
			lastSync = 0;
		}
		long now = System.currentTimeMillis();
		if (busy || now - lastSync < PERIOD_MS) return;
		lastSync = now;
		busy = true;

		UUID me = mc.player.getUUID();
		String name = mc.player.getName().getString();
		lastBase = base;
		lastUuid = me;
		EXEC.execute(() -> {
			try {
				request("PUT", base + "/darkx/users/" + me + ".json",
					"{\"n\":" + quote(name) + ",\"l\":" + quote(name.toLowerCase(Locale.ROOT)) + ",\"s\":" + quote(server) + ",\"t\":{\".sv\":\"timestamp\"}}");
				if (server.equals(SINGLE)) {
					users = Set.of(); // в одиночной игре «таких же» игроков для значка DX нет
					return;
				}
				String q = "?orderBy=" + enc("\"s\"") + "&equalTo=" + enc(quote(server));
				String body = request("GET", base + "/darkx/users.json" + q, null);
				users = parse(body);
			} catch (Exception ignored) {
				// сеть недоступна — молча пробуем в следующий раз
			} finally {
				busy = false;
			}
		});
	}

	/** Убрать себя из базы (выключение модуля / выход из игры). */
	public static void leave() {
		String base = lastBase;
		UUID me = lastUuid;
		users = Set.of();
		lastSync = 0;
		if (base == null || me == null) return;
		Thread t = new Thread(() -> {
			try {
				request("DELETE", base + "/darkx/users/" + me + ".json", null);
			} catch (Exception ignored) {
				// не страшно, запись протухнет сама
			}
		}, "darkx-network-leave");
		t.setDaemon(false);
		t.start();
		try {
			t.join(2000);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}

	private static Set<UUID> parse(String body) {
		Set<UUID> out = new HashSet<>();
		if (body == null || body.isBlank() || body.equals("null")) return out;
		JsonElement root = JsonParser.parseString(body);
		if (!root.isJsonObject()) return out;
		long max = 0;
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
			JsonObject o = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : null;
			if (o != null && o.has("t")) max = Math.max(max, o.get("t").getAsLong());
		}
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
			try {
				JsonObject o = e.getValue().getAsJsonObject();
				if (max - o.get("t").getAsLong() <= FRESH_MS) out.add(UUID.fromString(e.getKey()));
			} catch (Exception ignored) {
				// чужая или битая запись
			}
		}
		return out;
	}

	static String request(String method, String url, String json) throws Exception {
		HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
		c.setConnectTimeout(4000);
		c.setReadTimeout(4000);
		c.setRequestMethod(method);
		if (json != null) {
			c.setDoOutput(true);
			c.setRequestProperty("Content-Type", "application/json");
			try (OutputStream os = c.getOutputStream()) {
				os.write(json.getBytes(StandardCharsets.UTF_8));
			}
		}
		int code = c.getResponseCode();
		long date = c.getHeaderFieldDate("Date", 0L);
		if (date > 0) skew = date - System.currentTimeMillis();
		try (InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream()) {
			return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} finally {
			c.disconnect();
		}
	}

	static String quote(String s) {
		return new com.google.gson.JsonPrimitive(s).toString();
	}

	static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}
