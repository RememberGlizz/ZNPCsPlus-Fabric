package lol.pyr.znpcsplus.fabric.skin;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.concurrent.CompletableFuture;

public record UrlSkinDescriptor(String url, String skinVariant, SkinData data) implements SkinDescriptor {
    @Override public String kind() { return "url"; }
    @Override public String argument() { return url; }
    @Override public String variant() { return skinVariant; }
    @Override public CompletableFuture<SkinData> resolve(ServerPlayerEntity viewer, SkinCache cache) {
        if (data != null) return CompletableFuture.completedFuture(data);
        return cache.fetchByUrl(url, skinVariant);
    }
}
