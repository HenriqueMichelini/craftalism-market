package io.github.henriquemichelini.craftalism.market;

import io.github.henriquemichelini.craftalism.market.browse.ConfigBackedMarketBrowseSnapshotProvider;
import io.github.henriquemichelini.craftalism.market.command.MarketCommand;
import io.github.henriquemichelini.craftalism.market.gui.MarketGuiService;
import io.github.henriquemichelini.craftalism.market.gui.MarketInventoryListener;
import io.github.henriquemichelini.craftalism.market.session.MarketSessionRegistry;
import org.bukkit.plugin.java.JavaPlugin;

public final class MarketPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();

        MarketSessionRegistry sessionRegistry = new MarketSessionRegistry();
        ConfigBackedMarketBrowseSnapshotProvider snapshotProvider =
                new ConfigBackedMarketBrowseSnapshotProvider(getConfig());
        MarketGuiService guiService = new MarketGuiService(snapshotProvider, sessionRegistry, getConfig());

        if (getCommand("market") == null) {
            throw new IllegalStateException("The /market command must be declared in plugin.yml.");
        }

        getCommand("market").setExecutor(new MarketCommand(guiService));
        getServer().getPluginManager().registerEvents(new MarketInventoryListener(guiService, sessionRegistry), this);
        getLogger().info("Craftalism Market read-only browsing is enabled.");
    }
}
