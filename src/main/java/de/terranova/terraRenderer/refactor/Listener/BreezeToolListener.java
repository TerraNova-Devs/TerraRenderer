package de.terranova.terraRenderer.refactor.Listener;

import de.terranova.terraRenderer.refactor.BlockDisplayNode;
import de.terranova.terraRenderer.refactor.DisplayGroups.DisplayCube;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class BreezeToolListener implements Listener {

    private final JavaPlugin plugin;

    // Per-player wireframe cube
    private final Map<UUID, DisplayCube> activeCubes = new HashMap<>();
    // Corner markers
    private final Map<UUID, List<BlockDisplayNode>> activeMarkers = new HashMap<>();
    // Per-player region in *block* coordinates
    private final Map<UUID, RegionSelection> activeRegions = new HashMap<>();

    // How many ticks the Display should interpolate when extending the region
    private static final int INTERPOLATION_TICKS = 10;

    public BreezeToolListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Region in block coordinates (min/max inclusive), bound to a world.
     */
    private static class RegionSelection {
        final World world;
        int minX, minY, minZ;
        int maxX, maxY, maxZ;

        RegionSelection(World world, int x, int y, int z) {
            this.world = Objects.requireNonNull(world, "world");
            this.minX = this.maxX = x;
            this.minY = this.maxY = y;
            this.minZ = this.maxZ = z;
        }
    }

    @EventHandler
    public void onToolInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (player == null) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() != Material.BREEZE_ROD) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String name = ChatColor.stripColor(meta.getDisplayName());
        if (!"tool".equalsIgnoreCase(name)) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        BlockFace face = event.getBlockFace(); // which face was actually clicked
        if (face == null) return;

        event.setCancelled(true);

        UUID uuid = player.getUniqueId();

        if (action == Action.LEFT_CLICK_BLOCK) {
            // ALWAYS spawn/update wireframe attached to the clicked face (snap, no interpolation)
            RegionSelection region = createRegionFromClicked(clicked, face);
            if (region == null) return;

            activeRegions.put(uuid, region);
            renderOrUpdateSelection(player, region, false);

        } else { // RIGHT_CLICK_BLOCK
            RegionSelection region = activeRegions.get(uuid);
            boolean changed;
            boolean extendedExisting = false;

            if (region == null || !Objects.equals(region.world, clicked.getWorld())) {
                // No region or wrong world -> behave like left click (start new one on that face; no interpolation)
                region = createRegionFromClicked(clicked, face);
                if (region == null) return;
                activeRegions.put(uuid, region);
                changed = true;
            } else {
                // Try to extend existing region
                extendedExisting = true;
                changed = extendRegion(region, clicked, player.isSneaking());
            }

            // If nothing changed, do nothing
            if (!changed) return;

            // Only interpolate when we actually extended an existing region
            boolean interpolate = extendedExisting;
            renderOrUpdateSelection(player, region, interpolate);
        }
    }

    /**
     * Create a new region based on the clicked block and the face that was hit.
     * The region's first block is always attached to that face (never "inside" the block).
     */
    private RegionSelection createRegionFromClicked(Block clicked, BlockFace face) {
        if (face == null) return null;

        Block base = clicked.getRelative(face);
        World world = base.getWorld();

        return new RegionSelection(
                world,
                base.getX(),
                base.getY(),
                base.getZ()
        );
    }

    /**
     * Extend region towards the clicked block, based on where that block is
     * relative to the current region.
     *
     * By default the region stops one block before the clicked block;
     * when sneaking, it includes the clicked block.
     *
     * @return true if any min/max coordinate changed, false otherwise.
     */
    private boolean extendRegion(RegionSelection r, Block clicked, boolean includeClicked) {
        int cx = clicked.getX();
        int cy = clicked.getY();
        int cz = clicked.getZ();

        // Remember old bounds to detect changes
        int oldMinX = r.minX, oldMaxX = r.maxX;
        int oldMinY = r.minY, oldMaxY = r.maxY;
        int oldMinZ = r.minZ, oldMaxZ = r.maxZ;

        // How far is the clicked block outside the region on each side?
        int dxNeg = r.minX - cx; // >0 if clicked is left of minX
        int dxPos = cx - r.maxX; // >0 if clicked is right of maxX
        int dyNeg = r.minY - cy; // >0 if clicked is below minY
        int dyPos = cy - r.maxY; // >0 if clicked is above maxY
        int dzNeg = r.minZ - cz; // >0 if clicked is north of minZ
        int dzPos = cz - r.maxZ; // >0 if clicked is south of maxZ

        int bestDist = 0;
        char axis = 0; // 'X', 'Y', 'Z'
        int sign = 0;  // -1 = negative side, +1 = positive side

        if (dxNeg > bestDist) { bestDist = dxNeg; axis = 'X'; sign = -1; }
        if (dxPos > bestDist) { bestDist = dxPos; axis = 'X'; sign = +1; }

        if (dyNeg > bestDist) { bestDist = dyNeg; axis = 'Y'; sign = -1; }
        if (dyPos > bestDist) { bestDist = dyPos; axis = 'Y'; sign = +1; }

        if (dzNeg > bestDist) { bestDist = dzNeg; axis = 'Z'; sign = -1; }
        if (dzPos > bestDist) { bestDist = dzPos; axis = 'Z'; sign = +1; }

        // Inside or touching the region: do nothing
        if (bestDist == 0) {
            return false;
        }

        switch (axis) {
            case 'X' -> {
                if (sign < 0) {
                    int newMin = includeClicked ? cx : cx + 1;
                    if (newMin <= r.minX) r.minX = newMin;
                } else {
                    int newMax = includeClicked ? cx : cx - 1;
                    if (newMax >= r.maxX) r.maxX = newMax;
                }
            }
            case 'Y' -> {
                if (sign < 0) {
                    int newMin = includeClicked ? cy : cy + 1;
                    if (newMin <= r.minY) r.minY = newMin;
                } else {
                    int newMax = includeClicked ? cy : cy - 1;
                    if (newMax >= r.maxY) r.maxY = newMax;
                }
            }
            case 'Z' -> {
                if (sign < 0) {
                    int newMin = includeClicked ? cz : cz + 1;
                    if (newMin <= r.minZ) r.minZ = newMin;
                } else {
                    int newMax = includeClicked ? cz : cz - 1;
                    if (newMax >= r.maxZ) r.maxZ = newMax;
                }
            }
            default -> {
            }
        }

        // Did anything actually change?
        return r.minX != oldMinX || r.maxX != oldMaxX
                || r.minY != oldMinY || r.maxY != oldMaxY
                || r.minZ != oldMinZ || r.maxZ != oldMaxZ;
    }

    /**
     * Render or update the current region as a DisplayCube + corner markers.
     *
     * If visuals for this player don't exist yet, they are created.
     * Otherwise, the existing cube/markers are updated, optionally with interpolation.
     */
    private void renderOrUpdateSelection(Player player, RegionSelection region, boolean interpolate) {
        UUID uuid = player.getUniqueId();
        List<Player> viewers = List.of(player);

        double xMin = region.minX;
        double yMin = region.minY;
        double zMin = region.minZ;

        double xMax = region.maxX + 1;
        double yMax = region.maxY + 1;
        double zMax = region.maxZ + 1;

        // Wireframe cube corners (min/max are block edges, +1 to cover full block)
        Location cubeFrom = new Location(region.world, xMin, yMin, zMin);
        Location cubeTo   = new Location(region.world, xMax, yMax, zMax);

        // 8 corners
        Location c000 = new Location(region.world, xMin, yMin, zMin);
        Location c100 = new Location(region.world, xMax, yMin, zMin);
        Location c010 = new Location(region.world, xMin, yMax, zMin);
        Location c110 = new Location(region.world, xMax, yMax, zMin);

        Location c001 = new Location(region.world, xMin, yMin, zMax);
        Location c101 = new Location(region.world, xMax, yMin, zMax);
        Location c011 = new Location(region.world, xMin, yMax, zMax);
        Location c111 = new Location(region.world, xMax, yMax, zMax);

        DisplayCube cube = activeCubes.get(uuid);
        List<BlockDisplayNode> markers = activeMarkers.get(uuid);
        int interpTicks = interpolate ? INTERPOLATION_TICKS : 0;

        // --- CUBE ---
        if (cube == null) {
            // First time: create & spawn
            cube = new DisplayCube(
                    cubeFrom,
                    cubeTo,
                    0.10f,
                    Material.WHITE_CONCRETE,
                    true,
                    0xAA11EE
            );
            cube.spawn(viewers);
            activeCubes.put(uuid, cube);
        } else {
            // Update existing cube.
            // You need to implement something like this on DisplayCube:
            //   public void update(Location from, Location to, Collection<Player> viewers, int interpolationTicks)
            cube.update(cubeFrom, cubeTo, viewers, interpTicks);
        }

        // --- MARKERS ---
        List<Location> corners = List.of(c000, c100, c010, c110, c001, c101, c011, c111);

        if (markers == null || markers.size() != 8) {
            // (Re)create markers
            if (markers != null) {
                for (BlockDisplayNode node : markers) {
                    if (node != null) {
                        node.despawn(viewers);
                    }
                }
            }

            markers = new ArrayList<>(8);
            float markerSize = 0.20f;

            for (Location loc : corners) {
                markers.add(spawnMarker(loc, markerSize, viewers));
            }

            markers.removeIf(Objects::isNull);
            activeMarkers.put(uuid, markers);
        } else {
            // Update existing markers in-place with interpolation
            for (int i = 0; i < 8; i++) {
                BlockDisplayNode node = markers.get(i);
                Location loc = corners.get(i);
                if (node == null || loc == null) continue;

                node.location(loc);
                node.update(viewers, interpTicks);
            }
        }
    }

    private BlockDisplayNode spawnMarker(Location loc,
                                         float size,
                                         List<Player> viewers) {
        if (loc == null) return null;

        BlockDisplayNode node = new BlockDisplayNode()
                .location(loc)
                .size(size)
                .material(Material.COAL_BLOCK);

        node.glow(Color.BLACK.asRGB());
        node.spawn(viewers);
        return node;
    }

    private void clearPlayerDisplays(Player player, boolean clearRegion) {
        UUID uuid = player.getUniqueId();
        List<Player> viewers = List.of(player);

        DisplayCube cube = activeCubes.remove(uuid);
        if (cube != null) {
            cube.despawn(viewers);
        }

        List<BlockDisplayNode> nodes = activeMarkers.remove(uuid);
        if (nodes != null) {
            for (BlockDisplayNode node : nodes) {
                if (node != null) {
                    node.despawn(viewers);
                }
            }
        }

        if (clearRegion) {
            activeRegions.remove(uuid);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearPlayerDisplays(event.getPlayer(), true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        clearPlayerDisplays(event.getPlayer(), true);
    }
}
