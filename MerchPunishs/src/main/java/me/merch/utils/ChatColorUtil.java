package me.merch.utils;

import org.bukkit.ChatColor;

public class ChatColorUtil {

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}