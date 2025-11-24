package de.terranova.terraRenderer;

import de.terranova.terraRenderer.refactor.Listener.BreezeToolListener;
import de.terranova.terraRenderer.refactor.Listener.DisplayPacketListener;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class TerraRenderer extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("TerraRenderer enabled.");
        Bukkit.getPluginManager().registerEvents(new WorldeditEventListener(this), this);
        getServer().getPluginManager().registerEvents(new DisplayPacketListener(), this);
        // /debug -> DebugCommand
        Bukkit.getPluginManager().registerEvents(new BreezeToolListener(this), this);
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