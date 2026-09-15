package lol.pyr.znpcsplus.fabric.skin;

import com.google.gson.*;
import lol.pyr.znpcsplus.fabric.config.ConfigManager;
import org.slf4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;

/** Mojang + Ashcon fallback + MineSkin implementation matching ZNPCsPlus 2.X behaviour. */
public final class SkinCache {
    private final Logger logger;
    private final ConfigManager config;
    private final Map<String, Timed<SkinData>> skins = new ConcurrentHashMap<>();
    private final Map<String, Timed<String>> ids = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private static final long TTL = 60_000L;

    public SkinCache(Logger logger, ConfigManager config) {
        this.logger = logger;
        this.config = config;
        try { Files.createDirectories(config.skinsDir()); } catch (IOException e) { logger.warn("Could not create skins directory", e); }
    }

    public CompletableFuture<SkinData> fetchByName(String name) {
        if (name == null || name.isBlank()) return CompletableFuture.completedFuture(null);
        String key = name.toLowerCase();
        Timed<String> cachedId = ids.get(key);
        if (valid(cachedId)) return fetchByUuid(cachedId.value);
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject obj = getJson("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + url(name));
                if (obj != null && obj.has("id")) {
                    String id = obj.get("id").getAsString();
                    ids.put(key, new Timed<>(id));
                    SkinData data = fetchByUuid(id).join();
                    if (data != null) return data;
                }
            } catch (Exception e) { warn("Failed to get UUID for skin '" + name + "'; trying fallback", e); }
            try {
                JsonObject obj = getJson("https://api.ashcon.app/mojang/v2/user/" + url(name));
                if (obj == null || obj.has("error")) return null;
                String id = obj.get("uuid").getAsString().replace("-", "");
                JsonObject raw = obj.getAsJsonObject("textures").getAsJsonObject("raw");
                SkinData data = new SkinData(raw.get("value").getAsString(), raw.has("signature") ? raw.get("signature").getAsString() : null);
                ids.put(key, new Timed<>(id)); skins.put(id, new Timed<>(data)); return data;
            } catch (Exception e) { warn("Fallback skin lookup failed for '" + name + "'", e); return null; }
        }, executor);
    }

    public CompletableFuture<SkinData> fetchByUuid(String uuid) {
        String key = uuid.replace("-", "");
        Timed<SkinData> cached = skins.get(key);
        if (valid(cached)) return CompletableFuture.completedFuture(cached.value);
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject obj = getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + key + "?unsigned=false");
                if (obj == null || !obj.has("properties")) return null;
                for (JsonElement element : obj.getAsJsonArray("properties")) {
                    JsonObject p = element.getAsJsonObject();
                    if (!"textures".equalsIgnoreCase(p.get("name").getAsString())) continue;
                    SkinData data = new SkinData(p.get("value").getAsString(), p.has("signature") ? p.get("signature").getAsString() : null);
                    skins.put(key, new Timed<>(data)); return data;
                }
            } catch (Exception e) { warn("Failed to fetch skin UUID " + key, e); }
            return null;
        }, executor);
    }

    public CompletableFuture<SkinData> fetchByUrl(String textureUrl, String variant) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject body = new JsonObject(); body.addProperty("variant", variant); body.addProperty("url", textureUrl);
                JsonObject obj = postJson("https://api.mineskin.org/generate/url", body.toString(), "application/json");
                return mineSkinResult(obj);
            } catch (Exception e) { warn("Failed to generate skin from URL", e); return null; }
        }, executor);
    }

    public CompletableFuture<SkinData> fetchFromFile(String name) {
        return CompletableFuture.supplyAsync(() -> {
            Path path = config.skinsDir().resolve(name).normalize();
            if (!path.startsWith(config.skinsDir()) || Files.notExists(path)) return null;
            try {
                String boundary = "ZNPCsPlusFabric" + System.nanoTime();
                URL apiUrl = URI.create("https://api.mineskin.org/generate/upload").toURL();
                HttpURLConnection c = (HttpURLConnection) apiUrl.openConnection();
                setup(c, "POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+path.getFileName()+"\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    Files.copy(path, out);
                    out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                try (Reader reader = new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8)) {
                    return mineSkinResult(JsonParser.parseReader(reader).getAsJsonObject());
                } finally { c.disconnect(); }
            } catch (Exception e) { warn("Failed to generate skin from file " + name, e); return null; }
        }, executor);
    }

    private SkinData mineSkinResult(JsonObject obj) {
        if (obj == null || obj.has("error") || !obj.has("data")) return null;
        JsonObject tex = obj.getAsJsonObject("data").getAsJsonObject("texture");
        return new SkinData(tex.get("value").getAsString(), tex.has("signature") ? tex.get("signature").getAsString() : null);
    }

    private JsonObject getJson(String address) throws IOException {
        HttpURLConnection c=(HttpURLConnection)URI.create(address).toURL().openConnection(); setup(c,"GET");
        try (Reader r=new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8)) { return JsonParser.parseReader(r).getAsJsonObject(); }
        finally { c.disconnect(); }
    }
    private JsonObject postJson(String address, String body, String type) throws IOException {
        HttpURLConnection c=(HttpURLConnection)URI.create(address).toURL().openConnection(); setup(c,"POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type",type); c.setRequestProperty("accept","application/json");
        try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}
        try(Reader r=new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8)){return JsonParser.parseReader(r).getAsJsonObject();}
        finally{c.disconnect();}
    }
    private static void setup(HttpURLConnection c, String method) throws IOException { c.setConnectTimeout(15000); c.setReadTimeout(10000); c.setRequestMethod(method); c.setRequestProperty("User-Agent","ZNPCsPlus-Fabric/2"); }
    private static String url(String s) { return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private boolean valid(Timed<?> t){return t!=null && System.currentTimeMillis()-t.time<TTL;}
    public void clean(){skins.entrySet().removeIf(e->!valid(e.getValue())); ids.entrySet().removeIf(e->!valid(e.getValue()));}
    public void close(){executor.shutdownNow();}
    private void warn(String message, Throwable t){if(!config.get().disableSkinFetcherWarnings) logger.warn(message,t);}
    private record Timed<T>(T value,long time){Timed(T value){this(value,System.currentTimeMillis());}}
}
