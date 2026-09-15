package lol.pyr.znpcsplus.fabric.npc;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
public record NpcType(String name,EntityType packetType,double hologramOffset,float eyeHeight){}