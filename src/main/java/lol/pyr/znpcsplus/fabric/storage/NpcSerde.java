package lol.pyr.znpcsplus.fabric.storage;

import lol.pyr.znpcsplus.fabric.hologram.*;
import lol.pyr.znpcsplus.fabric.interaction.*;
import lol.pyr.znpcsplus.fabric.npc.Npc;
import lol.pyr.znpcsplus.fabric.skin.*;
import lol.pyr.znpcsplus.fabric.util.NpcLocation;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class NpcSerde {
    public Map<String,Object> serialize(Npc n){
        Map<String,Object> out=new LinkedHashMap<>();out.put("id",n.id);out.put("is-processed",n.processed);out.put("allow-commands",n.allowCommands);out.put("save",n.save);
        out.put("enabled",n.enabled);out.put("uuid",n.uuid.toString());out.put("world",n.world);out.put("type",n.type);
        Map<String,Object> loc=new LinkedHashMap<>();loc.put("x",n.location.x());loc.put("y",n.location.y());loc.put("z",n.location.z());loc.put("yaw",n.location.yaw());loc.put("pitch",n.location.pitch());out.put("location",loc);
        Map<String,Object> props=new LinkedHashMap<>(n.properties);String skin=SkinSerde.serialize(n.skin);if(skin!=null)props.put("skin",skin);out.put("properties",props);
        Map<String,Object> holo=new LinkedHashMap<>();if(n.hologram.offset!=0)holo.put("offset",n.hologram.offset);if(n.hologram.refreshDelay!=-1)holo.put("refresh-delay",n.hologram.refreshDelay);
        List<String> lines=new ArrayList<>();for(HologramLine l:n.hologram.lines)lines.add(l.kind==HologramLine.Kind.ITEM?(l.value.toLowerCase(Locale.ROOT).startsWith("item:")?l.value:"item:"+l.value):l.value);holo.put("lines",lines);out.put("hologram",holo);
        List<String> acts=new ArrayList<>();for(NpcAction a:n.actions)acts.add(action(a));out.put("actions",acts);return out;
    }

    @SuppressWarnings("unchecked")
    public Npc deserialize(Map<String,Object> in){
        Npc n=new Npc();n.id=str(in.get("id"),"npc");n.processed=bool(in.get("is-processed"),true);n.allowCommands=bool(in.get("allow-commands"),true);n.save=bool(in.get("save"),true);n.enabled=bool(in.get("enabled"),true);
        try{n.uuid=UUID.fromString(str(in.get("uuid"),UUID.randomUUID().toString()));}catch(Exception ignored){n.uuid=UUID.randomUUID();}
        n.world=str(in.get("world"),"minecraft:overworld");n.type=str(in.get("type"),"player");
        Map<String,Object> l=map(in.get("location"));n.location=new NpcLocation(num(l.get("x"),0),num(l.get("y"),64),num(l.get("z"),0),(float)num(l.get("yaw"),0),(float)num(l.get("pitch"),0));
        Map<String,Object> p=map(in.get("properties"));for(var e:p.entrySet()){if("skin".equalsIgnoreCase(e.getKey()))n.skin=SkinSerde.deserialize(String.valueOf(e.getValue()));else n.properties.put(e.getKey().toLowerCase(Locale.ROOT),String.valueOf(e.getValue()));}
        Map<String,Object> h=map(in.get("hologram"));n.hologram.offset=num(h.get("offset"),0);n.hologram.refreshDelay=(long)num(h.get("refresh-delay"),-1);
        Object lines=h.get("lines");if(lines instanceof Iterable<?>it)for(Object o:it){String s=String.valueOf(o);if(s.regionMatches(true,0,"item:",0,5))n.hologram.lines.add(HologramLine.item(s));else n.hologram.lines.add(HologramLine.text(s));}
        Object as=in.get("actions");if(as instanceof Iterable<?>it)for(Object o:it){NpcAction a=parseAction(String.valueOf(o));if(a!=null)n.actions.add(a);}
        return n;
    }

    private static String action(NpcAction a){
        String clazz=switch(a.kind){case CONSOLE->"lol.pyr.znpcsplus.interaction.consolecommand.ConsoleCommandAction";case PLAYER_COMMAND->"lol.pyr.znpcsplus.interaction.playercommand.PlayerCommandAction";case MESSAGE->"lol.pyr.znpcsplus.interaction.message.MessageAction";case PLAYER_CHAT->"lol.pyr.znpcsplus.interaction.playerchat.PlayerChatAction";case SWITCH_SERVER->"lol.pyr.znpcsplus.interaction.switchserver.SwitchServerAction";};
        String base=Base64.getEncoder().encodeToString(a.value.getBytes(StandardCharsets.UTF_8));return clazz+";"+base+";"+a.cooldown+";"+a.interactionType.name()+";"+a.delay;
    }
    private static NpcAction parseAction(String s){try{String[]a=s.split(";",5);if(a.length<3)return null;NpcAction.ActionKind k;if(a[0].contains("ConsoleCommand"))k=NpcAction.ActionKind.CONSOLE;else if(a[0].contains("PlayerCommand"))k=NpcAction.ActionKind.PLAYER_COMMAND;else if(a[0].contains("PlayerChat"))k=NpcAction.ActionKind.PLAYER_CHAT;else if(a[0].contains("SwitchServer"))k=NpcAction.ActionKind.SWITCH_SERVER;else k=NpcAction.ActionKind.MESSAGE;
      String value=new String(Base64.getDecoder().decode(a[1]),StandardCharsets.UTF_8);long cd=Long.parseLong(a[2]);InteractionType t=a.length>3?InteractionType.valueOf(a[3]):InteractionType.ANY_CLICK;long d=a.length>4?Long.parseLong(a[4]):0;return new NpcAction(k,t,cd,d,value);}catch(Exception e){return null;}}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object o){return o instanceof Map<?,?>m?(Map<String,Object>)m:new LinkedHashMap<>();}
    private static String str(Object o,String d){return o==null?d:String.valueOf(o);}private static boolean bool(Object o,boolean d){return o==null?d:Boolean.parseBoolean(String.valueOf(o));}private static double num(Object o,double d){try{return Double.parseDouble(String.valueOf(o));}catch(Exception e){return d;}}
}
