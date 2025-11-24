package de.terranova.terraRenderer;

import de.terranova.terraRenderer.refactor.BlockDisplayNode;
import de.terranova.terraRenderer.refactor.DisplayGroups.DisplayLine;
import de.terranova.terraRenderer.refactor.DisplayMath;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import org.bukkit.entity.Player;
import org.joml.Vector3f;

import java.util.List;

public class DebugCommand implements CommandExecutor {

    private final TerraRenderer plugin;

    public DebugCommand(TerraRenderer plugin) {
        this.plugin = plugin;
    }
    Location lastpos = null;
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!(sender instanceof Player p )) {
            return false;
        }
        Vector3f scale = new Vector3f(0.4f,0.4f,0.4f);
        BlockDisplayNode node = new BlockDisplayNode()
                .location(DisplayMath.roundGrid(p.getLocation()))
                .scale(scale)
                .rotationEulerDeg(new Vector3f(0f,0f,0f))
                .material(Material.LIGHT_BLUE_CONCRETE)
                .onClick((ctx) -> {
                    ctx.node().material(Material.RED_CONCRETE);
                    ctx.node().update(List.of(ctx.player()));
                })
                .glow(0x020202);
        node.spawn(List.of(p));

        float thickness = 0.2f;
        if (lastpos != null) {
            Location from = DisplayMath.roundGrid(lastpos);
            Location to   = DisplayMath.roundGrid(p.getLocation());

            DisplayLine line = new DisplayLine(
                    from,
                    to,
                    thickness,
                    Material.WHITE_CONCRETE,
                    true,
                    0xAA2288     // or any line glow you want
            );
            line.spawn(List.of(p));
        }
        lastpos = p.getLocation();

        return true;

    }
}
