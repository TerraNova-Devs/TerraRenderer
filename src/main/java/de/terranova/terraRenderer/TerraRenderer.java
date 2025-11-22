package de.terranova.terraRenderer;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class TerraRenderer extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("TerraRenderer enabled.");
        Bukkit.getPluginManager().registerEvents(new WorldeditEventListener(this), this);

        // /debug -> DebugCommand
        PluginCommand debug = getCommand("debug");
        if (debug != null) {
            debug.setExecutor(new DebugCommand(this));
        } else {
            getLogger().severe("Command 'debug' not found in plugin.yml!");
        }

    }

    @Override
    public void onDisable() {
        getLogger().info("TerraRenderer disabled.");
    }
}