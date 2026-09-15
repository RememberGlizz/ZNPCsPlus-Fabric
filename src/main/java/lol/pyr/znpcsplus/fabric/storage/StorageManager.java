package lol.pyr.znpcsplus.fabric.storage;

import lol.pyr.znpcsplus.fabric.config.*;
import lol.pyr.znpcsplus.fabric.npc.*;
import org.slf4j.Logger;

import java.nio.file.*;
import java.util.*;

public final class StorageManager {
    private final Logger logger;private final ConfigManager config;private final NpcRegistry registry;private NpcStorage storage;
    public StorageManager(Logger logger,ConfigManager config,NpcRegistry registry){this.logger=logger;this.config=config;this.registry=registry;}
    public synchronized void open()throws Exception{close();ModConfig c=config.get();String type=c.storageType.toUpperCase(Locale.ROOT);Path root=config.dataDir();Files.createDirectories(root);
      storage=switch(type){case"SQLITE"->SqlNpcStorage.sqlite(root.resolve("npcs.db").toString());case"MYSQL"->SqlNpcStorage.mysql(c.database.host,c.database.port,c.database.database,c.database.username,c.database.password,c.database.tablePrefix,c.database.useSsl);default->new YamlNpcStorage(root.resolve("npcs"));};}
    public synchronized int load()throws Exception{if(storage==null)open();Collection<Npc>npcs=storage.load();registry.clear();registry.registerAll(npcs);return npcs.size();}
    public synchronized void save()throws Exception{if(storage==null)open();storage.save(registry.all());}
    public synchronized void delete(String id){if(storage==null)return;try{storage.delete(id);}catch(Exception e){logger.error("Failed to delete NPC {} from storage",id,e);}}
    public synchronized void migrate(String to)throws Exception{NpcStorage target=create(to);target.save(registry.all());target.close();}
    private NpcStorage create(String type)throws Exception{ModConfig c=config.get();Path root=config.dataDir();return switch(type.toUpperCase(Locale.ROOT)){case"SQLITE"->SqlNpcStorage.sqlite(root.resolve("npcs.db").toString());case"MYSQL"->SqlNpcStorage.mysql(c.database.host,c.database.port,c.database.database,c.database.username,c.database.password,c.database.tablePrefix,c.database.useSsl);default->new YamlNpcStorage(root.resolve("npcs"));};}
    public synchronized void close(){if(storage!=null)try{storage.close();}catch(Exception e){logger.warn("Failed to close NPC storage",e);}storage=null;}
}
