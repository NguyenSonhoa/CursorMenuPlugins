package com.example.ui;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Commands implements CommandExecutor {

    private final CursorMenuPlugin plugin;

    public Commands(CursorMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("reloadmenu")) {
            if (sender.hasPermission("cursorplugin.reload")) {
                plugin.reloadPluginConfig();
                sender.sendMessage("Plugin configuration reloaded successfully!");
                return true;
            } else {
                sender.sendMessage("You do not have permission to use this command.");
                return false;
            }
        }

        if (command.getName().equalsIgnoreCase("startmenu")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (player.hasPermission("cursorplugin.startmenu")) {
                    plugin.setupCursor(player); // Gọi hàm để kích hoạt menu
                    player.sendMessage("Cursor menu activated!");
                    return true;
                } else {
                    player.sendMessage("You do not have permission to use this command.");
                    return false;
                }
            }
        }
        if (command.getName().equalsIgnoreCase("stopmenu")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (player.hasPermission("cursorplugin.startmenu")) {
                    plugin.stopCursor(player); // Gọi hàm để kích hoạt menu
                    player.sendMessage("Cursor menu OFF!");
                    return true;
                } else {
                    player.sendMessage("You do not have permission to use this command.");
                    return false;
                }
            }
        }
        return false;
    }
}
