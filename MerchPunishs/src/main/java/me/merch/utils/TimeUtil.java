package me.merch.utils;

import java.util.concurrent.TimeUnit;
import org.bukkit.ChatColor;

public class TimeUtil {

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