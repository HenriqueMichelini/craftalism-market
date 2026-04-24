package io.github.henriquemichelini.craftalism.market.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.henriquemichelini.craftalism.market.api.MarketApiRequestException;
import io.github.henriquemichelini.craftalism.market.api.MarketExecuteRejectedException;
import io.github.henriquemichelini.craftalism.market.api.MarketExecuteResult;
import io.github.henriquemichelini.craftalism.market.api.MarketQuotePair;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteResult;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarketGuiServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void closeSessionRemovesStoredSession() {
        MarketSessionRegistry registry = new MarketSessionRegistry();
        MarketGuiService guiService = new MarketGuiService(
            null,
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                throw new AssertionError();
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            new MarketInventoryService(),
            registry,
            new YamlConfiguration()
        );

        java.util.UUID playerId = java.util.UUID.randomUUID();
        registry.put(
            playerId,
            MarketSession.tradeView("farming", "wheat", false)
        );

        guiService.closeSession(playerId);

        assertFalse(registry.get(playerId).isPresent());
    }

    @Test
    void closeSessionDuringInternalTransitionPreservesSession()
        throws Exception {
        MarketSessionRegistry registry = new MarketSessionRegistry();
        MarketGuiService guiService = new MarketGuiService(
            null,
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                throw new AssertionError();
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            new MarketInventoryService(),
            registry,
            new YamlConfiguration()
        );
        UUID playerId = UUID.randomUUID();
        MarketSession session = MarketSession.tradeView(
            "farming",
            "wheat",
            false
        );
        registry.put(playerId, session);
        internalInventoryTransitions(guiService).add(playerId);

        guiService.closeSession(playerId);

        assertEquals(session, registry.get(playerId).orElseThrow());
        assertFalse(
            internalInventoryTransitions(guiService).contains(playerId)
        );
    }

    @Test
    void currentSnapshotPreservesLiveModeWhenSessionIsMissing()
        throws Exception {
        MarketBrowseSnapshotService snapshotService =
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor());
        snapshotService.loadForInitialOpen().get();
        UUID playerId = UUID.randomUUID();
        MarketGuiService guiService = new MarketGuiService(
            fakePlugin(fakePlayer(playerId)),
            snapshotService,
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                throw new AssertionError();
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            new MarketInventoryService(),
            new MarketSessionRegistry(),
            new YamlConfiguration()
        );

        Method method = MarketGuiService.class.getDeclaredMethod(
            "currentSnapshot",
            Player.class
        );
        method.setAccessible(true);
        MarketBrowseSnapshot snapshot = (MarketBrowseSnapshot) method.invoke(
            guiService,
            fakePlayer(playerId)
        );

        assertFalse(snapshot.readOnly());
    }

    @Test
    void snapshotServiceCanFallbackToReadOnlyCache() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MarketBrowseSnapshotService service = new MarketBrowseSnapshotService(
            () -> {
                if (calls.getAndIncrement() == 0) {
                    return sampleSnapshot(false);
                }
                throw new IllegalStateException("boom");
            },
            directExecutor()
        );

        service.loadForInitialOpen().get();
        var result = service.loadForInitialOpen().get();

        assertFalse(service.currentSnapshot().orElseThrow().readOnly());
        org.junit.jupiter.api.Assertions.assertTrue(
            result.snapshot().readOnly()
        );
    }

    @Test
    void refreshSnapshotReplacesCachedLiveSnapshot() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MarketBrowseSnapshotService service = new MarketBrowseSnapshotService(
            () -> {
                if (calls.getAndIncrement() == 0) {
                    return sampleSnapshot(false);
                }
                return updatedSnapshot(false);
            },
            directExecutor()
        );

        service.loadForInitialOpen().get();
        var refreshResult = service.refreshSnapshot().get();

        assertFalse(refreshResult.fromCache());
        assertEquals(
            "snapshot-v2",
            service.currentSnapshot().orElseThrow().snapshotVersion()
        );
        assertEquals(
            "Stock: 640",
            service
                .currentSnapshot()
                .orElseThrow()
                .findItem("farming", "wheat")
                .orElseThrow()
                .stockDisplay()
        );
    }

    @Test
    void sessionForSnapshotDisablesTradeWhenRefreshedItemIsBlocked() {
        MarketGuiService guiService = guiService(new YamlConfiguration());

        MarketSession refreshed = guiService.sessionForSnapshot(
            MarketSession.tradeView("farming", "wheat", false).withQuantity(4),
            blockedSnapshot(false)
        );

        assertEquals(MarketQuoteStatus.UNAVAILABLE, refreshed.quoteStatus());
        assertEquals(
            "&cThis item is currently blocked.",
            refreshed.quoteStatusMessage()
        );
        assertEquals(4, refreshed.quantity());
    }

    @Test
    void sessionForSnapshotDisablesTradeWhenItemIsNotOperating() {
        MarketGuiService guiService = guiService(new YamlConfiguration());

        MarketSession refreshed = guiService.sessionForSnapshot(
            MarketSession.tradeView("farming", "wheat", false).withQuantity(2),
            unavailableSnapshot(false)
        );

        assertEquals(MarketQuoteStatus.UNAVAILABLE, refreshed.quoteStatus());
        assertEquals(
            "&cThis item is not operating right now.",
            refreshed.quoteStatusMessage()
        );
        assertEquals(2, refreshed.quantity());
    }

    @Test
    void sessionForSnapshotDisablesTradeWhenItemDisappearsFromRefreshedSnapshot() {
        MarketGuiService guiService = guiService(new YamlConfiguration());

        MarketSession refreshed = guiService.sessionForSnapshot(
            MarketSession.tradeView("farming", "wheat", false).withQuantity(2),
            missingItemSnapshot(false)
        );

        assertEquals(MarketQuoteStatus.UNAVAILABLE, refreshed.quoteStatus());
        assertEquals(
            "&cThat item is no longer available.",
            refreshed.quoteStatusMessage()
        );
        assertEquals(2, refreshed.quantity());
    }

    @Test
    void sessionForSnapshotSwitchesToReadOnlyWhenRefreshFallsBackToCache() {
        MarketGuiService guiService = guiService(new YamlConfiguration());

        MarketSession refreshed = guiService.sessionForSnapshot(
            MarketSession.tradeView("farming", "wheat", false).withQuantity(3),
            sampleSnapshot(true)
        );

        assertTrue(refreshed.readOnly());
        assertEquals(MarketQuoteStatus.DISABLED, refreshed.quoteStatus());
        assertEquals("Cached preview only", refreshed.quoteStatusMessage());
    }

    @Test
    void sessionForSnapshotRestoresLiveTradingWithoutRequestingQuote() {
        MarketGuiService guiService = guiService(new YamlConfiguration());

        MarketSession refreshed = guiService.sessionForSnapshot(
            MarketSession.tradeView("farming", "wheat", true),
            updatedSnapshot(false)
        );

        assertFalse(refreshed.readOnly());
        assertEquals(MarketQuoteStatus.AVAILABLE, refreshed.quoteStatus());
        assertEquals("Ready to trade", refreshed.quoteStatusMessage());
    }

    @Test
    void sellRemovalFailurePlayerMessageIncludesSettlementDetails() {
        MarketGuiService guiService = guiService(new YamlConfiguration());

        String message = guiService.sellRemovalFailurePlayerMessage(
            Material.WHEAT,
            new MarketExecuteResult(5, "20.5", "4.1", "coins", "snapshot-v7"),
            3
        );

        assertEquals(
            "&cSell completed for 20.5 coins, but local removal only removed 3/5 WHEAT. Missing: 2. Contact staff.",
            message
        );
    }

    @Test
    void sellRemovalFailureLogMessageIncludesCompensationContext() {
        MarketGuiService guiService = guiService(new YamlConfiguration());
        java.util.UUID playerId = java.util.UUID.fromString(
            "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        );

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
        MarketGuiService guiService = guiService(
            new YamlConfiguration(),
            inventoryAccess
        );
        UUID playerId = UUID.randomUUID();
        Player player = fakePlayer(playerId);

        guiService.queueDeferredSettlement(
            playerId,
            new MarketGuiService.DeferredSettlement(
                MarketQuoteSide.BUY,
                Material.WHEAT,
                new MarketExecuteResult(
                    5,
                    "20.5",
                    "4.1",
                    "coins",
                    "snapshot-v7"
                )
            )
        );

        guiService.handlePlayerJoin(player);

        assertEquals(0, guiService.deferredSettlementCount(playerId));
        assertEquals(5, inventoryAccess.quantity(Material.WHEAT));
    }

    @Test
    void playerJoinLogsAppliedDeferredSettlement() {
        FakeInventoryAccess inventoryAccess = new FakeInventoryAccess();
        UUID playerId = UUID.randomUUID();
        TestLogger testLogger = new TestLogger();
        MarketGuiService guiService = new MarketGuiService(
            fakePlugin(fakePlayer(playerId), testLogger.logger()),
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                throw new AssertionError();
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            inventoryAccess,
            new MarketSessionRegistry(),
            new YamlConfiguration()
        );

        guiService.queueDeferredSettlement(
            playerId,
            new MarketGuiService.DeferredSettlement(
                MarketQuoteSide.BUY,
                Material.WHEAT,
                new MarketExecuteResult(
                    5,
                    "20.5",
                    "4.1",
                    "coins",
                    "snapshot-v7"
                )
            )
        );

        guiService.handlePlayerJoin(fakePlayer(playerId));

        assertTrue(
            testLogger
                .messages(Level.INFO)
                .stream()
                .anyMatch(
                    message ->
                        message.contains(
                            "Applied deferred market buy settlement for player " +
                                playerId
                        ) &&
                        message.contains("item=WHEAT") &&
                        message.contains("executed=5") &&
                        message.contains("snapshotVersion=snapshot-v7")
                )
        );
    }

    @Test
    void playerJoinAppliesDeferredSellSettlement() {
        FakeInventoryAccess inventoryAccess = new FakeInventoryAccess();
        inventoryAccess.setQuantity(Material.WHEAT, 4);
        MarketGuiService guiService = guiService(
            new YamlConfiguration(),
            inventoryAccess
        );
        UUID playerId = UUID.randomUUID();
        Player player = fakePlayer(playerId);

        guiService.queueDeferredSettlement(
            playerId,
            new MarketGuiService.DeferredSettlement(
                MarketQuoteSide.SELL,
                Material.WHEAT,
                new MarketExecuteResult(
                    4,
                    "16.4",
                    "4.1",
                    "coins",
                    "snapshot-v7"
                )
            )
        );

        guiService.handlePlayerJoin(player);

        assertEquals(0, guiService.deferredSettlementCount(playerId));
        assertEquals(0, inventoryAccess.quantity(Material.WHEAT));
    }

    @Test
    void deferredSettlementPersistsAcrossServiceRecreation() {
        FakeInventoryAccess inventoryAccess = new FakeInventoryAccess();
        UUID playerId = UUID.randomUUID();

        MarketGuiService firstService = new MarketGuiService(
            fakePlugin(
                fakeOfflinePlayer(playerId),
                Logger.getLogger("first"),
                tempDir.toFile()
            ),
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                throw new AssertionError();
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            inventoryAccess,
            new MarketSessionRegistry(),
            new YamlConfiguration()
        );
        firstService.queueDeferredSettlement(
            playerId,
            new MarketGuiService.DeferredSettlement(
                MarketQuoteSide.BUY,
                Material.WHEAT,
                new MarketExecuteResult(
                    6,
                    "24.6",
                    "4.1",
                    "coins",
                    "snapshot-v8"
                )
            )
        );

        MarketGuiService recreatedService = new MarketGuiService(
            fakePlugin(
                fakePlayer(playerId),
                Logger.getLogger("second"),
                tempDir.toFile()
            ),
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                throw new AssertionError();
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            inventoryAccess,
            new MarketSessionRegistry(),
            new YamlConfiguration()
        );

        assertEquals(1, recreatedService.deferredSettlementCount(playerId));

        recreatedService.handlePlayerJoin(fakePlayer(playerId));

        assertEquals(0, recreatedService.deferredSettlementCount(playerId));
        assertEquals(6, inventoryAccess.quantity(Material.WHEAT));
    }

    @Test
    void failedDeferredSellSettlementRemainsQueued() {
        FakeInventoryAccess inventoryAccess = new FakeInventoryAccess();
        inventoryAccess.setQuantity(Material.WHEAT, 2);
        UUID playerId = UUID.randomUUID();
        MarketGuiService guiService = new MarketGuiService(
            fakePlugin(
                fakePlayer(playerId),
                Logger.getLogger("failed-sell"),
                tempDir.toFile()
            ),
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                throw new AssertionError();
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            inventoryAccess,
            new MarketSessionRegistry(),
            configWithSettlementMessages()
        );
        guiService.queueDeferredSettlement(
            playerId,
            new MarketGuiService.DeferredSettlement(
                MarketQuoteSide.SELL,
                Material.WHEAT,
                new MarketExecuteResult(
                    4,
                    "16.4",
                    "4.1",
                    "coins",
                    "snapshot-v7"
                )
            )
        );

        guiService.handlePlayerJoin(fakePlayer(playerId));

        assertEquals(1, guiService.deferredSettlementCount(playerId));
        assertEquals(2, inventoryAccess.quantity(Material.WHEAT));
    }

    @Test
    void failedDeferredSellSettlementLogsStillQueuedState() {
        FakeInventoryAccess inventoryAccess = new FakeInventoryAccess();
        inventoryAccess.setQuantity(Material.WHEAT, 2);
        UUID playerId = UUID.randomUUID();
        TestLogger testLogger = new TestLogger();
        MarketGuiService guiService = new MarketGuiService(
            fakePlugin(
                fakePlayer(playerId),
                testLogger.logger(),
                tempDir.toFile()
            ),
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                throw new AssertionError();
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            inventoryAccess,
            new MarketSessionRegistry(),
            configWithSettlementMessages()
        );
        guiService.queueDeferredSettlement(
            playerId,
            new MarketGuiService.DeferredSettlement(
                MarketQuoteSide.SELL,
                Material.WHEAT,
                new MarketExecuteResult(
                    4,
                    "16.4",
                    "4.1",
                    "coins",
                    "snapshot-v7"
                )
            )
        );

        guiService.handlePlayerJoin(fakePlayer(playerId));

        assertTrue(
            testLogger
                .messages(Level.SEVERE)
                .stream()
                .anyMatch(
                    message ->
                        message.contains(
                            "Deferred market sell settlement remains queued for player " +
                                playerId
                        ) &&
                        message.contains("item=WHEAT") &&
                        message.contains("executed=4") &&
                        message.contains("snapshotVersion=snapshot-v7")
                )
        );
    }

    @Test
    void staleAndExpiredBuyRejectionsRequeueFreshQuotes() throws Exception {
        for (String code : List.of("STALE_QUOTE", "QUOTE_EXPIRED")) {
            YamlConfiguration config = new YamlConfiguration();
            config.set(
                "messages.rejections.STALE_QUOTE",
                "&eThat quote is stale. Refreshing now."
            );
            config.set(
                "messages.rejections.QUOTE_EXPIRED",
                "&eThat quote expired. Refreshing now."
            );

            MarketSessionRegistry registry = new MarketSessionRegistry();
            UUID playerId = UUID.randomUUID();
            Player player = fakeOfflinePlayer(playerId);
            Plugin plugin = fakePlugin(player);
            AtomicInteger quoteCalls = new AtomicInteger();
            MarketGuiService guiService = new MarketGuiService(
                plugin,
                new MarketBrowseSnapshotService(
                    sampleProvider(),
                    directExecutor()
                ),
                (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                    quoteCalls.incrementAndGet();
                    return quote(side, quantity, snapshotVersion);
                },
                (
                    ignoredPlayerId,
                    itemId,
                    side,
                    quantity,
                    quoteToken,
                    snapshotVersion
                ) -> {
                    throw new AssertionError();
                },
                new FakeInventoryAccess(),
                registry,
                config
            );

            MarketSession executingSession = MarketSession.tradeView(
                "farming",
                "wheat",
                false
            )
                .withQuantity(5)
                .withQuotePair(
                    new MarketQuotePair(
                        new MarketQuoteResult(
                            MarketQuoteSide.BUY,
                            5,
                            "24.0",
                            "4.8",
                            "coins",
                            "buy-token",
                            "snapshot-v1"
                        ),
                        new MarketQuoteResult(
                            MarketQuoteSide.SELL,
                            5,
                            "20.5",
                            "4.1",
                            "coins",
                            "sell-token",
                            "snapshot-v1"
                        )
                    )
                )
                .withExecutionPending(MarketQuoteSide.BUY);
            registry.put(playerId, executingSession);

            Method method = MarketGuiService.class.getDeclaredMethod(
                "applyBuyRejection",
                UUID.class,
                MarketSession.class,
                MarketExecuteRejectedException.class
            );
            method.setAccessible(true);
            method.invoke(
                guiService,
                playerId,
                executingSession,
                new MarketExecuteRejectedException(
                    code,
                    "Rejected",
                    "snapshot-v2"
                )
            );

            MarketSession updated = registry.get(playerId).orElseThrow();
            assertEquals(5, updated.quantity());
            assertEquals(MarketQuoteStatus.AVAILABLE, updated.quoteStatus());
            assertEquals("Quotes ready", updated.quoteStatusMessage());
            assertEquals(
                executingSession.quoteRequestVersion() + 1,
                updated.quoteRequestVersion()
            );
            assertEquals("buy-token-5", updated.buyQuoteToken());
            assertEquals("sell-token-5", updated.sellQuoteToken());
            assertEquals(2, quoteCalls.get());
            assertFalse(updated.executingBuy());
            assertFalse(updated.executingSell());
        }
    }

    @Test
    void staleAndExpiredSellRejectionsRequeueFreshQuotes() throws Exception {
        for (String code : List.of("STALE_QUOTE", "QUOTE_EXPIRED")) {
            YamlConfiguration config = new YamlConfiguration();
            config.set(
                "messages.rejections.STALE_QUOTE",
                "&eThat quote is stale. Refreshing now."
            );
            config.set(
                "messages.rejections.QUOTE_EXPIRED",
                "&eThat quote expired. Refreshing now."
            );

            MarketSessionRegistry registry = new MarketSessionRegistry();
            UUID playerId = UUID.randomUUID();
            Player player = fakeOfflinePlayer(playerId);
            Plugin plugin = fakePlugin(player);
            AtomicInteger quoteCalls = new AtomicInteger();
            MarketGuiService guiService = new MarketGuiService(
                plugin,
                new MarketBrowseSnapshotService(
                    sampleProvider(),
                    directExecutor()
                ),
                (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                    quoteCalls.incrementAndGet();
                    return quote(side, quantity, snapshotVersion);
                },
                (
                    ignoredPlayerId,
                    itemId,
                    side,
                    quantity,
                    quoteToken,
                    snapshotVersion
                ) -> {
                    throw new AssertionError();
                },
                new FakeInventoryAccess(),
                registry,
                config
            );

            MarketSession executingSession = MarketSession.tradeView(
                "farming",
                "wheat",
                false
            )
                .withQuantity(5)
                .withQuotePair(
                    new MarketQuotePair(
                        new MarketQuoteResult(
                            MarketQuoteSide.BUY,
                            5,
                            "24.0",
                            "4.8",
                            "coins",
                            "buy-token",
                            "snapshot-v1"
                        ),
                        new MarketQuoteResult(
                            MarketQuoteSide.SELL,
                            5,
                            "20.5",
                            "4.1",
                            "coins",
                            "sell-token",
                            "snapshot-v1"
                        )
                    )
                )
                .withExecutionPending(MarketQuoteSide.SELL);
            registry.put(playerId, executingSession);

            Method method = MarketGuiService.class.getDeclaredMethod(
                "applySellRejection",
                UUID.class,
                MarketSession.class,
                MarketExecuteRejectedException.class
            );
            method.setAccessible(true);
            method.invoke(
                guiService,
                playerId,
                executingSession,
                new MarketExecuteRejectedException(
                    code,
                    "Rejected",
                    "snapshot-v2"
                )
            );

            MarketSession updated = registry.get(playerId).orElseThrow();
            assertEquals(5, updated.quantity());
            assertEquals(MarketQuoteStatus.AVAILABLE, updated.quoteStatus());
            assertEquals("Quotes ready", updated.quoteStatusMessage());
            assertEquals(
                executingSession.quoteRequestVersion() + 1,
                updated.quoteRequestVersion()
            );
            assertEquals("buy-token-5", updated.buyQuoteToken());
            assertEquals("sell-token-5", updated.sellQuoteToken());
            assertEquals(2, quoteCalls.get());
            assertFalse(updated.executingBuy());
            assertFalse(updated.executingSell());
        }
    }

    @Test
    void staleQuoteDuringQuoteRefreshRequeuesFreshQuotes() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set(
            "messages.rejections.STALE_QUOTE",
            "&eThat quote is stale. Refreshing now."
        );

        AtomicInteger snapshotCalls = new AtomicInteger();
        MarketBrowseSnapshotService snapshotService =
            new MarketBrowseSnapshotService(
                () ->
                    snapshotCalls.getAndIncrement() == 0
                        ? sampleSnapshot(false)
                        : updatedSnapshot(false),
                directExecutor()
            );
        snapshotService.loadForInitialOpen().get();

        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        Player player = fakeOfflinePlayer(playerId);
        Plugin plugin = fakePlugin(player);
        AtomicInteger quoteCalls = new AtomicInteger();
        MarketGuiService guiService = new MarketGuiService(
            plugin,
            snapshotService,
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                if (quoteCalls.getAndIncrement() == 0) {
                    throw new MarketApiRequestException(
                        409,
                        "{\"status\":\"REJECTED\",\"code\":\"STALE_QUOTE\",\"message\":\"Snapshot is no longer current.\",\"snapshotVersion\":\"market:ff6dde56c6ed25f5\"}"
                    );
                }
                return quote(side, quantity, snapshotVersion);
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            new FakeInventoryAccess(),
            registry,
            config
        );
        registry.put(
            playerId,
            MarketSession.tradeView("farming", "wheat", false)
                .withQuoteRefreshPending()
        );

        Method method = MarketGuiService.class.getDeclaredMethod(
            "requestQuotePairAsync",
            UUID.class,
            String.class,
            String.class,
            int.class,
            int.class,
            String.class
        );
        method.setAccessible(true);
        method.invoke(
            guiService,
            playerId,
            "farming",
            "wheat",
            1,
            1,
            "snapshot-v1"
        );

        MarketSession updated = registry.get(playerId).orElseThrow();
        assertEquals(MarketQuoteStatus.AVAILABLE, updated.quoteStatus());
        assertEquals("Quotes ready", updated.quoteStatusMessage());
        assertEquals("buy-token-1", updated.buyQuoteToken());
        assertEquals("sell-token-1", updated.sellQuoteToken());
        assertEquals(3, quoteCalls.get());
        assertEquals(2, updated.quoteRequestVersion());
    }

    @Test
    void quantityAdjustmentIsIgnoredWhileTradeExecutionIsInFlight()
        throws Exception {
        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        Player player = fakePlayer(playerId);
        MarketGuiService guiService = new MarketGuiService(
            null,
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                throw new AssertionError();
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            new FakeInventoryAccess(),
            registry,
            new YamlConfiguration()
        );
        MarketSession executingSession = MarketSession.tradeView(
            "farming",
            "wheat",
            false
        )
            .withQuantity(4)
            .withQuotePair(
                new MarketQuotePair(
                    new MarketQuoteResult(
                        MarketQuoteSide.BUY,
                        4,
                        "19.2",
                        "4.8",
                        "coins",
                        "buy-token",
                        "snapshot-v1"
                    ),
                    new MarketQuoteResult(
                        MarketQuoteSide.SELL,
                        4,
                        "16.4",
                        "4.1",
                        "coins",
                        "sell-token",
                        "snapshot-v1"
                    )
                )
            )
            .withExecutionPending(MarketQuoteSide.BUY);
        registry.put(playerId, executingSession);

        Method method = MarketGuiService.class.getDeclaredMethod(
            "adjustTradeQuantity",
            Player.class,
            String.class,
            String.class,
            int.class
        );
        method.setAccessible(true);
        method.invoke(guiService, player, "farming", "wheat", 1);

        MarketSession updated = registry.get(playerId).orElseThrow();
        assertEquals(executingSession, updated);
    }

    @Test
    void tradeQuantityDeltaMappingCoversExpandedStepButtons() throws Exception {
        MarketGuiService guiService = guiService(new YamlConfiguration());

        Method method = MarketGuiService.class.getDeclaredMethod(
            "tradeQuantityDeltaForSlot",
            int.class
        );
        method.setAccessible(true);

        assertEquals(1, method.invoke(guiService, 7));
        assertEquals(8, method.invoke(guiService, 8));
        assertEquals(32, method.invoke(guiService, 16));
        assertEquals(64, method.invoke(guiService, 17));
        assertEquals(576, method.invoke(guiService, 25));
        assertEquals(2304, method.invoke(guiService, 26));
        assertEquals(-1, method.invoke(guiService, 0));
        assertEquals(-8, method.invoke(guiService, 1));
        assertEquals(-32, method.invoke(guiService, 9));
        assertEquals(-64, method.invoke(guiService, 10));
        assertEquals(-576, method.invoke(guiService, 18));
        assertEquals(-2304, method.invoke(guiService, 19));
    }

    @Test
    void tradeQuantityControlsMatchAuditLayout() throws Exception {
        Field field = MarketGuiService.class.getDeclaredField(
            "QUANTITY_CONTROLS"
        );
        field.setAccessible(true);
        List<?> controls = (List<?>) field.get(null);
        Map<Integer, ExpectedQuantityControl> expected = Map.ofEntries(
            Map.entry(
                7,
                new ExpectedQuantityControl(
                    1,
                    Material.LIME_STAINED_GLASS_PANE,
                    1,
                    false
                )
            ),
            Map.entry(
                8,
                new ExpectedQuantityControl(
                    8,
                    Material.LIME_STAINED_GLASS_PANE,
                    8,
                    false
                )
            ),
            Map.entry(
                16,
                new ExpectedQuantityControl(
                    32,
                    Material.LIME_STAINED_GLASS_PANE,
                    32,
                    false
                )
            ),
            Map.entry(
                17,
                new ExpectedQuantityControl(
                    64,
                    Material.LIME_STAINED_GLASS_PANE,
                    64,
                    false
                )
            ),
            Map.entry(
                25,
                new ExpectedQuantityControl(
                    576,
                    Material.GREEN_STAINED_GLASS_PANE,
                    1,
                    false
                )
            ),
            Map.entry(
                26,
                new ExpectedQuantityControl(
                    2304,
                    Material.GREEN_STAINED_GLASS_PANE,
                    1,
                    true
                )
            ),
            Map.entry(
                0,
                new ExpectedQuantityControl(
                    -1,
                    Material.PINK_STAINED_GLASS_PANE,
                    1,
                    false
                )
            ),
            Map.entry(
                1,
                new ExpectedQuantityControl(
                    -8,
                    Material.PINK_STAINED_GLASS_PANE,
                    8,
                    false
                )
            ),
            Map.entry(
                9,
                new ExpectedQuantityControl(
                    -32,
                    Material.PINK_STAINED_GLASS_PANE,
                    32,
                    false
                )
            ),
            Map.entry(
                10,
                new ExpectedQuantityControl(
                    -64,
                    Material.PINK_STAINED_GLASS_PANE,
                    64,
                    false
                )
            ),
            Map.entry(
                18,
                new ExpectedQuantityControl(
                    -576,
                    Material.RED_STAINED_GLASS_PANE,
                    1,
                    false
                )
            ),
            Map.entry(
                19,
                new ExpectedQuantityControl(
                    -2304,
                    Material.RED_STAINED_GLASS_PANE,
                    1,
                    true
                )
            )
        );

        assertEquals(expected.size(), controls.size());
        for (Object control : controls) {
            int slot = (int) recordValue(control, "slot");
            ExpectedQuantityControl expectedControl = expected.get(slot);

            assertEquals(
                expectedControl.delta(),
                recordValue(control, "delta")
            );
            assertEquals(
                expectedControl.material(),
                recordValue(control, "material")
            );
            assertEquals(
                expectedControl.itemAmount(),
                recordValue(control, "itemAmount")
            );
            assertEquals(
                expectedControl.enchanted(),
                recordValue(control, "enchanted")
            );
        }
    }

    @Test
    void tradeActionSlotsDoNotOverlapExpandedQuantityButtons()
        throws Exception {
        MarketGuiService guiService = guiService(new YamlConfiguration());
        Method quantityDelta = MarketGuiService.class.getDeclaredMethod(
            "tradeQuantityDeltaForSlot",
            int.class
        );
        quantityDelta.setAccessible(true);

        for (String fieldName : List.of(
            "TRADE_BUY_SLOT",
            "TRADE_ITEM_SLOT",
            "TRADE_SELL_SLOT"
        )) {
            Field field = MarketGuiService.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            int slot = field.getInt(null);

            assertEquals(null, quantityDelta.invoke(guiService, slot));
        }
    }

    @Test
    void quantityAdjustmentDoesNotSendFeedbackWhenQuantityIsUnchanged()
        throws Exception {
        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        MarketGuiService guiService = new MarketGuiService(
            null,
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                throw new AssertionError();
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            new FakeInventoryAccess(),
            registry,
            new YamlConfiguration()
        );
        registry.put(
            playerId,
            MarketSession.tradeView("farming", "wheat", false)
        );

        Method method = MarketGuiService.class.getDeclaredMethod(
            "adjustTradeQuantity",
            Player.class,
            String.class,
            String.class,
            int.class
        );
        method.setAccessible(true);
        method.invoke(
            guiService,
            fakeOnlinePlayer(playerId, messages),
            "farming",
            "wheat",
            -1
        );

        assertEquals(1, registry.get(playerId).orElseThrow().quantity());
        assertTrue(messages.isEmpty());
    }

    @Test
    void quantityAdjustmentRequestsFreshQuotesForUpdatedQuantity()
        throws Exception {
        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        AtomicInteger quoteCalls = new AtomicInteger();
        Player onlinePlayer = fakeOnlinePlayer(playerId, messages);
        MarketBrowseSnapshotService snapshotService =
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor());
        snapshotService.loadForInitialOpen().get();
        MarketGuiService guiService = new MarketGuiService(
            fakePlugin(onlinePlayer),
            snapshotService,
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                quoteCalls.incrementAndGet();
                return quote(side, quantity, snapshotVersion);
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            new FakeInventoryAccess(),
            registry,
            new YamlConfiguration()
        );
        registry.put(
            playerId,
            MarketSession.tradeView("farming", "wheat", false)
                .withQuotePair(
                    new MarketQuotePair(
                        new MarketQuoteResult(
                            MarketQuoteSide.BUY,
                            1,
                            "4.8",
                            "4.8",
                            "coins",
                            "buy-token",
                            "snapshot-v1"
                        ),
                        new MarketQuoteResult(
                            MarketQuoteSide.SELL,
                            1,
                            "4.1",
                            "4.1",
                            "coins",
                            "sell-token",
                            "snapshot-v1"
                        )
                    )
                )
        );

        Method method = MarketGuiService.class.getDeclaredMethod(
            "adjustTradeQuantity",
            Player.class,
            String.class,
            String.class,
            int.class
        );
        method.setAccessible(true);
        method.invoke(guiService, onlinePlayer, "farming", "wheat", 1);

        MarketSession updated = registry.get(playerId).orElseThrow();
        assertEquals(2, updated.quantity());
        assertEquals(MarketQuoteStatus.AVAILABLE, updated.quoteStatus());
        assertEquals("buy-token-2", updated.buyQuoteToken());
        assertEquals("sell-token-2", updated.sellQuoteToken());
        assertEquals(2, quoteCalls.get());
        assertTrue(
            messages.stream().anyMatch(message -> message.contains("Quantity:"))
        );
    }

    @Test
    void sellQuantityAdjustmentRequestsFreshQuotesForHeldQuantity()
        throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set(
            "messages.sell-quantity-adjusted",
            "&eAdjusted sell quantity to {quantity} based on your inventory."
        );

        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        FakeInventoryAccess inventoryAccess = new FakeInventoryAccess();
        inventoryAccess.setQuantity(Material.WHEAT, 2);
        MarketBrowseSnapshotService snapshotService =
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor());
        snapshotService.loadForInitialOpen().get();
        AtomicInteger quoteCalls = new AtomicInteger();
        Player offlinePlayer = fakeOfflinePlayer(playerId);
        MarketGuiService guiService = new MarketGuiService(
            fakePlugin(offlinePlayer),
            snapshotService,
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                quoteCalls.incrementAndGet();
                return quote(side, quantity, snapshotVersion);
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            inventoryAccess,
            registry,
            config
        );
        MarketSession availableSession = MarketSession.tradeView(
            "farming",
            "wheat",
            false
        )
            .withQuantity(5)
            .withQuotePair(
                new MarketQuotePair(
                    new MarketQuoteResult(
                        MarketQuoteSide.BUY,
                        5,
                        "24.0",
                        "4.8",
                        "coins",
                        "buy-token",
                        "snapshot-v1"
                    ),
                    new MarketQuoteResult(
                        MarketQuoteSide.SELL,
                        5,
                        "20.5",
                        "4.1",
                        "coins",
                        "sell-token",
                        "snapshot-v1"
                    )
                )
            );
        registry.put(playerId, availableSession);

        Method method = MarketGuiService.class.getDeclaredMethod(
            "handleSellClick",
            Player.class,
            String.class,
            String.class
        );
        method.setAccessible(true);
        method.invoke(guiService, fakePlayer(playerId), "farming", "wheat");

        MarketSession updated = registry.get(playerId).orElseThrow();
        assertEquals(2, updated.quantity());
        assertEquals(MarketQuoteStatus.AVAILABLE, updated.quoteStatus());
        assertEquals("Quotes ready", updated.quoteStatusMessage());
        assertEquals(availableSession.quoteRequestVersion() + 1, updated.quoteRequestVersion());
        assertEquals("buy-token-2", updated.buyQuoteToken());
        assertEquals("sell-token-2", updated.sellQuoteToken());
        assertEquals(2, quoteCalls.get());
        assertFalse(updated.executingBuy());
        assertFalse(updated.executingSell());
    }

    @Test
    void buyQuoteRejectionStillAllowsSellExecution() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set(
            "messages.rejections.INSUFFICIENT_STOCK",
            "&cThere is not enough stock for that purchase."
        );

        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        FakeInventoryAccess inventoryAccess = new FakeInventoryAccess();
        inventoryAccess.setQuantity(Material.WHEAT, 1);
        MarketBrowseSnapshotService snapshotService =
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor());
        snapshotService.loadForInitialOpen().get();
        List<String> messages = new ArrayList<>();
        Player onlinePlayer = fakeOnlinePlayer(playerId, messages);
        AtomicInteger executeCalls = new AtomicInteger();
        List<MarketQuoteSide> executedSides = new ArrayList<>();
        MarketGuiService guiService = new MarketGuiService(
            fakePlugin(onlinePlayer),
            snapshotService,
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                if (side == MarketQuoteSide.BUY) {
                    throw new MarketApiRequestException(
                        422,
                        "{\"status\":\"REJECTED\",\"code\":\"INSUFFICIENT_STOCK\",\"message\":\"Requested quantity exceeds restorable capacity.\",\"snapshotVersion\":\"market:a57dec47db0ebb2f\"}"
                    );
                }
                return quote(side, quantity, snapshotVersion);
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                executeCalls.incrementAndGet();
                executedSides.add(side);
                return new MarketExecuteResult(
                    quantity,
                    "4.1",
                    "4.1",
                    "coins",
                    "snapshot-v2"
                );
            },
            inventoryAccess,
            registry,
            config
        );

        registry.put(
            playerId,
            MarketSession.tradeView("farming", "wheat", false)
                .withQuoteRefreshPending()
        );

        Method quoteMethod = MarketGuiService.class.getDeclaredMethod(
            "requestQuotePairAsync",
            UUID.class,
            String.class,
            String.class,
            int.class,
            int.class,
            String.class
        );
        quoteMethod.setAccessible(true);
        quoteMethod.invoke(
            guiService,
            playerId,
            "farming",
            "wheat",
            1,
            1,
            "snapshot-v1"
        );

        MarketSession quotedSession = registry.get(playerId).orElseThrow();
        assertEquals(MarketQuoteStatus.AVAILABLE, quotedSession.quoteStatus());
        assertEquals("Partial quotes ready", quotedSession.quoteStatusMessage());
        assertEquals(
            "&cThere is not enough stock for that purchase.",
            quotedSession.buyQuoteMessage()
        );
        assertEquals("Quote ready", quotedSession.sellQuoteMessage());
        assertEquals(null, quotedSession.buyQuoteToken());
        assertEquals(null, quotedSession.buyQuoteSnapshotVersion());
        assertEquals("sell-token-1", quotedSession.sellQuoteToken());
        assertEquals("snapshot-v1", quotedSession.sellQuoteSnapshotVersion());
        assertEquals("4 coins", quotedSession.sellQuotedTotal());
        assertEquals(0, executeCalls.get());
    }

    @Test
    void sideUnavailableMessageUsesBuySideQuoteMessage() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        MarketGuiService guiService = guiService(config);
        MarketSession session = MarketSession.tradeView("farming", "wheat", false)
            .withQuoteResults(
                null,
                "&cThere is not enough stock for that purchase.",
                quote(MarketQuoteSide.SELL, 1, "snapshot-v1"),
                "Quote ready",
                "Partial quotes ready"
            );

        Method method = MarketGuiService.class.getDeclaredMethod(
            "sideUnavailableMessage",
            MarketSession.class,
            MarketQuoteSide.class
        );
        method.setAccessible(true);

        assertEquals(
            "&cThere is not enough stock for that purchase.",
            method.invoke(guiService, session, MarketQuoteSide.BUY)
        );
    }

    @Test
    void onlineBuySuccessDeliversItemsAndReturnsSessionToReadyTrading()
        throws Exception {
        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        FakeInventoryAccess inventoryAccess = new FakeInventoryAccess();
        MarketBrowseSnapshotService snapshotService =
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor());
        snapshotService.loadForInitialOpen().get();
        Player onlinePlayer = fakeOnlinePlayer(playerId);
        AtomicInteger quoteCalls = new AtomicInteger();
        MarketGuiService guiService = new MarketGuiService(
            fakePlugin(onlinePlayer),
            snapshotService,
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                quoteCalls.incrementAndGet();
                return quote(side, quantity, snapshotVersion);
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            inventoryAccess,
            registry,
            new YamlConfiguration()
        );
        MarketSession executingSession = MarketSession.tradeView(
            "farming",
            "wheat",
            false
        )
            .withQuantity(3)
            .withQuotePair(
                new MarketQuotePair(
                    new MarketQuoteResult(
                        MarketQuoteSide.BUY,
                        3,
                        "14.4",
                        "4.8",
                        "coins",
                        "buy-token",
                        "snapshot-v1"
                    ),
                    new MarketQuoteResult(
                        MarketQuoteSide.SELL,
                        3,
                        "12.3",
                        "4.1",
                        "coins",
                        "sell-token",
                        "snapshot-v1"
                    )
                )
            )
            .withExecutionPending(MarketQuoteSide.BUY);
        registry.put(playerId, executingSession);

        Method method = MarketGuiService.class.getDeclaredMethod(
            "applyBuySuccess",
            UUID.class,
            Material.class,
            MarketSession.class,
            MarketExecuteResult.class
        );
        method.setAccessible(true);
        method.invoke(
            guiService,
            playerId,
            Material.WHEAT,
            executingSession,
            new MarketExecuteResult(3, "14.4", "4.8", "coins", "snapshot-v2")
        );

        MarketSession updated = registry.get(playerId).orElseThrow();
        assertEquals(3, inventoryAccess.quantity(Material.WHEAT));
        assertEquals(3, updated.quantity());
        assertEquals(MarketQuoteStatus.AVAILABLE, updated.quoteStatus());
        assertEquals("Quotes ready", updated.quoteStatusMessage());
        assertEquals("buy-token-3", updated.buyQuoteToken());
        assertEquals("sell-token-3", updated.sellQuoteToken());
        assertEquals(2, quoteCalls.get());
        assertFalse(updated.executingBuy());
        assertFalse(updated.executingSell());
    }

    @Test
    void offlineBuySuccessQueuesDeferredSettlementAndReturnsSessionToReadyTrading()
        throws Exception {
        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        FakeInventoryAccess inventoryAccess = new FakeInventoryAccess();
        MarketBrowseSnapshotService snapshotService =
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor());
        snapshotService.loadForInitialOpen().get();
        Player offlinePlayer = fakeOfflinePlayer(playerId);
        AtomicInteger quoteCalls = new AtomicInteger();
        MarketGuiService guiService = new MarketGuiService(
            fakePlugin(offlinePlayer),
            snapshotService,
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                quoteCalls.incrementAndGet();
                return quote(side, quantity, snapshotVersion);
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            inventoryAccess,
            registry,
            new YamlConfiguration()
        );
        MarketSession executingSession = MarketSession.tradeView(
            "farming",
            "wheat",
            false
        )
            .withQuantity(3)
            .withQuotePair(
                new MarketQuotePair(
                    new MarketQuoteResult(
                        MarketQuoteSide.BUY,
                        3,
                        "14.4",
                        "4.8",
                        "coins",
                        "buy-token",
                        "snapshot-v1"
                    ),
                    new MarketQuoteResult(
                        MarketQuoteSide.SELL,
                        3,
                        "12.3",
                        "4.1",
                        "coins",
                        "sell-token",
                        "snapshot-v1"
                    )
                )
            )
            .withExecutionPending(MarketQuoteSide.BUY);
        registry.put(playerId, executingSession);

        Method method = MarketGuiService.class.getDeclaredMethod(
            "applyBuySuccess",
            UUID.class,
            Material.class,
            MarketSession.class,
            MarketExecuteResult.class
        );
        method.setAccessible(true);
        method.invoke(
            guiService,
            playerId,
            Material.WHEAT,
            executingSession,
            new MarketExecuteResult(3, "14.4", "4.8", "coins", "snapshot-v2")
        );

        MarketSession updated = registry.get(playerId).orElseThrow();
        assertEquals(0, inventoryAccess.quantity(Material.WHEAT));
        assertEquals(1, guiService.deferredSettlementCount(playerId));
        assertEquals(3, updated.quantity());
        assertEquals(MarketQuoteStatus.AVAILABLE, updated.quoteStatus());
        assertEquals("Quotes ready", updated.quoteStatusMessage());
        assertEquals("buy-token-3", updated.buyQuoteToken());
        assertEquals("sell-token-3", updated.sellQuoteToken());
        assertEquals(2, quoteCalls.get());
        assertFalse(updated.executingBuy());
        assertFalse(updated.executingSell());
    }

    @Test
    void onlineBuyFailureRestoresAvailableStateWithExecuteUnavailableMessage()
        throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set(
            "messages.execute-unavailable",
            "&cTrade execution is currently unavailable."
        );

        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        Player onlinePlayer = fakeOnlinePlayer(playerId);
        MarketGuiService guiService = new MarketGuiService(
            fakePlugin(onlinePlayer),
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                throw new AssertionError();
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            new FakeInventoryAccess(),
            registry,
            config
        );
        MarketSession executingSession = MarketSession.tradeView(
            "farming",
            "wheat",
            false
        )
            .withQuantity(2)
            .withQuotePair(
                new MarketQuotePair(
                    new MarketQuoteResult(
                        MarketQuoteSide.BUY,
                        2,
                        "9.6",
                        "4.8",
                        "coins",
                        "buy-token",
                        "snapshot-v1"
                    ),
                    new MarketQuoteResult(
                        MarketQuoteSide.SELL,
                        2,
                        "8.2",
                        "4.1",
                        "coins",
                        "sell-token",
                        "snapshot-v1"
                    )
                )
            )
            .withExecutionPending(MarketQuoteSide.BUY);
        registry.put(playerId, executingSession);

        Method method = MarketGuiService.class.getDeclaredMethod(
            "applyBuyFailure",
            UUID.class,
            MarketSession.class,
            RuntimeException.class
        );
        method.setAccessible(true);
        method.invoke(
            guiService,
            playerId,
            executingSession,
            new IllegalStateException("boom")
        );

        MarketSession updated = registry.get(playerId).orElseThrow();
        assertEquals(MarketQuoteStatus.AVAILABLE, updated.quoteStatus());
        assertEquals(
            "&cTrade execution is currently unavailable.",
            updated.quoteStatusMessage()
        );
        assertFalse(updated.executingBuy());
        assertFalse(updated.executingSell());
    }

    @Test
    void onlineSellRejectionRestoresAvailableStateWithMappedMessage()
        throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set(
            "messages.rejections.INSUFFICIENT_STOCK",
            "&cThere is not enough stock for that purchase."
        );

        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        Player onlinePlayer = fakeOnlinePlayer(playerId);
        MarketGuiService guiService = new MarketGuiService(
            fakePlugin(onlinePlayer),
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                throw new AssertionError();
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            new FakeInventoryAccess(),
            registry,
            config
        );
        MarketSession executingSession = MarketSession.tradeView(
            "farming",
            "wheat",
            false
        )
            .withQuantity(2)
            .withQuotePair(
                new MarketQuotePair(
                    new MarketQuoteResult(
                        MarketQuoteSide.BUY,
                        2,
                        "9.6",
                        "4.8",
                        "coins",
                        "buy-token",
                        "snapshot-v1"
                    ),
                    new MarketQuoteResult(
                        MarketQuoteSide.SELL,
                        2,
                        "8.2",
                        "4.1",
                        "coins",
                        "sell-token",
                        "snapshot-v1"
                    )
                )
            )
            .withExecutionPending(MarketQuoteSide.SELL);
        registry.put(playerId, executingSession);

        Method method = MarketGuiService.class.getDeclaredMethod(
            "applySellRejection",
            UUID.class,
            MarketSession.class,
            MarketExecuteRejectedException.class
        );
        method.setAccessible(true);
        method.invoke(
            guiService,
            playerId,
            executingSession,
            new MarketExecuteRejectedException(
                "INSUFFICIENT_STOCK",
                "Rejected",
                "snapshot-v2"
            )
        );

        MarketSession updated = registry.get(playerId).orElseThrow();
        assertEquals(MarketQuoteStatus.AVAILABLE, updated.quoteStatus());
        assertEquals(
            "&cThere is not enough stock for that purchase.",
            updated.quoteStatusMessage()
        );
        assertFalse(updated.executingBuy());
        assertFalse(updated.executingSell());
    }

    @Test
    void onlineSellSuccessRemovesItemsAndReturnsSessionToReadyTrading()
        throws Exception {
        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        FakeInventoryAccess inventoryAccess = new FakeInventoryAccess();
        inventoryAccess.setQuantity(Material.WHEAT, 4);
        MarketBrowseSnapshotService snapshotService =
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor());
        snapshotService.loadForInitialOpen().get();
        Player onlinePlayer = fakeOnlinePlayer(playerId);
        AtomicInteger quoteCalls = new AtomicInteger();
        MarketGuiService guiService = new MarketGuiService(
            fakePlugin(onlinePlayer),
            snapshotService,
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                quoteCalls.incrementAndGet();
                return quote(side, quantity, snapshotVersion);
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            inventoryAccess,
            registry,
            new YamlConfiguration()
        );
        MarketSession executingSession = MarketSession.tradeView(
            "farming",
            "wheat",
            false
        )
            .withQuantity(4)
            .withQuotePair(
                new MarketQuotePair(
                    new MarketQuoteResult(
                        MarketQuoteSide.BUY,
                        4,
                        "19.2",
                        "4.8",
                        "coins",
                        "buy-token",
                        "snapshot-v1"
                    ),
                    new MarketQuoteResult(
                        MarketQuoteSide.SELL,
                        4,
                        "16.4",
                        "4.1",
                        "coins",
                        "sell-token",
                        "snapshot-v1"
                    )
                )
            )
            .withExecutionPending(MarketQuoteSide.SELL);
        registry.put(playerId, executingSession);

        Method method = MarketGuiService.class.getDeclaredMethod(
            "applySellSuccess",
            UUID.class,
            Material.class,
            MarketSession.class,
            MarketExecuteResult.class
        );
        method.setAccessible(true);
        method.invoke(
            guiService,
            playerId,
            Material.WHEAT,
            executingSession,
            new MarketExecuteResult(4, "16.4", "4.1", "coins", "snapshot-v2")
        );

        MarketSession updated = registry.get(playerId).orElseThrow();
        assertEquals(0, inventoryAccess.quantity(Material.WHEAT));
        assertEquals(4, updated.quantity());
        assertEquals(MarketQuoteStatus.AVAILABLE, updated.quoteStatus());
        assertEquals("Quotes ready", updated.quoteStatusMessage());
        assertEquals("buy-token-4", updated.buyQuoteToken());
        assertEquals("sell-token-4", updated.sellQuoteToken());
        assertEquals(2, quoteCalls.get());
        assertFalse(updated.executingBuy());
        assertFalse(updated.executingSell());
    }

    @Test
    void offlineSellSuccessQueuesDeferredSettlementAndReturnsSessionToReadyTrading()
        throws Exception {
        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        FakeInventoryAccess inventoryAccess = new FakeInventoryAccess();
        inventoryAccess.setQuantity(Material.WHEAT, 4);
        MarketBrowseSnapshotService snapshotService =
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor());
        snapshotService.loadForInitialOpen().get();
        Player offlinePlayer = fakeOfflinePlayer(playerId);
        AtomicInteger quoteCalls = new AtomicInteger();
        MarketGuiService guiService = new MarketGuiService(
            fakePlugin(offlinePlayer),
            snapshotService,
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                quoteCalls.incrementAndGet();
                return quote(side, quantity, snapshotVersion);
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            inventoryAccess,
            registry,
            new YamlConfiguration()
        );
        MarketSession executingSession = MarketSession.tradeView(
            "farming",
            "wheat",
            false
        )
            .withQuantity(4)
            .withQuotePair(
                new MarketQuotePair(
                    new MarketQuoteResult(
                        MarketQuoteSide.BUY,
                        4,
                        "19.2",
                        "4.8",
                        "coins",
                        "buy-token",
                        "snapshot-v1"
                    ),
                    new MarketQuoteResult(
                        MarketQuoteSide.SELL,
                        4,
                        "16.4",
                        "4.1",
                        "coins",
                        "sell-token",
                        "snapshot-v1"
                    )
                )
            )
            .withExecutionPending(MarketQuoteSide.SELL);
        registry.put(playerId, executingSession);

        Method method = MarketGuiService.class.getDeclaredMethod(
            "applySellSuccess",
            UUID.class,
            Material.class,
            MarketSession.class,
            MarketExecuteResult.class
        );
        method.setAccessible(true);
        method.invoke(
            guiService,
            playerId,
            Material.WHEAT,
            executingSession,
            new MarketExecuteResult(4, "16.4", "4.1", "coins", "snapshot-v2")
        );

        MarketSession updated = registry.get(playerId).orElseThrow();
        assertEquals(4, inventoryAccess.quantity(Material.WHEAT));
        assertEquals(1, guiService.deferredSettlementCount(playerId));
        assertEquals(4, updated.quantity());
        assertEquals(MarketQuoteStatus.AVAILABLE, updated.quoteStatus());
        assertEquals("Quotes ready", updated.quoteStatusMessage());
        assertEquals("buy-token-4", updated.buyQuoteToken());
        assertEquals("sell-token-4", updated.sellQuoteToken());
        assertEquals(2, quoteCalls.get());
        assertFalse(updated.executingBuy());
        assertFalse(updated.executingSell());
    }

    @Test
    void onlineSellSuccessWithPartialLocalRemovalLeavesTradeUnavailable()
        throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set(
            "messages.sell-removal-failed",
            "&cSell completed for {total}, but local removal only removed {removed}/{executed} {item}. Missing: {missing}. Contact staff."
        );
        config.set(
            "messages.sell-removal-failed-log",
            "Market sell compensation issue for player {playerId}: item={item}, executed={executed}, removed={removed}, missing={missing}, settled={total}, snapshotVersion={snapshotVersion}"
        );

        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        FakeInventoryAccess inventoryAccess = new FakeInventoryAccess();
        inventoryAccess.setQuantity(Material.WHEAT, 2);
        MarketBrowseSnapshotService snapshotService =
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor());
        snapshotService.loadForInitialOpen().get();
        List<String> playerMessages = new ArrayList<>();
        Player onlinePlayer = fakeOnlinePlayer(playerId, playerMessages);
        TestLogger testLogger = new TestLogger();
        AtomicInteger quoteCalls = new AtomicInteger();
        MarketGuiService guiService = new MarketGuiService(
            fakePlugin(onlinePlayer, testLogger.logger()),
            snapshotService,
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                quoteCalls.incrementAndGet();
                return quote(side, quantity, snapshotVersion);
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            inventoryAccess,
            registry,
            config
        );
        MarketSession executingSession = MarketSession.tradeView(
            "farming",
            "wheat",
            false
        )
            .withQuantity(4)
            .withQuotePair(
                new MarketQuotePair(
                    new MarketQuoteResult(
                        MarketQuoteSide.BUY,
                        4,
                        "19.2",
                        "4.8",
                        "coins",
                        "buy-token",
                        "snapshot-v1"
                    ),
                    new MarketQuoteResult(
                        MarketQuoteSide.SELL,
                        4,
                        "16.4",
                        "4.1",
                        "coins",
                        "sell-token",
                        "snapshot-v1"
                    )
                )
            )
            .withExecutionPending(MarketQuoteSide.SELL);
        registry.put(playerId, executingSession);

        Method method = MarketGuiService.class.getDeclaredMethod(
            "applySellSuccess",
            UUID.class,
            Material.class,
            MarketSession.class,
            MarketExecuteResult.class
        );
        method.setAccessible(true);
        method.invoke(
            guiService,
            playerId,
            Material.WHEAT,
            executingSession,
            new MarketExecuteResult(4, "16.4", "4.1", "coins", "snapshot-v2")
        );

        MarketSession updated = registry.get(playerId).orElseThrow();
        assertEquals(0, inventoryAccess.quantity(Material.WHEAT));
        assertEquals(4, updated.quantity());
        assertEquals(MarketQuoteStatus.UNAVAILABLE, updated.quoteStatus());
        assertEquals(
            "&cSell settlement is incomplete. Trading stays disabled until it is resolved.",
            updated.quoteStatusMessage()
        );
        assertEquals(null, updated.buyQuoteToken());
        assertEquals(null, updated.sellQuoteToken());
        assertEquals(0, quoteCalls.get());
        assertFalse(updated.executingBuy());
        assertFalse(updated.executingSell());
        assertTrue(
            playerMessages
                .stream()
                .anyMatch(message ->
                    message.contains("local removal only removed 2/4 WHEAT")
                )
        );
        assertTrue(
            testLogger
                .messages(Level.SEVERE)
                .stream()
                .anyMatch(message -> message.contains("removed=2, missing=2"))
        );
    }

    @Test
    void deferredSettlementQueuesWarningLog() {
        UUID playerId = UUID.randomUUID();
        TestLogger testLogger = new TestLogger();
        MarketGuiService guiService = new MarketGuiService(
            fakePlugin(fakeOfflinePlayer(playerId), testLogger.logger()),
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                throw new AssertionError();
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            new FakeInventoryAccess(),
            new MarketSessionRegistry(),
            new YamlConfiguration()
        );

        guiService.queueDeferredSettlement(
            playerId,
            new MarketGuiService.DeferredSettlement(
                MarketQuoteSide.SELL,
                Material.WHEAT,
                new MarketExecuteResult(
                    3,
                    "12.3",
                    "4.1",
                    "coins",
                    "snapshot-v7"
                )
            )
        );

        assertTrue(
            testLogger
                .messages(Level.WARNING)
                .stream()
                .anyMatch(message ->
                    message.contains(
                        "Deferred market sell settlement for player " + playerId
                    )
                )
        );
    }

    private MarketGuiService guiService(YamlConfiguration config) {
        return guiService(config, new MarketInventoryService());
    }

    private MarketGuiService guiService(
        YamlConfiguration config,
        MarketInventoryAccess inventoryAccess
    ) {
        configWithSettlementMessages(config);

        return new MarketGuiService(
            null,
            new MarketBrowseSnapshotService(sampleProvider(), directExecutor()),
            (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> {
                throw new AssertionError();
            },
            (
                ignoredPlayerId,
                itemId,
                side,
                quantity,
                quoteToken,
                snapshotVersion
            ) -> {
                throw new AssertionError();
            },
            inventoryAccess,
            new MarketSessionRegistry(),
            config
        );
    }

    private YamlConfiguration configWithSettlementMessages() {
        return configWithSettlementMessages(new YamlConfiguration());
    }

    private YamlConfiguration configWithSettlementMessages(
        YamlConfiguration config
    ) {
        config.set(
            "messages.rejections.ITEM_BLOCKED",
            "&cThis item is currently blocked."
        );
        config.set(
            "messages.rejections.ITEM_NOT_OPERATING",
            "&cThis item is not operating right now."
        );
        config.set(
            "messages.rejections.UNKNOWN_ITEM",
            "&cThat item is no longer available."
        );
        config.set(
            "messages.sell-removal-failed",
            "&cSell completed for {total}, but local removal only removed {removed}/{executed} {item}. Missing: {missing}. Contact staff."
        );
        config.set(
            "messages.sell-removal-failed-log",
            "Market sell compensation issue for player {playerId}: item={item}, executed={executed}, removed={removed}, missing={missing}, settled={total}, snapshotVersion={snapshotVersion}"
        );
        return config;
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
        return new MarketBrowseSnapshot(
            "snapshot-v5",
            List.of(
                new MarketCategorySnapshot(
                    "farming",
                    "Farming",
                    Material.WHEAT,
                    List.of(),
                    List.of()
                )
            ),
            readOnly
        );
    }

    private MarketBrowseSnapshot snapshot(
        boolean readOnly,
        String snapshotVersion,
        String stockDisplay
    ) {
        return new MarketBrowseSnapshot(
            snapshotVersion,
            List.of(
                new MarketCategorySnapshot(
                    "farming",
                    "Farming",
                    Material.WHEAT,
                    List.of(),
                    List.of(
                        new MarketItemSnapshot(
                            "wheat",
                            "Wheat",
                            Material.WHEAT,
                            List.of(),
                            "4.8 coins",
                            "4.1 coins",
                            "2.3%",
                            stockDisplay,
                            "Fixture"
                        )
                    )
                )
            ),
            readOnly
        );
    }

    private Executor directExecutor() {
        return Runnable::run;
    }

    private MarketQuoteResult quote(
        MarketQuoteSide side,
        int quantity,
        String snapshotVersion
    ) {
        String prefix = side == MarketQuoteSide.BUY ? "buy" : "sell";
        return new MarketQuoteResult(
            side,
            quantity,
            Integer.toString(quantity * (side == MarketQuoteSide.BUY ? 5 : 4)),
            side == MarketQuoteSide.BUY ? "5" : "4",
            "coins",
            prefix + "-token-" + quantity,
            snapshotVersion
        );
    }

    private Player fakePlayer(UUID playerId) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class[] { Player.class },
            (proxy, method, args) ->
                switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "sendMessage" -> null;
                    default -> primitiveDefault(method.getReturnType());
                }
        );
    }

    private Player fakeOfflinePlayer(UUID playerId) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class[] { Player.class },
            (proxy, method, args) ->
                switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "isOnline" -> false;
                    case "sendMessage" -> null;
                    default -> primitiveDefault(method.getReturnType());
                }
        );
    }

    private Player fakeOnlinePlayer(UUID playerId) {
        return fakeOnlinePlayer(playerId, null);
    }

    private Player fakeOnlinePlayer(UUID playerId, List<String> messages) {
        Inventory topInventory = (Inventory) Proxy.newProxyInstance(
            Inventory.class.getClassLoader(),
            new Class[] { Inventory.class },
            (proxy, method, args) ->
                switch (method.getName()) {
                    case "getHolder" -> null;
                    default -> primitiveDefault(method.getReturnType());
                }
        );
        InventoryView openInventory = (InventoryView) Proxy.newProxyInstance(
            InventoryView.class.getClassLoader(),
            new Class[] { InventoryView.class },
            (proxy, method, args) ->
                switch (method.getName()) {
                    case "getTopInventory" -> topInventory;
                    default -> primitiveDefault(method.getReturnType());
                }
        );
        LegacyComponentSerializer serializer =
            LegacyComponentSerializer.legacySection();

        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class[] { Player.class },
            (proxy, method, args) ->
                switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "isOnline" -> true;
                    case "getOpenInventory" -> openInventory;
                    case "sendMessage" -> {
                        if (
                            messages != null && args != null && args.length > 0
                        ) {
                            Object message = args[0];
                            if (message instanceof Component component) {
                                messages.add(serializer.serialize(component));
                            } else {
                                messages.add(String.valueOf(message));
                            }
                        }
                        yield null;
                    }
                    default -> primitiveDefault(method.getReturnType());
                }
        );
    }

    private Plugin fakePlugin(Player player) {
        return fakePlugin(player, Logger.getLogger("MarketGuiServiceTest"));
    }

    private Plugin fakePlugin(Player player, Logger logger) {
        return fakePlugin(player, logger, null);
    }

    private Plugin fakePlugin(
        Player player,
        Logger logger,
        java.io.File dataFolder
    ) {
        BukkitScheduler scheduler = (BukkitScheduler) Proxy.newProxyInstance(
            BukkitScheduler.class.getClassLoader(),
            new Class[] { BukkitScheduler.class },
            (proxy, method, args) -> {
                if ("runTask".equals(method.getName())) {
                    ((Runnable) args[1]).run();
                    return null;
                }
                if ("runTaskAsynchronously".equals(method.getName())) {
                    ((Runnable) args[1]).run();
                    return null;
                }
                if ("runTaskLater".equals(method.getName())) {
                    ((Runnable) args[1]).run();
                    return fakeTask();
                }
                return primitiveDefault(method.getReturnType());
            }
        );
        Server server = (Server) Proxy.newProxyInstance(
            Server.class.getClassLoader(),
            new Class[] { Server.class },
            (proxy, method, args) ->
                switch (method.getName()) {
                    case "getPlayer" -> player;
                    case "getScheduler" -> scheduler;
                    default -> primitiveDefault(method.getReturnType());
                }
        );

        return (Plugin) Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class[] { Plugin.class },
            (proxy, method, args) ->
                switch (method.getName()) {
                    case "getServer" -> server;
                    case "getLogger" -> logger;
                    case "getDataFolder" -> dataFolder;
                    default -> primitiveDefault(method.getReturnType());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> internalInventoryTransitions(MarketGuiService guiService)
        throws Exception {
        Field field = MarketGuiService.class.getDeclaredField(
            "internalInventoryTransitions"
        );
        field.setAccessible(true);
        return (Set<UUID>) field.get(guiService);
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

    private BukkitTask fakeTask() {
        return (BukkitTask) Proxy.newProxyInstance(
            BukkitTask.class.getClassLoader(),
            new Class[] { BukkitTask.class },
            (proxy, method, args) -> primitiveDefault(method.getReturnType())
        );
    }

    private Object recordValue(Object record, String methodName)
        throws Exception {
        Method method = record.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(record);
    }

    private record ExpectedQuantityControl(
        int delta,
        Material material,
        int itemAmount,
        boolean enchanted
    ) {}

    private static final class FakeInventoryAccess
        implements MarketInventoryAccess
    {

        private final java.util.Map<Material, Integer> quantities =
            new java.util.EnumMap<>(Material.class);

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

    private static final class TestLogger {

        private final Logger logger = Logger.getAnonymousLogger();
        private final List<LogRecord> records = new ArrayList<>();

        private TestLogger() {
            logger.setUseParentHandlers(false);
            for (Handler handler : logger.getHandlers()) {
                logger.removeHandler(handler);
            }
            logger.addHandler(
                new Handler() {
                    @Override
                    public void publish(LogRecord record) {
                        records.add(record);
                    }

                    @Override
                    public void flush() {}

                    @Override
                    public void close() {}
                }
            );
        }

        private Logger logger() {
            return logger;
        }

        private List<String> messages(Level level) {
            return records
                .stream()
                .filter(record -> record.getLevel().equals(level))
                .map(LogRecord::getMessage)
                .toList();
        }
    }
}
