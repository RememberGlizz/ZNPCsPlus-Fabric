package lol.pyr.znpcsplus.fabric.skin;
import net.minecraft.server.network.ServerPlayerEntity;import java.util.concurrent.CompletableFuture;
public record UuidSkinDescriptor(String uuid) implements SkinDescriptor{public String kind(){return "fetching-uuid";}public String argument(){return uuid;}public CompletableFuture<SkinData> resolve(ServerPlayerEntity v,SkinCache c){return c.fetchByUuid(uuid);}}
