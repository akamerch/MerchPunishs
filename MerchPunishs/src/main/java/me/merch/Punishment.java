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
}