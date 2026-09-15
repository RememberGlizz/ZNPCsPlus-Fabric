package lol.pyr.znpcsplus.fabric.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.introspector.BeanAccess;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
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
            LoaderOptions opts = new LoaderOptions();
            Constructor constructor = new Constructor(ModConfig.class, opts);
            Yaml yaml = new Yaml(constructor);
            yaml.setBeanAccess(BeanAccess.FIELD);
            try (Reader reader = Files.newBufferedReader(file)) {
                ModConfig loaded = yaml.load(reader);
                config = loaded == null ? new ModConfig() : loaded;
            }
        } catch (Exception e) {
            logger.error("Failed to load ZNPCsPlus Fabric config; defaults will be used", e);
            config = new ModConfig();
        }
        return config;
    }

    public synchronized void save() {
        if (config == null) config = new ModConfig();
        try {
            Files.createDirectories(root);
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);
            Yaml yaml = new Yaml(options);
            yaml.setBeanAccess(BeanAccess.FIELD);
            try (Writer writer = Files.newBufferedWriter(file)) {
                yaml.dump(config, writer);
            }
        } catch (IOException e) {
            logger.error("Failed to save ZNPCsPlus Fabric config", e);
        }
    }

    public synchronized ModConfig reload() { return load(); }
    public ModConfig get() { return config == null ? load() : config; }
    public Path root() { return root; }
    public Path skinsDir() { return root.resolve("skins"); }
    public Path dataDir() { return root.resolve("data"); }
}
