package me.merch;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Punishment {

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

    // Helper method to parse duration strings
    public static long parseDuration(String time) {
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
