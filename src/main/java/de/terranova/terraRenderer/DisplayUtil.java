package de.terranova.terraRenderer;

import com.mojang.math.Transformation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;

public final class DisplayUtil {

    private DisplayUtil() {}

    /**
     * Spawns a BlockDisplay at the given location showing the given Material,
     * with XYZ scaling (1,1,1 = default size), visible to ALL players.
     * Packet-only (entity is not added to the world).
     */
    public static Display.BlockDisplay createBlockDisplay(Location loc,
                                                          Material mat,
                                                          Vector3f scale,
                                                          Vector3f rotationEulerDeg) {
        if (loc == null || loc.getWorld() == null) return null;
        if (mat == null || !mat.isBlock()) return null;
        if (scale == null) scale = new Vector3f(1f, 1f, 1f);
        if (rotationEulerDeg == null) rotationEulerDeg = new Vector3f(0f, 0f, 0f);

        // Bukkit → NMS world
        ServerLevel nmsWorld = ((CraftWorld) loc.getWorld()).getHandle();

        // Create NMS BlockDisplay
        Display.BlockDisplay nmsDisplay =
                new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, nmsWorld);

        // World position (will be the pivot/origin in world space)
        nmsDisplay.setPos(loc.getX(), loc.getY(), loc.getZ());

        // Blockstate from Material
        CraftBlockData cbd = (CraftBlockData) mat.createBlockData();
        nmsDisplay.setBlockState(cbd.getState());

        // No facing-to-player
        nmsDisplay.setBillboardConstraints(Display.BillboardConstraints.FIXED);

        // Euler (deg, YXZ) → Quaternion
        Quaternionf rotQ = eulerToQuaternion(rotationEulerDeg);

        // Half-extents in local space
        Vector3f half = new Vector3f(scale).mul(0.5f);

        // We want the *center* at the local origin after rotation:
        // translation + rotQ * half = 0  ⇒  translation = - rotQ * half
        Vector3f translation = new Vector3f(half);
        translation.rotate(rotQ).negate();

        nmsDisplay.setTransformation(new Transformation(
                translation,          // local translation: center at origin, even when rotated
                new Quaternionf(rotQ),// rotation around local origin
                new Vector3f(scale),  // local scale around same origin
                new Quaternionf()     // right rotation = identity
        ));

        return nmsDisplay;
    }



    public static void sendBlockDisplay(Display.BlockDisplay nmsDisplay,
                                        List<Player> players) {
        if (nmsDisplay == null) return;
        if (players == null || players.isEmpty()) return;

        // Get world from the entity
        ServerLevel nmsWorld = (ServerLevel) nmsDisplay.level();

        // Same pattern you used before
        ServerEntity serverEntity = new ServerEntity(
                nmsWorld,
                nmsDisplay,
                0,
                false,
                packet -> {},
                Collections.emptySet()
        );

        // Use the old working method: entity generates its spawn packet,
        // but needs ServerEntity for tracking context.
        Packet<?> spawnPacket = nmsDisplay.getAddEntityPacket(serverEntity);

        // Metadata / data watcher
        var dataItems = nmsDisplay.getEntityData().packAll();
        ClientboundSetEntityDataPacket dataPacket =
                new ClientboundSetEntityDataPacket(nmsDisplay.getId(), dataItems);

        // Send packets to all viewers
        for (Player p : players) {
            if (p == null || !p.isOnline()) continue;

            var handle = ((CraftPlayer) p).getHandle();
            handle.connection.send(spawnPacket);
            handle.connection.send(dataPacket);
        }
    }





    private static Quaternionf eulerToQuaternion(Vector3f eulerDeg) {
        float pitchRad = (float) Math.toRadians(eulerDeg.x());
        float yawRad   = (float) Math.toRadians(eulerDeg.y());
        float rollRad  = (float) Math.toRadians(eulerDeg.z());

        // YXZ order: yaw(Y), pitch(X), roll(Z)
        return new Quaternionf().rotateYXZ(yawRad, pitchRad, rollRad);
    }



    public static Location roundGrid(Location loc) {
        loc.setX(Math.floor(loc.x())+0.5);
        loc.setY(Math.floor(loc.y())+0.5);
        loc.setZ(Math.floor(loc.z())+0.5);
        //TODO remove DEBUG code
        System.out.println(loc.x() + " | " + loc.y() + " | " + loc.z());
        return loc;
    }

    /**
     * @deprecated Use {@link #roundGrid(Location)} instead.
     */
    @Deprecated
    public static Location middlePosition(Location loc, Vector3f scale) {
        loc.setX(Math.floor(loc.x())+(0.5-(scale.x/2)));
        loc.setY(Math.floor(loc.y())+(0.5-(scale.y/2)));
        loc.setZ(Math.floor(loc.z())+(0.5-(scale.z/2)));
        return loc;
    }

    public static void drawLine(Location start,
                                Location end,
                                Material mat,
                                float thickness,
                                List<Player> players) {

        if (start == null || end == null) return;
        if (players == null || players.isEmpty()) return;
        if (!start.getWorld().equals(end.getWorld())) return;

        // Direction from start → end
        Vector3f dir = new Vector3f(
                (float) (end.getX() - start.getX()),
                (float) (end.getY() - start.getY()),
                (float) (end.getZ() - start.getZ())
        );

        float length = dir.length();
        if (length == 0) return;

        dir.normalize();

        // Midpoint between both points (this is now the display's pivot)
        Location mid = start.clone().add(end).multiply(0.5);

        // Block stretched on ONE axis: local Z = length, X/Y = thickness
        Vector3f scale = new Vector3f(thickness, thickness, length);

        // Rotate local +Z (0,0,1) to dir
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), dir);

        // Use your existing helper: quaternion → YXZ Euler in degrees
        Vector3f rotationEuler = quaternionToEulerYXZDeg(q);

        // Now: loc = mid (center), not start
        Display.BlockDisplay display = createBlockDisplay(mid, mat, scale, rotationEuler);
        if (display == null) return;

        // Send to players
        sendBlockDisplay(display, players);
    }





    private static Vector3f quaternionToEulerYXZDeg(Quaternionf q) {
        Vector3f anglesRad = new Vector3f();
        // JOML: this matches rotateYXZ(...)
        q.getEulerAnglesYXZ(anglesRad);

        return new Vector3f(
                (float) Math.toDegrees(anglesRad.x),
                (float) Math.toDegrees(anglesRad.y),
                (float) Math.toDegrees(anglesRad.z)
        );
    }
}

