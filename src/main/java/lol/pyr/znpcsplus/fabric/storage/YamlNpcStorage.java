package lol.pyr.znpcsplus.fabric.storage;
import lol.pyr.znpcsplus.fabric.npc.Npc;import org.yaml.snakeyaml.Yaml;import java.io.*;import java.nio.file.*;import java.util.*;
public final class YamlNpcStorage implements NpcStorage{
 private final Path folder;private final NpcSerde serde=new NpcSerde();private final Yaml yaml=new Yaml();
 public YamlNpcStorage(Path folder)throws IOException{this.folder=folder;Files.createDirectories(folder);}
 public Collection<Npc> load()throws Exception{List<Npc>out=new ArrayList<>();try(var stream=Files.list(folder)){for(Path p:stream.filter(x->x.getFileName().toString().toLowerCase().endsWith(".yml")).toList())try(Reader r=Files.newBufferedReader(p)){Object o=yaml.load(r);if(o instanceof Map<?,?>m)out.add(serde.deserialize((Map<String,Object>)m));}}return out;}
 public void save(Collection<Npc>npcs)throws Exception{Files.createDirectories(folder);Set<String>keep=new HashSet<>();for(Npc n:npcs)if(n.save){Path p=folder.resolve(n.id+".yml");keep.add(p.getFileName().toString());try(Writer w=Files.newBufferedWriter(p)){yaml.dump(serde.serialize(n),w);}}try(var s=Files.list(folder)){for(Path p:s.filter(x->x.getFileName().toString().endsWith(".yml")).toList())if(!keep.contains(p.getFileName().toString()))Files.deleteIfExists(p);}}
 public void delete(String id)throws Exception{Files.deleteIfExists(folder.resolve(id+".yml"));}
}
