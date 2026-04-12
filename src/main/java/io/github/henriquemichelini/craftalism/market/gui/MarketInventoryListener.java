package io.github.henriquemichelini.craftalism.market.gui;

import io.github.henriquemichelini.craftalism.market.session.MarketSessionRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class MarketInventoryListener implements Listener {
    private final MarketGuiService guiService;
    private final MarketSessionRegistry sessionRegistry;

    public MarketInventoryListener(MarketGuiService guiService, MarketSessionRegistry sessionRegistry) {
        this.guiService = guiService;
        this.sessionRegistry = sessionRegistry;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        guiService.handleClick(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && event.getInventory().getHolder() instanceof MarketInventoryHolder) {
            sessionRegistry.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionRegistry.remove(event.getPlayer().getUniqueId());
    }
}
