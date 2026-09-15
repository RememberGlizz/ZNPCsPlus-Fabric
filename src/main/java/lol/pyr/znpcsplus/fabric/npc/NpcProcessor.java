package lol.pyr.znpcsplus.fabric.npc;

import lol.pyr.znpcsplus.fabric.config.ConfigManager;
import lol.pyr.znpcsplus.fabric.packet.PacketEngine;
import lol.pyr.znpcsplus.fabric.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcProcessor {
    private final MinecraftServer server;private final NpcRegistry registry;private final NpcTypeRegistry types;private final ConfigManager config;private final PacketEngine packets;
    private final Map<UUID,Long> knockback=new ConcurrentHashMap<>();private long tick;

    public NpcProcessor(MinecraftServer server,NpcRegistry registry,NpcTypeRegistry types,ConfigManager config,PacketEngine packets){this.server=server;this.registry=registry;this.types=types;this.config=config;this.packets=packets;}

    public void tick(){
        tick++; if(tick%3!=0){if(tick%20==0)refreshHolograms();return;}
        for(Npc npc:registry.all())process(npc);
        if(tick%60==0)knockback.entrySet().removeIf(e->e.getValue()<System.currentTimeMillis()-60000);
    }

    private void process(Npc npc){
        if(!npc.enabled){packets.hideAll(npc);return;}
        NpcType type=types.get(npc.type);if(type==null)return;
        double view=npc.number("view_distance",config.get().viewDistance),view2=view*view;
        double look=npc.number("look_distance",config.get().lookPropertyDistance),look2=look*look;
        LookType lookType;try{lookType=LookType.valueOf(npc.prop("look","fixed").toUpperCase(Locale.ROOT));}catch(Exception e){lookType=LookType.FIXED;}
        boolean lookReturn=npc.bool("look_return",false),permRequired=npc.bool("permission_required",false);
        String perm=npc.prop("premission_required_perm","znpcsplus.npc."+npc.id);
        ServerPlayerEntity closest=null;double closestDist=Double.MAX_VALUE;
        for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList()){
            boolean world=npc.world.equals(player.getServerWorld().getRegistryKey().getValue().toString());
            if(!world||(permRequired&&!PermissionBridge.has(player,perm))){if(npc.viewers.contains(player.getUuid()))packets.hide(player,npc);continue;}
            double d=npc.location.squaredDistanceTo(player.getX(),player.getY(),player.getZ());
            if(d>view2){if(npc.viewers.contains(player.getUuid()))packets.hide(player,npc);continue;}
            if(!npc.viewers.contains(player.getUuid()))packets.show(player,npc);
            if(lookType==LookType.PER_PLAYER){
                if(d<=look2){NpcLocation expected=npc.location.lookingAt(player.getEyePos(),npc.number("attribute_scale",1),type.eyeHeight(),0,0);packets.head(player,npc,expected.yaw(),expected.pitch());}
                else if(lookReturn)packets.head(player,npc,npc.location.yaw(),npc.location.pitch());
            }else if(d<closestDist){closestDist=d;closest=player;}
            processKnockback(npc,player,d);
        }
        if(lookType==LookType.CLOSEST_PLAYER){
            if(closest!=null&&closestDist<=look2){NpcLocation expected=npc.location.lookingAt(closest.getEyePos(),npc.number("attribute_scale",1),type.eyeHeight(),0,0);packets.headAll(npc,expected.yaw(),expected.pitch());}
            else if(lookReturn)packets.headAll(npc,npc.location.yaw(),npc.location.pitch());
        }else if(lookType==LookType.FIXED)packets.headAll(npc,npc.location.yaw(),npc.location.pitch());
    }

    private void processKnockback(Npc n,ServerPlayerEntity p,double distance){
        if(!n.bool("player_knockback",false))return;
        String exempt=n.prop("player_knockback_exempt_permission","");if(!exempt.isBlank()&&PermissionBridge.has(p,exempt))return;
        double max=n.number("player_knockback_distance",.4);if(distance>max*max)return;
        int cd=n.integer("player_knockback_cooldown",1500);long now=System.currentTimeMillis(),until=knockback.getOrDefault(p.getUuid(),0L);if(until>now)return;knockback.put(p.getUuid(),now+cd);
        double x=n.location.x()-p.getX(),z=n.location.z()-p.getZ(),angle=Math.atan2(z,x);
        double h=n.number("player_knockback_horizontal",.9),v=n.number("player_knockback_vertical",.4);
        Vec3d vel=p.getVelocity().add(-Math.cos(angle)*h,v,-Math.sin(angle)*h);p.setVelocity(vel);
    }

    private void refreshHolograms(){for(Npc n:registry.all())if(n.hologram.shouldRefresh()){n.hologram.markRefreshed();packets.refresh(n);}}
}
