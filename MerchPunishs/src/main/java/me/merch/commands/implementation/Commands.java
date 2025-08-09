package me.merch.commands.implementation;

import me.merch.MerchPunishs;
import me.merch.Punishment;
import me.merch.utils.ChatColorUtil;
import me.merch.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class Commands implements CommandExecutor, TabCompleter {

    private final MerchPunishs plugin;

    public Commands() {
        this.plugin = MerchPunishs.getPlugin();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase();

        switch (commandName) {
            case "ban":
            case "tempban":
            case "mute":
            case "tempmute":
            case "ipban":
            case "kick":
                return handlePunishmentCommand(sender, command, label, args);
            case "unban":
            case "unmute":
                return handleUnpunishCommand(sender, command, label, args);
            case "history":
                return handleHistoryCommand(sender, command, label, args);
            case "mpunishs":
                return handleHelpCommand(sender);
            default:
                return false;
        }
    }

    private boolean handlePunishmentCommand(CommandSender sender, Command command, String label, String[] args) {
        Punishment.Type type = Punishment.Type.valueOf(command.getName().toUpperCase());
        int requiredArgs = (type == Punishment.Type.TEMPBAN || type == Punishment.Type.TEMPMUTE) ? 3 : 2;

        if (!sender.hasPermission("merchpunishs." + command.getName())) {
            sender.sendMessage(ChatColorUtil.color(plugin.getConfig().getString("messages.no_permission")));
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
                duration = TimeUtil.parseDuration(args[1]);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + "Invalid time format. Example: 1d, 12h, 30m.");
                return true;
            }
            if (args.length > 2) {
                reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            } else {
                reason = "No reason provided.";
            }
        }

        final long finalDuration = duration;
        final String finalReason = reason;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = Arrays.stream(Bukkit.getOfflinePlayers())
                    .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(targetName))
                    .findFirst().orElse(null);

            if (target == null) {
                sender.sendMessage(ChatColorUtil.color(plugin.getConfig().getString("messages.player_not_found")));
                return;
            }

            Punishment punishment = new Punishment(type, target.getUniqueId(), target.getName(), staffName, finalReason, System.currentTimeMillis(), finalDuration);
            plugin.getDatabaseManager().addPunishment(punishment);
            sender.sendMessage(ChatColorUtil.color(plugin.getConfig().getString("messages.punishment_applied").replace("%player%", target.getName())));

            if (type == Punishment.Type.BAN || type == Punishment.Type.TEMPBAN || type == Punishment.Type.IPBAN || type == Punishment.Type.KICK) {
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    String kickMessage = ChatColorUtil.color(plugin.getConfig().getString("messages.player_banned")
                            .replace("%reason%", finalReason)
                            .replace("%expiry%", finalDuration == -1 ? "Never" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(punishment.getTimestamp() + punishment.getDuration()))));

                    plugin.getServer().getScheduler().runTask(plugin, () -> onlineTarget.kickPlayer(kickMessage));
                }
            }
        });
        return true;
    }

    private boolean handleUnpunishCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("merchpunishs." + command.getName())) {
            sender.sendMessage(ChatColorUtil.color(plugin.getConfig().getString("messages.no_permission")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + command.getUsage());
            return true;
        }

        final String targetName = args[0];
        Punishment.Type type = (command.getName().equals("unban")) ? Punishment.Type.BAN : Punishment.Type.MUTE;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = Arrays.stream(Bukkit.getOfflinePlayers())
                    .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(targetName))
                    .findFirst().orElse(null);
            if (target == null) {
                sender.sendMessage(ChatColorUtil.color(plugin.getConfig().getString("messages.player_not_found")));
                return;
            }
            if (command.getName().equals("unban")) {
                plugin.getDatabaseManager().unPunish(target.getUniqueId(), Punishment.Type.BAN);
                plugin.getDatabaseManager().unPunish(target.getUniqueId(), Punishment.Type.TEMPBAN);
                plugin.getDatabaseManager().unPunish(target.getUniqueId(), Punishment.Type.IPBAN);
                sender.sendMessage(ChatColorUtil.color(plugin.getConfig().getString("messages.unban_success").replace("%player%", target.getName())));
            } else {
                plugin.getDatabaseManager().unPunish(target.getUniqueId(), Punishment.Type.MUTE);
                plugin.getDatabaseManager().unPunish(target.getUniqueId(), Punishment.Type.TEMPMUTE);
                sender.sendMessage(ChatColorUtil.color(plugin.getConfig().getString("messages.unmute_success").replace("%player%", target.getName())));
            }
        });
        return true;
    }

    private boolean handleHistoryCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }
        if (!sender.hasPermission("merchpunishs.history")) {
            sender.sendMessage(ChatColorUtil.color(plugin.getConfig().getString("messages.no_permission")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + command.getUsage());
            return true;
        }

        final String targetName = args[0];
        final Player player = (Player) sender;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = Arrays.stream(Bukkit.getOfflinePlayers())
                    .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(targetName))
                    .findFirst().orElse(null);
            if (target == null) {
                player.sendMessage(ChatColorUtil.color(plugin.getConfig().getString("messages.player_not_found")));
                return;
            }
            List<Punishment> history = plugin.getDatabaseManager().getHistory(target.getUniqueId());
            if (history.isEmpty()) {
                player.sendMessage(ChatColorUtil.color(plugin.getConfig().getString("messages.prefix") + "&a&lNo punishment history found for " + target.getName()));
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> openHistoryMenu(player, target.getName(), history));
        });
        return true;
    }

    private void openHistoryMenu(Player player, String targetName, List<Punishment> history) {
        int size = plugin.getConfig().getInt("history_menu.size", 54);
        Inventory menu = plugin.getServer().createInventory(null, size, ChatColorUtil.color(plugin.getConfig().getString("messages.history_title").replace("%player%", targetName)));

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
        String materialName = plugin.getConfig().getString("history_menu.fill_item.material", "GRAY_STAINED_GLASS_PANE");
        String name = plugin.getConfig().getString("history_menu.fill_item.name", " ");
        ItemStack item = new ItemStack(Material.getMaterial(materialName));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColorUtil.color(name));
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
        meta.setDisplayName(ChatColorUtil.color("&c&l" + punishment.getType().toString()));
        List<String> lore = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (String line : plugin.getConfig().getStringList("history_menu.item_lore")) {
            line = line.replace("%type%", punishment.getType().toString())
                    .replace("%reason%", punishment.getReason())
                    .replace("%staff%", punishment.getStaffName())
                    .replace("%date%", sdf.format(new Date(punishment.getTimestamp())))
                    .replace("%expiry%", punishment.getDuration() == -1 ? "Never" : sdf.format(new Date(punishment.getTimestamp() + punishment.getDuration())));
            lore.add(ChatColorUtil.color(line));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private boolean handleHelpCommand(CommandSender sender) {
        if (!sender.hasPermission("merchpunishs.help")) {
            sender.sendMessage(ChatColorUtil.color(plugin.getConfig().getString("messages.no_permission")));
            return true;
        }
        List<String> helpMessages = plugin.getConfig().getStringList("messages.help_message");
        for (String message : helpMessages) {
            sender.sendMessage(ChatColorUtil.color(message));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase();
        if (args.length == 1 && (commandName.equals("ban") || commandName.equals("tempban") || commandName.equals("unban") || commandName.equals("mute") || commandName.equals("tempmute") || commandName.equals("unmute") || commandName.equals("ipban") || commandName.equals("kick") || commandName.equals("history"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}