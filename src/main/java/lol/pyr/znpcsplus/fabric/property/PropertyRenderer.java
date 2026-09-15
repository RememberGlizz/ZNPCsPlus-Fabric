package lol.pyr.znpcsplus.fabric.property;

import com.github.retrooper.packetevents.protocol.entity.data.*;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.entity.villager.VillagerData;
import com.github.retrooper.packetevents.protocol.entity.villager.profession.VillagerProfessions;
import com.github.retrooper.packetevents.protocol.entity.villager.type.VillagerTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import lol.pyr.znpcsplus.fabric.hologram.ItemCodec;
import lol.pyr.znpcsplus.fabric.npc.Npc;
import lol.pyr.znpcsplus.fabric.util.TextUtil;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;

public final class PropertyRenderer {
    public List<EntityData<?>> metadata(Npc npc, ServerPlayerEntity viewer){
        Map<Integer,EntityData<?>> m=new TreeMap<>();
        byte flags=0;
        if(npc.bool("fire",false))flags|=0x01;
        if(npc.bool("invisible",false))flags|=0x20;
        if(!npc.prop("glow","").isBlank())flags|=0x40;
        if(flags!=0) put(m,0,EntityDataTypes.BYTE,flags);

        if(npc.properties.containsKey("name")){
            put(m,2,EntityDataTypes.OPTIONAL_ADV_COMPONENT,Optional.of(TextUtil.component(npc.prop("name",""),viewer)));
            put(m,3,EntityDataTypes.BOOLEAN,true);
        }
        if(npc.properties.containsKey("silent"))put(m,4,EntityDataTypes.BOOLEAN,npc.bool("silent",false));
        if(npc.properties.containsKey("pose")){
            try{put(m,6,EntityDataTypes.ENTITY_POSE,EntityPose.valueOf(npc.prop("pose","standing").toUpperCase(Locale.ROOT)));}catch(Exception ignored){}
        }
        if(npc.properties.containsKey("shaking"))put(m,7,EntityDataTypes.INT,npc.bool("shaking",false)?140:0);
        if(npc.properties.containsKey("health"))put(m,9,EntityDataTypes.FLOAT,npc.decimal("health",20f));
        if(npc.properties.containsKey("potion_color"))put(m,10,EntityDataTypes.INT,parseColor(npc.prop("potion_color","0")));
        if(npc.properties.containsKey("potion_ambient"))put(m,11,EntityDataTypes.BOOLEAN,npc.bool("potion_ambient",false));

        String t=npc.type;
        switch(t){
            case "player" -> player(npc,m);
            case "armor_stand" -> armorStand(npc,m);
            case "bat" -> bool(npc,m,"hanging",16,true);
            case "blaze" -> bit(npc,m,"blaze_on_fire",16,0x01);
            case "creeper" -> { integer(npc,m,"creeper_state",16,-1); bool(npc,m,"creeper_charged",17,false); }
            case "end_crystal" -> bool(npc,m,"show_base",9,true);
            case "ghast" -> bool(npc,m,"attacking",16,false);
            case "guardian","elder_guardian" -> bool(npc,m,"is_retracting_spikes",16,false);
            case "horse" -> horse(npc,m);
            case "magma_cube","slime","phantom" -> integer(npc,m,"size",16,1);
            case "mooshroom" -> string(npc,m,"mooshroom_variant",17,"red");
            case "pig" -> bool(npc,m,"pig_saddled",17,false);
            case "rabbit" -> integer(npc,m,"rabbit_type",17,enumOrdinal(npc.prop("rabbit_type","brown"),"brown","white","black","black_and_white","gold","salt_and_pepper","killer_bunny"));
            case "sheep" -> sheep(npc,m);
            case "snow_golem" -> {if(npc.properties.containsKey("derpy_snowgolem"))put(m,16,EntityDataTypes.BYTE,(byte)(npc.bool("derpy_snowgolem",false)?0:0x10));}
            case "villager" -> villager(npc,m);
            case "wither" -> integer(npc,m,"invulnerable_time",19,0);
            case "wolf" -> wolf(npc,m);
            case "shulker" -> { integer(npc,m,"shield_height",17,0); integerByte(npc,m,"shulker_color",18,0); }
            case "polar_bear" -> bool(npc,m,"polar_bear_standing",17,false);
            case "evoker","illusioner" -> integerByte(npc,m,"spell",17,0);
            case "llama","trader_llama" -> integer(npc,m,"llama_variant",20,0);
            case "parrot" -> integer(npc,m,"parrot_variant",19,0);
            case "pufferfish" -> integer(npc,m,"puff_state",17,0);
            case "tropical_fish" -> integer(npc,m,"tropical_fish_variant",17,0);
            case "cat" -> cat(npc,m);
            case "fox" -> fox(npc,m);
            case "panda" -> panda(npc,m);
            case "bee" -> bee(npc,m);
            case "hoglin" -> bool(npc,m,"hoglin_immune_to_zombification",17,false);
            case "piglin" -> { bool(npc,m,"piglin_immune_to_zombification",16,false);bool(npc,m,"piglin_baby",17,false);bool(npc,m,"piglin_charging_crossbow",18,false);bool(npc,m,"piglin_dancing",19,false);}
            case "pillager" -> bool(npc,m,"pillager_charging",17,false);
            case "vindicator" -> bool(npc,m,"celebrating",16,false);
            case "axolotl" -> {integer(npc,m,"axolotl_variant",17,0);bool(npc,m,"playing_dead",18,false);}
            case "goat" -> {bool(npc,m,"has_left_horn",18,true);bool(npc,m,"has_right_horn",19,true);}
            case "frog" -> integer(npc,m,"frog_variant",17,0);
            case "warden" -> integer(npc,m,"warden_anger",16,Math.max(0,Math.min(150,npc.integer("warden_anger",0))));
            case "camel" -> {bool(npc,m,"bashing",18,false);}
            case "sniffer" -> integer(npc,m,"sniffer_state",17,0);
            case "armadillo" -> integer(npc,m,"armadillo_state",17,0);
            case "bogged" -> bool(npc,m,"bogged_sheared",16,false);
        }
        if(isAgeable(t)&&npc.properties.containsKey("baby"))put(m,16,EntityDataTypes.BOOLEAN,npc.bool("baby",false));
        return new ArrayList<>(m.values());
    }

