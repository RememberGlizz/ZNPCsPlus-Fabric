package lol.pyr.znpcsplus.fabric.storage;
import lol.pyr.znpcsplus.fabric.npc.Npc;import java.util.*;
public interface NpcStorage extends AutoCloseable{Collection<Npc> load() throws Exception;void save(Collection<Npc> npcs)throws Exception;void delete(String id)throws Exception;default void close()throws Exception{}}
