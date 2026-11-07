package dev.darkx.visuals.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.darkx.visuals.modules.NetworkModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Близкие друзья (Trusted Friends) поверх DarkX Network.
 * Оба игрока пишут в чат «.dx tf add ник» — тогда дружба взаимная, и друг виден в меню:
 * в сети ли он, на каком сервере, и можно зайти к нему одним кликом.
 *
 * Хранение в Firebase (ключи — ники в нижнем регистре):
 *   /darkx/tf/{я}/{он}      — «я добавил его»
 *   /darkx/inbox/{он}/{я}   — входящий запрос у него
 * Дружба взаимная, когда у меня есть и tf/{я}/{он}, и inbox/{я}/{он}.
 */
public final class Friends {
	private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

	public enum State { MUTUAL, INCOMING, OUTGOING }

	/** key — ник в нижнем регистре, name — ник как его пишет игрок. */
	public record Friend(String key, String name, State state, boolean online, String server) {}

	private static final long PERIOD_MS = 15_000L;
	private static final long FRESH_MS = 90_000L;
	private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{1,16}");

	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "darkx-friends");
		t.setDaemon(true);
		return t;
	});

	private static volatile List<Friend> list = List.of();
	private static volatile String error = "";
	private static volatile boolean busy;
	private static volatile String base;
	private static volatile String myName = "";
	private static long lastSync;

	// только из потока EXEC
	private static Map<String, Friend> prev = new HashMap<>();
	private static boolean firstSync = true;

	private Friends() {}

	public static List<Friend> list() {
		return list;
	}

	/** Текст последней ошибки базы (пусто, если всё хорошо). */
	public static String error() {
		return error;
	}

	public static int mutualCount() {
		int n = 0;
		for (Friend f : list) if (f.state() == State.MUTUAL) n++;
		return n;
	}

	public static int onlineCount() {
		int n = 0;
		for (Friend f : list) if (f.online()) n++;
		return n;
	}

	/** Обновить список при ближайшем тике (например, при открытии вкладки). */
	public static void refresh() {
		lastSync = 0;
	}

	public static void clear() {
		list = List.of();
		error = "";
		prev = new HashMap<>();
		firstSync = true;
		lastSync = 0;
	}

	/** Вызывается каждый тик из NetworkModule. */
	public static void tick(Minecraft mc, String rawUrl) {
		String b = Ecosystem.baseUrl(rawUrl);
		if (b == null || mc.player == null) return;
		base = b;
		myName = mc.player.getName().getString();
		long now = System.currentTimeMillis();
		if (busy || now - lastSync < PERIOD_MS) return;
		lastSync = now;
		busy = true;
		String me = myName.toLowerCase(Locale.ROOT);
		EXEC.execute(() -> {
			try {
				sync(b, me);
			} catch (Exception e) {
				error = "нет связи с базой";
			} finally {
				busy = false;
			}
		});
	}

	// ============================================================== чат-команды

	/** Перехват сообщений вида «.dx ...». @return false — сообщение не уходит на сервер. */
	public static boolean onChat(String raw) {
		String s = raw.trim();
		String low = s.toLowerCase(Locale.ROOT);
		if (!low.equals(".dx") && !low.startsWith(".dx ")) return true;

		String[] a = s.split("\\s+");
		if (a.length < 2 || a[1].equalsIgnoreCase("help")) {
			help();
			return false;
		}
		if (!a[1].equalsIgnoreCase("tf")) {
			say("Неизвестная команда. Список: .dx help");
			return false;
		}
		if (!NetworkModule.active()) {
			say("Включи модуль DarkX Network (Функции → Разное).");
			return false;
		}
		String sub = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
		String nick = a.length > 3 ? a[3] : "";
		switch (sub) {
			case "add" -> add(nick);
			case "remove", "rem", "del" -> remove(nick);
			case "join" -> joinFriend(nick);
			case "list" -> printList();
			default -> help();
		}
		return false;
	}

	private static void help() {
		say("Близкие друзья (оба игрока пишут команду друг на друга):");
		say(".dx tf add <ник> — добавить / принять запрос");
		say(".dx tf remove <ник> — убрать / отклонить");
		say(".dx tf join <ник> — зайти на сервер друга");
		say(".dx tf list — список друзей");
	}

	private static void printList() {
		List<Friend> l = list;
		if (l.isEmpty()) {
			say("Список пуст. Добавь друга: .dx tf add <ник>");
			return;
		}
		for (Friend f : l) {
			String st = switch (f.state()) {
				case MUTUAL -> f.online() ? "в сети · " + where(f.server()) : "не в сети";
				case INCOMING -> "хочет дружить (.dx tf add " + f.name() + ")";
				case OUTGOING -> "ждёт подтверждения";
			};
			say(f.name() + " — " + st);
		}
	}

	private static String checkNick(String nick) {
		if (nick.isEmpty()) return "Укажи ник: .dx tf add <ник>";
		if (!NICK.matcher(nick).matches()) return "Некорректный ник.";
		if (nick.equalsIgnoreCase(myName)) return "Себя добавлять не нужно :)";
		return null;
	}

	/** Добавить в близкие (или принять входящий запрос). */
	public static void add(String nick) {
		String bad = checkNick(nick);
		if (bad != null) {
			say(bad);
			return;
		}
		String b = base, myKey = myName.toLowerCase(Locale.ROOT), my = myName;
		if (b == null) {
			say("База недоступна — включи DarkX Network.");
			return;
		}
		String key = nick.toLowerCase(Locale.ROOT);
		EXEC.execute(() -> {
			try {
				String ts = ",\"t\":{\".sv\":\"timestamp\"}}";
				write("PUT", b + "/darkx/tf/" + myKey + "/" + key + ".json", "{\"n\":" + Ecosystem.quote(nick) + ts);
				write("PUT", b + "/darkx/inbox/" + key + "/" + myKey + ".json", "{\"n\":" + Ecosystem.quote(my) + ts);
				sync(b, myKey);
				Friend f = find(key);
				if (f != null && f.state() == State.MUTUAL) say("Вы с " + f.name() + " теперь близкие друзья!");
				else say("Запрос отправлен. " + nick + " должен написать: .dx tf add " + my);
			} catch (Exception e) {
				fail(e);
			}
		});
	}

	/** Убрать из друзей / отклонить запрос / отменить свой запрос. */
	public static void remove(String nick) {
		String bad = checkNick(nick);
		if (bad != null) {
			say(bad);
			return;
		}
		String b = base, myKey = myName.toLowerCase(Locale.ROOT);
		if (b == null) return;
		String key = nick.toLowerCase(Locale.ROOT);
		EXEC.execute(() -> {
			try {
				write("DELETE", b + "/darkx/tf/" + myKey + "/" + key + ".json", null);
				write("DELETE", b + "/darkx/inbox/" + key + "/" + myKey + ".json", null);
				write("DELETE", b + "/darkx/inbox/" + myKey + "/" + key + ".json", null);
				sync(b, myKey);
				say(nick + " убран из списка.");
			} catch (Exception e) {
				fail(e);
			}
		});
	}

	/** Человекочитаемое «где играет». */
	public static String where(String server) {
		return Ecosystem.SINGLE.equals(server) ? "одиночная игра" : server;
	}

	private static void joinFriend(String nick) {
		Friend f = find(nick.toLowerCase(Locale.ROOT));
		if (f == null || f.state() != State.MUTUAL) say("Такого близкого друга нет.");
		else if (!f.online() || f.server().isEmpty()) say(f.name() + " сейчас не в сети.");
		else if (Ecosystem.SINGLE.equals(f.server())) say(f.name() + " сейчас в одиночной игре — зайти нельзя.");
		else {
			say("Захожу к " + f.name() + " на " + f.server() + "…");
			ServerJoin.join(f.server());
		}
	}

	private static Friend find(String key) {
		for (Friend f : list) if (f.key().equals(key)) return f;
		return null;
	}

	// ================================================================ синхронизация

	private static void sync(String b, String me) throws Exception {
		String bodyOut = Ecosystem.request("GET", b + "/darkx/tf/" + me + ".json", null);
		String bodyIn = Ecosystem.request("GET", b + "/darkx/inbox/" + me + ".json", null);
		String err = errorOf(bodyOut);
		if (err == null) err = errorOf(bodyIn);
		if (err != null) {
			error = err;
			LOG.warn("[DarkX] friends: {}", err);
			return;
		}
		Map<String, String> out = names(bodyOut), in = names(bodyIn);
		Set<String> keys = new TreeSet<>();
		keys.addAll(out.keySet());
		keys.addAll(in.keySet());

		long now = Ecosystem.serverNow();
		List<Friend> res = new ArrayList<>();
		for (String k : keys) {
			boolean o = out.containsKey(k), i = in.containsKey(k);
			String name = i ? in.get(k) : out.get(k);
			if (o && i) {
				String server = presence(b, k, now);
				res.add(new Friend(k, name, State.MUTUAL, server != null, server == null ? "" : server));
			} else {
				res.add(new Friend(k, name, i ? State.INCOMING : State.OUTGOING, false, ""));
			}
		}
		res.sort(Comparator.comparingInt((Friend f) -> f.state() == State.INCOMING ? 0 : f.state() == State.MUTUAL ? 1 : 2)
			.thenComparing(f -> !f.online())
			.thenComparing(Friend::key));
		error = "";
		notifyChanges(res);
		list = List.copyOf(res);
	}

	/** Сервер друга, если он сейчас онлайн с DarkX Network; иначе null. */
	private static String presence(String b, String key, long now) throws Exception {
		String body = Ecosystem.request("GET", b + "/darkx/users.json?orderBy=" + Ecosystem.enc("\"l\"")
			+ "&equalTo=" + Ecosystem.enc(Ecosystem.quote(key)), null);
		String err = errorOf(body);
		if (err != null) {
			error = err;
			LOG.warn("[DarkX] friends presence: {}", err);
			return null;
		}
		if (body == null || body.isBlank() || body.equals("null")) return null;
		JsonElement root = JsonParser.parseString(body);
		if (!root.isJsonObject()) return null;
		long bestT = 0;
		String best = null;
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
			try {
				JsonObject o = e.getValue().getAsJsonObject();
				long t = o.get("t").getAsLong();
				if (now - t <= FRESH_MS && t >= bestT) {
					bestT = t;
					best = o.get("s").getAsString();
				}
			} catch (Exception ignored) {
				// битая запись
			}
		}
		return best;
	}

	private static void notifyChanges(List<Friend> res) {
		Map<String, Friend> cur = new HashMap<>();
		for (Friend f : res) cur.put(f.key(), f);
		if (!firstSync) {
			for (Friend f : res) {
				Friend p = prev.get(f.key());
				if (f.state() == State.INCOMING && (p == null || p.state() != State.INCOMING)) {
					say(f.name() + " хочет добавить тебя в близкие друзья. Принять: .dx tf add " + f.name());
					toast("Запрос в друзья", f.name() + " · .dx tf add " + f.name());
				} else if (f.state() == State.MUTUAL && p != null && p.state() == State.OUTGOING) {
					toast("Близкие друзья", f.name() + " принял(а) запрос");
				} else if (f.state() == State.MUTUAL && f.online() && (p == null || !p.online())) {
					toast("Друг в сети", f.name() + " · " + where(f.server()));
				}
			}
		}
		prev = cur;
		firstSync = false;
	}

	// ===================================================================== утилиты

	private static Map<String, String> names(String body) {
		Map<String, String> out = new HashMap<>();
		if (body == null || body.isBlank() || body.equals("null")) return out;
		try {
			JsonElement root = JsonParser.parseString(body);
			if (!root.isJsonObject()) return out;
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
				if (!e.getValue().isJsonObject()) continue;
				JsonObject o = e.getValue().getAsJsonObject();
				out.put(e.getKey(), o.has("n") ? o.get("n").getAsString() : e.getKey());
			}
		} catch (Exception ignored) {
			// битый ответ
		}
		return out;
	}

	/** {"error":"..."} от Firebase (нет прав, нет индекса). */
	private static String errorOf(String body) {
		if (body == null || !body.startsWith("{\"error\"")) return null;
		try {
			return JsonParser.parseString(body).getAsJsonObject().get("error").getAsString();
		} catch (Exception e) {
			return "ошибка базы";
		}
	}

	private static void write(String method, String url, String json) throws Exception {
		String err = errorOf(Ecosystem.request(method, url, json));
		if (err != null) throw new IOException(err);
	}

	private static void fail(Exception e) {
		String m = e.getMessage() == null ? e.toString() : e.getMessage();
		error = m;
		say("Не получилось: " + m + (m.toLowerCase(Locale.ROOT).contains("permission") ? " (обнови правила Firebase, см. README)" : ""));
	}

	private static void say(String text) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.player == null) return;
			mc.player.displayClientMessage(Component.literal("[DX] ").withStyle(st -> st.withColor(0xF97316))
				.append(Component.literal(text)), false);
		});
	}

	private static void toast(String title, String text) {
		Minecraft.getInstance().execute(() -> Notifications.push(title, text, Theme.accent()));
	}
}
