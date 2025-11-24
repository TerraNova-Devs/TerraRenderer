package de.terranova.terraRenderer;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import de.terranova.terraRenderer.refactor.BlockDisplayNode;
import de.terranova.terraRenderer.refactor.DisplayGroups.DisplayCube;
import de.terranova.terraRenderer.refactor.DisplayPackets;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Visualizes the WorldEdit cuboid selection as:
 * - one wireframe cube (DisplayCube)
 * - simple BlockDisplayNode corner markers (pos1, pos2 + 6 others)
 */
public class WorldeditEventListener implements Listener {

    private final JavaPlugin plugin;
    private final WorldEditPlugin worldEdit;

    // Per player: the active wireframe cube
    private final Map<UUID, DisplayCube> activeCubes = new HashMap<>();
    // Per player: all active corner display entity IDs
    private final Map<UUID, List<Integer>> activeCornerDisplays = new HashMap<>();

    public WorldeditEventListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.worldEdit = (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");

        if (this.worldEdit == null) {
            throw new IllegalStateException("WorldEditPlugin nicht gefunden!");
        }
    }

    @EventHandler
    public void onSelectionChange(PlayerInteractEvent event) {
        // Only main hand, otherwise Off-Hand triggers as well
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (player == null) return;

        // Only react if holding the default WorldEdit wand (wooden axe)
        if (player.getInventory().getItemInMainHand().getType() != Material.WOODEN_AXE) return;

        // Let WorldEdit update its selection first
        Bukkit.getScheduler().runTask(plugin, () -> readSelection(player));
    }

    private void readSelection(Player player) {
        LocalSession session = worldEdit.getSession(player);
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(player.getWorld());

        Region region;
        try {
            region = session.getSelection(weWorld);
        } catch (IncompleteRegionException e) {
            // Selection incomplete (pos1 or pos2 missing) -> clear visualization
            clearPlayerDisplays(player);
            return;
        } catch (Exception ex) {
            ex.printStackTrace();
            clearPlayerDisplays(player);
            return;
        }

        if (!(region instanceof CuboidRegion cuboid)) {
            clearPlayerDisplays(player);
            return;
        }

        BlockVector3 min = cuboid.getMinimumPoint();
        BlockVector3 max = cuboid.getMaximumPoint();

        String worldName = player.getWorld().getName();
        player.sendMessage("§7Auswahl geändert (WorldEdit-Style):");
        player.sendMessage("§7Welt: §e" + worldName);
        player.sendMessage("§7Min: §e" + min.x() + ", " + min.y() + ", " + min.z());
        player.sendMessage("§7Max: §e" + max.x() + ", " + max.y() + ", " + max.z());

        clearPlayerDisplays(player);
        drawCubeSelection(player, min, max);
    }

    /**
     * Despawns the existing wireframe cube and removes all corner blocks.
     */
    private void clearPlayerDisplays(Player player) {
        UUID uuid = player.getUniqueId();
        List<Player> viewers = List.of(player);

        // Despawn previous cube (this removes all its internal edges)
        DisplayCube cube = activeCubes.remove(uuid);
        if (cube != null) {
            cube.despawn(viewers);
        }

        // Remove all corner displays by entityId
        List<Integer> cornerIds = activeCornerDisplays.remove(uuid);
        if (cornerIds != null && !cornerIds.isEmpty()) {
            for (int id : cornerIds) {
                DisplayPackets.remove(id, viewers);
            }
        }
    }

    /**
     * One wireframe cube for the selection, plus simple BlockDisplayNode corners.
     */
    private void drawCubeSelection(Player player, BlockVector3 min, BlockVector3 max) {
        // WorldEdit regions are block-inclusive, so outer edge goes to max+1
        double xMin = min.x();
        double yMin = min.y();
        double zMin = min.z();

        double xMax = max.x() + 1;
        double yMax = max.y() + 1;
        double zMax = max.z() + 1;

        var world = player.getWorld();
        List<Player> viewers = List.of(player);
        UUID uuid = player.getUniqueId();

        List<Integer> cornerIds = new ArrayList<>();

        // === Wireframe selection cube ===
        // DisplayCube is implemented as 12 line segments (BlockDisplays).
        Material lineMat = Material.WHITE_CONCRETE; // invisible; change to GLASS etc. if you want visible edges
        int lineGlow = 0x22CCDD;
        float lineThickness = 0.10f;

        Location cubeFrom = new Location(world, xMin, yMin, zMin);
        Location cubeTo   = new Location(world, xMax, yMax, zMax);

        DisplayCube selectionCube = new DisplayCube(
                cubeFrom,
                cubeTo,
                lineThickness,
                lineMat,
                true,
                lineGlow
        );
        selectionCube.spawn(viewers);

        // store cube for later despawn
        activeCubes.put(uuid, selectionCube);

        // === Corner positions ===
        Location c000 = new Location(world, xMin, yMin, zMin); // pos1 corner (min,min,min)
        Location c100 = new Location(world, xMax, yMin, zMin);
        Location c010 = new Location(world, xMin, yMax, zMin);
        Location c110 = new Location(world, xMax, yMax, zMin);

        Location c001 = new Location(world, xMin, yMin, zMax);
        Location c101 = new Location(world, xMax, yMin, zMax);
        Location c011 = new Location(world, xMin, yMax, zMax);
        Location c111 = new Location(world, xMax, yMax, zMax); // pos2 corner (max,max,max)

        float cornerSize = 0.20f;

        // === Special WorldEdit corners as simple BlockDisplayNodes ===
        // pos1 (min) -> emerald block with green glow
        cornerIds.add(spawnCornerBlock(c000, Material.EMERALD_BLOCK, cornerSize, Color.LIME, viewers));
        // pos2 (max) -> redstone block with red glow
        cornerIds.add(spawnCornerBlock(c111, Material.REDSTONE_BLOCK, cornerSize, Color.RED, viewers));

        // Remaining 6 corners as black blocks with black glow
        Material defaultCornerMat = Material.BLACK_CONCRETE;
        Color defaultCornerGlow = Color.BLACK;

        cornerIds.add(spawnCornerBlock(c100, defaultCornerMat, cornerSize, defaultCornerGlow, viewers));
        cornerIds.add(spawnCornerBlock(c010, defaultCornerMat, cornerSize, defaultCornerGlow, viewers));
        cornerIds.add(spawnCornerBlock(c110, defaultCornerMat, cornerSize, defaultCornerGlow, viewers));

        cornerIds.add(spawnCornerBlock(c001, defaultCornerMat, cornerSize, defaultCornerGlow, viewers));
        cornerIds.add(spawnCornerBlock(c101, defaultCornerMat, cornerSize, defaultCornerGlow, viewers));
        cornerIds.add(spawnCornerBlock(c011, defaultCornerMat, cornerSize, defaultCornerGlow, viewers));

        cornerIds.removeIf(id -> id < 0);
        activeCornerDisplays.put(uuid, cornerIds);
    }

    /**
     * Simple BlockDisplay corner marker, centered at loc, with given size and glow.
     * This mimics your old "corner block" behavior.
     */
    private int spawnCornerBlock(Location loc,
                                 Material mat,
                                 float size,
                                 Color glow,
                                 List<Player> viewers) {
        if (loc == null) return -1;

        BlockDisplayNode node = new BlockDisplayNode()
                .location(loc)
                .size(size)
                .material(mat);

        if (glow != null) {
            node.glow(glow.asRGB());
        } else {
            node.glow(); // no glow
        }

        node.spawn(viewers);
        return node.getDisplayEntityId();
    }
}
