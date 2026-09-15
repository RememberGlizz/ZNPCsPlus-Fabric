package lol.pyr.znpcsplus.fabric.skin;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.concurrent.CompletableFuture;

public final class MirrorSkinDescriptor implements SkinDescriptor {
    @Override public String kind() { return "mirror"; }
    @Override public CompletableFuture<SkinData> resolve(ServerPlayerEntity viewer, SkinCache cache) {
        return cache.fetchByUuid(viewer.getUuidAsString().replace("-", ""));
    }
}
