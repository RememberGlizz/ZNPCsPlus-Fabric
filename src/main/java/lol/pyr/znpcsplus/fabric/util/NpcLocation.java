package lol.pyr.znpcsplus.fabric.util;

import com.github.retrooper.packetevents.util.Vector3d;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

public record NpcLocation(double x, double y, double z, float yaw, float pitch) {
    public static NpcLocation from(ServerPlayerEntity player) {
        return new NpcLocation(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
    }

    public NpcLocation withY(double newY) { return new NpcLocation(x, newY, z, yaw, pitch); }
    public NpcLocation withRotation(float newYaw, float newPitch) { return new NpcLocation(x, y, z, newYaw, newPitch); }
    public Vector3d packetVector() { return new Vector3d(x, y, z); }
    public Vec3d vanillaVector() { return new Vec3d(x, y, z); }

    public NpcLocation lookingAt(Vec3d target, double scale, float eyeHeight, float yawOffset, float pitchOffset) {
        double eyeY = y + (eyeHeight * scale);
        double dx = target.x - x;
        double dy = target.y - eyeY;
        double dz = target.z - z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)) + yawOffset;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal)) + pitchOffset;
        return withRotation(normalizeYaw(yaw), Math.max(-90.0F, Math.min(90.0F, pitch)));
    }

    public double squaredDistanceTo(double ox, double oy, double oz) {
        double dx=x-ox, dy=y-oy, dz=z-oz;
        return dx*dx+dy*dy+dz*dz;
    }

    private static float normalizeYaw(float yaw) {
        yaw %= 360.0F;
        if (yaw >= 180.0F) yaw -= 360.0F;
        if (yaw < -180.0F) yaw += 360.0F;
        return yaw;
    }
}
