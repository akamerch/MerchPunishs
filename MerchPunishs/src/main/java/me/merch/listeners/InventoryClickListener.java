package me.merch.listeners;

import me.merch.MerchPunishs;
import me.merch.utils.ChatColorUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class InventoryClickListener implements Listener {
    private final MerchPunishs plugin = MerchPunishs.getPlugin();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().contains(ChatColorUtil.color(plugin.getConfig().getString("messages.history_title").replace("%player%", "")))) {
            event.setCancelled(true);
        }
    }
}