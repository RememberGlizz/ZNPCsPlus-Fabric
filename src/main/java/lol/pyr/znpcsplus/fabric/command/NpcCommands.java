package lol.pyr.znpcsplus.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import lol.pyr.znpcsplus.fabric.config.ConfigManager;
import lol.pyr.znpcsplus.fabric.hologram.*;
import lol.pyr.znpcsplus.fabric.interaction.*;
import lol.pyr.znpcsplus.fabric.npc.*;
import lol.pyr.znpcsplus.fabric.packet.PacketEngine;
import lol.pyr.znpcsplus.fabric.property.PropertyCatalog;
import lol.pyr.znpcsplus.fabric.skin.*;
import lol.pyr.znpcsplus.fabric.storage.*;
import lol.pyr.znpcsplus.fabric.util.*;

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class NpcCommands {
    private final NpcRegistry registry;private final NpcTypeRegistry types;private final PacketEngine packets;private final ConfigManager config;private final StorageManager storage;private final SkinCache skins;private final NpcSerde serde=new NpcSerde();

    public NpcCommands(NpcRegistry registry,NpcTypeRegistry types,PacketEngine packets,ConfigManager config,StorageManager storage,SkinCache skins){
        this.registry=registry;this.types=types;this.packets=packets;this.config=config;this.storage=storage;this.skins=skins;
    }

    public void register(CommandDispatcher<ServerCommandSource> d){
        LiteralArgumentBuilder<ServerCommandSource> root=literal("npc").requires(this::allowed);
        root.then(literal("create").then(argument("id",StringArgumentType.word()).suggests((c,b)->ids(b))
                .executes(c->create(c,"player"))
                .then(argument("type",StringArgumentType.word()).suggests((c,b)->types(b)).executes(c->create(c,StringArgumentType.getString(c,"type"))))));
        root.then(literal("center").then(idArg().executes(this::center)));
        root.then(literal("clone").then(idArg().then(argument("newid",StringArgumentType.word()).executes(this::cloneNpc))));
        root.then(literal("reloadconfig").executes(this::reloadConfig));
        root.then(literal("toggle").then(idArg().executes(this::toggle)));
        root.then(literal("skin").then(idArg()
                .then(literal("mirror").executes(this::skinMirror))
                .then(literal("static").then(argument("name",StringArgumentType.greedyString()).executes(this::skinStatic)))
                .then(literal("dynamic").then(argument("name",StringArgumentType.greedyString()).executes(this::skinDynamic)))
                .then(literal("url").then(argument("variant",StringArgumentType.word()).suggests((c,b)->CommandSource.suggestMatching(List.of("slim","classic"),b))
                        .then(argument("url",StringArgumentType.greedyString()).executes(this::skinUrl))))
                .then(literal("file").then(argument("file",StringArgumentType.greedyString()).executes(this::skinFile)))));
        root.then(literal("delete").then(idArg().executes(this::delete)));
        root.then(literal("move").then(idArg().executes(this::move)));
        root.then(literal("teleport").then(idArg().executes(this::teleport)));
        root.then(literal("list").executes(this::list));
        root.then(literal("near").executes(c->near(c,10)).then(argument("distance",DoubleArgumentType.doubleArg(0)).executes(c->near(c,DoubleArgumentType.getDouble(c,"distance")))));
        root.then(literal("type").then(idArg().then(argument("type",StringArgumentType.word()).suggests((c,b)->types(b)).executes(this::type))));
        root.then(literal("setlocation").then(idArg()
                .then(argument("x",StringArgumentType.word()).then(argument("y",StringArgumentType.word()).then(argument("z",StringArgumentType.word()).executes(this::setLocation))))));
        root.then(literal("lookatme").then(idArg().executes(this::lookAtMe)));
        root.then(literal("setrotation").then(idArg().then(argument("yaw",FloatArgumentType.floatArg()).then(argument("pitch",FloatArgumentType.floatArg(-90,90)).executes(this::setRotation)))));
        root.then(literal("changeid").then(idArg().then(argument("newid",StringArgumentType.word()).executes(this::changeId))));

        root.then(literal("property")
                .then(literal("set").then(idArg().then(argument("property",StringArgumentType.word()).suggests((c,b)->CommandSource.suggestMatching(PropertyCatalog.NAMES,b))
                        .then(argument("value",StringArgumentType.greedyString()).executes(this::propertySet)))))
                .then(literal("remove").then(idArg().then(argument("property",StringArgumentType.word()).suggests((c,b)->CommandSource.suggestMatching(PropertyCatalog.NAMES,b)).executes(this::propertyRemove)))));

        root.then(storageTree());
        root.then(hologramTree());
        root.then(actionTree());
        root.then(literal("version").executes(this::version));

        var node=d.register(root);
        d.register(literal("znpc").requires(this::allowed).redirect(node));
        d.register(literal("znpcs").requires(this::allowed).redirect(node));
        d.register(literal("npcs").requires(this::allowed).redirect(node));
    }

    private ArgumentBuilder<ServerCommandSource,?> storageTree(){
        return literal("storage")
            .then(literal("save").executes(c->{try{storage.save();ok(c,"Saved all NPCs.");return 1;}catch(Exception e){return fail(c,e);}}))
            .then(literal("reload").executes(c->{try{for(Npc n:registry.all())packets.hideAll(n);int count=storage.load();ok(c,"Reloaded "+count+" NPCs.");return count;}catch(Exception e){return fail(c,e);}}))
            .then(literal("saveall").executes(c->{try{storage.save();ok(c,"Saved all NPCs.");return 1;}catch(Exception e){return fail(c,e);}}))
            .then(literal("loadall").executes(c->{try{for(Npc n:registry.all())packets.hideAll(n);int count=storage.load();ok(c,"Loaded "+count+" NPCs.");return count;}catch(Exception e){return fail(c,e);}}))
            .then(literal("migrate").then(argument("type",StringArgumentType.word()).suggests((c,b)->CommandSource.suggestMatching(List.of("YAML","SQLITE","MYSQL"),b)).executes(c->{try{storage.migrate(StringArgumentType.getString(c,"type"));ok(c,"Migrated NPCs.");return 1;}catch(Exception e){return fail(c,e);}})));
    }

    private ArgumentBuilder<ServerCommandSource,?> hologramTree(){
        return literal("holo")
            .then(literal("add").then(idArg().then(argument("text",StringArgumentType.greedyString()).executes(c->{Npc n=npc(c);n.hologram.lines.add(HologramLine.text(StringArgumentType.getString(c,"text")));packets.respawn(n);ok(c,"Hologram line added.");return 1;}))))
            .then(literal("additem").then(idArg().then(argument("item",StringArgumentType.greedyString()).executes(c->{String v=StringArgumentType.getString(c,"item");if(!ItemCodec.valid(v)){error(c,"Invalid item.");return 0;}Npc n=npc(c);n.hologram.lines.add(HologramLine.item(v));packets.respawn(n);ok(c,"Item line added.");return 1;}))))
            .then(literal("delete").then(idArg().then(argument("index",IntegerArgumentType.integer(0)).executes(c->{Npc n=npc(c);int i=IntegerArgumentType.getInteger(c,"index");if(i>=n.hologram.lines.size()){error(c,"No hologram line at index "+i);return 0;}n.hologram.lines.remove(i);packets.respawn(n);ok(c,"Hologram line removed.");return 1;}))))
            .then(literal("info").then(idArg().executes(c->{Npc n=npc(c);ok(c,"Hologram: "+n.hologram.lines.size()+" lines, offset "+n.hologram.offset+", refresh "+n.hologram.refreshDelay+"ms");for(int i=0;i<n.hologram.lines.size();i++)ok(c,i+": "+n.hologram.lines.get(i).value);return n.hologram.lines.size();})))
            .then(literal("insert").then(idArg().then(argument("index",IntegerArgumentType.integer(0)).then(argument("text",StringArgumentType.greedyString()).executes(c->{Npc n=npc(c);int i=IntegerArgumentType.getInteger(c,"index");if(i>n.hologram.lines.size()){error(c,"Index out of range.");return 0;}n.hologram.lines.add(i,HologramLine.text(StringArgumentType.getString(c,"text")));packets.respawn(n);ok(c,"Hologram line inserted.");return 1;})))))
            .then(literal("insertitem").then(idArg().then(argument("index",IntegerArgumentType.integer(0)).then(argument("item",StringArgumentType.greedyString()).executes(c->{Npc n=npc(c);int i=IntegerArgumentType.getInteger(c,"index");String v=StringArgumentType.getString(c,"item");if(i>n.hologram.lines.size()||!ItemCodec.valid(v)){error(c,"Invalid index or item.");return 0;}n.hologram.lines.add(i,HologramLine.item(v));packets.respawn(n);ok(c,"Item line inserted.");return 1;})))))
            .then(literal("set").then(idArg().then(argument("index",IntegerArgumentType.integer(0)).then(argument("text",StringArgumentType.greedyString()).executes(c->{Npc n=npc(c);int i=IntegerArgumentType.getInteger(c,"index");if(i>=n.hologram.lines.size()){error(c,"Index out of range.");return 0;}n.hologram.lines.set(i,HologramLine.text(StringArgumentType.getString(c,"text")));packets.respawn(n);ok(c,"Hologram line set.");return 1;})))))
            .then(literal("setitem").then(idArg().then(argument("index",IntegerArgumentType.integer(0)).then(argument("item",StringArgumentType.greedyString()).executes(c->{Npc n=npc(c);int i=IntegerArgumentType.getInteger(c,"index");String v=StringArgumentType.getString(c,"item");if(i>=n.hologram.lines.size()||!ItemCodec.valid(v)){error(c,"Invalid index or item.");return 0;}n.hologram.lines.set(i,HologramLine.item(v));packets.respawn(n);ok(c,"Item line set.");return 1;})))))
            .then(literal("offset").then(idArg().then(argument("offset",DoubleArgumentType.doubleArg()).executes(c->{Npc n=npc(c);n.hologram.offset=DoubleArgumentType.getDouble(c,"offset");packets.respawn(n);ok(c,"Hologram offset updated.");return 1;}))))
            .then(literal("refreshdelay").then(idArg().then(argument("milliseconds",LongArgumentType.longArg(-1)).executes(c->{Npc n=npc(c);n.hologram.refreshDelay=LongArgumentType.getLong(c,"milliseconds");ok(c,"Hologram refresh delay updated.");return 1;}))));
    }

    private ArgumentBuilder<ServerCommandSource,?> actionTree(){
        return literal("action")
            .then(literal("add").then(actionArgs(false)))
            .then(literal("edit").then(actionArgs(true)))
            .then(literal("clear").then(idArg().executes(c->{Npc n=npc(c);int z=n.actions.size();n.actions.clear();ok(c,"Cleared "+z+" actions.");return z;})))
            .then(literal("delete").then(idArg().then(argument("index",IntegerArgumentType.integer(0)).executes(c->{Npc n=npc(c);int i=IntegerArgumentType.getInteger(c,"index");if(i>=n.actions.size()){error(c,"No action at index "+i);return 0;}n.actions.remove(i);ok(c,"Removed action "+i+".");return 1;}))))
            .then(literal("list").then(idArg().executes(c->{Npc n=npc(c);for(int i=0;i<n.actions.size();i++){NpcAction a=n.actions.get(i);ok(c,i+": "+a.kind+" "+a.interactionType+" cooldown="+a.cooldown+"ms delay="+a.delay+"t -> "+a.value);}return n.actions.size();})));
    }

    private ArgumentBuilder<ServerCommandSource,?> actionArgs(boolean edit){
        ArgumentBuilder<ServerCommandSource,?> current=idArg();
        if(edit)current=current.then(argument("index",IntegerArgumentType.integer(0)).then(actionTypeTail(true)));
        else current=current.then(actionTypeTail(false));
        return current;
    }
    private ArgumentBuilder<ServerCommandSource,?> actionTypeTail(boolean edit){
        return argument("actiontype",StringArgumentType.word()).suggests((c,b)->CommandSource.suggestMatching(List.of("consolecommand","playercommand","switchserver","message","playerchat"),b))
            .then(argument("click",StringArgumentType.word()).suggests((c,b)->CommandSource.suggestMatching(List.of("ANY_CLICK","LEFT_CLICK","RIGHT_CLICK"),b))
            .then(argument("cooldown",DoubleArgumentType.doubleArg(0))
            .then(argument("delay",IntegerArgumentType.integer(0))
            .then(argument("value",StringArgumentType.greedyString()).executes(c->putAction(c,edit))))));
    }

    private int putAction(CommandContext<ServerCommandSource> c,boolean edit){
        try{
            Npc n=npc(c);NpcAction.ActionKind kind=NpcAction.parseKind(StringArgumentType.getString(c,"actiontype"));InteractionType click=InteractionType.valueOf(StringArgumentType.getString(c,"click").toUpperCase(Locale.ROOT));
            long cooldown=(long)(DoubleArgumentType.getDouble(c,"cooldown")*1000D);long delay=IntegerArgumentType.getInteger(c,"delay");String value=StringArgumentType.getString(c,"value");NpcAction a=new NpcAction(kind,click,cooldown,delay,value);
            if(edit){int i=IntegerArgumentType.getInteger(c,"index");if(i>=n.actions.size()){error(c,"No action at index "+i);return 0;}a.id=n.actions.get(i).id;n.actions.set(i,a);ok(c,"Action "+i+" updated.");}
            else{n.actions.add(a);ok(c,"Action added at index "+(n.actions.size()-1)+".");}return 1;
        }catch(Exception e){return fail(c,e);}
    }

    private int create(CommandContext<ServerCommandSource> c,String typeName){
        ServerPlayerEntity p=player(c);if(p==null)return 0;NpcType type=types.get(typeName);if(type==null){error(c,"Unknown NPC type: "+typeName);return 0;}
        String id=StringArgumentType.getString(c,"id");if(registry.get(id)!=null){error(c,"NPC with that ID already exists.");return 0;}
        Npc n=registry.create(id,p.getServerWorld().getRegistryKey().getValue().toString(),type.name(),NpcLocation.from(p));ok(c,"Created a "+type.name()+" NPC with ID "+n.id+".");return 1;
    }
    private int center(CommandContext<ServerCommandSource>c){Npc n=npc(c);n.location=new NpcLocation(Math.floor(n.location.x())+.5,n.location.y(),Math.floor(n.location.z())+.5,n.location.yaw(),n.location.pitch());packets.teleport(n);ok(c,"NPC centered on its current block.");return 1;}
    private int cloneNpc(CommandContext<ServerCommandSource>c){Npc src=npc(c);String id=StringArgumentType.getString(c,"newid");if(registry.get(id)!=null){error(c,"NPC with that ID exists.");return 0;}Map<String,Object>m=serde.serialize(src);m.put("id",id);m.put("uuid",UUID.randomUUID().toString());Npc copy=serde.deserialize(m);registry.register(copy);ok(c,"Cloned "+src.id+" to "+copy.id+".");return 1;}
    private int reloadConfig(CommandContext<ServerCommandSource>c){config.reload();ok(c,"ZNPCsPlus Fabric configuration reloaded.");return 1;}
    private int toggle(CommandContext<ServerCommandSource>c){Npc n=npc(c);n.enabled=!n.enabled;if(!n.enabled)packets.hideAll(n);ok(c,"NPC "+n.id+" is now "+(n.enabled?"enabled":"disabled")+".");return 1;}
    private int skinMirror(CommandContext<ServerCommandSource>c){Npc n=npc(c);if(!"player".equals(n.type))return notPlayerNpc(c);n.skin=new MirrorSkinDescriptor();packets.respawn(n);ok(c,"NPC skin will mirror each viewer.");return 1;}
    private int skinDynamic(CommandContext<ServerCommandSource>c){Npc n=npc(c);if(!"player".equals(n.type))return notPlayerNpc(c);n.skin=new DynamicSkinDescriptor(StringArgumentType.getString(c,"name"));packets.respawn(n);ok(c,"NPC skin will resolve dynamically.");return 1;}
    private int skinStatic(CommandContext<ServerCommandSource>c){Npc n=npc(c);if(!"player".equals(n.type))return notPlayerNpc(c);String name=StringArgumentType.getString(c,"name");ok(c,"Fetching skin \""+name+"\"...");skins.fetchByName(name).thenAccept(s->c.getSource().getServer().execute(()->{if(s==null){error(c,"Failed to fetch skin.");return;}n.skin=new StaticSkinDescriptor(name,s);packets.respawn(n);ok(c,"NPC skin set to \""+name+"\".");}));return 1;}
    private int skinUrl(CommandContext<ServerCommandSource>c){Npc n=npc(c);if(!"player".equals(n.type))return notPlayerNpc(c);String variant=StringArgumentType.getString(c,"variant").toLowerCase(Locale.ROOT);if(!Set.of("slim","classic").contains(variant)){error(c,"Variant must be slim or classic.");return 0;}String url=StringArgumentType.getString(c,"url");ok(c,"Fetching URL skin...");skins.fetchByUrl(url,variant).thenAccept(s->c.getSource().getServer().execute(()->{if(s==null){error(c,"Failed to fetch skin.");return;}n.skin=new UrlSkinDescriptor(url,variant,s);packets.respawn(n);ok(c,"NPC URL skin set.");}));return 1;}
    private int skinFile(CommandContext<ServerCommandSource>c){Npc n=npc(c);if(!"player".equals(n.type))return notPlayerNpc(c);String file=StringArgumentType.getString(c,"file");ok(c,"Fetching skin file...");skins.fetchFromFile(file).thenAccept(s->c.getSource().getServer().execute(()->{if(s==null){error(c,"Failed to fetch skin file.");return;}n.skin=new FileSkinDescriptor(file,s);packets.respawn(n);ok(c,"NPC file skin set.");}));return 1;}
    private int delete(CommandContext<ServerCommandSource>c){Npc n=npc(c);packets.hideAll(n);registry.delete(n.id);storage.delete(n.id);ok(c,"NPC deleted.");return 1;}
    private int move(CommandContext<ServerCommandSource>c){ServerPlayerEntity p=player(c);if(p==null)return 0;Npc n=npc(c);n.world=p.getServerWorld().getRegistryKey().getValue().toString();n.location=NpcLocation.from(p);packets.respawn(n);ok(c,"NPC moved to your current location.");return 1;}
    private int teleport(CommandContext<ServerCommandSource>c){ServerPlayerEntity p=player(c);if(p==null)return 0;Npc n=npc(c);String cmd="execute in "+n.world+" run tp "+p.getGameProfile().getName()+" "+n.location.x()+" "+n.location.y()+" "+n.location.z()+" "+n.location.yaw()+" "+n.location.pitch();c.getSource().getServer().getCommandManager().executeWithPrefix(c.getSource().getServer().getCommandSource(),cmd);ok(c,"Teleported to NPC.");return 1;}
    private int list(CommandContext<ServerCommandSource>c){ok(c,"NPCs ("+registry.size()+"): "+String.join(", ",registry.ids()));return registry.size();}
    private int near(CommandContext<ServerCommandSource>c,double dist){ServerPlayerEntity p=player(c);if(p==null)return 0;List<String>near=new ArrayList<>();double d2=dist*dist,px=p.getX(),py=p.getY(),pz=p.getZ();String w=p.getServerWorld().getRegistryKey().getValue().toString();for(Npc n:registry.all())if(w.equals(n.world)&&n.location.squaredDistanceTo(px,py,pz)<=d2)near.add(n.id);ok(c,"Nearby NPCs: "+(near.isEmpty()?"none":String.join(", ",near)));return near.size();}
    private int type(CommandContext<ServerCommandSource>c){Npc n=npc(c);String t=StringArgumentType.getString(c,"type").toLowerCase(Locale.ROOT);if(types.get(t)==null){error(c,"Unknown type.");return 0;}packets.hideAll(n);n.type=t;packets.respawn(n);ok(c,"NPC type changed to "+t+".");return 1;}
    private int setLocation(CommandContext<ServerCommandSource>c){Npc n=npc(c);try{double x=relative(StringArgumentType.getString(c,"x"),n.location.x()),y=relative(StringArgumentType.getString(c,"y"),n.location.y()),z=relative(StringArgumentType.getString(c,"z"),n.location.z());n.location=new NpcLocation(x,y,z,n.location.yaw(),n.location.pitch());packets.teleport(n);ok(c,"NPC moved to "+x+", "+y+", "+z+".");return 1;}catch(Exception e){return fail(c,e);}}
    private int lookAtMe(CommandContext<ServerCommandSource>c){ServerPlayerEntity p=player(c);if(p==null)return 0;Npc n=npc(c);NpcType t=types.get(n.type);NpcLocation r=n.location.lookingAt(p.getEyePos(),n.number("attribute_scale",1),t==null?1.62f:t.eyeHeight(),0,0);n.location=n.location.withRotation(r.yaw(),r.pitch());packets.teleport(n);ok(c,"NPC is now looking at you.");return 1;}
    private int setRotation(CommandContext<ServerCommandSource>c){Npc n=npc(c);n.location=n.location.withRotation(FloatArgumentType.getFloat(c,"yaw"),FloatArgumentType.getFloat(c,"pitch"));packets.teleport(n);ok(c,"NPC rotation updated.");return 1;}
    private int changeId(CommandContext<ServerCommandSource>c){Npc n=npc(c);String newId=StringArgumentType.getString(c,"newid");if(!registry.changeId(n.id,newId)){error(c,"That ID is already in use.");return 0;}ok(c,"NPC ID changed to "+newId+".");return 1;}
    private int propertySet(CommandContext<ServerCommandSource>c){Npc n=npc(c);String key=StringArgumentType.getString(c,"property").toLowerCase(Locale.ROOT),value=StringArgumentType.getString(c,"value");if("skin".equals(key)){error(c,"Use /npc skin for skin properties.");return 0;}n.properties.put(key,value);if(Set.of("display_name","glow").contains(key)||key.startsWith("skin_"))packets.respawn(n);else packets.refresh(n);ok(c,"Set "+key+" = "+value+".");return 1;}
    private int propertyRemove(CommandContext<ServerCommandSource>c){Npc n=npc(c);String key=StringArgumentType.getString(c,"property").toLowerCase(Locale.ROOT);if("skin".equals(key))n.skin=null;else n.properties.remove(key);packets.respawn(n);ok(c,"Removed property "+key+".");return 1;}
    private int version(CommandContext<ServerCommandSource>c){String v=FabricLoader.getInstance().getModContainer("znpcsplus_fabric").map(x->x.getMetadata().getVersion().getFriendlyString()).orElse("unknown");ok(c,"ZNPCsPlus Fabric "+v+" — port target: upstream 2.X @ 942d00e");return 1;}

    private boolean allowed(ServerCommandSource s){if(s.hasPermissionLevel(2))return true;return s.getEntity() instanceof ServerPlayerEntity p&&PermissionBridge.has(p,"znpcsplus.command.npc");}
    private RequiredArgumentBuilder<ServerCommandSource,String> idArg(){return argument("id",StringArgumentType.word()).suggests((c,b)->ids(b));}
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> ids(com.mojang.brigadier.suggestion.SuggestionsBuilder b){return CommandSource.suggestMatching(registry.ids(),b);}
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> types(com.mojang.brigadier.suggestion.SuggestionsBuilder b){return CommandSource.suggestMatching(types.names(),b);}
    private Npc npc(CommandContext<ServerCommandSource>c){Npc n=registry.get(StringArgumentType.getString(c,"id"));if(n==null)throw new IllegalArgumentException("Unknown NPC ID");return n;}
    private ServerPlayerEntity player(CommandContext<ServerCommandSource>c){ServerPlayerEntity p=c.getSource().getPlayer();if(p==null)error(c,"This command must be run by a player.");return p;}
    private int notPlayerNpc(CommandContext<ServerCommandSource>c){error(c,"The NPC must be a player to have a skin.");return 0;}
    private static double relative(String s,double current){if(s.equals("~"))return current;if(s.startsWith("~"))return current+Double.parseDouble(s.substring(1));return Double.parseDouble(s);}
    private static void ok(CommandContext<ServerCommandSource>c,String s){c.getSource().sendFeedback(()->Text.literal(s),false);}
    private static void error(CommandContext<ServerCommandSource>c,String s){c.getSource().sendError(Text.literal(s));}
    private static int fail(CommandContext<ServerCommandSource>c,Throwable e){error(c,e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());return 0;}
}
