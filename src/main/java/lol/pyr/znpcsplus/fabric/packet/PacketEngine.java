package lol.pyr.znpcsplus.fabric.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import lol.pyr.znpcsplus.fabric.config.ConfigManager;
import lol.pyr.znpcsplus.fabric.api.NpcEvents;
import lol.pyr.znpcsplus.fabric.hologram.HologramLine;
import lol.pyr.znpcsplus.fabric.hologram.ItemCodec;
import lol.pyr.znpcsplus.fabric.npc.*;
import lol.pyr.znpcsplus.fabric.property.PropertyRenderer;
import lol.pyr.znpcsplus.fabric.scheduler.TickScheduler;
import lol.pyr.znpcsplus.fabric.skin.SkinCache;
import lol.pyr.znpcsplus.fabric.skin.SkinData;
import lol.pyr.znpcsplus.fabric.util.NpcLocation;
import lol.pyr.znpcsplus.fabric.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class PacketEngine {
    private final MinecraftServer server;
    private final ConfigManager config;
    private final NpcRegistry registry;
    private final NpcTypeRegistry types;
    private final SkinCache skins;
    private final TickScheduler scheduler;
    private final PropertyRenderer properties = new PropertyRenderer();

    public PacketEngine(MinecraftServer server, ConfigManager config, NpcRegistry registry, NpcTypeRegistry types, SkinCache skins, TickScheduler scheduler) {
        this.server=server; this.config=config; this.registry=registry; this.types=types; this.skins=skins; this.scheduler=scheduler;
    }

    public void show(ServerPlayerEntity viewer, Npc npc) {
        if(!npc.enabled || npc.viewers.contains(viewer.getUuid())) return;
        if(!NpcEvents.SPAWN.invoker().onSpawn(viewer,npc)) return;
        npc.viewers.add(viewer.getUuid());
        npc.perPlayerLook.put(viewer.getUuid(), new float[]{npc.location.yaw(), npc.location.pitch()});
        NpcType type=types.get(npc.type); if(type==null){npc.viewers.remove(viewer.getUuid());return;}
        if("player".equals(npc.type)) {
            CompletableFuture<SkinData> future = npc.skin == null ? CompletableFuture.completedFuture(null) : npc.skin.resolve(viewer,skins);
            future.exceptionally(x->null).thenAccept(skin -> server.execute(() -> {
                if(!npc.viewers.contains(viewer.getUuid()) || viewer.isDisconnected()) return;
                spawnPlayer(viewer,npc,type,skin);
                spawnHologram(viewer,npc,type);
            }));
        } else {
            spawnEntity(viewer,npc,type.packetType(),npc.location,npc.entityId,npc.uuid);
            postSpawn(viewer,npc);
            spawnHologram(viewer,npc,type);
        }
    }

    private void spawnPlayer(ServerPlayerEntity viewer,Npc npc,NpcType type,SkinData skin) {
        UserProfile profile=new UserProfile(npc.uuid,Integer.toString(npc.entityId));
        if(skin!=null)skin.apply(profile);
        String tab=config.get().tabDisplayName.replace("{id}",Integer.toString(npc.entityId)).replace("{name}",npc.prop("display_name",""));
        var info=new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profile,false,1,GameMode.CREATIVE,Component.text(tab),null);
        send(viewer,new WrapperPlayServerPlayerInfoUpdate(EnumSet.of(
                WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME),info));
        createTeam(viewer,npc);
        NpcLocation l=npc.location;
        send(viewer,new WrapperPlayServerSpawnEntity(npc.entityId,Optional.of(npc.uuid),EntityTypes.PLAYER,l.packetVector(),l.pitch(),l.yaw(),l.yaw(),0,Optional.of(new Vector3d())));
        send(viewer,new WrapperPlayServerEntityHeadLook(npc.entityId,l.yaw()));
        sendMetaEquipmentAttributes(viewer,npc);
        scheduler.later(config.get().tabHideDelay,()->{
            if(npc.viewers.contains(viewer.getUuid())&&!npc.bool("always_visible_in_tab",false))
                send(viewer,new WrapperPlayServerPlayerInfoRemove(npc.uuid));
        });
    }

    private void spawnEntity(ServerPlayerEntity viewer,Npc npc,EntityType type,NpcLocation l,int entityId,UUID uuid) {
        send(viewer,new WrapperPlayServerSpawnEntity(entityId,Optional.of(uuid),type,l.packetVector(),l.pitch(),l.yaw(),l.yaw(),0,Optional.of(new Vector3d())));
        send(viewer,new WrapperPlayServerEntityHeadLook(entityId,l.yaw()));
        if(entityId==npc.entityId)postSpawn(viewer,npc);
    }

    private void postSpawn(ServerPlayerEntity viewer,Npc npc){
        createTeam(viewer,npc); sendMetaEquipmentAttributes(viewer,npc);
    }

    public void hide(ServerPlayerEntity viewer,Npc npc) {
        if(!npc.viewers.contains(viewer.getUuid())) return;
        if(!NpcEvents.DESPAWN.invoker().onDespawn(viewer,npc)) return;
        npc.viewers.remove(viewer.getUuid()); npc.perPlayerLook.remove(viewer.getUuid());
        send(viewer,new WrapperPlayServerDestroyEntities(npc.entityId));
        for(int id:npc.hologramEntityIds)send(viewer,new WrapperPlayServerDestroyEntities(id));
        if("player".equals(npc.type))send(viewer,new WrapperPlayServerPlayerInfoRemove(npc.uuid));
        removeTeam(viewer,npc);
    }

    public void hideAll(Npc npc){
        for(UUID id:new HashSet<>(npc.viewers)){
            ServerPlayerEntity p=server.getPlayerManager().getPlayer(id);
            if(p!=null)hide(p,npc); else npc.viewers.remove(id);
        }
    }

    public void respawn(Npc npc){
        List<ServerPlayerEntity> viewers=new ArrayList<>();
        for(UUID id:new HashSet<>(npc.viewers)){ServerPlayerEntity p=server.getPlayerManager().getPlayer(id);if(p!=null)viewers.add(p);}
        for(ServerPlayerEntity p:viewers)hide(p,npc);
        for(ServerPlayerEntity p:viewers)show(p,npc);
    }

    public void refresh(Npc npc){
        for(UUID id:npc.viewers){ServerPlayerEntity p=server.getPlayerManager().getPlayer(id);if(p!=null){sendMetaEquipmentAttributes(p,npc);refreshHologram(p,npc);}}
    }

    public void teleport(Npc npc){
        for(UUID id:npc.viewers){ServerPlayerEntity p=server.getPlayerManager().getPlayer(id);if(p==null)continue;NpcLocation l=npc.location;
            send(p,new WrapperPlayServerEntityTeleport(npc.entityId,l.packetVector(),l.yaw(),l.pitch(),true));
            send(p,new WrapperPlayServerEntityHeadLook(npc.entityId,l.yaw()));
            respawnHologramFor(p,npc);
        }
    }

    public void head(ServerPlayerEntity viewer,Npc npc,float yaw,float pitch){
        send(viewer,new WrapperPlayServerEntityHeadLook(npc.entityId,yaw));
        send(viewer,new WrapperPlayServerEntityRotation(npc.entityId,yaw,pitch,true));
        npc.perPlayerLook.put(viewer.getUuid(),new float[]{yaw,pitch});
    }

    public void headAll(Npc npc,float yaw,float pitch){
        for(UUID id:npc.viewers){ServerPlayerEntity p=server.getPlayerManager().getPlayer(id);if(p!=null)head(p,npc,yaw,pitch);}
    }

    public void swing(Npc npc,boolean offhand){
        for(UUID id:npc.viewers){ServerPlayerEntity p=server.getPlayerManager().getPlayer(id);if(p!=null)send(p,new WrapperPlayServerEntityAnimation(npc.entityId,offhand?WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_OFF_HAND:WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM));}
    }

    private void sendMetaEquipmentAttributes(ServerPlayerEntity viewer,Npc npc){
        List<EntityData<?>> meta=properties.metadata(npc,viewer);if(!meta.isEmpty())send(viewer,new WrapperPlayServerEntityMetadata(npc.entityId,meta));
        var eq=properties.equipment(npc);if(!eq.isEmpty())send(viewer,new WrapperPlayServerEntityEquipment(npc.entityId,eq));
        var attrs=properties.attributes(npc);if(!attrs.isEmpty())send(viewer,new WrapperPlayServerUpdateAttributes(npc.entityId,attrs));
    }

    private void createTeam(ServerPlayerEntity viewer,Npc npc){
        NamedTextColor color=parseColor(npc.prop("glow",""));
        String name="npc_"+Integer.toUnsignedString(npc.entityId,36);if(name.length()>16)name=name.substring(0,16);
        send(viewer,new WrapperPlayServerTeams(name,WrapperPlayServerTeams.TeamMode.CREATE,
            new WrapperPlayServerTeams.ScoreBoardTeamInfo(Component.text(" "),null,null,
                WrapperPlayServerTeams.NameTagVisibility.NEVER,WrapperPlayServerTeams.CollisionRule.NEVER,
                color==null?NamedTextColor.WHITE:color,WrapperPlayServerTeams.OptionData.NONE)));
        send(viewer,new WrapperPlayServerTeams(name,WrapperPlayServerTeams.TeamMode.ADD_ENTITIES,
            (WrapperPlayServerTeams.ScoreBoardTeamInfo)null,
            "player".equals(npc.type)?Integer.toString(npc.entityId):npc.uuid.toString()));
    }

    private void removeTeam(ServerPlayerEntity viewer,Npc npc){
        String name="npc_"+Integer.toUnsignedString(npc.entityId,36);if(name.length()>16)name=name.substring(0,16);
        send(viewer,new WrapperPlayServerTeams(name,WrapperPlayServerTeams.TeamMode.REMOVE,(WrapperPlayServerTeams.ScoreBoardTeamInfo)null));
    }

    private static NamedTextColor parseColor(String s){
        if(s==null||s.isBlank())return null;
        return NamedTextColor.NAMES.value(s.toLowerCase(Locale.ROOT));
    }

    private void spawnHologram(ServerPlayerEntity viewer,Npc npc,NpcType type){
        ensureHologramIds(npc);
        double y=npc.location.y()+type.hologramOffset()+(npc.hologram.lines.size()-1)*config.get().lineSpacing+npc.hologram.offset;
        for(int i=0;i<npc.hologram.lines.size();i++){
            HologramLine line=npc.hologram.lines.get(i);int id=npc.hologramEntityIds.get(i);
            spawnHologramLine(viewer,npc,line,id,i,y);
            y-=config.get().lineSpacing;
        }
    }

    private void ensureHologramIds(Npc npc){
        while(npc.hologramEntityIds.size()<npc.hologram.lines.size())npc.hologramEntityIds.add(registry.nextPacketId());
        while(npc.hologramEntityIds.size()>npc.hologram.lines.size())npc.hologramEntityIds.remove(npc.hologramEntityIds.size()-1);
    }

    private void spawnHologramLine(ServerPlayerEntity viewer,Npc npc,HologramLine line,int id,int index,double y){
        UUID uuid=UUID.nameUUIDFromBytes((npc.uuid+":"+index).getBytes(StandardCharsets.UTF_8));
        if(line.kind==HologramLine.Kind.TEXT){
            if("%blank%".equals(line.value))return;
            NpcLocation l=new NpcLocation(npc.location.x(),y,npc.location.z(),0,0);
            spawnRaw(viewer,id,uuid,EntityTypes.ARMOR_STAND,l);
            List<EntityData<?>> meta=new ArrayList<>();
            meta.add(new EntityData<>(0,EntityDataTypes.BYTE,(byte)0x20));
            meta.add(new EntityData<>(2,EntityDataTypes.OPTIONAL_ADV_COMPONENT,Optional.of(TextUtil.component(line.value,viewer))));
            meta.add(new EntityData<>(3,EntityDataTypes.BOOLEAN,true));
            send(viewer,new WrapperPlayServerEntityMetadata(id,meta));
        }else{
            NpcLocation l=new NpcLocation(npc.location.x(),y+2.05,npc.location.z(),0,0);
            spawnRaw(viewer,id,uuid,EntityTypes.ITEM,l);
            List<EntityData<?>> meta=List.of(
                    new EntityData<>(5,EntityDataTypes.BOOLEAN,true),
                    new EntityData<>(8,EntityDataTypes.ITEMSTACK,ItemCodec.decode(line.value)));
            send(viewer,new WrapperPlayServerEntityMetadata(id,meta));
        }
    }

    private void spawnRaw(ServerPlayerEntity viewer,int id,UUID uuid,EntityType type,NpcLocation l){
        send(viewer,new WrapperPlayServerSpawnEntity(id,Optional.of(uuid),type,l.packetVector(),l.pitch(),l.yaw(),l.yaw(),0,Optional.of(new Vector3d())));
    }

    private void refreshHologram(ServerPlayerEntity viewer,Npc npc){
        ensureHologramIds(npc);
        for(int i=0;i<npc.hologram.lines.size();i++){
            HologramLine line=npc.hologram.lines.get(i);int id=npc.hologramEntityIds.get(i);
            if(line.kind==HologramLine.Kind.TEXT&&! "%blank%".equals(line.value))
                send(viewer,new WrapperPlayServerEntityMetadata(id,List.of(
                        new EntityData<>(0,EntityDataTypes.BYTE,(byte)0x20),
                        new EntityData<>(2,EntityDataTypes.OPTIONAL_ADV_COMPONENT,Optional.of(TextUtil.component(line.value,viewer))),
                        new EntityData<>(3,EntityDataTypes.BOOLEAN,true))));
            else if(line.kind==HologramLine.Kind.ITEM)
                send(viewer,new WrapperPlayServerEntityMetadata(id,List.of(new EntityData<>(5,EntityDataTypes.BOOLEAN,true),new EntityData<>(8,EntityDataTypes.ITEMSTACK,ItemCodec.decode(line.value)))));
        }
    }

    private void respawnHologramFor(ServerPlayerEntity viewer,Npc npc){
        for(int id:npc.hologramEntityIds)send(viewer,new WrapperPlayServerDestroyEntities(id));
        NpcType t=types.get(npc.type);if(t!=null)spawnHologram(viewer,npc,t);
    }

    private void send(ServerPlayerEntity player,PacketWrapper<?> packet){
        if(player==null||player.isDisconnected())return;
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,packet);
    }
}
