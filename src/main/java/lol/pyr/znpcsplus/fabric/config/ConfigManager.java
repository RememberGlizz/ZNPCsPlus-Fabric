package lol.pyr.znpcsplus.fabric.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Config loader for the Fabric port.
 *
 * The original port dumped Java beans directly with SnakeYAML, producing
 * !!lol.pyr... global tags. SnakeYAML 2.x may reject those tags when another
 * mod supplies a stricter SnakeYAML runtime. This loader treats config.yml as
 * plain data instead. It strips only this mod's two legacy type tags in memory,
 * then maps the resulting YAML onto ModConfig explicitly.
 *
 * NPC persistence is intentionally untouched; this class only handles
 * config/znpcsplus/config.yml.
 */
public final class ConfigManager {
    private static final String LEGACY_CONFIG_TAG = "!!lol.pyr.znpcsplus.fabric.config.ModConfig";
    private static final String LEGACY_DATABASE_TAG = "!!lol.pyr.znpcsplus.fabric.config.ModConfig$Database";

    private final Logger logger;
    private final Path root;
    private final Path file;
    private volatile ModConfig config;

    public ConfigManager(Logger logger) {
        this.logger = logger;
        this.root = FabricLoader.getInstance().getConfigDir().resolve("znpcsplus");
        this.file = root.resolve("config.yml");
    }

    public synchronized ModConfig load() {
        try {
            Files.createDirectories(root);
            if (Files.notExists(file)) {
                config = new ModConfig();
                save();
                return config;
            }

            String raw = Files.readString(file);
            boolean legacyTagged = raw.contains(LEGACY_CONFIG_TAG) || raw.contains(LEGACY_DATABASE_TAG);
            String safeYaml = stripLegacyTypeTags(raw);

            LoaderOptions loaderOptions = new LoaderOptions();
            Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
            Object parsed = yaml.load(safeYaml);

            if (parsed == null) {
                config = new ModConfig();
            } else if (parsed instanceof Map<?, ?> map) {
                config = fromMap(map);
                if (legacyTagged && hasYamlNpcFiles() && !"YAML".equalsIgnoreCase(config.storageType)) {
                    logger.warn("Legacy ZNPCsPlus config requested a non-YAML backend, but existing YAML NPC files were found; preserving YAML storage to protect existing NPCs");
                    config.storageType = "YAML";
                }
            } else {
                throw new IOException("Expected a YAML mapping at the root of " + file + " but found " + parsed.getClass().getName());
            }
        } catch (Exception e) {
            logger.error("Failed to load ZNPCsPlus Fabric config; defaults will be used", e);
            config = new ModConfig();
        }
        return config;
    }

    private boolean hasYamlNpcFiles() {
        Path npcFolder = root.resolve("data").resolve("npcs");
        if (!Files.isDirectory(npcFolder)) return false;
        try (var stream = Files.list(npcFolder)) {
            return stream.anyMatch(p -> p.getFileName().toString().toLowerCase().endsWith(".yml"));
        } catch (IOException e) {
            logger.warn("Could not inspect existing YAML NPC storage; leaving configured storage backend unchanged");
            return false;
        }
    }

    private static String stripLegacyTypeTags(String raw) {
        return raw.replace(LEGACY_DATABASE_TAG, "")
                .replace(LEGACY_CONFIG_TAG, "");
    }

