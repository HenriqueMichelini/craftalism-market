package io.github.henriquemichelini.craftalism.market.command;

import io.github.henriquemichelini.craftalism.market.gui.MarketGuiService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MarketCommand implements CommandExecutor {
    private final MarketGuiService guiService;

    public MarketCommand(MarketGuiService guiService) {
        this.guiService = guiService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /market.");
            return true;
        }

        guiService.openMainMenu(player);
        return true;
    }
}
