package me.merch;

import me.merch.PunishmentCommands;
import me.merch.DatabaseManager;
import me.merch.PunishmentListeners;
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
        PunishmentCommands punishmentCommands = new PunishmentCommands(this);
        getServer().getPluginManager().registerEvents(new PunishmentListeners(), this);

        // Register all command executors and tab completers
        getCommand("ban").setExecutor(punishmentCommands.new BanCommand());
        getCommand("ban").setTabCompleter(punishmentCommands.new PunishmentTabCompleter());

        getCommand("tempban").setExecutor(punishmentCommands.new TempBanCommand());
        getCommand("tempban").setTabCompleter(punishmentCommands.new PunishmentTabCompleter());

        getCommand("unban").setExecutor(punishmentCommands.new UnbanCommand());
        getCommand("unban").setTabCompleter(punishmentCommands.new PunishmentTabCompleter());

        getCommand("mute").setExecutor(punishmentCommands.new MuteCommand());
        getCommand("mute").setTabCompleter(punishmentCommands.new PunishmentTabCompleter());

        getCommand("tempmute").setExecutor(punishmentCommands.new TempMuteCommand());
        getCommand("tempmute").setTabCompleter(punishmentCommands.new PunishmentTabCompleter());

        getCommand("unmute").setExecutor(punishmentCommands.new UnmuteCommand());
        getCommand("unmute").setTabCompleter(punishmentCommands.new PunishmentTabCompleter());

        getCommand("ipban").setExecutor(punishmentCommands.new IPBanCommand());
        getCommand("ipban").setTabCompleter(punishmentCommands.new PunishmentTabCompleter());

        getCommand("kick").setExecutor(punishmentCommands.new KickCommand());
        getCommand("kick").setTabCompleter(punishmentCommands.new PunishmentTabCompleter());

        getCommand("history").setExecutor(punishmentCommands.new HistoryCommand());
        getCommand("history").setTabCompleter(punishmentCommands.new PunishmentTabCompleter());

        getCommand("mpunishs").setExecutor(punishmentCommands.new HelpCommand());
    }
}
