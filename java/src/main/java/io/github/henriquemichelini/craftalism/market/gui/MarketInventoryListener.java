package io.github.henriquemichelini.craftalism.market.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class MarketInventoryListener implements Listener {
    private final MarketGuiService guiService;

    public MarketInventoryListener(MarketGuiService guiService) {
        this.guiService = guiService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        guiService.handleClick(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && event.getInventory().getHolder() instanceof MarketInventoryHolder) {
            guiService.closeSession(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        guiService.closeSession(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        guiService.handlePlayerJoin(event.getPlayer());
    }
}
