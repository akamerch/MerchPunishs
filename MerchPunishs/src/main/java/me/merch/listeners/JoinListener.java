package me.merch.listeners;

import me.merch.MerchPunishs;
import me.merch.Punishment;
import me.merch.utils.ChatColorUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.text.SimpleDateFormat;
import java.util.Date;

public class JoinListener implements Listener {
    private final MerchPunishs plugin = MerchPunishs.getPlugin();

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Punishment activeBan = plugin.getDatabaseManager().getActiveBan(player.getUniqueId());

            if (activeBan != null) {
                final String reason = activeBan.getReason();
                final String expiry = activeBan.getDuration() == -1 ? "Never" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(activeBan.getTimestamp() + activeBan.getDuration()));
                final String banMessage = plugin.getConfig().getString("messages.player_banned")
                        .replace("%reason%", reason)
                        .replace("%expiry%", expiry);
                plugin.getServer().getScheduler().runTask(plugin, () -> player.kickPlayer(ChatColorUtil.color(banMessage)));
            }
        });
    }
}