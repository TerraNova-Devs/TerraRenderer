package de.terranova.terraRenderer.refactor.DisplayGroups;

import de.terranova.terraRenderer.refactor.Anchor.Anchor3D;
import de.terranova.terraRenderer.refactor.BlockDisplayNode;
import de.terranova.terraRenderer.refactor.DisplayGroup;
import de.terranova.terraRenderer.refactor.DisplayMath;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A DisplayGroup representing a cuboid as 12 line segments (wireframe),
 * not a filled block.
 */
public class DisplayCube extends DisplayGroup {

    private Location from;
    private Location to;
    private Material material;
    private boolean glowing;
    private Color glowColor;
    private float thickness;

    public DisplayCube(Location from,
                       Location to,
                       Material material,
                       boolean glowing,
                       Color glowColor) {
        this(from, to, 0.10f, material, glowing, glowColor); // default thickness
    }

    public DisplayCube(Location from,
                       Location to,
                       float thickness,
                       Material material,
                       boolean glowing,
                       Color glowColor) {
        this.from = from == null ? null : from.clone();
        this.to = to == null ? null : to.clone();
        this.material = material;
        this.glowing = glowing;
        this.glowColor = glowColor;
        this.thickness = thickness;

        buildNodes();
    }

    private void buildNodes() {
        nodes.clear();
        if (from == null || to == null) return;
        if (!from.getWorld().equals(to.getWorld())) return;

        double minX = Math.min(from.getX(), to.getX());
        double minY = Math.min(from.getY(), to.getY());
        double minZ = Math.min(from.getZ(), to.getZ());
        double maxX = Math.max(from.getX(), to.getX());
        double maxY = Math.max(from.getY(), to.getY());
        double maxZ = Math.max(from.getZ(), to.getZ());

        var world = from.getWorld();

        Location c000 = new Location(world, minX, minY, minZ);
        Location c100 = new Location(world, maxX, minY, minZ);
        Location c010 = new Location(world, minX, maxY, minZ);
        Location c110 = new Location(world, maxX, maxY, minZ);

        Location c001 = new Location(world, minX, minY, maxZ);
        Location c101 = new Location(world, maxX, minY, maxZ);
        Location c011 = new Location(world, minX, maxY, maxZ);
        Location c111 = new Location(world, maxX, maxY, maxZ);

        // Bottom rectangle
        addEdge(c000, c100);
        addEdge(c100, c110);
        addEdge(c110, c010);
        addEdge(c010, c000);

        // Top rectangle
        addEdge(c001, c101);
        addEdge(c101, c111);
        addEdge(c111, c011);
        addEdge(c011, c001);

        // Vertical edges
        addEdge(c000, c001);
        addEdge(c100, c101);
        addEdge(c110, c111);
        addEdge(c010, c011);
    }

    private void addEdge(Location a, Location b) {
        BlockDisplayNode edge = createEdgeNode(a, b);
        if (edge != null) {
            add(edge);
        }
    }

    private BlockDisplayNode createEdgeNode(Location start, Location end) {
        if (start == null || end == null) return null;
        if (!start.getWorld().equals(end.getWorld())) return null;

        Vector3f dir = new Vector3f(
                (float) (end.getX() - start.getX()),
                (float) (end.getY() - start.getY()),
                (float) (end.getZ() - start.getZ())
        );
        float length = dir.length();
        if (length == 0) return null;

        dir.normalize();

        // Midpoint of the edge
        Location mid = start.clone().add(end).multiply(0.5);

        Vector3f scale = new Vector3f(thickness, thickness, length);

        // Rotate local +Z to dir
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), dir);
        Vector3f rotationEuler = DisplayMath.quaternionToEulerYXZDeg(q);

        BlockDisplayNode node = new BlockDisplayNode()
                .location(mid)
                .scale(scale)
                .rotationEulerDeg(rotationEuler)
                .material(material);

        if (glowing && glowColor != null) {
            node.glow(glowColor.asRGB());
        } else {
            node.glow(); // ensure glow is off
        }

        return node;
    }

    public DisplayCube anchor(Anchor3D anchor, Location worldLocation) {
        super.anchor(worldLocation, anchor);
        return this;
    }
}