    public List<Equipment> equipment(Npc n){
        List<Equipment> out=new ArrayList<>();
        eq(n,out,"helmet",EquipmentSlot.HELMET);eq(n,out,"chestplate",EquipmentSlot.CHEST_PLATE);eq(n,out,"leggings",EquipmentSlot.LEGGINGS);
        eq(n,out,"boots",EquipmentSlot.BOOTS);eq(n,out,"hand",EquipmentSlot.MAIN_HAND);eq(n,out,"offhand",EquipmentSlot.OFF_HAND);eq(n,out,"body",EquipmentSlot.BODY);
        return out;
    }
    public List<WrapperPlayServerUpdateAttributes.Property> attributes(Npc n){
        List<WrapperPlayServerUpdateAttributes.Property> out=new ArrayList<>();
        if(n.properties.containsKey("attribute_max_health"))out.add(new WrapperPlayServerUpdateAttributes.Property(Attributes.MAX_HEALTH,Attributes.MAX_HEALTH.sanitizeValue(n.number("attribute_max_health",20)),Collections.emptyList()));
        if(n.properties.containsKey("attribute_scale"))out.add(new WrapperPlayServerUpdateAttributes.Property(Attributes.SCALE,Attributes.SCALE.sanitizeValue(n.number("attribute_scale",1)),Collections.emptyList()));
        return out;
    }

