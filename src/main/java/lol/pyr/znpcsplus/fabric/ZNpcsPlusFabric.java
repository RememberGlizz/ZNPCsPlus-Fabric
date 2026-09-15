package lol.pyr.znpcsplus.fabric;

import com.github.retrooper.packetevents.PacketEvents;
import lol.pyr.znpcsplus.fabric.command.NpcCommands;
import lol.pyr.znpcsplus.fabric.config.ConfigManager;
import lol.pyr.znpcsplus.fabric.interaction.*;
import lol.pyr.znpcsplus.fabric.npc.*;
import lol.pyr.znpcsplus.fabric.packet.PacketEngine;
import lol.pyr.znpcsplus.fabric.scheduler.TickScheduler;
import lol.pyr.znpcsplus.fabric.skin.SkinCache;
import lol.pyr.znpcsplus.fabric.storage.StorageManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ZNpcsPlusFabric implements ModInitializer {
    public static final String MOD_ID="znpcsplus_fabric";
    public static final Logger LOGGER=LoggerFactory.getLogger("ZNPCsPlus-Fabric");
    private static ConfigManager config;private static NpcRegistry registry;private static NpcTypeRegistry types;private static SkinCache skins;private static TickScheduler scheduler;
    private static volatile Runtime runtime;

    @Override public void onInitialize(){
        config=new ConfigManager(LOGGER);config.load();registry=new NpcRegistry();types=new NpcTypeRegistry();skins=new SkinCache(LOGGER,config);scheduler=new TickScheduler();

        ServerLifecycleEvents.SERVER_STARTING.register(this::start);
        ServerTickEvents.END_SERVER_TICK.register(server->{Runtime r=runtime;if(r==null||r.server!=server)return;scheduler.tick(server);r.processor.tick();r.ticks++;int sec=config.get().autoSaveInterval;if(sec>0&&r.ticks%(sec*20L)==0)try{r.storage.save();}catch(Exception e){LOGGER.error("Auto-save failed",e);}});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{for(Npc n:registry.all()){n.viewers.remove(handler.player.getUuid());n.perPlayerLook.remove(handler.player.getUuid());}});
        ServerLifecycleEvents.SERVER_STOPPING.register(this::stop);
        LOGGER.info("ZNPCsPlus Fabric initialized. Targeting Minecraft 1.21.1.");
    }

    private void start(MinecraftServer server){
        try{
            StorageManager storage=new StorageManager(LOGGER,config,registry);storage.open();int count=storage.load();
            PacketEngine packets=new PacketEngine(server,config,registry,types,skins,scheduler);
            ActionExecutor actionExecutor=new ActionExecutor(server,scheduler);
            InteractionListener interaction=new InteractionListener(server,registry,actionExecutor,config);
            PacketEvents.getAPI().getEventManager().registerListener(interaction);
            NpcProcessor processor=new NpcProcessor(server,registry,types,config,packets);
            runtime=new Runtime(server,storage,packets,processor,interaction);
            new NpcCommands(registry,types,packets,config,storage,skins).register(server.getCommandManager().getDispatcher());
            LOGGER.info("Loaded {} NPCs using {} storage; registered {} entity types.",count,config.get().storageType,types.all().size());
        }catch(Throwable t){LOGGER.error("Failed to start ZNPCsPlus Fabric",t);}
    }

    private void stop(MinecraftServer server){
        Runtime r=runtime;if(r==null)return;
        try{r.storage.save();}catch(Exception e){LOGGER.error("Final NPC save failed",e);}
        try{PacketEvents.getAPI().getEventManager().unregisterListener(r.interaction);}catch(Throwable ignored){}
        r.storage.close();scheduler.clear();runtime=null;skins.close();
    }

    public static NpcRegistry registry(){return registry;}
    public static NpcTypeRegistry types(){return types;}
    public static ConfigManager config(){return config;}
    public static Runtime runtime(){return runtime;}

    public static final class Runtime{
        public final MinecraftServer server;public final StorageManager storage;public final PacketEngine packets;public final NpcProcessor processor;public final InteractionListener interaction;private long ticks;
        private Runtime(MinecraftServer s,StorageManager st,PacketEngine p,NpcProcessor pr,InteractionListener i){server=s;storage=st;packets=p;processor=pr;interaction=i;}
    }
}
