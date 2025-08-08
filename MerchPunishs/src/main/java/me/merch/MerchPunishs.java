package me.merch;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
            registerCommands();
            registerListeners();
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

    private void registerCommands() {
        // Registering commands and their executors
        getCommand("ban").setExecutor(new BanCommand());
        getCommand("tempban").setExecutor(new TempBanCommand());
        getCommand("unban").setExecutor(new UnbanCommand());
        getCommand("mute").setExecutor(new MuteCommand());
        getCommand("tempmute").setExecutor(new TempMuteCommand());
        getCommand("unmute").setExecutor(new UnmuteCommand());
        getCommand("ipban").setExecutor(new IPBanCommand());
        getCommand("kick").setExecutor(new KickCommand());
        getCommand("history").setExecutor(new HistoryCommand());
        getCommand("mpunishs").setExecutor(new HelpCommand());

        // Registering tab completers
        PunishmentTabCompleter tabCompleter = new PunishmentTabCompleter();
        getCommand("ban").setTabCompleter(tabCompleter);
        getCommand("tempban").setTabCompleter(tabCompleter);
        getCommand("unban").setTabCompleter(tabCompleter);
        getCommand("mute").setTabCompleter(tabCompleter);
        getCommand("tempmute").setTabCompleter(tabCompleter);
        getCommand("unmute").setTabCompleter(tabCompleter);
        getCommand("ipban").setTabCompleter(tabCompleter);
        getCommand("kick").setTabCompleter(tabCompleter);
        getCommand("history").setTabCompleter(tabCompleter);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new JoinListener(), this);
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(new InventoryClickListener(), this);
    }

    public static class Punishment {
        public enum Type { BAN, TEMPBAN, MUTE, TEMPMUTE, IPBAN, KICK }

        private final Type type;
        private final UUID playerUUID;
        private final String playerName;
        private final String staffName;
        private final String reason;
        private final long timestamp;
        private final long duration;

        public Punishment(Type type, UUID playerUUID, String playerName, String staffName, String reason, long timestamp, long duration) {
            this.type = type;
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.staffName = staffName;
            this.reason = reason;
            this.timestamp = timestamp;
            this.duration = duration;
        }

        public Type getType() { return type; }
        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public String getStaffName() { return staffName; }
        public String getReason() { return reason; }
        public long getTimestamp() { return timestamp; }
        public long getDuration() { return duration; }

        public boolean isExpired() {
            return duration != -1 && (System.currentTimeMillis() > timestamp + duration);
        }
    }

    public class DatabaseManager {

        private final String databaseType;

        private Connection connection;

        private MongoClient mongoClient;
        private MongoDatabase mongoDatabase;
        private MongoCollection<Document> punishmentsCollection;

        public DatabaseManager() {
            this.databaseType = getConfig().getString("database.type", "SQLite");
        }

        public boolean connect() {
            try {
                switch (databaseType) {
                    case "MySQL":
                        String mysqlUrl = "jdbc:mysql://" +
                                getConfig().getString("database.mysql.host") + ":" +
                                getConfig().getInt("database.mysql.port") + "/" +
                                getConfig().getString("database.mysql.database") +
                                "?user=" + getConfig().getString("database.mysql.username") +
                                "&password=" + getConfig().getString("database.mysql.password");
                        connection = DriverManager.getConnection(mysqlUrl);
                        setupSQLTable("MySQL");
                        break;
                    case "SQLite":
                        Class.forName("org.sqlite.JDBC");
                        connection = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/punishments.db");
                        setupSQLTable("SQLite");
                        break;
                    case "MongoDB":
                        String mongoUri = getConfig().getString("database.mongodb.uri");
                        mongoClient = MongoClients.create(mongoUri);
                        mongoDatabase = mongoClient.getDatabase(getConfig().getString("database.mongodb.database"));
                        punishmentsCollection = mongoDatabase.getCollection("punishments");
                        break;
                    default:
                        getLogger().severe("Invalid database type specified in config.yml.");
                        return false;
                }
                return true;
            } catch (Exception e) {
                getLogger().severe("Could not connect to database: " + e.getMessage());
                return false;
            }
        }

        private void setupSQLTable(String type) throws SQLException {
            String query = "CREATE TABLE IF NOT EXISTS punishments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(16) NOT NULL," +
                    "staff_name VARCHAR(16) NOT NULL," +
                    "reason TEXT NOT NULL," +
                    "type VARCHAR(10) NOT NULL," +
                    "timestamp BIGINT NOT NULL," +
                    "duration BIGINT NOT NULL," +
                    "unpunished BOOLEAN NOT NULL DEFAULT 0)";
            if (type.equals("MySQL")) {
                query = query.replace("AUTOINCREMENT", "AUTO_INCREMENT")
                        .replace("INTEGER", "INT")
                        .replace("BOOLEAN", "TINYINT(1)");
            }
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.execute();
            }
        }

        public void disconnect() {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
                if (mongoClient != null) {
                    mongoClient.close();
                }
            } catch (SQLException e) {
                getLogger().severe("Failed to disconnect from database: " + e.getMessage());
            }
        }

        public void addPunishment(Punishment punishment) {
            getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    switch (databaseType) {
                        case "SQLite":
                        case "MySQL":
                            String sql = "INSERT INTO punishments (player_uuid, player_name, staff_name, reason, type, timestamp, duration) VALUES (?, ?, ?, ?, ?, ?, ?)";
                            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                                statement.setString(1, punishment.getPlayerUUID().toString());
                                statement.setString(2, punishment.getPlayerName());
                                statement.setString(3, punishment.getStaffName());
                                statement.setString(4, punishment.getReason());
                                statement.setString(5, punishment.getType().toString());
                                statement.setLong(6, punishment.getTimestamp());
                                statement.setLong(7, punishment.getDuration());
                                statement.executeUpdate();
                            }
                            break;
                        case "MongoDB":
                            Document document = new Document("player_uuid", punishment.getPlayerUUID().toString())
                                    .append("player_name", punishment.getPlayerName())
                                    .append("staff_name", punishment.getStaffName())
                                    .append("reason", punishment.getReason())
                                    .append("type", punishment.getType().toString())
                                    .append("timestamp", punishment.getTimestamp())
                                    .append("duration", punishment.getDuration())
                                    .append("unpunished", false);
                            punishmentsCollection.insertOne(document);
                            break;
                    }
                } catch (SQLException e) {
                    getLogger().severe("Error adding punishment to SQL database: " + e.getMessage());
                }
            });
        }

        public Punishment getActivePunishment(UUID uuid, Punishment.Type... types) {
            try {
                switch (databaseType) {
                    case "SQLite":
                    case "MySQL":
                        StringBuilder queryBuilder = new StringBuilder("SELECT * FROM punishments WHERE player_uuid = ? AND unpunished = 0 AND type IN (");
                        for (int i = 0; i < types.length; i++) {
                            queryBuilder.append("?");
                            if (i < types.length - 1) {
                                queryBuilder.append(",");
                            }
                        }
                        queryBuilder.append(") ORDER BY timestamp DESC LIMIT 1");

                        try (PreparedStatement statement = connection.prepareStatement(queryBuilder.toString())) {
                            statement.setString(1, uuid.toString());
                            for (int i = 0; i < types.length; i++) {
                                statement.setString(i + 2, types[i].toString());
                            }
                            ResultSet resultSet = statement.executeQuery();
                            if (resultSet.next()) {
                                Punishment punishment = createPunishmentFromSQLResult(resultSet);
                                if (!punishment.isExpired()) {
                                    return punishment;
                                }
                            }
                        }
                        break;
                    case "MongoDB":
                        List<Document> documents = punishmentsCollection.find(
                                new Document("player_uuid", uuid.toString())
                                        .append("unpunished", false)
                                        .append("type", new Document("$in", Arrays.stream(types).map(Enum::toString).collect(Collectors.toList())))
                        ).sort(new Document("timestamp", -1)).limit(1).into(new ArrayList<>());
                        if (!documents.isEmpty()) {
                            Punishment punishment = createPunishmentFromMongoDocument(documents.get(0));
                            if (!punishment.isExpired()) {
                                return punishment;
                            }
                        }
                        break;
                }
            } catch (SQLException e) {
                getLogger().severe("Error getting active punishment from SQL database: " + e.getMessage());
            }
            return null;
        }

        public Punishment getActiveBan(UUID uuid) {
            return getActivePunishment(uuid, Punishment.Type.BAN, Punishment.Type.TEMPBAN, Punishment.Type.IPBAN);
        }

        public Punishment getActiveMute(UUID uuid) {
            return getActivePunishment(uuid, Punishment.Type.MUTE, Punishment.Type.TEMPMUTE);
        }

        public List<Punishment> getHistory(UUID uuid) {
            List<Punishment> history = new ArrayList<>();
            try {
                switch (databaseType) {
                    case "SQLite":
                    case "MySQL":
                        String sql = "SELECT * FROM punishments WHERE player_uuid = ? ORDER BY timestamp DESC";
                        try (PreparedStatement statement = connection.prepareStatement(sql)) {
                            statement.setString(1, uuid.toString());
                            ResultSet resultSet = statement.executeQuery();
                            while (resultSet.next()) {
                                history.add(createPunishmentFromSQLResult(resultSet));
                            }
                        }
                        break;
                    case "MongoDB":
                        List<Document> documents = punishmentsCollection.find(
                                new Document("player_uuid", uuid.toString())
                        ).sort(new Document("timestamp", -1)).into(new ArrayList<>());
                        for (Document doc : documents) {
                            history.add(createPunishmentFromMongoDocument(doc));
                        }
                        break;
                }
            } catch (SQLException e) {
                getLogger().severe("Error getting history from SQL database: " + e.getMessage());
            }
            return history;
        }

        public void unPunish(UUID uuid, Punishment.Type type) {
            getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    switch (databaseType) {
                        case "SQLite":
                        case "MySQL":
                            String sql = "UPDATE punishments SET unpunished = 1 WHERE player_uuid = ? AND type = ? AND unpunished = 0";
                            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                                statement.setString(1, uuid.toString());
                                statement.setString(2, type.toString());
                                statement.executeUpdate();
                            }
                            break;
                        case "MongoDB":
                            punishmentsCollection.updateMany(
                                    new Document("player_uuid", uuid.toString())
                                            .append("type", type.toString())
                                            .append("unpunished", false),
                                    new Document("$set", new Document("unpunished", true))
                            );
                            break;
                    }
                } catch (SQLException e) {
                    getLogger().severe("Error unpunishing player: " + e.getMessage());
                }
            });
        }

        private Punishment createPunishmentFromSQLResult(ResultSet resultSet) throws SQLException {
            return new Punishment(
                    Punishment.Type.valueOf(resultSet.getString("type")),
                    UUID.fromString(resultSet.getString("player_uuid")),
                    resultSet.getString("player_name"),
                    resultSet.getString("staff_name"),
                    resultSet.getString("reason"),
                    resultSet.getLong("timestamp"),
                    resultSet.getLong("duration")
            );
        }

        private Punishment createPunishmentFromMongoDocument(Document document) {
            return new Punishment(
                    Punishment.Type.valueOf(document.getString("type")),
                    UUID.fromString(document.getString("player_uuid")),
                    document.getString("player_name"),
                    document.getString("staff_name"),
                    document.getString("reason"),
                    document.getLong("timestamp"),
                    document.getLong("duration")
            );
        }
    }

    private class PunishmentTabCompleter implements TabCompleter {
        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) {
                List<String> playerNames = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    playerNames.add(player.getName());
                }
                return playerNames;
            }
            return new ArrayList<>();
        }
    }

    private abstract class AbstractPunishmentCommand implements CommandExecutor {
        private final Punishment.Type type;
        private final int requiredArgs;

        public AbstractPunishmentCommand(Punishment.Type type, int requiredArgs) {
            this.type = type;
            this.requiredArgs = requiredArgs;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("merchpunishs." + type.toString().toLowerCase())) {
                sender.sendMessage(color(getConfig().getString("messages.no_permission")));
                return true;
            }

            if (args.length < requiredArgs) {
                sender.sendMessage(ChatColor.RED + "Usage: " + command.getUsage());
                return true;
            }

            final String targetName = args[0];
            String reason = String.join(" ", Arrays.copyOfRange(args, requiredArgs - 1, args.length));
            final String staffName = sender instanceof Player ? sender.getName() : "Console";
            long duration = -1;

            if (type == Punishment.Type.TEMPBAN || type == Punishment.Type.TEMPMUTE) {
                try {
                    duration = parseDuration(args[1]);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid time format. Example: 1d, 12h, 30m.");
                    return true;
                }
                reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            }

            final long finalDuration = duration;
            final String finalReason = reason;

            getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                OfflinePlayer target = Arrays.stream(Bukkit.getOfflinePlayers())
                        .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(targetName))
                        .findFirst().orElse(null);

                if (target == null) {
                    sender.sendMessage(color(getConfig().getString("messages.player_not_found")));
                    return;
                }

                Punishment punishment = new Punishment(type, target.getUniqueId(), target.getName(), staffName, finalReason, System.currentTimeMillis(), finalDuration);
                databaseManager.addPunishment(punishment);
                sender.sendMessage(color(getConfig().getString("messages.punishment_applied").replace("%player%", target.getName())));

                if (type == Punishment.Type.BAN || type == Punishment.Type.TEMPBAN || type == Punishment.Type.IPBAN || type == Punishment.Type.KICK) {
                    Player onlineTarget = target.getPlayer();
                    if (onlineTarget != null) {
                        String kickMessage = color(getConfig().getString("messages.player_banned")
                                .replace("%reason%", finalReason)
                                .replace("%expiry%", finalDuration == -1 ? "Never" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(punishment.getTimestamp() + punishment.getDuration()))));

                        getServer().getScheduler().runTask(plugin, () -> onlineTarget.kickPlayer(kickMessage));
                    }
                }
            });

            return true;
        }

        private long parseDuration(String time) {
            String[] parts = time.split("(?<=\\d)(?=\\D)|(?<=\\D)(?=\\d)");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid time format");
            }
            int value = Integer.parseInt(parts[0]);
            String unit = parts[1].toLowerCase();

            switch (unit) {
                case "s": return TimeUnit.SECONDS.toMillis(value);
                case "m": return TimeUnit.MINUTES.toMillis(value);
                case "h": return TimeUnit.HOURS.toMillis(value);
                case "d": return TimeUnit.DAYS.toMillis(value);
                case "w": return TimeUnit.DAYS.toMillis(value * 7);
                default: throw new IllegalArgumentException("Invalid time unit");
            }
        }
    }

    private class BanCommand extends AbstractPunishmentCommand {
        public BanCommand() {
            super(Punishment.Type.BAN, 2);
        }
    }

    private class TempBanCommand extends AbstractPunishmentCommand {
        public TempBanCommand() {
            super(Punishment.Type.TEMPBAN, 3);
        }
    }

    private class MuteCommand extends AbstractPunishmentCommand {
        public MuteCommand() {
            super(Punishment.Type.MUTE, 2);
        }
    }

    private class TempMuteCommand extends AbstractPunishmentCommand {
        public TempMuteCommand() {
            super(Punishment.Type.TEMPMUTE, 3);
        }
    }

    private class IPBanCommand extends AbstractPunishmentCommand {
        public IPBanCommand() {
            super(Punishment.Type.IPBAN, 2);
        }
    }

    private class KickCommand extends AbstractPunishmentCommand {
        public KickCommand() {
            super(Punishment.Type.KICK, 2);
        }
    }

    private class UnbanCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("merchpunishs.unban")) {
                sender.sendMessage(color(getConfig().getString("messages.no_permission")));
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /unban <player>");
                return true;
            }

            final String targetName = args[0];
            getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                OfflinePlayer target = Arrays.stream(Bukkit.getOfflinePlayers())
                        .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(targetName))
                        .findFirst().orElse(null);

                if (target == null) {
                    sender.sendMessage(color(getConfig().getString("messages.player_not_found")));
                    return;
                }

                databaseManager.unPunish(target.getUniqueId(), Punishment.Type.BAN);
                databaseManager.unPunish(target.getUniqueId(), Punishment.Type.TEMPBAN);
                databaseManager.unPunish(target.getUniqueId(), Punishment.Type.IPBAN);
                sender.sendMessage(color(getConfig().getString("messages.unban_success").replace("%player%", target.getName())));
            });
            return true;
        }
    }

    private class UnmuteCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("merchpunishs.unmute")) {
                sender.sendMessage(color(getConfig().getString("messages.no_permission")));
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /unmute <player>");
                return true;
            }

            final String targetName = args[0];
            getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                OfflinePlayer target = Arrays.stream(Bukkit.getOfflinePlayers())
                        .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(targetName))
                        .findFirst().orElse(null);

                if (target == null) {
                    sender.sendMessage(color(getConfig().getString("messages.player_not_found")));
                    return;
                }

                databaseManager.unPunish(target.getUniqueId(), Punishment.Type.MUTE);
                databaseManager.unPunish(target.getUniqueId(), Punishment.Type.TEMPMUTE);
                sender.sendMessage(color(getConfig().getString("messages.unmute_success").replace("%player%", target.getName())));
            });
            return true;
        }
    }

    private class HelpCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("merchpunishs.help")) {
                sender.sendMessage(color(getConfig().getString("messages.no_permission")));
                return true;
            }

            List<String> helpMessages = getConfig().getStringList("messages.help_message");
            for (String message : helpMessages) {
                sender.sendMessage(color(message));
            }
            return true;
        }
    }

    private class HistoryCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                return true;
            }

            if (!sender.hasPermission("merchpunishs.history")) {
                sender.sendMessage(color(getConfig().getString("messages.no_permission")));
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /history <player>");
                return true;
            }

            final String targetName = args[0];
            final Player player = (Player) sender;

            getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                OfflinePlayer target = Arrays.stream(Bukkit.getOfflinePlayers())
                        .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(targetName))
                        .findFirst().orElse(null);

                if (target == null) {
                    player.sendMessage(color(getConfig().getString("messages.player_not_found")));
                    return;
                }

                List<Punishment> history = databaseManager.getHistory(target.getUniqueId());

                if (history.isEmpty()) {
                    player.sendMessage(color(getConfig().getString("messages.prefix") + "&a&lNo punishment history found for " + target.getName()));
                    return;
                }

                getServer().getScheduler().runTask(plugin, () -> {
                    openHistoryMenu(player, target.getName(), history);
                });
            });

            return true;
        }

        private void openHistoryMenu(Player player, String targetName, List<Punishment> history) {
            int size = getConfig().getInt("history_menu.size", 54);
            Inventory menu = Bukkit.createInventory(null, size, color(getConfig().getString("messages.history_title").replace("%player%", targetName)));

            ItemStack fillItem = createFillItem();
            for (int i = 0; i < size; i++) {
                menu.setItem(i, fillItem);
            }

            for (int i = 0; i < history.size() && i < size; i++) {
                Punishment punishment = history.get(i);
                menu.setItem(i, createPunishmentItem(punishment));
            }

            player.openInventory(menu);
        }

        private ItemStack createFillItem() {
            String materialName = getConfig().getString("history_menu.fill_item.material", "BLACK_STAINED_GLASS_PANE");
            String name = getConfig().getString("history_menu.fill_item.name", " ");
            ItemStack item = new ItemStack(Material.getMaterial(materialName));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(color(name));
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createPunishmentItem(Punishment punishment) {
            ItemStack item;
            switch (punishment.getType()) {
                case BAN:
                case TEMPBAN:
                case IPBAN:
                    item = new ItemStack(Material.RED_WOOL);
                    break;
                case MUTE:
                case TEMPMUTE:
                    item = new ItemStack(Material.ORANGE_WOOL);
                    break;
                case KICK:
                default:
                    item = new ItemStack(Material.LIME_WOOL);
                    break;
            }

            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(color("&c&l" + punishment.getType().toString()));
            List<String> lore = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            for (String line : getConfig().getStringList("history_menu.item_lore")) {
                line = line.replace("%type%", punishment.getType().toString())
                        .replace("%reason%", punishment.getReason())
                        .replace("%staff%", punishment.getStaffName())
                        .replace("%date%", sdf.format(new Date(punishment.getTimestamp())))
                        .replace("%expiry%", punishment.getDuration() == -1 ? "Never" : sdf.format(new Date(punishment.getTimestamp() + punishment.getDuration())));
                lore.add(color(line));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;
        }
    }

    private class JoinListener implements Listener {
        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                Punishment activeBan = databaseManager.getActiveBan(player.getUniqueId());

                if (activeBan != null) {
                    final String reason = activeBan.getReason();
                    final String expiry = activeBan.getDuration() == -1 ? "Never" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(activeBan.getTimestamp() + activeBan.getDuration()));
                    final String banMessage = getConfig().getString("messages.player_banned")
                            .replace("%reason%", reason)
                            .replace("%expiry%", expiry);

                    getServer().getScheduler().runTask(plugin, () -> player.kickPlayer(color(banMessage)));
                }
            });
        }
    }

    private class ChatListener implements Listener {
        @EventHandler
        public void onPlayerChat(AsyncPlayerChatEvent event) {
            Player player = event.getPlayer();

            // Fixed the mute bug by performing a synchronous check.
            Punishment activeMute = databaseManager.getActiveMute(player.getUniqueId());
            if (activeMute != null) {
                event.setCancelled(true);
                final String reason = activeMute.getReason();
                final String expiry = activeMute.getDuration() == -1 ? "Never" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(activeMute.getTimestamp() + activeMute.getDuration()));
                final String muteMessage = getConfig().getString("messages.player_muted")
                        .replace("%reason%", reason)
                        .replace("%expiry%", expiry);
                player.sendMessage(color(muteMessage));
            }
        }
    }

    private class InventoryClickListener implements Listener {
        @EventHandler
        public void onInventoryClick(InventoryClickEvent event) {
            if (event.getView().getTitle().contains(color(getConfig().getString("messages.history_title").replace("%player%", "")))) {
                event.setCancelled(true);
            }
        }
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
