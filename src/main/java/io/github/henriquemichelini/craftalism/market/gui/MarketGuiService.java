package io.github.henriquemichelini.craftalism.market.gui;

import io.github.henriquemichelini.craftalism.market.api.MarketExecuteClient;
import io.github.henriquemichelini.craftalism.market.api.MarketExecuteRejectedException;
import io.github.henriquemichelini.craftalism.market.api.MarketExecuteResult;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteClient;
import io.github.henriquemichelini.craftalism.market.api.MarketQuotePair;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteResult;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteSide;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotLoadResult;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotService;
import io.github.henriquemichelini.craftalism.market.browse.MarketCategorySnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketItemSnapshot;
import io.github.henriquemichelini.craftalism.market.inventory.MarketInventoryService;
import io.github.henriquemichelini.craftalism.market.session.MarketQuoteStatus;
import io.github.henriquemichelini.craftalism.market.session.MarketScreen;
import io.github.henriquemichelini.craftalism.market.session.MarketSession;
import io.github.henriquemichelini.craftalism.market.session.MarketSessionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MarketGuiService {
    private static final int QUANTITY_DECREMENT_SLOT = 10;
    private static final int QUANTITY_DISPLAY_SLOT = 12;
    private static final int TRADE_BUY_SLOT = 11;
    private static final int TRADE_ITEM_SLOT = 13;
    private static final int TRADE_SELL_SLOT = 15;
    private static final int QUANTITY_INCREMENT_SLOT = 16;

    private final Plugin plugin;
    private final MarketBrowseSnapshotService snapshotService;
    private final MarketQuoteClient quoteClient;
    private final MarketExecuteClient executeClient;
    private final MarketInventoryService inventoryService;
    private final MarketSessionRegistry sessionRegistry;
    private final FileConfiguration config;
    private final Map<UUID, BukkitTask> pendingQuoteTasks = new ConcurrentHashMap<>();

    public MarketGuiService(
            Plugin plugin,
            MarketBrowseSnapshotService snapshotService,
            MarketQuoteClient quoteClient,
            MarketExecuteClient executeClient,
            MarketInventoryService inventoryService,
            MarketSessionRegistry sessionRegistry,
            FileConfiguration config
    ) {
        this.plugin = plugin;
        this.snapshotService = snapshotService;
        this.quoteClient = quoteClient;
        this.executeClient = executeClient;
        this.inventoryService = inventoryService;
        this.sessionRegistry = sessionRegistry;
        this.config = config;
    }

    public void openMainMenu(Player player, MarketBrowseSnapshot snapshot) {
        if (snapshot.categories().isEmpty()) {
            player.sendMessage(colorize(message("messages.unavailable-no-cache")));
            return;
        }

        CategoryListHolder holder = new CategoryListHolder();
        Inventory inventory = createInventory(holder, rowsFor(snapshot.categories().size()), title("titles.categories"));

        int slot = 0;
        for (MarketCategorySnapshot category : snapshot.categories()) {
            inventory.setItem(slot++, categoryIcon(category, snapshot.readOnly()));
        }

        holder.setInventory(inventory);
        player.openInventory(inventory);
        sessionRegistry.put(player.getUniqueId(), MarketSession.categoryList(snapshot.readOnly()));
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof MarketInventoryHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (holder instanceof CategoryListHolder) {
            handleCategoryListClick(player, rawSlot);
            return;
        }

        if (holder instanceof CategoryItemsHolder categoryItemsHolder) {
            handleCategoryItemsClick(player, categoryItemsHolder.categoryId(), rawSlot);
            return;
        }

        if (holder instanceof TradeViewHolder tradeViewHolder) {
            handleTradeClick(player, tradeViewHolder.categoryId(), tradeViewHolder.itemId(), rawSlot);
        }
    }

    private void handleCategoryListClick(Player player, int rawSlot) {
        MarketBrowseSnapshot snapshot = currentSnapshot(player);
        if (snapshot == null) {
            return;
        }

        if (rawSlot >= snapshot.categories().size()) {
            return;
        }

        openCategory(player, snapshot.categories().get(rawSlot).id());
    }

    private void handleCategoryItemsClick(Player player, String categoryId, int rawSlot) {
        MarketBrowseSnapshot snapshot = currentSnapshot(player);
        if (snapshot == null) {
            return;
        }

        MarketCategorySnapshot category = snapshot.findCategory(categoryId).orElse(null);
        if (category == null) {
            player.closeInventory();
            player.sendMessage(colorize(message("messages.unavailable-no-cache")));
            return;
        }

        if (rawSlot == backSlot(rowsFor(category.items().size() + 1))) {
            openCurrentMainMenu(player);
            return;
        }

        if (rawSlot >= category.items().size()) {
            return;
        }

        openTrade(player, categoryId, category.items().get(rawSlot).id());
    }

    private void handleTradeClick(Player player, String categoryId, String itemId, int rawSlot) {
        if (rawSlot == backSlot(3)) {
            cancelPendingQuote(player.getUniqueId());
            openCategory(player, categoryId);
            return;
        }

        if (rawSlot == QUANTITY_DECREMENT_SLOT) {
            adjustTradeQuantity(player, categoryId, itemId, -1);
            return;
        }

        if (rawSlot == QUANTITY_INCREMENT_SLOT) {
            adjustTradeQuantity(player, categoryId, itemId, 1);
            return;
        }

        if (rawSlot == TRADE_BUY_SLOT) {
            handleBuyClick(player, categoryId, itemId);
            return;
        }

        if (rawSlot == TRADE_SELL_SLOT) {
            handleSellClick(player, categoryId, itemId);
        }
    }

    public void openCategory(Player player, String categoryId) {
        MarketBrowseSnapshot snapshot = currentSnapshot(player);
        if (snapshot == null) {
            return;
        }

        MarketCategorySnapshot category = snapshot.findCategory(categoryId).orElse(null);
        if (category == null) {
            player.sendMessage(colorize(message("messages.unavailable-no-cache")));
            return;
        }

        int rows = rowsFor(category.items().size() + 1);
        CategoryItemsHolder holder = new CategoryItemsHolder(categoryId);
        Inventory inventory = createInventory(holder, rows, colorize(category.title()));

        int slot = 0;
        for (MarketItemSnapshot item : category.items()) {
            inventory.setItem(slot++, itemIcon(item, snapshot.readOnly()));
        }

        inventory.setItem(backSlot(rows), simpleItem(
                Material.ARROW,
                "&eBack",
                List.of("&7Return to the category list.")
        ));

        holder.setInventory(inventory);
        player.openInventory(inventory);
        sessionRegistry.put(player.getUniqueId(), MarketSession.itemList(categoryId, snapshot.readOnly()));
    }

    public void openTrade(Player player, String categoryId, String itemId) {
        openTrade(player, categoryId, itemId, null);
    }

    private void openTrade(Player player, String categoryId, String itemId, MarketSession existingSession) {
        MarketBrowseSnapshot snapshot = currentSnapshot(player);
        if (snapshot == null) {
            return;
        }

        MarketCategorySnapshot category = snapshot.findCategory(categoryId).orElse(null);
        MarketItemSnapshot item = snapshot.findItem(categoryId, itemId).orElse(null);
        if (category == null || item == null) {
            player.sendMessage(colorize(message("messages.unavailable-no-cache")));
            return;
        }

        MarketSession session = existingSession != null
                ? existingSession
                : MarketSession.tradeView(categoryId, itemId, snapshot.readOnly());
        session = sessionForSnapshot(session, snapshot);
        renderTrade(player, snapshot, category, item, session);
        sessionRegistry.put(player.getUniqueId(), session);

        if (!session.readOnly() && session.quoteStatus() == MarketQuoteStatus.PENDING) {
            scheduleQuoteRefresh(player.getUniqueId(), session, snapshot.snapshotVersion());
        }
    }

    private void renderTrade(
            Player player,
            MarketBrowseSnapshot snapshot,
            MarketCategorySnapshot category,
            MarketItemSnapshot item,
            MarketSession session
    ) {
        TradeViewHolder holder = new TradeViewHolder(category.id(), item.id());
        Inventory inventory = createInventory(holder, 3, colorize(item.displayName()));

        inventory.setItem(QUANTITY_DECREMENT_SLOT, quantityButton(Material.RED_STAINED_GLASS_PANE, "&c-1"));
        inventory.setItem(QUANTITY_DISPLAY_SLOT, quantityDisplay(session.quantity(), session.quoteStatusMessage()));
        inventory.setItem(TRADE_BUY_SLOT, quoteActionButton("Buy", session.buyQuotedTotal(), session.quoteStatus(), snapshot.readOnly()));
        inventory.setItem(TRADE_ITEM_SLOT, tradePreview(item, session, snapshot.readOnly()));
        inventory.setItem(TRADE_SELL_SLOT, quoteActionButton("Sell", session.sellQuotedTotal(), session.quoteStatus(), snapshot.readOnly()));
        inventory.setItem(QUANTITY_INCREMENT_SLOT, quantityButton(Material.LIME_STAINED_GLASS_PANE, "&a+1"));
        inventory.setItem(backSlot(3), simpleItem(
                Material.ARROW,
                "&eBack",
                List.of("&7Return to " + category.title() + ".")
        ));

        holder.setInventory(inventory);
        player.openInventory(inventory);
    }

    public void closeSession(UUID playerId) {
        cancelPendingQuote(playerId);
        sessionRegistry.remove(playerId);
    }

    private Inventory createInventory(MarketInventoryHolder holder, int rows, String title) {
        return Bukkit.createInventory(holder, rows * 9, colorize(title));
    }

    private void openCurrentMainMenu(Player player) {
        MarketBrowseSnapshot snapshot = currentSnapshot(player);
        if (snapshot != null) {
            openMainMenu(player, snapshot);
        }
    }

    private MarketBrowseSnapshot currentSnapshot(Player player) {
        MarketBrowseSnapshot current = snapshotService.currentSnapshot().orElse(null);
        if (current == null) {
            player.sendMessage(colorize(message("messages.unavailable-no-cache")));
            return null;
        }

        boolean readOnly = sessionRegistry.get(player.getUniqueId())
                .map(MarketSession::readOnly)
                .orElse(true);
        return current.withReadOnly(readOnly);
    }

    private void adjustTradeQuantity(Player player, String categoryId, String itemId, int delta) {
        MarketSession updatedSession = sessionRegistry.update(player.getUniqueId(), session -> {
            if (session.screen() != MarketScreen.TRADE_VIEW
                    || !categoryId.equals(session.selectedCategoryId())
                    || !itemId.equals(session.selectedItemId())) {
                return session;
            }

            int quantity = Math.max(1, session.quantity() + delta);
            if (quantity == session.quantity()) {
                return session;
            }

            return session.withQuantityPending(quantity);
        }).orElse(null);

        if (updatedSession == null || updatedSession.screen() != MarketScreen.TRADE_VIEW) {
            return;
        }

        refreshTrade(player, categoryId, itemId);
    }

    private void refreshTrade(Player player, String categoryId, String itemId) {
        openTrade(player, categoryId, itemId, sessionRegistry.get(player.getUniqueId()).orElse(null));
    }

    private void handleBuyClick(Player player, String categoryId, String itemId) {
        MarketBrowseSnapshot snapshot = currentSnapshot(player);
        if (snapshot == null) {
            return;
        }

        MarketItemSnapshot item = snapshot.findItem(categoryId, itemId).orElse(null);
        if (item == null) {
            player.sendMessage(colorize(message("messages.unavailable-no-cache")));
            return;
        }

        MarketSession executingSession = sessionRegistry.update(player.getUniqueId(), current -> {
            if (current.screen() != MarketScreen.TRADE_VIEW
                    || current.readOnly()
                    || current.executingBuy()
                    || current.executingSell()
                    || current.quoteStatus() != MarketQuoteStatus.AVAILABLE
                    || current.buyQuoteToken() == null
                    || current.buyQuoteSnapshotVersion() == null
                    || !categoryId.equals(current.selectedCategoryId())
                    || !itemId.equals(current.selectedItemId())) {
                return current;
            }

                return current.withExecutionPending(MarketQuoteSide.BUY);
        }).orElse(null);

        if (executingSession == null
                || executingSession.screen() != MarketScreen.TRADE_VIEW
                || !executingSession.executingBuy()) {
            player.sendMessage(colorize(message("messages.trade-disabled")));
            refreshTrade(player, categoryId, itemId);
            return;
        }

        refreshTrade(player, categoryId, itemId);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarketExecuteResult result = executeClient.executeTrade(
                        executingSession.selectedItemId(),
                        MarketQuoteSide.BUY,
                        executingSession.quantity(),
                        executingSession.buyQuoteToken(),
                        executingSession.buyQuoteSnapshotVersion()
                );
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        applyBuySuccess(player.getUniqueId(), item.icon(), executingSession, result)
                );
            } catch (MarketExecuteRejectedException rejection) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        applyBuyRejection(player.getUniqueId(), executingSession, rejection)
                );
            } catch (RuntimeException error) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        applyBuyFailure(player.getUniqueId(), executingSession, error)
                );
            }
        });
    }

    private void handleSellClick(Player player, String categoryId, String itemId) {
        MarketBrowseSnapshot snapshot = currentSnapshot(player);
        if (snapshot == null) {
            return;
        }

        MarketItemSnapshot item = snapshot.findItem(categoryId, itemId).orElse(null);
        if (item == null) {
            player.sendMessage(colorize(message("messages.unavailable-no-cache")));
            return;
        }

        MarketSession currentSession = sessionRegistry.get(player.getUniqueId()).orElse(null);
        if (currentSession == null
                || currentSession.screen() != MarketScreen.TRADE_VIEW
                || currentSession.readOnly()
                || currentSession.executingBuy()
                || currentSession.executingSell()
                || currentSession.quoteStatus() != MarketQuoteStatus.AVAILABLE
                || currentSession.sellQuoteToken() == null
                || currentSession.sellQuoteSnapshotVersion() == null
                || !categoryId.equals(currentSession.selectedCategoryId())
                || !itemId.equals(currentSession.selectedItemId())) {
            player.sendMessage(colorize(message("messages.trade-disabled")));
            refreshTrade(player, categoryId, itemId);
            return;
        }

        int heldQuantity = inventoryService.count(player, item.icon());
        if (heldQuantity <= 0) {
            player.sendMessage(colorize(message("messages.sell-no-items")));
            refreshTrade(player, categoryId, itemId);
            return;
        }

        if (heldQuantity < currentSession.quantity()) {
            MarketSession updated = sessionRegistry.update(player.getUniqueId(), session -> {
                if (!sameTradeSession(session, currentSession)) {
                    return session;
                }

                return session.withQuantityPending(heldQuantity);
            }).orElse(null);

            player.sendMessage(colorize(message("messages.sell-quantity-adjusted")
                    .replace("{quantity}", Integer.toString(heldQuantity))));
            if (updated != null) {
                rerenderTradeIfVisible(player.getUniqueId(), updated);
            }
            return;
        }

        MarketSession executingSession = sessionRegistry.update(player.getUniqueId(), session -> {
            if (!sameTradeSession(session, currentSession)
                    || session.executingBuy()
                    || session.executingSell()) {
                return session;
            }

            return session.withExecutionPending(MarketQuoteSide.SELL);
        }).orElse(null);

        if (executingSession == null || !executingSession.executingSell()) {
            player.sendMessage(colorize(message("messages.trade-disabled")));
            refreshTrade(player, categoryId, itemId);
            return;
        }

        refreshTrade(player, categoryId, itemId);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarketExecuteResult result = executeClient.executeTrade(
                        executingSession.selectedItemId(),
                        MarketQuoteSide.SELL,
                        executingSession.quantity(),
                        executingSession.sellQuoteToken(),
                        executingSession.sellQuoteSnapshotVersion()
                );
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        applySellSuccess(player.getUniqueId(), item.icon(), executingSession, result)
                );
            } catch (MarketExecuteRejectedException rejection) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        applySellRejection(player.getUniqueId(), executingSession, rejection)
                );
            } catch (RuntimeException error) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        applySellFailure(player.getUniqueId(), executingSession, error)
                );
            }
        });
    }

    private void scheduleQuoteRefresh(UUID playerId, MarketSession session, String snapshotVersion) {
        cancelPendingQuote(playerId);
        long debounceTicks = Math.max(1L, config.getLong("market-api.quote-debounce-ticks", 6L));
        BukkitTask task = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                MarketQuoteResult buyQuote = quoteClient.requestQuote(
                        session.selectedItemId(),
                        MarketQuoteSide.BUY,
                        session.quantity(),
                        snapshotVersion
                );
                MarketQuoteResult sellQuote = quoteClient.requestQuote(
                        session.selectedItemId(),
                        MarketQuoteSide.SELL,
                        session.quantity(),
                        snapshotVersion
                );
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        applyQuoteResult(playerId, session, new MarketQuotePair(buyQuote, sellQuote))
                );
            } catch (RuntimeException error) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        applyQuoteFailure(playerId, session, quoteFailureMessage(error))
                );
            }
        }, debounceTicks);
        pendingQuoteTasks.put(playerId, task);
    }

    private void applyQuoteResult(UUID playerId, MarketSession expectedSession, MarketQuotePair quotePair) {
        pendingQuoteTasks.remove(playerId);
        MarketSession updated = sessionRegistry.update(playerId, current -> {
            if (!current.matchesTradeRequest(
                    expectedSession.selectedCategoryId(),
                    expectedSession.selectedItemId(),
                    expectedSession.quantity(),
                    expectedSession.quoteRequestVersion()
            )) {
                return current;
            }

            return current.withQuotePair(quotePair);
        }).orElse(null);

        if (updated != null) {
            rerenderTradeIfVisible(playerId, updated);
        }
    }

    private void applyQuoteFailure(UUID playerId, MarketSession expectedSession, String failureMessage) {
        pendingQuoteTasks.remove(playerId);
        MarketSession updated = sessionRegistry.update(playerId, current -> {
            if (!current.matchesTradeRequest(
                    expectedSession.selectedCategoryId(),
                    expectedSession.selectedItemId(),
                    expectedSession.quantity(),
                    expectedSession.quoteRequestVersion()
            )) {
                return current;
            }

            return current.withQuoteUnavailable(failureMessage);
        }).orElse(null);

        if (updated != null) {
            rerenderTradeIfVisible(playerId, updated);
        }
    }

    private void rerenderTradeIfVisible(UUID playerId, MarketSession session) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline() || session.screen() != MarketScreen.TRADE_VIEW) {
            return;
        }

        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof TradeViewHolder)) {
            return;
        }

        openTrade(player, session.selectedCategoryId(), session.selectedItemId(), session);
    }

    private void applyBuySuccess(UUID playerId, Material material, MarketSession expectedSession, MarketExecuteResult result) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            int delivered = inventoryService.addOrDrop(player, material, result.executedQuantity());
            if (delivered < result.executedQuantity()) {
                player.sendMessage(colorize(message("messages.buy-overflow")
                        .replace("{quantity}", Integer.toString(result.executedQuantity()))
                        .replace("{total}", result.totalPrice() + " " + result.currency())
                        .replace("{dropped}", Integer.toString(result.executedQuantity() - delivered))));
            } else {
                player.sendMessage(colorize(message("messages.buy-success")
                        .replace("{quantity}", Integer.toString(result.executedQuantity()))
                        .replace("{total}", result.totalPrice() + " " + result.currency())));
            }
        }

        MarketSession updated = sessionRegistry.update(playerId, current -> {
            if (!sameTradeSession(current, expectedSession)) {
                return current;
            }

            return current.withQuantityPending(current.quantity());
        }).orElse(null);

        if (updated != null) {
            rerenderTradeIfVisible(playerId, updated);
            refreshTradeSnapshot(playerId, updated);
        }
    }

    private void applySellSuccess(UUID playerId, Material material, MarketSession expectedSession, MarketExecuteResult result) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        int removed = inventoryService.remove(player, material, result.executedQuantity());
        if (removed != result.executedQuantity()) {
            plugin.getLogger().severe(sellRemovalFailureLogMessage(playerId, material, result, removed));
            player.sendMessage(colorize(sellRemovalFailurePlayerMessage(material, result, removed)));
        } else {
            player.sendMessage(colorize(message("messages.sell-success")
                    .replace("{quantity}", Integer.toString(result.executedQuantity()))
                    .replace("{total}", result.totalPrice() + " " + result.currency())));
        }

        MarketSession updated = sessionRegistry.update(playerId, current -> {
            if (!sameTradeSession(current, expectedSession)) {
                return current;
            }

            return current.withQuantityPending(current.quantity());
        }).orElse(null);

        if (updated != null) {
            rerenderTradeIfVisible(playerId, updated);
            refreshTradeSnapshot(playerId, updated);
        }
    }

    private void applyBuyRejection(UUID playerId, MarketSession expectedSession, MarketExecuteRejectedException rejection) {
        String code = rejection.rejectionCode();
        if ("STALE_QUOTE".equals(code) || "QUOTE_EXPIRED".equals(code)) {
            MarketSession updated = sessionRegistry.update(playerId, current -> {
                if (!sameTradeSession(current, expectedSession)) {
                    return current;
                }

                return current.withQuantityPending(current.quantity());
            }).orElse(null);

            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(colorize(rejectionMessage(code)));
            }

            if (updated != null) {
                rerenderTradeIfVisible(playerId, updated);
                refreshTradeSnapshot(playerId, updated);
            }
            return;
        }

        MarketSession updated = sessionRegistry.update(playerId, current -> {
            if (!sameTradeSession(current, expectedSession)) {
                return current;
            }

            return current.withQuoteMessage(MarketQuoteStatus.AVAILABLE, rejectionMessage(code));
        }).orElse(null);

        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(colorize(rejectionMessage(code)));
        }

        if (updated != null) {
            rerenderTradeIfVisible(playerId, updated);
        }
    }

    private void applyBuyFailure(UUID playerId, MarketSession expectedSession, RuntimeException error) {
        MarketSession updated = sessionRegistry.update(playerId, current -> {
            if (!sameTradeSession(current, expectedSession)) {
                return current;
            }

            return current.withQuoteMessage(MarketQuoteStatus.AVAILABLE, message("messages.execute-unavailable"));
        }).orElse(null);

        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(colorize(message("messages.execute-unavailable")));
        }

        if (updated != null) {
            rerenderTradeIfVisible(playerId, updated);
        }
    }

    private void applySellRejection(UUID playerId, MarketSession expectedSession, MarketExecuteRejectedException rejection) {
        String code = rejection.rejectionCode();
        if ("STALE_QUOTE".equals(code) || "QUOTE_EXPIRED".equals(code)) {
            MarketSession updated = sessionRegistry.update(playerId, current -> {
                if (!sameTradeSession(current, expectedSession)) {
                    return current;
                }

                return current.withQuantityPending(current.quantity());
            }).orElse(null);

            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(colorize(rejectionMessage(code)));
            }

            if (updated != null) {
                rerenderTradeIfVisible(playerId, updated);
                refreshTradeSnapshot(playerId, updated);
            }
            return;
        }

        MarketSession updated = sessionRegistry.update(playerId, current -> {
            if (!sameTradeSession(current, expectedSession)) {
                return current;
            }

            return current.withQuoteMessage(MarketQuoteStatus.AVAILABLE, rejectionMessage(code));
        }).orElse(null);

        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(colorize(rejectionMessage(code)));
        }

        if (updated != null) {
            rerenderTradeIfVisible(playerId, updated);
        }
    }

    private void applySellFailure(UUID playerId, MarketSession expectedSession, RuntimeException error) {
        MarketSession updated = sessionRegistry.update(playerId, current -> {
            if (!sameTradeSession(current, expectedSession)) {
                return current;
            }

            return current.withQuoteMessage(MarketQuoteStatus.AVAILABLE, message("messages.execute-unavailable"));
        }).orElse(null);

        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(colorize(message("messages.execute-unavailable")));
        }

        if (updated != null) {
            rerenderTradeIfVisible(playerId, updated);
        }
    }

    private void refreshTradeSnapshot(UUID playerId, MarketSession expectedSession) {
        snapshotService.refreshSnapshot().whenComplete((result, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        applyTradeSnapshotRefresh(playerId, expectedSession, result, error))
        );
    }

    private void applyTradeSnapshotRefresh(
            UUID playerId,
            MarketSession expectedSession,
            MarketBrowseSnapshotLoadResult result,
            Throwable error
    ) {
        MarketSession updated = sessionRegistry.update(playerId, current -> {
            if (!sameTradeSession(current, expectedSession)) {
                return current;
            }

            if (error != null) {
                return current.withQuoteUnavailable(message("messages.quote-unavailable"));
            }

            return sessionForSnapshot(current, result.snapshot());
        }).orElse(null);

        if (updated != null) {
            rerenderTradeIfVisible(playerId, updated);
        }
    }

    private boolean sameTradeSession(MarketSession current, MarketSession expectedSession) {
        return current.screen() == MarketScreen.TRADE_VIEW
                && current.selectedCategoryId() != null
                && current.selectedCategoryId().equals(expectedSession.selectedCategoryId())
                && current.selectedItemId() != null
                && current.selectedItemId().equals(expectedSession.selectedItemId())
                && current.quantity() == expectedSession.quantity();
    }

    MarketSession sessionForSnapshot(MarketSession session, MarketBrowseSnapshot snapshot) {
        if (session.screen() != MarketScreen.TRADE_VIEW) {
            return session;
        }

        if (snapshot.readOnly()) {
            return session.asReadOnlyPreview();
        }

        MarketItemSnapshot item = snapshot.findItem(session.selectedCategoryId(), session.selectedItemId()).orElse(null);
        if (item == null) {
            return session.withQuoteUnavailable(rejectionMessage("UNKNOWN_ITEM"));
        }

        if ("Blocked".equals(item.stockDisplay())) {
            return session.withQuoteUnavailable(rejectionMessage("ITEM_BLOCKED"));
        }

        if ("Unavailable".equals(item.stockDisplay())) {
            return session.withQuoteUnavailable(rejectionMessage("ITEM_NOT_OPERATING"));
        }

        if (session.readOnly() || session.quoteStatus() == MarketQuoteStatus.DISABLED) {
            return session.asLiveQuotePending();
        }

        return session;
    }

    String sellRemovalFailureLogMessage(UUID playerId, Material material, MarketExecuteResult result, int removedQuantity) {
        return message("messages.sell-removal-failed-log")
                .replace("{playerId}", playerId.toString())
                .replace("{item}", material.name())
                .replace("{executed}", Integer.toString(result.executedQuantity()))
                .replace("{removed}", Integer.toString(removedQuantity))
                .replace("{missing}", Integer.toString(Math.max(0, result.executedQuantity() - removedQuantity)))
                .replace("{total}", result.totalPrice() + " " + result.currency())
                .replace("{snapshotVersion}", result.snapshotVersion());
    }

    String sellRemovalFailurePlayerMessage(Material material, MarketExecuteResult result, int removedQuantity) {
        return message("messages.sell-removal-failed")
                .replace("{item}", material.name())
                .replace("{executed}", Integer.toString(result.executedQuantity()))
                .replace("{removed}", Integer.toString(removedQuantity))
                .replace("{missing}", Integer.toString(Math.max(0, result.executedQuantity() - removedQuantity)))
                .replace("{total}", result.totalPrice() + " " + result.currency());
    }

    private void cancelPendingQuote(UUID playerId) {
        BukkitTask task = pendingQuoteTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private int rowsFor(int itemCount) {
        int itemRows = Math.max(1, (int) Math.ceil(itemCount / 9.0d));
        return Math.min(6, Math.max(3, itemRows + 1));
    }

    private int backSlot(int rows) {
        return rows * 9 - 5;
    }

    private ItemStack categoryIcon(MarketCategorySnapshot category, boolean readOnly) {
        List<String> lore = new ArrayList<>(colorize(category.description()));
        lore.add("");
        lore.add(colorize(readOnly ? "&6Read-only browsing" : "&aLive market data"));
        lore.add(colorize("&7Click to browse items."));
        return simpleItem(category.icon(), category.title(), lore);
    }

    private ItemStack itemIcon(MarketItemSnapshot item, boolean readOnly) {
        List<String> lore = new ArrayList<>(colorize(item.description()));
        lore.add("");
        lore.add(colorize("&7Buy estimate: &f" + item.buyEstimate()));
        lore.add(colorize("&7Sell estimate: &f" + item.sellEstimate()));
        lore.add(colorize("&7Variation: &f" + item.variationPercent()));
        lore.add(colorize("&7Stock: &f" + item.stockDisplay()));
        lore.add(colorize("&7Updated: &f" + item.lastUpdated()));
        lore.add("");
        lore.add(colorize(readOnly ? "&6Cached preview only" : "&aClick to trade"));
        return simpleItem(item.icon(), item.displayName(), lore);
    }

    private ItemStack tradePreview(MarketItemSnapshot item, MarketSession session, boolean readOnly) {
        List<String> lore = new ArrayList<>(colorize(item.description()));
        lore.add("");
        lore.add(colorize("&7Buy estimate: &f" + item.buyEstimate()));
        lore.add(colorize("&7Sell estimate: &f" + item.sellEstimate()));
        lore.add(colorize("&7Variation: &f" + item.variationPercent()));
        lore.add(colorize("&7Stock: &f" + item.stockDisplay()));
        lore.add(colorize("&7Updated: &f" + item.lastUpdated()));
        lore.add("");
        lore.add(colorize("&7Quantity: &f" + session.quantity()));
        lore.add(colorize("&7Quote status: &f" + quoteStatusLabel(session.quoteStatus(), session.quoteStatusMessage())));
        if (session.buyQuotedTotal() != null) {
            lore.add(colorize("&7Quoted buy total: &f" + session.buyQuotedTotal()));
        }
        if (session.sellQuotedTotal() != null) {
            lore.add(colorize("&7Quoted sell total: &f" + session.sellQuotedTotal()));
        }
        lore.add("");
        lore.add(colorize(readOnly ? "&6Read-only market preview" : "&aLive market view"));
        lore.add(colorize(readOnly ? "&7Trading stays disabled while using cache." : "&7Quote updates are debounced automatically."));
        return simpleItem(item.icon(), item.displayName(), lore);
    }

    private ItemStack quoteActionButton(String action, String quotedTotal, MarketQuoteStatus quoteStatus, boolean readOnly) {
        List<String> lore = new ArrayList<>();
        lore.add(colorize("&7Click to execute using the latest quote."));
        if (quotedTotal != null) {
            lore.add(colorize("&7Latest quoted total: &f" + quotedTotal));
        }
        lore.add(colorize("&7State: &f" + quoteStatusLabel(quoteStatus, null)));

        Material material = Material.BARRIER;
        String name = "&c" + action + " Unavailable";
        if (!readOnly && quoteStatus == MarketQuoteStatus.PENDING) {
            material = Material.CLOCK;
            name = "&6" + action + " Quote Pending";
        } else if (!readOnly && quoteStatus == MarketQuoteStatus.AVAILABLE) {
            material = "Buy".equals(action) ? Material.EMERALD : Material.REDSTONE;
            name = "&a" + action + " Now";
        }

        return simpleItem(material, name, lore);
    }

    private ItemStack quantityButton(Material material, String name) {
        return simpleItem(material, name, List.of("&7Adjust trade quantity."));
    }

    private ItemStack quantityDisplay(int quantity, String statusMessage) {
        List<String> lore = new ArrayList<>();
        lore.add(colorize("&7Current quantity for quotes."));
        if (statusMessage != null && !statusMessage.isBlank()) {
            lore.add(colorize("&7Status: &f" + statusMessage));
        }
        return simpleItem(Material.PAPER, "&fQuantity: &e" + quantity, lore);
    }

    private String quoteStatusLabel(MarketQuoteStatus status, String fallbackMessage) {
        return switch (status) {
            case DISABLED -> fallbackMessage != null ? fallbackMessage : "Read-only";
            case PENDING -> fallbackMessage != null ? fallbackMessage : "Refreshing";
            case AVAILABLE -> fallbackMessage != null ? fallbackMessage : "Ready";
            case UNAVAILABLE -> fallbackMessage != null ? fallbackMessage : "Unavailable";
        };
    }

    private String quoteFailureMessage(RuntimeException error) {
        return config.getString("messages.quote-unavailable", "Quote unavailable.");
    }

    private String rejectionMessage(String code) {
        return config.getString("messages.rejections." + code, "&cTrade request rejected.");
    }

    private ItemStack simpleItem(Material material, String name, List<String> lore) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }

        itemMeta.setDisplayName(colorize(name));
        itemMeta.setLore(colorize(lore));
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private String title(String path) {
        return config.getString(path, "Market");
    }

    private String message(String path) {
        return config.getString(path, "&cMarket data is unavailable.");
    }

    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private List<String> colorize(List<String> lines) {
        return lines.stream()
                .map(this::colorize)
                .toList();
    }
}