    private static ModConfig fromMap(Map<?, ?> map) {
        ModConfig cfg = new ModConfig();

        cfg.viewDistance = intValue(map.get("viewDistance"), cfg.viewDistance);
        cfg.lineSpacing = doubleValue(map.get("lineSpacing"), cfg.lineSpacing);
        cfg.checkForUpdates = booleanValue(map.get("checkForUpdates"), cfg.checkForUpdates);
        cfg.debugEnabled = booleanValue(map.get("debugEnabled"), cfg.debugEnabled);
        cfg.storageType = stringValue(map.get("storageType"), cfg.storageType);
        cfg.disableSkinFetcherWarnings = booleanValue(map.get("disableSkinFetcherWarnings"), cfg.disableSkinFetcherWarnings);
        cfg.autoSaveInterval = intValue(map.get("autoSaveInterval"), cfg.autoSaveInterval);
        cfg.lookPropertyDistance = doubleValue(map.get("lookPropertyDistance"), cfg.lookPropertyDistance);
        cfg.tabHideDelay = intValue(map.get("tabHideDelay"), cfg.tabHideDelay);
        cfg.tabDisplayName = stringValue(map.get("tabDisplayName"), cfg.tabDisplayName);
        cfg.fakeEnforceSecureChat = booleanValue(map.get("fakeEnforceSecureChat"), cfg.fakeEnforceSecureChat);
        cfg.interactionDebounceMillis = intValue(map.get("interactionDebounceMillis"), cfg.interactionDebounceMillis);

        Object dbValue = map.get("database");
        if (dbValue instanceof Map<?, ?> db) {
            if (cfg.database == null) cfg.database = new ModConfig.Database();
            cfg.database.host = stringValue(db.get("host"), cfg.database.host);
            cfg.database.port = intValue(db.get("port"), cfg.database.port);
            cfg.database.database = stringValue(db.get("database"), cfg.database.database);
            cfg.database.username = stringValue(db.get("username"), cfg.database.username);
            cfg.database.password = stringValue(db.get("password"), cfg.database.password);
            cfg.database.tablePrefix = stringValue(db.get("tablePrefix"), cfg.database.tablePrefix);
            cfg.database.useSsl = booleanValue(db.get("useSsl"), cfg.database.useSsl);
        }

        Object messagesValue = map.get("messages");
        if (messagesValue instanceof Map<?, ?> messages) {
            cfg.messages.clear();
            for (Map.Entry<?, ?> entry : messages.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    cfg.messages.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }
        return cfg;
    }

    public synchronized void save() {
        if (config == null) config = new ModConfig();
        try {
            Files.createDirectories(root);
            backupLegacyConfigIfNeeded();

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);
            Yaml yaml = new Yaml(options);

            try (Writer writer = Files.newBufferedWriter(file)) {
                yaml.dump(toMap(config), writer);
            }
        } catch (IOException e) {
            logger.error("Failed to save ZNPCsPlus Fabric config", e);
        }
    }

    private void backupLegacyConfigIfNeeded() throws IOException {
        if (Files.notExists(file)) return;
        String existing = Files.readString(file);
        if (!existing.contains(LEGACY_CONFIG_TAG) && !existing.contains(LEGACY_DATABASE_TAG)) return;

        Path backup = root.resolve("config.yml.legacy-tagged-backup");
        if (Files.notExists(backup)) {
            Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static Map<String, Object> toMap(ModConfig cfg) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("viewDistance", cfg.viewDistance);
        root.put("lineSpacing", cfg.lineSpacing);
        root.put("checkForUpdates", cfg.checkForUpdates);
        root.put("debugEnabled", cfg.debugEnabled);
        root.put("storageType", cfg.storageType);

        ModConfig.Database db = cfg.database == null ? new ModConfig.Database() : cfg.database;
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("host", db.host);
        database.put("port", db.port);
        database.put("database", db.database);
        database.put("username", db.username);
        database.put("password", db.password);
        database.put("tablePrefix", db.tablePrefix);
        database.put("useSsl", db.useSsl);
        root.put("database", database);

        root.put("disableSkinFetcherWarnings", cfg.disableSkinFetcherWarnings);
        root.put("autoSaveInterval", cfg.autoSaveInterval);
        root.put("lookPropertyDistance", cfg.lookPropertyDistance);
        root.put("tabHideDelay", cfg.tabHideDelay);
        root.put("tabDisplayName", cfg.tabDisplayName);
        root.put("fakeEnforceSecureChat", cfg.fakeEnforceSecureChat);
        root.put("interactionDebounceMillis", cfg.interactionDebounceMillis);
        root.put("messages", cfg.messages == null ? new LinkedHashMap<>() : new LinkedHashMap<>(cfg.messages));
        return root;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    private static double doubleValue(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) {
            if ("true".equalsIgnoreCase(s)) return true;
            if ("false".equalsIgnoreCase(s)) return false;
        }
        return fallback;
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    public synchronized ModConfig reload() { return load(); }
    public ModConfig get() { return config == null ? load() : config; }
    public Path root() { return root; }
    public Path skinsDir() { return root.resolve("skins"); }
    public Path dataDir() { return root.resolve("data"); }
}
