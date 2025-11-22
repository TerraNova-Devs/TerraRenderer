package de.terranova.terraRenderer.refactor;

import com.mojang.math.Transformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * High-level wrapper around an NMS BlockDisplay entity.
 * - Renders a BlockDisplay via packets only (not added to the world)
 * - Optionally spawns a packet-only ArmorStand as a click hitbox
 * - Clicks are captured via Netty (ServerboundInteractPacket) and routed to onClick(...)
 */
public class BlockDisplayNode {

    // ------------------------------------------------------------------------
    // Static registries for hitboxes
    // ------------------------------------------------------------------------

    /** Map of hitbox entity id (ArmorStand) -> BlockDisplayNode. */
    private static final Map<Integer, BlockDisplayNode> NODES_BY_HITBOX_ID =
            new ConcurrentHashMap<>();

    // ------------------------------------------------------------------------
    // Per-node state
    // ------------------------------------------------------------------------

    private Location location;
    private Vector3f scale = new Vector3f(1f, 1f, 1f);
    private Vector3f rotationEulerDeg = new Vector3f(0f, 0f, 0f);
    private Material material = Material.BARRIER;

    private boolean glowing = false;
    private Color glowColor = null;

    /** Packet-only BlockDisplay entity id. */
    private int displayEntityId = -1;

    /** Packet-only hitbox entity id (ArmorStand), or -1 if none. */
    private int hitboxEntityId = -1;

    /** Optional click handler. */
    private Consumer<ClickContext> clickHandler;

    /** Optional hover handler (not triggered yet, reserved for future use). */
    private Consumer<ClickContext> hoverHandler;

    // ------------------------------------------------------------------------
    // Fluent configuration API
    // ------------------------------------------------------------------------

    public BlockDisplayNode location(Location location) {
        this.location = (location == null) ? null : location.clone();
        return this;
    }

    /** Convenience: uniform scale in all directions. */
    public BlockDisplayNode size(float size) {
        this.scale = new Vector3f(size, size, size);
        return this;
    }

    public BlockDisplayNode scale(Vector3f scale) {
        this.scale = (scale == null) ? new Vector3f(1f, 1f, 1f) : new Vector3f(scale);
        return this;
    }

    public BlockDisplayNode rotationEulerDeg(Vector3f eulerDeg) {
        this.rotationEulerDeg = (eulerDeg == null) ? new Vector3f(0f, 0f, 0f) : new Vector3f(eulerDeg);
        return this;
    }

    public BlockDisplayNode material(Material material) {
        this.material = material;
        return this;
    }

    /** Disable glow. */
    public BlockDisplayNode glow() {
        this.glowing = false;
        this.glowColor = null;
        return this;
    }

    /** Enable glow with the given RGB color (0xRRGGBB). */
    public BlockDisplayNode glow(int rgb) {
        this.glowing = true;
        this.glowColor = Color.fromRGB(rgb & 0xFFFFFF);
        return this;
    }

    /**
     * Register a click handler. When set, a packet-only hitbox entity is spawned
     * and click packets are routed via Netty to this handler.
     */
    public BlockDisplayNode onClick(Consumer<ClickContext> handler) {
        this.clickHandler = handler;
        return this;
    }

    /**
     * Register a hover handler placeholder.
     * NOTE: not triggered yet – reserved for ray-trace/hover implementation.
     */
    public BlockDisplayNode onHover(Consumer<ClickContext> handler) {
        this.hoverHandler = handler;
        return this;
    }

    // ------------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------------

    public Location getLocation() {
        return location == null ? null : location.clone();
    }

    public Vector3f getScale() {
        return new Vector3f(scale);
    }

    public Vector3f getRotationEulerDeg() {
        return new Vector3f(rotationEulerDeg);
    }

    public int getDisplayEntityId() {
        return displayEntityId;
    }

    public int getHitboxEntityId() {
        return hitboxEntityId;
    }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    /**
     * Spawn this BlockDisplay visually via packets and, if needed, a hitbox
     * entity for click/hover interactions.
     */
    public void spawn(Collection<Player> players) {
        Display.BlockDisplay nmsDisplay = createDisplayNmsEntity();
        if (nmsDisplay == null) return;

        this.displayEntityId = nmsDisplay.getId();
        DisplayPackets.spawn(nmsDisplay, players);

        // If any interaction handler is present, spawn a hitbox entity
        if (clickHandler != null || hoverHandler != null) {
            ArmorStand nmsHitbox = createHitboxNmsEntity();
            if (nmsHitbox != null) {
                this.hitboxEntityId = nmsHitbox.getId();
                NODES_BY_HITBOX_ID.put(this.hitboxEntityId, this);
                DisplayPackets.spawn(nmsHitbox, players);
            }
        }
    }

