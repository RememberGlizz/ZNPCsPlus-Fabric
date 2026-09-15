package lol.pyr.znpcsplus.fabric.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fabric equivalent of ZNPCsPlus' MainConfig + DatabaseConfig. */
public final class ModConfig {
    public int viewDistance = 32;
    public double lineSpacing = 0.3D;
    public boolean checkForUpdates = true;
    public boolean debugEnabled = false;
    public String storageType = "YAML";
    public Database database = new Database();
    public boolean disableSkinFetcherWarnings = false;
    public int autoSaveInterval = 300;
    public double lookPropertyDistance = 10.0D;
    public int tabHideDelay = 60;
    public String tabDisplayName = "ZNPC[{id}]";
    public boolean fakeEnforceSecureChat = false;
    public int interactionDebounceMillis = 100;
    public Map<String, String> messages = new LinkedHashMap<>();

    public static final class Database {
        public String host = "localhost";
        public int port = 3306;
        public String database = "znpcsplus";
        public String username = "root";
        public String password = "";
        public String tablePrefix = "znpcsplus_";
        public boolean useSsl = false;
    }
}
