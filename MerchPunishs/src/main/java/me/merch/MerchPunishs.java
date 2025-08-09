package me.merch;

import me.merch.commands.implementation.Commands;
import me.merch.database.DatabaseManager;
import me.merch.listeners.ChatListener;
import me.merch.listeners.InventoryClickListener;
import me.merch.listeners.JoinListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class MerchPunishs extends JavaPlugin {

    private static MerchPunishs plugin;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();

        getLogger().info("Connecting to database...");
        databaseManager = new DatabaseManager();
        if (databaseManager.connect()) {
            getLogger().info("Successfully connected to the database.");
            registerComponents();
        } else {
            getLogger().severe("Failed to connect to the database! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
    }

    public static MerchPunishs getPlugin() {
        return plugin;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    private void registerComponents() {
        // Registering listeners
        getServer().getPluginManager().registerEvents(new JoinListener(), this);
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(new InventoryClickListener(), this);

        // Registering a single class for all commands
        Commands commandsHandler = new Commands();
        getCommand("ban").setExecutor(commandsHandler);
        getCommand("tempban").setExecutor(commandsHandler);
        getCommand("unban").setExecutor(commandsHandler);
        getCommand("mute").setExecutor(commandsHandler);
        getCommand("tempmute").setExecutor(commandsHandler);
        getCommand("unmute").setExecutor(commandsHandler);
        getCommand("ipban").setExecutor(commandsHandler);
        getCommand("kick").setExecutor(commandsHandler);
        getCommand("history").setExecutor(commandsHandler);
        getCommand("mpunishs").setExecutor(commandsHandler);

        // Registering tab completer for all commands that need it
        getCommand("ban").setTabCompleter(commandsHandler);
        getCommand("tempban").setTabCompleter(commandsHandler);
        getCommand("unban").setTabCompleter(commandsHandler);
        getCommand("mute").setTabCompleter(commandsHandler);
        getCommand("tempmute").setTabCompleter(commandsHandler);
        getCommand("unmute").setTabCompleter(commandsHandler);
        getCommand("ipban").setTabCompleter(commandsHandler);
        getCommand("kick").setTabCompleter(commandsHandler);
        getCommand("history").setTabCompleter(commandsHandler);
    }
}
