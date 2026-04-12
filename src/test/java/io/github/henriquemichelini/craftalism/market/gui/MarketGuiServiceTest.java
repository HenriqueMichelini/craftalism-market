package io.github.henriquemichelini.craftalism.market.gui;

import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotProvider;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotService;
import io.github.henriquemichelini.craftalism.market.browse.MarketCategorySnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketItemSnapshot;
import io.github.henriquemichelini.craftalism.market.inventory.MarketInventoryService;
import io.github.henriquemichelini.craftalism.market.session.MarketSession;
import io.github.henriquemichelini.craftalism.market.session.MarketSessionRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MarketGuiServiceTest {
    @Test
    void closeSessionRemovesStoredSession() {
        MarketSessionRegistry registry = new MarketSessionRegistry();
        MarketGuiService guiService = new MarketGuiService(
                null,
                new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
                (itemId, side, quantity, snapshotVersion) -> { throw new AssertionError(); },
                (itemId, side, quantity, quoteToken, snapshotVersion) -> { throw new AssertionError(); },
                new MarketInventoryService(),
                registry,
                new YamlConfiguration()
        );

        java.util.UUID playerId = java.util.UUID.randomUUID();
        registry.put(playerId, MarketSession.tradeView("farming", "wheat", false));

        guiService.closeSession(playerId);

        assertFalse(registry.get(playerId).isPresent());
    }

    @Test
    void snapshotServiceCanFallbackToReadOnlyCache() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MarketBrowseSnapshotService service = new MarketBrowseSnapshotService(() -> {
            if (calls.getAndIncrement() == 0) {
                return sampleSnapshot(false);
            }
            throw new IllegalStateException("boom");
        }, directExecutor());

        service.loadForInitialOpen().get();
        var result = service.loadForInitialOpen().get();

        assertFalse(service.currentSnapshot().orElseThrow().readOnly());
        org.junit.jupiter.api.Assertions.assertTrue(result.snapshot().readOnly());
    }

    private MarketBrowseSnapshotProvider sampleProvider() {
        return () -> sampleSnapshot(false);
    }

    private MarketBrowseSnapshot sampleSnapshot(boolean readOnly) {
        return new MarketBrowseSnapshot("snapshot-v1", List.of(
                new MarketCategorySnapshot(
                        "farming",
                        "Farming",
                        Material.WHEAT,
                        List.of(),
                        List.of(new MarketItemSnapshot(
                                "wheat",
                                "Wheat",
                                Material.WHEAT,
                                List.of(),
                                "4.8 coins",
                                "4.1 coins",
                                "2.3%",
                                "Stock: 1820",
                                "Fixture"
                        ))
                )
        ), readOnly);
    }

    private Executor directExecutor() {
        return Runnable::run;
    }
}
