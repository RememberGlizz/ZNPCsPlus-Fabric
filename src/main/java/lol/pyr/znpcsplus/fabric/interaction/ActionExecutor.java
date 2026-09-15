package lol.pyr.znpcsplus.fabric.interaction;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import lol.pyr.znpcsplus.fabric.scheduler.TickScheduler;
import lol.pyr.znpcsplus.fabric.util.TextUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActionExecutor {
    private final MinecraftServer server;
    private final TickScheduler scheduler;
    private final Map<String,Long> cooldowns=new ConcurrentHashMap<>();

    public ActionExecutor(MinecraftServer server,TickScheduler scheduler){this.server=server;this.scheduler=scheduler;}

    public boolean ready(ServerPlayerEntity player,NpcAction action){
        if(action.cooldown<=0)return true;
        String key=player.getUuid()+":"+action.id;
        long now=System.currentTimeMillis(),until=cooldowns.getOrDefault(key,0L);
        if(until>now)return false;
        cooldowns.put(key,now+action.cooldown);return true;
    }

    public void run(ServerPlayerEntity player,NpcAction action){
        scheduler.later(action.delay,()->execute(player,action));
    }

    private void execute(ServerPlayerEntity p,NpcAction a){
        if(p==null||p.isDisconnected())return;
        String value=TextUtil.placeholders(a.value,p);
        try{
            switch(a.kind){
                case CONSOLE -> server.getCommandManager().executeWithPrefix(server.getCommandSource(),stripSlash(value));
                case PLAYER_COMMAND -> server.getCommandManager().executeWithPrefix(p.getCommandSource(),stripSlash(value));
                case MESSAGE -> p.sendMessage(TextUtil.vanilla(value,p),false);
                case PLAYER_CHAT -> server.getCommandManager().executeWithPrefix(p.getCommandSource(),"say "+value);
                case SWITCH_SERVER -> switchServer(p,value);
            }
        }catch(Throwable t){org.slf4j.LoggerFactory.getLogger("ZNPCsPlus-Fabric").error("NPC action failed: "+a.kind,t);}
    }

    private void switchServer(ServerPlayerEntity p,String target)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){out.writeUTF("Connect");out.writeUTF(target);}
        PacketEvents.getAPI().getPlayerManager().sendPacket(p,new WrapperPlayServerPluginMessage("bungeecord:main",bytes.toByteArray()));
    }
    private static String stripSlash(String s){return s.startsWith("/")?s.substring(1):s;}
}
