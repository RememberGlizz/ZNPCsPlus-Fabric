package lol.pyr.znpcsplus.fabric.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.UserConnectEvent;
import com.github.retrooper.packetevents.event.UserLoginEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.PacketSide;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.PacketEventsImplHelper;
import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.SharedConstants;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Attaches PacketEvents only after Minecraft LOGIN has finished.
 *
 * PacketEvents' stock Fabric mixin hooks ClientConnection.addHandlers and therefore sees
 * Velocity/Fabric login forwarding packets. On this network that stream is not safe for
 * PacketEvents to decode and results in impossible LOGIN packet IDs (for example 400).
 * The final jar disables those stock mixins and this class attaches the handlers at
 * Fabric's CONFIGURATION/PLAY lifecycle instead.
 *
 * The outbound PacketEvents handler is deliberately placed AFTER Minecraft's vanilla
 * packet encoder in pipeline order. Netty walks outbound handlers in reverse order, so
 * ordinary vanilla Packet objects reach PacketEvents first, are passed through untouched,
 * and are then encoded by Minecraft. PacketEvents therefore never sees/re-writes ordinary
 * CobbleClub/vanilla outbound ByteBufs such as text_display metadata. ZNPCsPlus' own
 * already-encoded PacketEvents ByteBufs still hit PacketEvents first and then pass through
 * the vanilla encoder (which ignores ByteBuf messages) into compression/framing.
 */
public final class PacketEventsLateInjector {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Set<Channel> MANAGED_CHANNELS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final String DECODER_CLASS = "io.github.retrooper.packetevents.handler.PacketDecoder";
    private static final String ENCODER_CLASS = "io.github.retrooper.packetevents.handler.PacketEncoder";

    private PacketEventsLateInjector() {}

    public static void register(Logger logger) {
        if (!REGISTERED.compareAndSet(false, true)) return;

        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((handler, server) -> {
            try {
                injectConfiguration(handler, logger);
            } catch (Throwable t) {
                logger.error("Failed to attach PacketEvents after LOGIN during CONFIGURATION", t);
            }
        });

        ServerPlayConnectionEvents.INIT.register((handler, server) -> {
            try {
                ensurePlay(handler, logger);
            } catch (Throwable t) {
                logger.error("Failed to attach PacketEvents during PLAY init", t);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                Channel channel = channel(handler);
                User user = PacketEvents.getAPI().getProtocolManager().getUser(channel);
                if (user != null) {
                    PacketEvents.getAPI().getEventManager().callEvent(new UserLoginEvent(user, handler.player));
                }
            } catch (Throwable t) {
                logger.warn("Failed to fire PacketEvents user login event", t);
            }
        });
    }

    private static void injectConfiguration(ServerConfigurationNetworkHandler handler, Logger logger) throws Exception {
        ClientConnection connection = fieldByType(handler, ClientConnection.class);
        Channel channel = fieldByType(connection, Channel.class);
        GameProfile profile = fieldByType(handler, GameProfile.class);
        if (profile == null) {
            throw new IllegalStateException("Could not find authenticated GameProfile on configuration handler");
        }

        inject(channel, profile, ConnectionState.CONFIGURATION, null, logger);
    }

    private static void ensurePlay(ServerPlayNetworkHandler handler, Logger logger) throws Exception {
        Channel channel = channel(handler);
        GameProfile profile = handler.player.getGameProfile();
        User user = PacketEvents.getAPI().getProtocolManager().getUser(channel);

        if (user == null || channel.pipeline().get(PacketEvents.DECODER_NAME) == null || channel.pipeline().get(PacketEvents.ENCODER_NAME) == null) {
            user = inject(channel, profile, ConnectionState.PLAY, handler.player, logger);
        } else {
            user.setConnectionState(ConnectionState.PLAY);
            PacketEvents.getAPI().getProtocolManager().setChannel(profile.getId(), channel);
            PacketEvents.getAPI().getInjector().setPlayer(channel, handler.player);
        }
    }

