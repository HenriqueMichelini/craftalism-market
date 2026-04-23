package io.github.henriquemichelini.craftalism.market.gui;

import io.github.henriquemichelini.craftalism.market.api.MarketApiRequestException;
import io.github.henriquemichelini.craftalism.market.api.MarketExecuteClient;
import io.github.henriquemichelini.craftalism.market.api.MarketExecuteRejectedException;
import io.github.henriquemichelini.craftalism.market.api.MarketExecuteResult;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteClient;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteResult;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteSide;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotLoadResult;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotService;
import io.github.henriquemichelini.craftalism.market.browse.MarketCategorySnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketItemSnapshot;
import io.github.henriquemichelini.craftalism.market.inventory.MarketInventoryAccess;
import io.github.henriquemichelini.craftalism.market.session.MarketQuoteStatus;
import io.github.henriquemichelini.craftalism.market.session.MarketScreen;
import io.github.henriquemichelini.craftalism.market.session.MarketSession;
import io.github.henriquemichelini.craftalism.market.session.MarketSessionRegistry;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public final class MarketGuiService {

    private static final int TRADE_BUY_SLOT = 11;
    private static final int TRADE_ITEM_SLOT = 13;
    private static final int TRADE_SELL_SLOT = 15;
    private static final int TRADE_ROWS = 4;
    private static final List<QuantityControl> QUANTITY_CONTROLS = List.of(
        new QuantityControl(
            0,
            -1,
            Material.PINK_STAINED_GLASS_PANE,
            "&c-1",
            1,
            false
        ),
        new QuantityControl(
            1,
            -8,
            Material.PINK_STAINED_GLASS_PANE,
            "&c-8",
            8,
            false
        ),
        new QuantityControl(
            9,
            -32,
            Material.PINK_STAINED_GLASS_PANE,
            "&c-32",
            32,
            false
        ),
        new QuantityControl(
            10,
            -64,
            Material.PINK_STAINED_GLASS_PANE,
            "&c-64",
            64,
            false
        ),
        new QuantityControl(
            18,
            -576,
            Material.RED_STAINED_GLASS_PANE,
            "&4-576",
            1,
            false
        ),
        new QuantityControl(
            19,
            -2304,
            Material.RED_STAINED_GLASS_PANE,
            "&4-2304",
            1,
            true
        ),
        new QuantityControl(
            7,
            1,
            Material.LIME_STAINED_GLASS_PANE,
            "&a+1",
            1,
            false
        ),
        new QuantityControl(
            8,
            8,
            Material.LIME_STAINED_GLASS_PANE,
            "&a+8",
            8,
            false
        ),
        new QuantityControl(
            16,
            32,
            Material.LIME_STAINED_GLASS_PANE,
            "&a+32",
            32,
            false
        ),
        new QuantityControl(
            17,
            64,
            Material.LIME_STAINED_GLASS_PANE,
            "&a+64",
            64,
            false
        ),
        new QuantityControl(
            25,
            576,
            Material.GREEN_STAINED_GLASS_PANE,
            "&2+576",
            1,
            false
        ),
        new QuantityControl(
            26,
            2304,
            Material.GREEN_STAINED_GLASS_PANE,
            "&2+2304",
            1,
            true
        )
    );

    private final Plugin plugin;
    private final MarketBrowseSnapshotService snapshotService;
    private final MarketQuoteClient quoteClient;
    private final MarketExecuteClient executeClient;
    private final MarketInventoryAccess inventoryService;
    private final MarketSessionRegistry sessionRegistry;
    private final MarketGuiRenderer renderer;
    private final MarketSettlementService settlementService;
    private final Set<UUID> internalInventoryTransitions =
        ConcurrentHashMap.newKeySet();

    public MarketGuiService(
        Plugin plugin,
        MarketBrowseSnapshotService snapshotService,
        MarketQuoteClient quoteClient,
        MarketExecuteClient executeClient,
        MarketInventoryAccess inventoryService,
        MarketSessionRegistry sessionRegistry,
        FileConfiguration config
    ) {
        this.plugin = plugin;
        this.snapshotService = snapshotService;
        this.quoteClient = quoteClient;
        this.executeClient = executeClient;
        this.inventoryService = inventoryService;
        this.sessionRegistry = sessionRegistry;
        this.renderer = new MarketGuiRenderer(config);
        this.settlementService =
            new MarketSettlementService(plugin, inventoryService, renderer);
    }

    public void openMainMenu(Player player, MarketBrowseSnapshot snapshot) {
        if (snapshot.categories().isEmpty()) {
            renderer.sendMessage(player, renderer.message("messages.unavailable-no-cache"));
            return;
        }

        CategoryListHolder holder = new CategoryListHolder();
        Inventory inventory = createInventory(
            holder,
            rowsFor(snapshot.categories().size()),
            renderer.title("titles.categories")
        );

        int slot = 0;
        for (MarketCategorySnapshot category : snapshot.categories()) {
            inventory.setItem(
                slot++,
                renderer.categoryIcon(category, snapshot.readOnly())
            );
        }

        holder.setInventory(inventory);
        openMarketInventory(player, inventory);
        replaceSession(
            player.getUniqueId(),
            MarketSession.categoryList(snapshot.readOnly())
        );
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (
            !(event.getView().getTopInventory().getHolder() instanceof
                    MarketInventoryHolder holder)
        ) {
            return;
        }

        event.setCancelled(true);
        if (
            event.getClickedInventory() == null ||
            !event
                .getClickedInventory()
                .equals(event.getView().getTopInventory())
        ) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (
            rawSlot < 0 ||
            rawSlot >= event.getView().getTopInventory().getSize()
        ) {
            return;
        }

        if (holder instanceof CategoryListHolder) {
            handleCategoryListClick(player, rawSlot);
            return;
        }

        if (holder instanceof CategoryItemsHolder categoryItemsHolder) {
            handleCategoryItemsClick(
                player,
                categoryItemsHolder.categoryId(),
                rawSlot
            );
            return;
        }

        if (holder instanceof TradeViewHolder tradeViewHolder) {
            handleTradeClick(
                player,
                tradeViewHolder.categoryId(),
                tradeViewHolder.itemId(),
                rawSlot
            );
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

    private void handleCategoryItemsClick(
        Player player,
        String categoryId,
        int rawSlot
    ) {
        MarketBrowseSnapshot snapshot = currentSnapshot(player);
        if (snapshot == null) {
            return;
        }

        MarketCategorySnapshot category = snapshot
            .findCategory(categoryId)
            .orElse(null);
        if (category == null) {
            player.closeInventory();
            renderer.sendMessage(player, renderer.message("messages.unavailable-no-cache"));
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

    private void handleTradeClick(
        Player player,
        String categoryId,
        String itemId,
        int rawSlot
    ) {
        if (rawSlot == backSlot(TRADE_ROWS)) {
            openCategory(player, categoryId);
            return;
        }

        Integer quantityDelta = tradeQuantityDeltaForSlot(rawSlot);
        if (quantityDelta != null) {
            adjustTradeQuantity(player, categoryId, itemId, quantityDelta);
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

        MarketCategorySnapshot category = snapshot
            .findCategory(categoryId)
            .orElse(null);
        if (category == null) {
            renderer.sendMessage(player, renderer.message("messages.unavailable-no-cache"));
            return;
        }

        int rows = rowsFor(category.items().size() + 1);
        CategoryItemsHolder holder = new CategoryItemsHolder(categoryId);
        Inventory inventory = createInventory(
            holder,
            rows,
            renderer.colorize(category.title())
        );

        int slot = 0;
        for (MarketItemSnapshot item : category.items()) {
            inventory.setItem(slot++, renderer.itemIcon(item, snapshot.readOnly()));
        }

        inventory.setItem(
            backSlot(rows),
            renderer.simpleItem(
                Material.ARROW,
                "&eBack",
                List.of("&7Return to the category list.")
            )
        );

        holder.setInventory(inventory);
        openMarketInventory(player, inventory);
        replaceSession(
            player.getUniqueId(),
            MarketSession.itemList(categoryId, snapshot.readOnly())
        );
    }

    public void openTrade(Player player, String categoryId, String itemId) {
        openTrade(player, categoryId, itemId, null);
    }

    private void openTrade(
        Player player,
        String categoryId,
        String itemId,
        MarketSession existingSession
    ) {
        MarketBrowseSnapshot snapshot = currentSnapshot(player);
        if (snapshot == null) {
            return;
        }

        MarketCategorySnapshot category = snapshot
            .findCategory(categoryId)
            .orElse(null);
        MarketItemSnapshot item = snapshot
            .findItem(categoryId, itemId)
            .orElse(null);
        if (category == null || item == null) {
            renderer.sendMessage(player, renderer.message("messages.unavailable-no-cache"));
            return;
        }

        MarketSession session =
            existingSession != null
                ? existingSession
                : MarketSession.tradeView(
                      categoryId,
                      itemId,
                      snapshot.readOnly()
                  );
        session = sessionForSnapshot(session, snapshot);
        renderTrade(player, snapshot, category, item, session);
        replaceSession(player.getUniqueId(), session);
    }

    private void renderTrade(
        Player player,
        MarketBrowseSnapshot snapshot,
        MarketCategorySnapshot category,
        MarketItemSnapshot item,
        MarketSession session
    ) {
        TradeViewHolder holder = new TradeViewHolder(category.id(), item.id());
        Inventory inventory = createInventory(
            holder,
            TRADE_ROWS,
            renderer.colorize(item.displayName())
        );

        for (QuantityControl control : QUANTITY_CONTROLS) {
            inventory.setItem(
                control.slot(),
                renderer.quantityButton(
                    control.material(),
                    control.name(),
                    control.itemAmount(),
                    control.enchanted()
                )
            );
        }
        inventory.setItem(
            TRADE_BUY_SLOT,
            renderer.quoteActionButton(
                "Buy",
                session.buyQuotedTotal(),
                session.quoteStatus(),
                snapshot.readOnly()
            )
        );
        inventory.setItem(
            TRADE_ITEM_SLOT,
            renderer.tradePreview(item, session, snapshot.readOnly())
        );
        inventory.setItem(
            TRADE_SELL_SLOT,
            renderer.quoteActionButton(
                "Sell",
                session.sellQuotedTotal(),
                session.quoteStatus(),
                snapshot.readOnly()
            )
        );
        inventory.setItem(
            backSlot(TRADE_ROWS),
            renderer.simpleItem(
                Material.ARROW,
                "&eBack",
                List.of("&7Return to " + category.title() + ".")
            )
        );

        holder.setInventory(inventory);
        openMarketInventory(player, inventory);
    }

    public void closeSession(UUID playerId) {
        if (internalInventoryTransitions.remove(playerId)) {
            return;
        }

        sessionRegistry.remove(playerId);
    }

    private void openMarketInventory(Player player, Inventory inventory) {
        UUID playerId = player.getUniqueId();
        boolean replacingMarketInventory = isMarketInventoryOpen(player);
        if (replacingMarketInventory) {
            internalInventoryTransitions.add(playerId);
        }

        player.openInventory(inventory);

        if (replacingMarketInventory && plugin != null) {
            plugin
                .getServer()
                .getScheduler()
                .runTask(plugin, () ->
                    internalInventoryTransitions.remove(playerId)
                );
        } else {
            internalInventoryTransitions.remove(playerId);
        }
    }

    private boolean isMarketInventoryOpen(Player player) {
        try {
            return (
                player
                        .getOpenInventory()
                        .getTopInventory()
                        .getHolder() instanceof
                    MarketInventoryHolder
            );
        } catch (RuntimeException error) {
            return false;
        }
    }

    private void replaceSession(UUID playerId, MarketSession session) {
        sessionRegistry.replace(playerId, session);
    }

    public void handlePlayerJoin(Player player) {
        applyDeferredSettlements(player);
    }

    private Inventory createInventory(
        MarketInventoryHolder holder,
        int rows,
        String title
    ) {
        return Bukkit.createInventory(holder, rows * 9, renderer.render(title));
    }

    private void openCurrentMainMenu(Player player) {
        MarketBrowseSnapshot snapshot = currentSnapshot(player);
        if (snapshot != null) {
            openMainMenu(player, snapshot);
        }
    }

    private MarketBrowseSnapshot currentSnapshot(Player player) {
        MarketBrowseSnapshot current = snapshotService
            .currentSnapshot()
            .orElse(null);
        if (current == null) {
            renderer.sendMessage(player, renderer.message("messages.unavailable-no-cache"));
            return null;
        }

        boolean readOnly = sessionRegistry
            .get(player.getUniqueId())
            .map(MarketSession::readOnly)
            .orElse(current.readOnly());
        return current.withReadOnly(readOnly);
    }

    private void adjustTradeQuantity(
        Player player,
        String categoryId,
        String itemId,
        int delta
    ) {
        MarketSession currentSession = sessionRegistry
            .get(player.getUniqueId())
            .orElse(null);
        if (
            currentSession == null ||
            currentSession.screen() != MarketScreen.TRADE_VIEW ||
            !categoryId.equals(currentSession.selectedCategoryId()) ||
            !itemId.equals(currentSession.selectedItemId()) ||
            currentSession.executingBuy() ||
            currentSession.executingSell()
        ) {
            return;
        }

        MarketSession updatedSession = sessionRegistry
            .update(player.getUniqueId(), session -> {
                if (
                    session.screen() != MarketScreen.TRADE_VIEW ||
                    !categoryId.equals(session.selectedCategoryId()) ||
                    !itemId.equals(session.selectedItemId()) ||
                    session.executingBuy() ||
                    session.executingSell()
                ) {
                    return session;
                }

                int quantity = Math.min(
                    2304,
                    Math.max(1, session.quantity() + delta)
                );

                if (quantity == session.quantity()) {
                    return session;
                }

                return session.withQuantity(quantity);
            })
            .orElse(null);

        if (
            updatedSession == null ||
            updatedSession.screen() != MarketScreen.TRADE_VIEW
        ) {
            return;
        }
        if (updatedSession.quantity() == currentSession.quantity()) {
            return;
        }

        renderer.sendMessage(
            player,
            (delta > 0 ? "&a" : "&6") +
                "Quantity: &f" +
                updatedSession.quantity()
        );
        refreshTrade(player, categoryId, itemId);
    }

    private void refreshTrade(Player player, String categoryId, String itemId) {
        openTrade(
            player,
            categoryId,
            itemId,
            sessionRegistry.get(player.getUniqueId()).orElse(null)
        );
    }

    private void handleBuyClick(
        Player player,
        String categoryId,
        String itemId
    ) {
        MarketBrowseSnapshot snapshot = currentSnapshot(player);
        if (snapshot == null) {
            return;
        }

        MarketItemSnapshot item = snapshot
            .findItem(categoryId, itemId)
            .orElse(null);
        if (item == null) {
            renderer.sendMessage(player, renderer.message("messages.unavailable-no-cache"));
            return;
        }

        MarketSession executingSession = sessionRegistry
            .update(player.getUniqueId(), current -> {
                if (
                    current.screen() != MarketScreen.TRADE_VIEW ||
                    current.readOnly() ||
                    current.executingBuy() ||
                    current.executingSell() ||
                    current.quoteStatus() != MarketQuoteStatus.AVAILABLE ||
                    !categoryId.equals(current.selectedCategoryId()) ||
                    !itemId.equals(current.selectedItemId())
                ) {
                    return current;
                }

                return current.withExecutionPending(MarketQuoteSide.BUY);
            })
            .orElse(null);

        if (
            executingSession == null ||
            executingSession.screen() != MarketScreen.TRADE_VIEW ||
            !executingSession.executingBuy()
        ) {
            renderer.sendMessage(player, renderer.message("messages.trade-disabled"));
            refreshTrade(player, categoryId, itemId);
            return;
        }

        refreshTrade(player, categoryId, itemId);
        executeTradeAsync(
            player.getUniqueId(),
            item.icon(),
            snapshot.snapshotVersion(),
            executingSession,
            MarketQuoteSide.BUY
        );
    }

    private void handleSellClick(
        Player player,
        String categoryId,
        String itemId
    ) {
        MarketBrowseSnapshot snapshot = currentSnapshot(player);
        if (snapshot == null) {
            return;
        }

        MarketItemSnapshot item = snapshot
            .findItem(categoryId, itemId)
            .orElse(null);
        if (item == null) {
            renderer.sendMessage(player, renderer.message("messages.unavailable-no-cache"));
            return;
        }

        MarketSession currentSession = sessionRegistry
            .get(player.getUniqueId())
            .orElse(null);
        if (
            currentSession == null ||
            currentSession.screen() != MarketScreen.TRADE_VIEW ||
            currentSession.readOnly() ||
            currentSession.executingBuy() ||
            currentSession.executingSell() ||
            currentSession.quoteStatus() != MarketQuoteStatus.AVAILABLE ||
            !categoryId.equals(currentSession.selectedCategoryId()) ||
            !itemId.equals(currentSession.selectedItemId())
        ) {
            renderer.sendMessage(player, renderer.message("messages.trade-disabled"));
            refreshTrade(player, categoryId, itemId);
            return;
        }

        int heldQuantity = inventoryService.count(player, item.icon());
        if (heldQuantity <= 0) {
            renderer.sendMessage(player, renderer.message("messages.sell-no-items"));
            refreshTrade(player, categoryId, itemId);
            return;
        }

        if (heldQuantity < currentSession.quantity()) {
            MarketSession updated = sessionRegistry
                .update(player.getUniqueId(), session -> {
                    if (!sameTradeSession(session, currentSession)) {
                        return session;
                    }

                    return session.withQuantity(heldQuantity);
                })
                .orElse(null);

            renderer.sendMessage(
                player,
                renderer.message("messages.sell-quantity-adjusted").replace(
                    "{quantity}",
                    Integer.toString(heldQuantity)
                )
            );
            if (updated != null) {
                rerenderTradeIfVisible(player.getUniqueId(), updated);
            }
            return;
        }

        MarketSession executingSession = sessionRegistry
            .update(player.getUniqueId(), session -> {
                if (
                    !sameTradeSession(session, currentSession) ||
                    session.executingBuy() ||
                    session.executingSell()
                ) {
                    return session;
                }

                return session.withExecutionPending(MarketQuoteSide.SELL);
            })
            .orElse(null);

        if (executingSession == null || !executingSession.executingSell()) {
            renderer.sendMessage(player, renderer.message("messages.trade-disabled"));
            refreshTrade(player, categoryId, itemId);
            return;
        }

        refreshTrade(player, categoryId, itemId);
        executeTradeAsync(
            player.getUniqueId(),
            item.icon(),
            snapshot.snapshotVersion(),
            executingSession,
            MarketQuoteSide.SELL
        );
    }

    private void executeTradeAsync(
        UUID playerId,
        Material material,
        String snapshotVersion,
        MarketSession executingSession,
        MarketQuoteSide side
    ) {
        plugin
            .getServer()
            .getScheduler()
            .runTaskAsynchronously(plugin, () -> {
                try {
                    MarketQuoteResult quote = quoteClient.requestQuote(
                        playerId,
                        executingSession.selectedItemId(),
                        side,
                        executingSession.quantity(),
                        snapshotVersion
                    );
                    MarketExecuteResult result = executeClient.executeTrade(
                        playerId,
                        executingSession.selectedItemId(),
                        side,
                        executingSession.quantity(),
                        quote.quoteToken(),
                        quote.snapshotVersion()
                    );
                    plugin
                        .getServer()
                        .getScheduler()
                        .runTask(plugin, () ->
                            applyTradeSuccess(
                                side,
                                playerId,
                                material,
                                executingSession,
                                result
                            )
                        );
                } catch (MarketExecuteRejectedException rejection) {
                    plugin
                        .getServer()
                        .getScheduler()
                        .runTask(plugin, () ->
                            applyTradeRejection(
                                playerId,
                                executingSession,
                                rejection
                            )
                        );
                } catch (RuntimeException error) {
                    plugin
                        .getServer()
                        .getScheduler()
                        .runTask(plugin, () ->
                            applyTradeFailure(playerId, executingSession)
                        );
                }
            });
    }

    private String apiResponseDetails(Throwable error) {
        if (!(error instanceof MarketApiRequestException requestException)) {
            return "";
        }

        String responseBody = requestException.responseBody();
        if (responseBody == null || responseBody.isBlank()) {
            return ", responseBody=<empty>";
        }

        String compactBody = responseBody.replaceAll("\\s+", " ").trim();
        if (compactBody.length() > 500) {
            compactBody = compactBody.substring(0, 500) + "...";
        }
        return ", responseBody=" + compactBody;
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void rerenderTradeIfVisible(UUID playerId, MarketSession session) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (
            player == null ||
            !player.isOnline() ||
            session.screen() != MarketScreen.TRADE_VIEW
        ) {
            return;
        }

        if (
            !(player.getOpenInventory().getTopInventory().getHolder() instanceof
                    TradeViewHolder)
        ) {
            return;
        }

        openTrade(
            player,
            session.selectedCategoryId(),
            session.selectedItemId(),
            session
        );
    }

    private void applyBuySuccess(
        UUID playerId,
        Material material,
        MarketSession expectedSession,
        MarketExecuteResult result
    ) {
        applyTradeSuccess(
            MarketQuoteSide.BUY,
            playerId,
            material,
            expectedSession,
            result
        );
    }

    private void applySellSuccess(
        UUID playerId,
        Material material,
        MarketSession expectedSession,
        MarketExecuteResult result
    ) {
        applyTradeSuccess(
            MarketQuoteSide.SELL,
            playerId,
            material,
            expectedSession,
            result
        );
    }

    private void applyTradeSuccess(
        MarketQuoteSide side,
        UUID playerId,
        Material material,
        MarketSession expectedSession,
        MarketExecuteResult result
    ) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            applySettlement(player, material, result, side);
        } else {
            settlementService.queueDeferredSettlement(
                playerId,
                new DeferredSettlement(side, material, result)
            );
        }

        MarketSession updated = sessionRegistry
            .update(playerId, current -> {
                if (!sameTradeSession(current, expectedSession)) {
                    return current;
                }

                return current.withQuantity(current.quantity());
            })
            .orElse(null);

        if (updated != null) {
            rerenderTradeIfVisible(playerId, updated);
            refreshTradeSnapshot(playerId, updated);
        }
    }

    private void applySettlement(
        Player player,
        Material material,
        MarketExecuteResult result,
        MarketQuoteSide side
    ) {
        if (side == MarketQuoteSide.BUY) {
            settlementService.applyBuySettlement(player, material, result);
            return;
        }

        settlementService.applySellSettlement(player, material, result);
    }

    private void applyBuyRejection(
        UUID playerId,
        MarketSession expectedSession,
        MarketExecuteRejectedException rejection
    ) {
        applyTradeRejection(playerId, expectedSession, rejection);
    }

    private void applyTradeRejection(
        UUID playerId,
        MarketSession expectedSession,
        MarketExecuteRejectedException rejection
    ) {
        String code = rejection.rejectionCode();
        if ("STALE_QUOTE".equals(code) || "QUOTE_EXPIRED".equals(code)) {
            MarketSession updated = sessionRegistry
                .update(playerId, current -> {
                    if (!sameTradeSession(current, expectedSession)) {
                        return current;
                    }

                    return current.withQuantity(current.quantity());
                })
                .orElse(null);

            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                renderer.sendMessage(player, rejectionMessage(code));
            }

            if (updated != null) {
                rerenderTradeIfVisible(playerId, updated);
                refreshTradeSnapshot(playerId, updated);
            }
            return;
        }

        MarketSession updated = sessionRegistry
            .update(playerId, current -> {
                if (!sameTradeSession(current, expectedSession)) {
                    return current;
                }

                return current.withQuoteMessage(
                    MarketQuoteStatus.AVAILABLE,
                    rejectionMessage(code)
                );
            })
            .orElse(null);

        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            renderer.sendMessage(player, rejectionMessage(code));
        }

        if (updated != null) {
            rerenderTradeIfVisible(playerId, updated);
        }
    }

    private void applyBuyFailure(
        UUID playerId,
        MarketSession expectedSession,
        RuntimeException error
    ) {
        applyTradeFailure(playerId, expectedSession);
    }

    private void applySellRejection(
        UUID playerId,
        MarketSession expectedSession,
        MarketExecuteRejectedException rejection
    ) {
        applyTradeRejection(playerId, expectedSession, rejection);
    }

    private void applySellFailure(
        UUID playerId,
        MarketSession expectedSession,
        RuntimeException error
    ) {
        applyTradeFailure(playerId, expectedSession);
    }

    private void applyTradeFailure(
        UUID playerId,
        MarketSession expectedSession
    ) {
        MarketSession updated = sessionRegistry
            .update(playerId, current -> {
                if (!sameTradeSession(current, expectedSession)) {
                    return current;
                }

                return current.withQuoteMessage(
                    MarketQuoteStatus.AVAILABLE,
                    renderer.message("messages.execute-unavailable")
                );
            })
            .orElse(null);

        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            renderer.sendMessage(player, renderer.message("messages.execute-unavailable"));
        }

        if (updated != null) {
            rerenderTradeIfVisible(playerId, updated);
        }
    }

    private void refreshTradeSnapshot(
        UUID playerId,
        MarketSession expectedSession
    ) {
        snapshotService
            .refreshSnapshot()
            .whenComplete((result, error) ->
                plugin
                    .getServer()
                    .getScheduler()
                    .runTask(plugin, () ->
                        applyTradeSnapshotRefresh(
                            playerId,
                            expectedSession,
                            result,
                            error
                        )
                    )
            );
    }

    private void applyTradeSnapshotRefresh(
        UUID playerId,
        MarketSession expectedSession,
        MarketBrowseSnapshotLoadResult result,
        Throwable error
    ) {
        MarketSession updated = sessionRegistry
            .update(playerId, current -> {
                if (!sameTradeSession(current, expectedSession)) {
                    return current;
                }

                if (error != null) {
                    return current.withQuoteUnavailable(
                        renderer.message("messages.quote-unavailable")
                    );
                }

                return sessionForSnapshot(current, result.snapshot());
            })
            .orElse(null);

        if (updated != null) {
            rerenderTradeIfVisible(playerId, updated);
        }
    }

    private boolean sameTradeSession(
        MarketSession current,
        MarketSession expectedSession
    ) {
        return (
            current.screen() == MarketScreen.TRADE_VIEW &&
            current.selectedCategoryId() != null &&
            current
                .selectedCategoryId()
                .equals(expectedSession.selectedCategoryId()) &&
            current.selectedItemId() != null &&
            current.selectedItemId().equals(expectedSession.selectedItemId()) &&
            current.quantity() == expectedSession.quantity()
        );
    }

    MarketSession sessionForSnapshot(
        MarketSession session,
        MarketBrowseSnapshot snapshot
    ) {
        if (session.screen() != MarketScreen.TRADE_VIEW) {
            return session;
        }

        if (snapshot.readOnly()) {
            return session.asReadOnlyPreview();
        }

        MarketItemSnapshot item = snapshot
            .findItem(session.selectedCategoryId(), session.selectedItemId())
            .orElse(null);
        if (item == null) {
            return session.withQuoteUnavailable(
                rejectionMessage("UNKNOWN_ITEM")
            );
        }

        if ("Blocked".equals(item.stockDisplay())) {
            return session.withQuoteUnavailable(
                rejectionMessage("ITEM_BLOCKED")
            );
        }

        if ("Unavailable".equals(item.stockDisplay())) {
            return session.withQuoteUnavailable(
                rejectionMessage("ITEM_NOT_OPERATING")
            );
        }

        if (
            session.readOnly() ||
            session.quoteStatus() == MarketQuoteStatus.DISABLED
        ) {
            return session.asLiveTradingAvailable();
        }

        return session;
    }

    String sellRemovalFailureLogMessage(
        UUID playerId,
        Material material,
        MarketExecuteResult result,
        int removedQuantity
    ) {
        return settlementService.sellRemovalFailureLogMessage(
            playerId,
            material,
            result,
            removedQuantity
        );
    }

    String sellRemovalFailurePlayerMessage(
        Material material,
        MarketExecuteResult result,
        int removedQuantity
    ) {
        return settlementService.sellRemovalFailurePlayerMessage(
            material,
            result,
            removedQuantity
        );
    }

    void queueDeferredSettlement(UUID playerId, DeferredSettlement settlement) {
        settlementService.queueDeferredSettlement(playerId, settlement);
    }

    int deferredSettlementCount(UUID playerId) {
        return settlementService.deferredSettlementCount(playerId);
    }

    private void applyDeferredSettlements(Player player) {
        settlementService.applyDeferredSettlements(player);
    }

    private int rowsFor(int itemCount) {
        int itemRows = Math.max(1, (int) Math.ceil(itemCount / 9.0d));
        return Math.min(6, Math.max(3, itemRows + 1));
    }

    private int backSlot(int rows) {
        return rows * 9 - 5;
    }

    private String rejectionMessage(String code) {
        return renderer.rejectionMessage(code);
    }

    private Integer tradeQuantityDeltaForSlot(int rawSlot) {
        return QUANTITY_CONTROLS.stream()
            .filter(control -> control.slot() == rawSlot)
            .map(QuantityControl::delta)
            .findFirst()
            .orElse(null);
    }

    record DeferredSettlement(
        MarketQuoteSide side,
        Material material,
        MarketExecuteResult result
    ) {}
}
