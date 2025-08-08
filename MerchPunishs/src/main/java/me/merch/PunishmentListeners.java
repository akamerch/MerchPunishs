package me.merch;

import me.merch.MerchPunishs;
import me.merch.Punishment;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.text.SimpleDateFormat;
import java.util.Date;

public class PunishmentListeners implements Listener {

    private final MerchPunishs plugin;

    public PunishmentListeners() {
        this.plugin = MerchPunishs.getPlugin();
    }

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
                plugin.getServer().getScheduler().runTask(plugin, () -> player.kickPlayer(color(banMessage)));
            }
        });
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // This check is now synchronous to prevent players from chatting while muted
        Punishment activeMute = plugin.getDatabaseManager().getActiveMute(player.getUniqueId());
        if (activeMute != null) {
            event.setCancelled(true);
            final String reason = activeMute.getReason();
            final String expiry = activeMute.getDuration() == -1 ? "Never" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(activeMute.getTimestamp() + activeMute.getDuration()));
            final String muteMessage = plugin.getConfig().getString("messages.player_muted")
                    .replace("%reason%", reason)
                    .replace("%expiry%", expiry);
            player.sendMessage(color(muteMessage));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().contains(color(plugin.getConfig().getString("messages.history_title").replace("%player%", "")))) {
            event.setCancelled(true);
        }
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