    private void player(Npc n,Map<Integer,EntityData<?>>m){
        byte b=0;if(n.bool("skin_cape",true))b|=1;if(n.bool("skin_jacket",true))b|=2;if(n.bool("skin_left_sleeve",true))b|=4;if(n.bool("skin_right_sleeve",true))b|=8;
        if(n.bool("skin_left_leg",true))b|=16;if(n.bool("skin_right_leg",true))b|=32;if(n.bool("skin_hat",true))b|=64;put(m,17,EntityDataTypes.BYTE,b);
    }
    private void armorStand(Npc n,Map<Integer,EntityData<?>>m){byte b=0;if(n.bool("small",false))b|=1;if(n.bool("arms",false))b|=4;if(!n.bool("base_plate",true))b|=8;put(m,15,EntityDataTypes.BYTE,b);}
    private void horse(Npc n,Map<Integer,EntityData<?>>m){byte b=0;if(n.bool("is_tame",false))b|=2;if(n.bool("is_eating",false))b|=0x10;if(n.bool("is_rearing",false))b|=0x20;if(n.bool("has_mouth_open",false))b|=0x40;put(m,17,EntityDataTypes.BYTE,b);integer(n,m,"horse_variant",18,0);}
    private void sheep(Npc n,Map<Integer,EntityData<?>>m){int color=n.integer("sheep_color",0)&15;byte b=(byte)color;if(n.bool("sheep_sheared",false))b|=0x10;put(m,17,EntityDataTypes.BYTE,b);}
    private void villager(Npc n,Map<Integer,EntityData<?>>m){
        try{int type=n.integer("villager_type",0),prof=n.integer("villager_profession",0),level=Math.max(1,n.integer("villager_level",1));
            put(m,18,EntityDataTypes.VILLAGER_DATA,new VillagerData(VillagerTypes.getById(type),VillagerProfessions.getById(prof),level));}catch(Throwable ignored){}
    }
    private void wolf(Npc n,Map<Integer,EntityData<?>>m){bool(n,m,"wolf_begging",19,false);integer(n,m,"wolf_collar",20,14);integer(n,m,"wolf_angry",21,n.bool("wolf_angry",false)?1:0);integer(n,m,"wolf_variant",22,0);}
    private void cat(Npc n,Map<Integer,EntityData<?>>m){integer(n,m,"cat_variant",19,0);bool(n,m,"cat_laying",20,false);bool(n,m,"cat_relaxed",21,false);integer(n,m,"cat_collar",22,14);}
    private void fox(Npc n,Map<Integer,EntityData<?>>m){integer(n,m,"fox_variant",17,0);byte b=0;if(n.bool("fox_sitting",false))b|=1;if(n.bool("fox_crouching",false))b|=4;if(n.bool("fox_sleeping",false))b|=0x20;if(n.bool("fox_faceplanted",false))b|=0x40;put(m,18,EntityDataTypes.BYTE,b);}
    private void panda(Npc n,Map<Integer,EntityData<?>>m){integerByte(n,m,"panda_main_gene",20,0);integerByte(n,m,"panda_hidden_gene",21,0);byte b=0;if(n.bool("panda_sneezing",false))b|=2;if(n.bool("panda_rolling",false))b|=4;if(n.bool("panda_sitting",false))b|=8;if(n.bool("panda_on_back",false))b|=16;put(m,22,EntityDataTypes.BYTE,b);}
    private void bee(Npc n,Map<Integer,EntityData<?>>m){byte b=0;if(n.bool("has_nectar",false))b|=8;put(m,17,EntityDataTypes.BYTE,b);integer(n,m,"angry",18,n.bool("angry",false)?1:0);}
    private static boolean isAgeable(String t){return Set.of("cow","chicken","pig","sheep","villager","zombie","husk","drowned","zombie_villager","hoglin","zoglin","bee","goat").contains(t);}

    private void eq(Npc n,List<Equipment>o,String k,EquipmentSlot s){if(n.properties.containsKey(k))o.add(new Equipment(s,ItemCodec.decode(n.prop(k,"air"))));}
    private static <T> void put(Map<Integer,EntityData<?>>m,int i,EntityDataType<T>t,T v){m.put(i,new EntityData<>(i,t,v));}
    private static void bool(Npc n,Map<Integer,EntityData<?>>m,String k,int i,boolean d){if(n.properties.containsKey(k))put(m,i,EntityDataTypes.BOOLEAN,n.bool(k,d));}
    private static void integer(Npc n,Map<Integer,EntityData<?>>m,String k,int i,int d){if(n.properties.containsKey(k))put(m,i,EntityDataTypes.INT,n.integer(k,d));}
    private static void integerByte(Npc n,Map<Integer,EntityData<?>>m,String k,int i,int d){if(n.properties.containsKey(k))put(m,i,EntityDataTypes.BYTE,(byte)n.integer(k,d));}
    private static void string(Npc n,Map<Integer,EntityData<?>>m,String k,int i,String d){if(n.properties.containsKey(k))put(m,i,EntityDataTypes.STRING,n.prop(k,d));}
    private static void bit(Npc n,Map<Integer,EntityData<?>>m,String k,int i,int bit){if(n.bool(k,false)){byte old=m.containsKey(i)&&m.get(i).getValue() instanceof Number x?x.byteValue():0;put(m,i,EntityDataTypes.BYTE,(byte)(old|bit));}}
    private static int parseColor(String s){try{return Integer.decode(s);}catch(Exception e){try{return Integer.parseInt(s.replace("#",""),16);}catch(Exception ignored){return 0;}}}
    private static int enumOrdinal(String s,String... names){for(int i=0;i<names.length;i++)if(names[i].equalsIgnoreCase(s))return i;try{return Integer.parseInt(s);}catch(Exception e){return 0;}}
}