    /**
     * Despawn both the BlockDisplay and the hitbox entity via packets
     * and cleanup static mappings.
     */
    public void despawn(Collection<Player> players) {
        if (displayEntityId != -1) {
            DisplayPackets.remove(displayEntityId, players);
            displayEntityId = -1;
        }

        if (hitboxEntityId != -1) {
            DisplayPackets.remove(hitboxEntityId, players);
            NODES_BY_HITBOX_ID.remove(hitboxEntityId);
            hitboxEntityId = -1;
        }
    }

    /**
     * Simple "update" strategy: despawn then respawn with the new state.
     */
    public void update(Collection<Player> players) {
        despawn(players);
        spawn(players);
    }

    // ------------------------------------------------------------------------
    // NMS construction for BlockDisplay (visual)
    // ------------------------------------------------------------------------

    /**
     * Build the underlying NMS BlockDisplay with the current settings.
     * Does not send any packets or register the node.
     */
    public Display.BlockDisplay createDisplayNmsEntity() {
        if (location == null || location.getWorld() == null) return null;
        if (material == null || !material.isBlock()) return null;

        ServerLevel nmsWorld = ((CraftWorld) location.getWorld()).getHandle();
        Display.BlockDisplay nmsDisplay =
                new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, nmsWorld);

        // World position (pivot in world space)
        nmsDisplay.setPos(location.getX(), location.getY(), location.getZ());

        // Blockstate from Material
        CraftBlockData cbd = (CraftBlockData) material.createBlockData();
        nmsDisplay.setBlockState(cbd.getState());

        // No billboard (no facing-to-player)
        nmsDisplay.setBillboardConstraints(Display.BillboardConstraints.FIXED);

        // Euler (deg, YXZ) → Quaternion
        Quaternionf rotQ = DisplayMath.eulerToQuaternion(rotationEulerDeg);

        // Half-extents in local space
        Vector3f half = new Vector3f(scale).mul(0.5f);

        // Center at local origin even when rotated:
        // translation + rotQ * half = 0  ⇒  translation = - rotQ * half
        Vector3f translation = new Vector3f(half);
        translation.rotate(rotQ).negate();

        nmsDisplay.setTransformation(new Transformation(
                translation,            // local translation
                new Quaternionf(rotQ),  // rotation around local origin
                new Vector3f(scale),    // local scale
                new Quaternionf()       // right rotation = identity
        ));

        // Apply glow via Bukkit wrapper
        org.bukkit.entity.BlockDisplay bukkitDisplay =
                (org.bukkit.entity.BlockDisplay) nmsDisplay.getBukkitEntity();

        if (!glowing) {
            bukkitDisplay.setGlowing(false);
            bukkitDisplay.setGlowColorOverride(null);
        } else {
            bukkitDisplay.setGlowing(true);
            bukkitDisplay.setGlowColorOverride(glowColor);
        }

        return nmsDisplay;
    }

    // ------------------------------------------------------------------------
    // NMS construction for hitbox (ArmorStand), packet-only
    // ------------------------------------------------------------------------

    /**
     * Build a packet-only ArmorStand to act as a click hitbox.
     * The client will see it as an entity (for raytracing) but we never
     * register it in the world server-side.
     */
    private ArmorStand createHitboxNmsEntity() {
        if (location == null || location.getWorld() == null) return null;

        ServerLevel nmsWorld = ((CraftWorld) location.getWorld()).getHandle();

        ArmorStand armorStand = new ArmorStand(nmsWorld, location.getX(), location.getY(), location.getZ());
        armorStand.setInvisible(true);
        armorStand.setNoBasePlate(true);
        armorStand.setNoGravity(true);
        armorStand.setSmall(true);

        // NOTE: Do NOT set Marker(true) here, because that makes the bounding box 0
        // and the client can't click it anymore. Small + invisible is good enough.

        return armorStand;
    }

    // ------------------------------------------------------------------------
    // Static hook used by Netty packet listener
    // ------------------------------------------------------------------------

    /**
     * Called from the Netty handler when a ServerboundInteractPacket arrives.
     * If the entity id matches one of our hitboxes, the click handler is fired.
     */
    public static void handlePacketClick(int entityId, Player player) {
        BlockDisplayNode node = NODES_BY_HITBOX_ID.get(entityId);
        if (node == null) return;

        if (node.clickHandler != null) {
            node.clickHandler.accept(new ClickContext(player, node));
        }
        // hoverHandler could be triggered here for more complex logic if desired
    }
}

