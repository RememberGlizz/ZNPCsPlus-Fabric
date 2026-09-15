package lol.pyr.znpcsplus.fabric.skin;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.concurrent.CompletableFuture;

public record DynamicSkinDescriptor(String template) implements SkinDescriptor {
    @Override public String kind() { return "dynamic"; }
    @Override public String argument() { return template; }
    @Override public CompletableFuture<SkinData> resolve(ServerPlayerEntity viewer, SkinCache cache) {
        String name = template
                .replace("{player}", viewer.getGameProfile().getName())
                .replace("%player_name%", viewer.getGameProfile().getName());
        return cache.fetchByName(name);
    }
}
