package lol.pyr.znpcsplus.fabric.skin;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.concurrent.CompletableFuture;

public interface SkinDescriptor {
    String kind();
    CompletableFuture<SkinData> resolve(ServerPlayerEntity viewer, SkinCache cache);
    default String argument() { return ""; }
    default String variant() { return "classic"; }
}
