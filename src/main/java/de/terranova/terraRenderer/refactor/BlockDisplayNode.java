package de.terranova.terraRenderer.refactor;

import com.mojang.math.Transformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;

public class BlockDisplayNode {

    private Location location;
    private Vector3f scale = new Vector3f(1, 1, 1);
    private Vector3f rotationEulerDeg = new Vector3f(0, 0, 0);
    private Material material = Material.BARRIER;

    private boolean glowing = false;
    private Color glowColor = null;
    private boolean enchanted = false; // placeholder for glint logic

    private int entityId = -1;

    // --- fluent setters ---

    public BlockDisplayNode location(Location location) {
        this.location = location == null ? null : location.clone();
        return this;
    }

    public BlockDisplayNode size(float size) {
        this.scale = new Vector3f(size, size, size);
        return this;
    }

    public BlockDisplayNode scale(Vector3f scale) {
        this.scale = scale == null ? new Vector3f(1, 1, 1) : new Vector3f(scale);
        return this;
    }

    public BlockDisplayNode rotationEulerDeg(Vector3f eulerDeg) {
        this.rotationEulerDeg = eulerDeg == null ? new Vector3f(0, 0, 0) : new Vector3f(eulerDeg);
        return this;
    }

    public BlockDisplayNode material(Material material) {
        this.material = material;
        return this;
    }

    public BlockDisplayNode glow() {
        this.glowing = false;
        this.glowColor = null;
        return this;
    }

    public BlockDisplayNode glow(int rgb) {
        this.glowing = true;
        this.glowColor = Color.fromRGB(rgb & 0xFFFFFF);
        return this;
    }

    public BlockDisplayNode enchanted(boolean enchanted) {
        this.enchanted = enchanted;
        return this;
    }

    // --- getters, for groups/anchors ---

    public Location getLocation() {
        return location == null ? null : location.clone();
    }

    public Vector3f getScale() {
        return new Vector3f(scale);
    }

    public Vector3f getRotationEulerDeg() {
        return new Vector3f(rotationEulerDeg);
    }

    public int getEntityId() {
        return entityId;
    }

    // --- lifecycle ---

    public void spawn(Collection<Player> players) {
        Display.BlockDisplay nms = createNmsEntity();
        if (nms == null) return;
        this.entityId = nms.getId();
        DisplayPackets.spawn(nms, players);
    }

    public void despawn(Collection<Player> players) {
        if (entityId == -1) return;
        DisplayPackets.remove(entityId, players);
        entityId = -1;
    }

    /**
     * Simple update strategy: remove old entity, spawn new one with current state.
     */
    public void update(Collection<Player> players) {
        despawn(players);
        spawn(players);
    }

    /**
     * Builds the NMS BlockDisplay (but does NOT send packets).
     */
    public Display.BlockDisplay createNmsEntity() {
        if (location == null || location.getWorld() == null) return null;
        if (material == null || !material.isBlock()) return null;

        ServerLevel nmsWorld = ((CraftWorld) location.getWorld()).getHandle();
        Display.BlockDisplay nmsDisplay =
                new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, nmsWorld);

        nmsDisplay.setPos(location.getX(), location.getY(), location.getZ());

        CraftBlockData cbd = (CraftBlockData) material.createBlockData();
        nmsDisplay.setBlockState(cbd.getState());

        nmsDisplay.setBillboardConstraints(Display.BillboardConstraints.FIXED);

        Quaternionf rotQ = DisplayMath.eulerToQuaternion(rotationEulerDeg);

        Vector3f half = new Vector3f(scale).mul(0.5f);
        Vector3f translation = new Vector3f(half);
        translation.rotate(rotQ).negate();

        nmsDisplay.setTransformation(new Transformation(
                translation,
                new Quaternionf(rotQ),
                new Vector3f(scale),
                new Quaternionf()
        ));

        org.bukkit.entity.BlockDisplay bukkitDisplay =
                (org.bukkit.entity.BlockDisplay) nmsDisplay.getBukkitEntity();

        if (!glowing) {
            bukkitDisplay.setGlowing(false);
            bukkitDisplay.setGlowColorOverride(null);
        } else {
            bukkitDisplay.setGlowing(true);
            bukkitDisplay.setGlowColorOverride(glowColor);
        }

        // enchanted glint logic can go here later

        return nmsDisplay;
    }

}