    private static User inject(Channel channel, GameProfile profile, ConnectionState state, Object player, Logger logger) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();
        User existing = PacketEvents.getAPI().getProtocolManager().getUser(channel);
        if (existing != null && pipeline.get(PacketEvents.DECODER_NAME) != null && pipeline.get(PacketEvents.ENCODER_NAME) != null) {
            return existing;
        }

        if (pipeline.get(PacketEvents.DECODER_NAME) != null) pipeline.remove(PacketEvents.DECODER_NAME);
        if (pipeline.get(PacketEvents.ENCODER_NAME) != null) pipeline.remove(PacketEvents.ENCODER_NAME);

        ClientVersion version = ClientVersion.getById(SharedConstants.getProtocolVersion());
        User user = new User(channel, state, version, new UserProfile(profile.getId(), profile.getName()));
        PacketSide side = PacketEvents.getAPI().getInjector().getPacketSide();

        ChannelHandler decoder = newPacketHandler(DECODER_CLASS, side, user);
        ChannelHandler encoder = newPacketHandler(ENCODER_CLASS, side, user);

        if (pipeline.get("splitter") == null || pipeline.get("prepender") == null) {
            throw new IllegalStateException("Vanilla packet splitter/prepender are not present in the Netty pipeline");
        }

        // Inbound PacketEvents still needs decompressed packet bodies for NPC interaction
        // detection. This handler only observes client -> server traffic.
        String inboundAnchor = pipeline.get("decompress") != null ? "decompress" : "splitter";
        pipeline.addAfter(inboundAnchor, PacketEvents.DECODER_NAME, decoder);

        // Outbound PacketEvents needs to run after compression is installed when present,
        // otherwise immediately after the vanilla prepender. This is the placement used by
        // the verified fabric.7/fabric.10 runtime baseline.
        String outboundAnchor = pipeline.get("compress") != null ? "compress" : "prepender";
        pipeline.addAfter(outboundAnchor, PacketEvents.ENCODER_NAME, encoder);

        // setUser must happen before setChannel: FabricChannelInjector.updateUser checks
        // whether the channel has already been mapped and otherwise expects both handlers.
        PacketEvents.getAPI().getProtocolManager().setUser(channel, user);
        PacketEvents.getAPI().getProtocolManager().setChannel(profile.getId(), channel);
        if (player != null) PacketEvents.getAPI().getInjector().setPlayer(channel, player);

        if (MANAGED_CHANNELS.add(channel)) {
            UserConnectEvent connectEvent = new UserConnectEvent(user);
            PacketEvents.getAPI().getEventManager().callEvent(connectEvent);
            if (connectEvent.isCancelled()) {
                channel.close();
                return user;
            }

            channel.closeFuture().addListener((ChannelFutureListener) future -> {
                try {
                    PacketEventsImplHelper.handleDisconnection(channel, user.getUUID());
                } catch (Throwable t) {
                    logger.debug("PacketEvents disconnect cleanup failed", t);
                } finally {
                    MANAGED_CHANNELS.remove(channel);
                }
            });
        }

        logger.debug("Attached PacketEvents after LOGIN in {} state for {} (inbound after {}, outbound after {}, pipeline={})",
                state, profile.getName(), inboundAnchor, outboundAnchor, pipeline.names());
        return user;
    }

    private static ChannelHandler newPacketHandler(String className, PacketSide side, User user) throws Exception {
        Class<?> clazz = Class.forName(className);
        Constructor<?> constructor = clazz.getConstructor(PacketSide.class, User.class);
        return (ChannelHandler) constructor.newInstance(side, user);
    }

    private static Channel channel(ServerPlayNetworkHandler handler) throws Exception {
        ClientConnection connection = fieldByType(handler, ClientConnection.class);
        return fieldByType(connection, Channel.class);
    }

    private static <T> T fieldByType(Object instance, Class<T> type) throws Exception {
        if (instance == null) throw new IllegalArgumentException("instance");
        Class<?> cursor = instance.getClass();
        while (cursor != null) {
            for (Field field : cursor.getDeclaredFields()) {
                if (!type.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value = field.get(instance);
                if (value != null) return type.cast(value);
            }
            cursor = cursor.getSuperclass();
        }
        throw new IllegalStateException("Could not find field of type " + type.getName() + " on " + instance.getClass().getName());
    }
}
