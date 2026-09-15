package lol.pyr.znpcsplus.fabric.api;
import net.fabricmc.fabric.api.event.Event;import net.fabricmc.fabric.api.event.EventFactory;import net.minecraft.server.network.ServerPlayerEntity;import lol.pyr.znpcsplus.fabric.npc.Npc;import lol.pyr.znpcsplus.fabric.interaction.InteractionType;
public final class NpcEvents{private NpcEvents(){}
 @FunctionalInterface public interface Spawn{boolean onSpawn(ServerPlayerEntity viewer,Npc npc);}
 @FunctionalInterface public interface Despawn{boolean onDespawn(ServerPlayerEntity viewer,Npc npc);}
 @FunctionalInterface public interface Interact{boolean onInteract(ServerPlayerEntity player,Npc npc,InteractionType type);}
 public static final Event<Spawn> SPAWN=EventFactory.createArrayBacked(Spawn.class,ls->(p,n)->{for(Spawn l:ls)if(!l.onSpawn(p,n))return false;return true;});
 public static final Event<Despawn> DESPAWN=EventFactory.createArrayBacked(Despawn.class,ls->(p,n)->{for(Despawn l:ls)if(!l.onDespawn(p,n))return false;return true;});
 public static final Event<Interact> INTERACT=EventFactory.createArrayBacked(Interact.class,ls->(p,n,t)->{for(Interact l:ls)if(!l.onInteract(p,n,t))return false;return true;});
}
