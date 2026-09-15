package lol.pyr.znpcsplus.fabric.api;
import lol.pyr.znpcsplus.fabric.ZNpcsPlusFabric;import lol.pyr.znpcsplus.fabric.npc.*;
public final class ZNpcsPlusApi{private ZNpcsPlusApi(){}public static NpcRegistry npcs(){return ZNpcsPlusFabric.registry();}public static NpcTypeRegistry types(){return ZNpcsPlusFabric.types();}}
