package io.github.henriquemichelini.craftalism.market;

import io.github.henriquemichelini.craftalism.market.api.HttpMarketBrowseSnapshotProvider;
import io.github.henriquemichelini.craftalism.market.api.HttpMarketExecuteClient;
import io.github.henriquemichelini.craftalism.market.api.HttpMarketQuoteClient;
import io.github.henriquemichelini.craftalism.market.api.JavaHttpMarketApiTransport;
import io.github.henriquemichelini.craftalism.market.api.MarketApiConfiguration;
import io.github.henriquemichelini.craftalism.market.api.MarketApiConfigurationResolver;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotProvider;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotService;
import io.github.henriquemichelini.craftalism.market.api.MarketApiTransport;
import io.github.henriquemichelini.craftalism.market.api.MarketBearerTokenProvider;
import io.github.henriquemichelini.craftalism.market.api.MarketBearerTokenProviderFactory;
import io.github.henriquemichelini.craftalism.market.api.MarketExecuteClient;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteClient;
import io.github.henriquemichelini.craftalism.market.api.SystemMarketEnvironment;
import io.github.henriquemichelini.craftalism.market.command.MarketCommand;
import io.github.henriquemichelini.craftalism.market.gui.MarketGuiService;
import io.github.henriquemichelini.craftalism.market.gui.MarketInventoryListener;
import io.github.henriquemichelini.craftalism.market.inventory.MarketInventoryService;
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
        MarketApiConfiguration apiConfiguration = new MarketApiConfigurationResolver(
                getConfig(),
                new SystemMarketEnvironment(),
                getLogger()
        ).resolve();
        MarketApiTransport apiTransport = new JavaHttpMarketApiTransport(apiConfiguration.connectTimeout());
        MarketBearerTokenProvider bearerTokenProvider = MarketBearerTokenProviderFactory.create(
                apiConfiguration,
                apiTransport,
                getLogger()
        );
        MarketBrowseSnapshotProvider snapshotProvider = new HttpMarketBrowseSnapshotProvider(
                apiTransport,
                URI.create(apiConfiguration.baseUrl() + apiConfiguration.snapshotPath()),
                apiConfiguration.requestTimeout(),
                getConfig(),
                bearerTokenProvider
        );
        MarketQuoteClient quoteClient = new HttpMarketQuoteClient(
                apiTransport,
                URI.create(apiConfiguration.baseUrl() + apiConfiguration.quotePath()),
                apiConfiguration.requestTimeout(),
                bearerTokenProvider
        );
        MarketExecuteClient executeClient = new HttpMarketExecuteClient(
                apiTransport,
                URI.create(apiConfiguration.baseUrl() + apiConfiguration.executePath()),
                apiConfiguration.requestTimeout(),
                bearerTokenProvider
        );
        MarketInventoryService inventoryService = new MarketInventoryService();
        Executor asyncExecutor = runnable -> getServer().getScheduler().runTaskAsynchronously(this, runnable);
        MarketBrowseSnapshotService snapshotService = new MarketBrowseSnapshotService(snapshotProvider, asyncExecutor);
        MarketGuiService guiService = new MarketGuiService(
                this,
                snapshotService,
                quoteClient,
                executeClient,
                inventoryService,
                sessionRegistry,
                getConfig()
        );

        if (getCommand("market") == null) {
            throw new IllegalStateException("The /market command must be declared in plugin.yml.");
        }

        getCommand("market").setExecutor(new MarketCommand(this, snapshotService, guiService));
        getServer().getPluginManager().registerEvents(new MarketInventoryListener(guiService), this);
        getLogger().info("Craftalism Market read-only browsing is enabled.");
    }
}
