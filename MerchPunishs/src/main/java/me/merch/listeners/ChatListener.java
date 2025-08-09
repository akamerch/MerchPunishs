package me.merch.listeners;

import me.merch.MerchPunishs;
import me.merch.Punishment;
import me.merch.utils.ChatColorUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatListener implements Listener {
    private final MerchPunishs plugin = MerchPunishs.getPlugin();

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Punishment activeMute = plugin.getDatabaseManager().getActiveMute(player.getUniqueId());
        if (activeMute != null) {
            event.setCancelled(true);
            final String reason = activeMute.getReason();
            final String expiry = activeMute.getDuration() == -1 ? "Never" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(activeMute.getTimestamp() + activeMute.getDuration()));
            final String muteMessage = plugin.getConfig().getString("messages.player_muted")
                    .replace("%reason%", reason)
                    .replace("%expiry%", expiry);
            player.sendMessage(ChatColorUtil.color(muteMessage));
        }
    }
}