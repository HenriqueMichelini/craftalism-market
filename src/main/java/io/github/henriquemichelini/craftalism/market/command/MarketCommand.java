package io.github.henriquemichelini.craftalism.market.command;

import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotLoadResult;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotService;
import io.github.henriquemichelini.craftalism.market.gui.MarketGuiService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class MarketCommand implements CommandExecutor {

    private final Plugin plugin;
    private final MarketBrowseSnapshotService snapshotService;
    private final MarketGuiService guiService;

    public MarketCommand(
        Plugin plugin,
        MarketBrowseSnapshotService snapshotService,
        MarketGuiService guiService
    ) {
        this.plugin = plugin;
        this.snapshotService = snapshotService;
        this.guiService = guiService;
    }

    @Override
    public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /market.");
            return true;
        }

        snapshotService
            .loadForInitialOpen()
            .whenComplete((result, error) ->
                plugin
                    .getServer()
                    .getScheduler()
                    .runTask(plugin, () -> handleResult(player, result, error))
            );
        return true;
    }

    private void handleResult(
        Player player,
        MarketBrowseSnapshotLoadResult result,
        Throwable error
    ) {
        if (!player.isOnline()) {
            return;
        }

        if (error != null) {
            // player.sendMessage(ChatColor.translateAlternateColorCodes(
            //         '&',
            //         plugin.getConfig().getString("messages.unavailable-no-cache", "&cThe market is unavailable right now.")
            // ));

            player.sendMessage(
                Component.text(
                    plugin
                        .getConfig()
                        .getString(
                            "messages.unavailable-no-cache",
                            "&cThe market is unavailable right now."
                        )
                ).color(TextColor.color(255, 0, 0))
            );
            return;
        }

        guiService.openMainMenu(player, result.snapshot());
        String messagePath = result.fromCache()
            ? "messages.opened-read-only"
            : "messages.opened-live";
        player.sendMessage(
            ChatColor.translateAlternateColorCodes(
                '&',
                plugin
                    .getConfig()
                    .getString(messagePath, "&7Market data loaded.")
            )
        );
    }
}
