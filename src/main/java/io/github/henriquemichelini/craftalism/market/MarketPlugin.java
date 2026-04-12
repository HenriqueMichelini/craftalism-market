package io.github.henriquemichelini.craftalism.market;

import io.github.henriquemichelini.craftalism.market.api.HttpMarketBrowseSnapshotProvider;
import io.github.henriquemichelini.craftalism.market.api.JavaHttpMarketApiTransport;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotProvider;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotService;
import io.github.henriquemichelini.craftalism.market.command.MarketCommand;
import io.github.henriquemichelini.craftalism.market.gui.MarketGuiService;
import io.github.henriquemichelini.craftalism.market.gui.MarketInventoryListener;
import io.github.henriquemichelini.craftalism.market.session.MarketSessionRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executor;

public final class MarketPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();

        MarketSessionRegistry sessionRegistry = new MarketSessionRegistry();
        MarketBrowseSnapshotProvider snapshotProvider = new HttpMarketBrowseSnapshotProvider(
                new JavaHttpMarketApiTransport(Duration.ofMillis(getConfig().getLong("market-api.connect-timeout-ms", 3000L))),
                URI.create(getConfig().getString("market-api.base-url", "http://127.0.0.1:8080")
                        + getConfig().getString("market-api.snapshot-path", "/market/snapshot")),
                Duration.ofMillis(getConfig().getLong("market-api.request-timeout-ms", 5000L)),
                getConfig()
        );
        Executor asyncExecutor = runnable -> getServer().getScheduler().runTaskAsynchronously(this, runnable);
        MarketBrowseSnapshotService snapshotService = new MarketBrowseSnapshotService(snapshotProvider, asyncExecutor);
        MarketGuiService guiService = new MarketGuiService(snapshotService, sessionRegistry, getConfig());

        if (getCommand("market") == null) {
            throw new IllegalStateException("The /market command must be declared in plugin.yml.");
        }

        getCommand("market").setExecutor(new MarketCommand(this, snapshotService, guiService));
        getServer().getPluginManager().registerEvents(new MarketInventoryListener(guiService, sessionRegistry), this);
        getLogger().info("Craftalism Market read-only browsing is enabled.");
    }
}
