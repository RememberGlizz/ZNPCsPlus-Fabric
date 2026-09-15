package lol.pyr.znpcsplus.fabric.skin;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.concurrent.CompletableFuture;

public record FileSkinDescriptor(String path, SkinData data) implements SkinDescriptor {
    @Override public String kind() { return "file"; }
    @Override public String argument() { return path; }
    @Override public CompletableFuture<SkinData> resolve(ServerPlayerEntity viewer, SkinCache cache) {
        if (data != null) return CompletableFuture.completedFuture(data);
        return cache.fetchFromFile(path);
    }
}
