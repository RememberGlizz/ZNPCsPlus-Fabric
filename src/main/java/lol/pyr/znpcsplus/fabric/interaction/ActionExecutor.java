package lol.pyr.znpcsplus.fabric.interaction;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import lol.pyr.znpcsplus.fabric.scheduler.TickScheduler;
import lol.pyr.znpcsplus.fabric.util.TextUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ActionExecutor {
    private static final Pattern URL_PATTERN=Pattern.compile("https?://\\S+",Pattern.CASE_INSENSITIVE);

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
                case MESSAGE -> p.sendMessage(clickableMessage(value,p),false);
                case PLAYER_CHAT -> server.getCommandManager().executeWithPrefix(p.getCommandSource(),"say "+value);
                case SWITCH_SERVER -> switchServer(p,value);
            }
        }catch(Throwable t){org.slf4j.LoggerFactory.getLogger("ZNPCsPlus-Fabric").error("NPC action failed: "+a.kind,t);}
    }

    /**
     * Sends normal NPC messages as native Minecraft text while automatically turning
     * http/https URLs into clickable OPEN_URL components. This avoids routing rich JSON
     * through /tellraw and Brigadier just to make a shop link clickable.
     */
    private static Text clickableMessage(String value,ServerPlayerEntity player){
        Matcher matcher=URL_PATTERN.matcher(value);
        if(!matcher.find())return TextUtil.vanilla(value,player);

        MutableText result=Text.empty();
        int last=0;
        do{
            if(matcher.start()>last)result.append(TextUtil.vanilla(value.substring(last,matcher.start()),player));
            String url=matcher.group();
            result.append(Text.literal(url).styled(style->style
                    .withColor(Formatting.AQUA)
                    .withUnderline(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,url))));
            last=matcher.end();
        }while(matcher.find());
        if(last<value.length())result.append(TextUtil.vanilla(value.substring(last),player));
        return result;
    }

    private void switchServer(ServerPlayerEntity p,String target)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){out.writeUTF("Connect");out.writeUTF(target);}
        PacketEvents.getAPI().getPlayerManager().sendPacket(p,new WrapperPlayServerPluginMessage("bungeecord:main",bytes.toByteArray()));
    }
    private static String stripSlash(String s){return s.startsWith("/")?s.substring(1):s;}
}
