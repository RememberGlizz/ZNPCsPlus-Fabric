package lol.pyr.znpcsplus.fabric.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class TextUtil {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexCharacter('#').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private TextUtil(){}

    public static String placeholders(String input, ServerPlayerEntity player) {
        if(input==null)return "";
        String name=player==null?"":player.getGameProfile().getName();
        return input.replace("{player}",name).replace("%player_name%",name)
                .replace("<player>",name).replace("{uuid}",player==null?"":player.getUuidAsString());
    }
    public static Component component(String text,ServerPlayerEntity player){
        String s=placeholders(text,player);
        try {
            if(s.contains("<")&&s.contains(">")) return MINI.deserialize(s);
            return LEGACY.deserialize(s);
        } catch(Throwable ignored){ return Component.text(s); }
    }
    public static net.minecraft.text.Text vanilla(String text,ServerPlayerEntity player){
        return net.minecraft.text.Text.literal(placeholders(text,player).replace('&','§'));
    }
}
