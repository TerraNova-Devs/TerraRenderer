package de.terranova.terraRenderer;

import net.minecraft.world.entity.Display;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
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
        Display.BlockDisplay display = DisplayUtil.createBlockDisplay(DisplayUtil.roundGrid(p.getLocation()),Material.LIGHT_BLUE_CONCRETE, scale ,new Vector3f(0f,0f,0f));
        DisplayUtil.sendBlockDisplay(display,List.of(p));

        float thickness = 0.2f;
        if(lastpos != null) {
            DisplayUtil.drawLine(DisplayUtil.roundGrid(lastpos),DisplayUtil.roundGrid(p.getLocation()),Material.WHITE_CONCRETE,thickness,List.of(p));
        }
        lastpos = p.getLocation();
        return true;
    }
}
