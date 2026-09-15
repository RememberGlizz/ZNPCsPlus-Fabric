package lol.pyr.znpcsplus.fabric.skin;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.concurrent.CompletableFuture;

public record StaticSkinDescriptor(String name, SkinData data) implements SkinDescriptor {
    @Override public String kind() { return "static"; }
    @Override public String argument() { return name == null ? "" : name; }
    @Override public CompletableFuture<SkinData> resolve(ServerPlayerEntity viewer, SkinCache cache) {
        if (data != null) return CompletableFuture.completedFuture(data);
        return cache.fetchByName(name);
    }
}
