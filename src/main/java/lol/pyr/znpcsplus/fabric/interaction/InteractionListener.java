package lol.pyr.znpcsplus.fabric.interaction;

import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import lol.pyr.znpcsplus.fabric.config.ConfigManager;
import lol.pyr.znpcsplus.fabric.api.NpcEvents;
import lol.pyr.znpcsplus.fabric.npc.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class InteractionListener implements PacketListener {
    private final MinecraftServer server;private final NpcRegistry registry;private final ActionExecutor actions;private final ConfigManager config;
    public InteractionListener(MinecraftServer server,NpcRegistry registry,ActionExecutor actions,ConfigManager config){this.server=server;this.registry=registry;this.actions=actions;this.config=config;}
    @Override public void onPacketReceive(PacketReceiveEvent event){
        if(event.getPacketType()!=PacketType.Play.Client.INTERACT_ENTITY)return;
        if(!(event.getPlayer() instanceof ServerPlayerEntity player))return;
        WrapperPlayClientInteractEntity packet=new WrapperPlayClientInteractEntity(event);
        Npc npc=registry.byEntityId(packet.getEntityId());if(npc==null||!npc.enabled||!npc.viewers.contains(player.getUuid()))return;
        long now=System.currentTimeMillis();Long last=npc.interactionDebounce.put(player.getUuid(),now);if(last!=null&&now-last<config.get().interactionDebounceMillis)return;
        InteractionType type=switch(packet.getAction()){case ATTACK->InteractionType.LEFT_CLICK;case INTERACT,INTERACT_AT->InteractionType.RIGHT_CLICK;};
        if(!NpcEvents.INTERACT.invoker().onInteract(player,npc,type))return;
        for(NpcAction action:npc.actions){
            if(action.interactionType!=InteractionType.ANY_CLICK&&action.interactionType!=type)continue;
            if(!actions.ready(player,action))continue;
            actions.run(player,action);
        }
    }
}
