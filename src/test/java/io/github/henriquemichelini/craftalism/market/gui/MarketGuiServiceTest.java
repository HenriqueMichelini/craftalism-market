package io.github.henriquemichelini.craftalism.market.gui;

import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotProvider;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotService;
import io.github.henriquemichelini.craftalism.market.browse.MarketCategorySnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketItemSnapshot;
import io.github.henriquemichelini.craftalism.market.inventory.MarketInventoryService;
import io.github.henriquemichelini.craftalism.market.session.MarketQuoteStatus;
import io.github.henriquemichelini.craftalism.market.session.MarketSession;
import io.github.henriquemichelini.craftalism.market.session.MarketSessionRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void refreshSnapshotReplacesCachedLiveSnapshot() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MarketBrowseSnapshotService service = new MarketBrowseSnapshotService(() -> {
            if (calls.getAndIncrement() == 0) {
                return sampleSnapshot(false);
            }
            return updatedSnapshot(false);
        }, directExecutor());

        service.loadForInitialOpen().get();
        var refreshResult = service.refreshSnapshot().get();

        assertFalse(refreshResult.fromCache());
        assertEquals("snapshot-v2", service.currentSnapshot().orElseThrow().snapshotVersion());
        assertEquals("Stock: 640", service.currentSnapshot().orElseThrow()
                .findItem("farming", "wheat")
                .orElseThrow()
                .stockDisplay());
    }

    @Test
    void sessionForSnapshotDisablesTradeWhenRefreshedItemIsBlocked() {
        MarketGuiService guiService = guiService(new YamlConfiguration());

        MarketSession refreshed = guiService.sessionForSnapshot(
                MarketSession.tradeView("farming", "wheat", false).withQuantityPending(4),
                blockedSnapshot(false)
        );

        assertEquals(MarketQuoteStatus.UNAVAILABLE, refreshed.quoteStatus());
        assertEquals("&cThis item is currently blocked.", refreshed.quoteStatusMessage());
        assertEquals(4, refreshed.quantity());
    }

    @Test
    void sessionForSnapshotSwitchesToReadOnlyWhenRefreshFallsBackToCache() {
        MarketGuiService guiService = guiService(new YamlConfiguration());

        MarketSession refreshed = guiService.sessionForSnapshot(
                MarketSession.tradeView("farming", "wheat", false).withQuantityPending(3),
                sampleSnapshot(true)
        );

        assertTrue(refreshed.readOnly());
        assertEquals(MarketQuoteStatus.DISABLED, refreshed.quoteStatus());
        assertEquals("Cached preview only", refreshed.quoteStatusMessage());
    }

    @Test
    void sessionForSnapshotRequestsFreshQuotesWhenLiveSnapshotReturnsFromReadOnlyState() {
        MarketGuiService guiService = guiService(new YamlConfiguration());

        MarketSession refreshed = guiService.sessionForSnapshot(
                MarketSession.tradeView("farming", "wheat", true),
                updatedSnapshot(false)
        );

        assertFalse(refreshed.readOnly());
        assertEquals(MarketQuoteStatus.PENDING, refreshed.quoteStatus());
        assertEquals("Refreshing quote...", refreshed.quoteStatusMessage());
    }

    private MarketGuiService guiService(YamlConfiguration config) {
        config.set("messages.rejections.ITEM_BLOCKED", "&cThis item is currently blocked.");
        config.set("messages.rejections.ITEM_NOT_OPERATING", "&cThis item is not operating right now.");
        config.set("messages.rejections.UNKNOWN_ITEM", "&cThat item is no longer available.");

        return new MarketGuiService(
                null,
                new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
                (itemId, side, quantity, snapshotVersion) -> { throw new AssertionError(); },
                (itemId, side, quantity, quoteToken, snapshotVersion) -> { throw new AssertionError(); },
                new MarketInventoryService(),
                new MarketSessionRegistry(),
                config
        );
    }

    private MarketBrowseSnapshotProvider sampleProvider() {
        return () -> sampleSnapshot(false);
    }

    private MarketBrowseSnapshot sampleSnapshot(boolean readOnly) {
        return snapshot(readOnly, "snapshot-v1", "Stock: 1820");
    }

    private MarketBrowseSnapshot updatedSnapshot(boolean readOnly) {
        return snapshot(readOnly, "snapshot-v2", "Stock: 640");
    }

    private MarketBrowseSnapshot blockedSnapshot(boolean readOnly) {
        return snapshot(readOnly, "snapshot-v3", "Blocked");
    }

    private MarketBrowseSnapshot snapshot(boolean readOnly, String snapshotVersion, String stockDisplay) {
        return new MarketBrowseSnapshot(snapshotVersion, List.of(
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
                                stockDisplay,
                                "Fixture"
                        ))
                )
        ), readOnly);
    }

    private Executor directExecutor() {
        return Runnable::run;
    }
}
