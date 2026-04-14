package io.github.henriquemichelini.craftalism.market.gui;

import io.github.henriquemichelini.craftalism.market.api.MarketExecuteResult;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteSide;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotProvider;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotService;
import io.github.henriquemichelini.craftalism.market.browse.MarketCategorySnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketItemSnapshot;
import io.github.henriquemichelini.craftalism.market.inventory.MarketInventoryAccess;
import io.github.henriquemichelini.craftalism.market.inventory.MarketInventoryService;
import io.github.henriquemichelini.craftalism.market.session.MarketQuoteStatus;
import io.github.henriquemichelini.craftalism.market.session.MarketSession;
import io.github.henriquemichelini.craftalism.market.session.MarketSessionRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
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
    void sessionForSnapshotDisablesTradeWhenItemIsNotOperating() {
        MarketGuiService guiService = guiService(new YamlConfiguration());

        MarketSession refreshed = guiService.sessionForSnapshot(
                MarketSession.tradeView("farming", "wheat", false).withQuantityPending(2),
                unavailableSnapshot(false)
        );

        assertEquals(MarketQuoteStatus.UNAVAILABLE, refreshed.quoteStatus());
        assertEquals("&cThis item is not operating right now.", refreshed.quoteStatusMessage());
        assertEquals(2, refreshed.quantity());
    }

    @Test
    void sessionForSnapshotDisablesTradeWhenItemDisappearsFromRefreshedSnapshot() {
        MarketGuiService guiService = guiService(new YamlConfiguration());

        MarketSession refreshed = guiService.sessionForSnapshot(
                MarketSession.tradeView("farming", "wheat", false).withQuantityPending(2),
                missingItemSnapshot(false)
        );

        assertEquals(MarketQuoteStatus.UNAVAILABLE, refreshed.quoteStatus());
        assertEquals("&cThat item is no longer available.", refreshed.quoteStatusMessage());
        assertEquals(2, refreshed.quantity());
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

    @Test
    void sellRemovalFailurePlayerMessageIncludesSettlementDetails() {
        MarketGuiService guiService = guiService(new YamlConfiguration());

        String message = guiService.sellRemovalFailurePlayerMessage(
                Material.WHEAT,
                new MarketExecuteResult(5, "20.5", "4.1", "coins", "snapshot-v7"),
                3
        );

        assertEquals("&cSell completed for 20.5 coins, but local removal only removed 3/5 WHEAT. Missing: 2. Contact staff.", message);
    }

    @Test
    void sellRemovalFailureLogMessageIncludesCompensationContext() {
        MarketGuiService guiService = guiService(new YamlConfiguration());
        java.util.UUID playerId = java.util.UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        String message = guiService.sellRemovalFailureLogMessage(
                playerId,
                Material.WHEAT,
                new MarketExecuteResult(5, "20.5", "4.1", "coins", "snapshot-v7"),
                3
        );

        assertEquals(
                "Market sell compensation issue for player aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee: item=WHEAT, executed=5, removed=3, missing=2, settled=20.5 coins, snapshotVersion=snapshot-v7",
                message
        );
    }

    @Test
    void playerJoinAppliesDeferredBuySettlement() {
        FakeInventoryAccess inventoryAccess = new FakeInventoryAccess();
        MarketGuiService guiService = guiService(new YamlConfiguration(), inventoryAccess);
        UUID playerId = UUID.randomUUID();
        Player player = fakePlayer(playerId);

        guiService.queueDeferredSettlement(
                playerId,
                new MarketGuiService.DeferredSettlement(
                        MarketQuoteSide.BUY,
                        Material.WHEAT,
                        new MarketExecuteResult(5, "20.5", "4.1", "coins", "snapshot-v7")
                )
        );

        guiService.handlePlayerJoin(player);

        assertEquals(0, guiService.deferredSettlementCount(playerId));
        assertEquals(5, inventoryAccess.quantity(Material.WHEAT));
    }

    @Test
    void playerJoinAppliesDeferredSellSettlement() {
        FakeInventoryAccess inventoryAccess = new FakeInventoryAccess();
        inventoryAccess.setQuantity(Material.WHEAT, 4);
        MarketGuiService guiService = guiService(new YamlConfiguration(), inventoryAccess);
        UUID playerId = UUID.randomUUID();
        Player player = fakePlayer(playerId);

        guiService.queueDeferredSettlement(
                playerId,
                new MarketGuiService.DeferredSettlement(
                        MarketQuoteSide.SELL,
                        Material.WHEAT,
                        new MarketExecuteResult(4, "16.4", "4.1", "coins", "snapshot-v7")
                )
        );

        guiService.handlePlayerJoin(player);

        assertEquals(0, guiService.deferredSettlementCount(playerId));
        assertEquals(0, inventoryAccess.quantity(Material.WHEAT));
    }

    private MarketGuiService guiService(YamlConfiguration config) {
        return guiService(config, new MarketInventoryService());
    }

    private MarketGuiService guiService(
            YamlConfiguration config,
            MarketInventoryAccess inventoryAccess
    ) {
        config.set("messages.rejections.ITEM_BLOCKED", "&cThis item is currently blocked.");
        config.set("messages.rejections.ITEM_NOT_OPERATING", "&cThis item is not operating right now.");
        config.set("messages.rejections.UNKNOWN_ITEM", "&cThat item is no longer available.");
        config.set("messages.sell-removal-failed", "&cSell completed for {total}, but local removal only removed {removed}/{executed} {item}. Missing: {missing}. Contact staff.");
        config.set("messages.sell-removal-failed-log", "Market sell compensation issue for player {playerId}: item={item}, executed={executed}, removed={removed}, missing={missing}, settled={total}, snapshotVersion={snapshotVersion}");

        return new MarketGuiService(
                null,
                new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
                (itemId, side, quantity, snapshotVersion) -> { throw new AssertionError(); },
                (itemId, side, quantity, quoteToken, snapshotVersion) -> { throw new AssertionError(); },
                inventoryAccess,
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

    private MarketBrowseSnapshot unavailableSnapshot(boolean readOnly) {
        return snapshot(readOnly, "snapshot-v4", "Unavailable");
    }

    private MarketBrowseSnapshot missingItemSnapshot(boolean readOnly) {
        return new MarketBrowseSnapshot("snapshot-v5", List.of(
                new MarketCategorySnapshot(
                        "farming",
                        "Farming",
                        Material.WHEAT,
                        List.of(),
                        List.of()
                )
        ), readOnly);
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

    private Player fakePlayer(UUID playerId) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "sendMessage" -> null;
                    default -> primitiveDefault(method.getReturnType());
                }
        );
    }

    private Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    private static final class FakeInventoryAccess implements MarketInventoryAccess {
        private final java.util.Map<Material, Integer> quantities = new java.util.EnumMap<>(Material.class);

        @Override
        public int count(Player player, Material material) {
            return quantity(material);
        }

        @Override
        public int remove(Player player, Material material, int quantity) {
            int removed = Math.min(quantity(material), quantity);
            quantities.put(material, quantity(material) - removed);
            return removed;
        }

        @Override
        public int addOrDrop(Player player, Material material, int quantity) {
            quantities.put(material, quantity(material) + quantity);
            return quantity;
        }

        private void setQuantity(Material material, int quantity) {
            quantities.put(material, quantity);
        }

        private int quantity(Material material) {
            return quantities.getOrDefault(material, 0);
        }
    }
}
