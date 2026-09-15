package lol.pyr.znpcsplus.fabric.skin;

import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;

import java.util.List;

public record SkinData(String texture, String signature) {
    public UserProfile apply(UserProfile profile) {
        if (texture != null && !texture.isBlank()) {
            profile.setTextureProperties(List.of(new TextureProperty("textures", texture, signature)));
        }
        return profile;
    }
}
