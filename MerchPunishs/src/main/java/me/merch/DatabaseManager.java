package me.merch;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import me.merch.MerchPunishs;
import me.merch.Punishment;
import org.bson.Document;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class DatabaseManager {

    private final String databaseType;
    private final MerchPunishs plugin;

    private Connection connection;
    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;
    private MongoCollection<Document> punishmentsCollection;

    public DatabaseManager() {
        this.plugin = MerchPunishs.getPlugin();
        this.databaseType = plugin.getConfig().getString("database.type", "SQLite");
    }

    public boolean connect() {
        try {
            switch (databaseType) {
                case "MySQL":
                    String mysqlUrl = "jdbc:mysql://" +
                            plugin.getConfig().getString("database.mysql.host") + ":" +
                            plugin.getConfig().getInt("database.mysql.port") + "/" +
                            plugin.getConfig().getString("database.mysql.database") +
                            "?user=" + plugin.getConfig().getString("database.mysql.username") +
                            "&password=" + plugin.getConfig().getString("database.mysql.password");
                    connection = DriverManager.getConnection(mysqlUrl);
                    setupSQLTable("MySQL");
                    break;
                case "SQLite":
                    Class.forName("org.sqlite.JDBC");
                    connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/punishments.db");
                    setupSQLTable("SQLite");
                    break;
                case "MongoDB":
                    String mongoUri = plugin.getConfig().getString("database.mongodb.uri");
                    mongoClient = MongoClients.create(mongoUri);
                    mongoDatabase = mongoClient.getDatabase(plugin.getConfig().getString("database.mongodb.database"));
                    punishmentsCollection = mongoDatabase.getCollection("punishments");
                    break;
                default:
                    plugin.getLogger().severe("Invalid database type specified in config.yml.");
                    return false;
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Could not connect to database: " + e.getMessage());
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
            plugin.getLogger().severe("Failed to disconnect from database: " + e.getMessage());
        }
    }

    public void addPunishment(Punishment punishment) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
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
                plugin.getLogger().severe("Error adding punishment to SQL database: " + e.getMessage());
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
            plugin.getLogger().severe("Error getting active punishment from SQL database: " + e.getMessage());
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
            plugin.getLogger().severe("Error getting history from SQL database: " + e.getMessage());
        }
        return history;
    }

    public void unPunish(UUID uuid, Punishment.Type type) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
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
                plugin.getLogger().severe("Error unpunishing player: " + e.getMessage());
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
