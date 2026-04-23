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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class MarketGuiService {

    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER =
        LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION_SERIALIZER =
        LegacyComponentSerializer.legacySection();
    private static final int QUANTITY_DISPLAY_SLOT = 22;
    private static final int TRADE_BUY_SLOT = 23;
    private static final int TRADE_ITEM_SLOT = 24;
    private static final int TRADE_SELL_SLOT = 28;
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
    private final FileConfiguration config;
    private final Set<UUID> internalInventoryTransitions =
        ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<DeferredSettlement>> deferredSettlements =
        new ConcurrentHashMap<>();
    private final DeferredSettlementStore deferredSettlementStore;

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
        this.config = config;
        this.deferredSettlementStore = createDeferredSettlementStore(plugin);
        deferredSettlements.putAll(loadDeferredSettlements());
    }

    public void openMainMenu(Player player, MarketBrowseSnapshot snapshot) {
        if (snapshot.categories().isEmpty()) {
            sendMessage(player, message("messages.unavailable-no-cache"));
            return;
        }

        CategoryListHolder holder = new CategoryListHolder();
        Inventory inventory = createInventory(
            holder,
            rowsFor(snapshot.categories().size()),
            title("titles.categories")
        );

        int slot = 0;
        for (MarketCategorySnapshot category : snapshot.categories()) {
            inventory.setItem(
                slot++,
                categoryIcon(category, snapshot.readOnly())
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
            sendMessage(player, message("messages.unavailable-no-cache"));
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
            sendMessage(player, message("messages.unavailable-no-cache"));
            return;
        }

        int rows = rowsFor(category.items().size() + 1);
        CategoryItemsHolder holder = new CategoryItemsHolder(categoryId);
        Inventory inventory = createInventory(
            holder,
            rows,
            colorize(category.title())
        );

        int slot = 0;
        for (MarketItemSnapshot item : category.items()) {
            inventory.setItem(slot++, itemIcon(item, snapshot.readOnly()));
        }

        inventory.setItem(
            backSlot(rows),
            simpleItem(
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
            sendMessage(player, message("messages.unavailable-no-cache"));
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
            colorize(item.displayName())
        );

        for (QuantityControl control : QUANTITY_CONTROLS) {
            inventory.setItem(
                control.slot(),
                quantityButton(
                    control.material(),
                    control.name(),
                    control.itemAmount(),
                    control.enchanted()
                )
            );
        }
        inventory.setItem(
            TRADE_BUY_SLOT,
            quoteActionButton(
                "Buy",
                session.buyQuotedTotal(),
                session.quoteStatus(),
                snapshot.readOnly()
            )
        );
        inventory.setItem(
            TRADE_ITEM_SLOT,
            tradePreview(item, session, snapshot.readOnly())
        );
        inventory.setItem(
            TRADE_SELL_SLOT,
            quoteActionButton(
                "Sell",
                session.sellQuotedTotal(),
                session.quoteStatus(),
                snapshot.readOnly()
            )
        );
        inventory.setItem(
            backSlot(TRADE_ROWS),
            simpleItem(
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
        return Bukkit.createInventory(holder, rows * 9, render(title));
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
            sendMessage(player, message("messages.unavailable-no-cache"));
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

                int quantity = Math.max(1, session.quantity() + delta);
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

        sendMessage(
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
            sendMessage(player, message("messages.unavailable-no-cache"));
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
            sendMessage(player, message("messages.trade-disabled"));
            refreshTrade(player, categoryId, itemId);
            return;
        }

        refreshTrade(player, categoryId, itemId);
        plugin
            .getServer()
            .getScheduler()
            .runTaskAsynchronously(plugin, () -> {
                try {
                    MarketQuoteResult quote = quoteClient.requestQuote(
                        player.getUniqueId(),
                        executingSession.selectedItemId(),
                        MarketQuoteSide.BUY,
                        executingSession.quantity(),
                        snapshot.snapshotVersion()
                    );
                    MarketExecuteResult result = executeClient.executeTrade(
                        player.getUniqueId(),
                        executingSession.selectedItemId(),
                        MarketQuoteSide.BUY,
                        executingSession.quantity(),
                        quote.quoteToken(),
                        quote.snapshotVersion()
                    );
                    plugin
                        .getServer()
                        .getScheduler()
                        .runTask(plugin, () ->
                            applyBuySuccess(
                                player.getUniqueId(),
                                item.icon(),
                                executingSession,
                                result
                            )
                        );
                } catch (MarketExecuteRejectedException rejection) {
                    plugin
                        .getServer()
                        .getScheduler()
                        .runTask(plugin, () ->
                            applyBuyRejection(
                                player.getUniqueId(),
                                executingSession,
                                rejection
                            )
                        );
                } catch (RuntimeException error) {
                    plugin
                        .getServer()
                        .getScheduler()
                        .runTask(plugin, () ->
                            applyBuyFailure(
                                player.getUniqueId(),
                                executingSession,
                                error
                            )
                        );
                }
            });
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
            sendMessage(player, message("messages.unavailable-no-cache"));
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
            sendMessage(player, message("messages.trade-disabled"));
            refreshTrade(player, categoryId, itemId);
            return;
        }

        int heldQuantity = inventoryService.count(player, item.icon());
        if (heldQuantity <= 0) {
            sendMessage(player, message("messages.sell-no-items"));
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

            sendMessage(
                player,
                message("messages.sell-quantity-adjusted").replace(
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
            sendMessage(player, message("messages.trade-disabled"));
            refreshTrade(player, categoryId, itemId);
            return;
        }

        refreshTrade(player, categoryId, itemId);
        plugin
            .getServer()
            .getScheduler()
            .runTaskAsynchronously(plugin, () -> {
                try {
                    MarketQuoteResult quote = quoteClient.requestQuote(
                        player.getUniqueId(),
                        executingSession.selectedItemId(),
                        MarketQuoteSide.SELL,
                        executingSession.quantity(),
                        snapshot.snapshotVersion()
                    );
                    MarketExecuteResult result = executeClient.executeTrade(
                        player.getUniqueId(),
                        executingSession.selectedItemId(),
                        MarketQuoteSide.SELL,
                        executingSession.quantity(),
                        quote.quoteToken(),
                        quote.snapshotVersion()
                    );
                    plugin
                        .getServer()
                        .getScheduler()
                        .runTask(plugin, () ->
                            applySellSuccess(
                                player.getUniqueId(),
                                item.icon(),
                                executingSession,
                                result
                            )
                        );
                } catch (MarketExecuteRejectedException rejection) {
                    plugin
                        .getServer()
                        .getScheduler()
                        .runTask(plugin, () ->
                            applySellRejection(
                                player.getUniqueId(),
                                executingSession,
                                rejection
                            )
                        );
                } catch (RuntimeException error) {
                    plugin
                        .getServer()
                        .getScheduler()
                        .runTask(plugin, () ->
                            applySellFailure(
                                player.getUniqueId(),
                                executingSession,
                                error
                            )
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
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            applyBuySettlement(player, material, result);
        } else {
            queueDeferredSettlement(
                playerId,
                new DeferredSettlement(MarketQuoteSide.BUY, material, result)
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

    private void applySellSuccess(
        UUID playerId,
        Material material,
        MarketSession expectedSession,
        MarketExecuteResult result
    ) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            queueDeferredSettlement(
                playerId,
                new DeferredSettlement(MarketQuoteSide.SELL, material, result)
            );
        } else {
            applySellSettlement(player, material, result);
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

    private void applyBuyRejection(
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
                sendMessage(player, rejectionMessage(code));
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
            sendMessage(player, rejectionMessage(code));
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
        MarketSession updated = sessionRegistry
            .update(playerId, current -> {
                if (!sameTradeSession(current, expectedSession)) {
                    return current;
                }

                return current.withQuoteMessage(
                    MarketQuoteStatus.AVAILABLE,
                    message("messages.execute-unavailable")
                );
            })
            .orElse(null);

        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            sendMessage(player, message("messages.execute-unavailable"));
        }

        if (updated != null) {
            rerenderTradeIfVisible(playerId, updated);
        }
    }

    private void applySellRejection(
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
                sendMessage(player, rejectionMessage(code));
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
            sendMessage(player, rejectionMessage(code));
        }

        if (updated != null) {
            rerenderTradeIfVisible(playerId, updated);
        }
    }

    private void applySellFailure(
        UUID playerId,
        MarketSession expectedSession,
        RuntimeException error
    ) {
        MarketSession updated = sessionRegistry
            .update(playerId, current -> {
                if (!sameTradeSession(current, expectedSession)) {
                    return current;
                }

                return current.withQuoteMessage(
                    MarketQuoteStatus.AVAILABLE,
                    message("messages.execute-unavailable")
                );
            })
            .orElse(null);

        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            sendMessage(player, message("messages.execute-unavailable"));
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
                        message("messages.quote-unavailable")
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
        return message("messages.sell-removal-failed-log")
            .replace("{playerId}", playerId.toString())
            .replace("{item}", material.name())
            .replace("{executed}", Integer.toString(result.executedQuantity()))
            .replace("{removed}", Integer.toString(removedQuantity))
            .replace(
                "{missing}",
                Integer.toString(
                    Math.max(0, result.executedQuantity() - removedQuantity)
                )
            )
            .replace("{total}", result.totalPrice() + " " + result.currency())
            .replace("{snapshotVersion}", result.snapshotVersion());
    }

    String sellRemovalFailurePlayerMessage(
        Material material,
        MarketExecuteResult result,
        int removedQuantity
    ) {
        return message("messages.sell-removal-failed")
            .replace("{item}", material.name())
            .replace("{executed}", Integer.toString(result.executedQuantity()))
            .replace("{removed}", Integer.toString(removedQuantity))
            .replace(
                "{missing}",
                Integer.toString(
                    Math.max(0, result.executedQuantity() - removedQuantity)
                )
            )
            .replace("{total}", result.totalPrice() + " " + result.currency());
    }

    void queueDeferredSettlement(UUID playerId, DeferredSettlement settlement) {
        deferredSettlements.compute(playerId, (ignored, current) -> {
            List<DeferredSettlement> updated =
                current == null ? new ArrayList<>() : new ArrayList<>(current);
            updated.add(settlement);
            return List.copyOf(updated);
        });
        persistDeferredSettlements();

        if (plugin != null) {
            plugin
                .getLogger()
                .warning(
                    "Deferred market " +
                        settlement.side().name().toLowerCase() +
                        " settlement for player " +
                        playerId +
                        " until they reconnect."
                );
        }
    }

    int deferredSettlementCount(UUID playerId) {
        return deferredSettlements.getOrDefault(playerId, List.of()).size();
    }

    private void applyDeferredSettlements(Player player) {
        List<DeferredSettlement> settlements = deferredSettlements.remove(
            player.getUniqueId()
        );
        if (settlements == null || settlements.isEmpty()) {
            return;
        }

        List<DeferredSettlement> remaining = new ArrayList<>();
        for (DeferredSettlement settlement : settlements) {
            if (settlement.side() == MarketQuoteSide.BUY) {
                if (
                    !applyBuySettlement(
                        player,
                        settlement.material(),
                        settlement.result()
                    )
                ) {
                    remaining.add(settlement);
                    logDeferredSettlementStillPending(
                        player.getUniqueId(),
                        settlement
                    );
                } else {
                    logDeferredSettlementApplied(
                        player.getUniqueId(),
                        settlement
                    );
                }
                continue;
            }

            if (
                inventoryService.count(player, settlement.material()) <
                settlement.result().executedQuantity()
            ) {
                reportSellRemovalFailure(
                    player,
                    settlement.material(),
                    settlement.result(),
                    0
                );
                remaining.add(settlement);
                logDeferredSettlementStillPending(
                    player.getUniqueId(),
                    settlement
                );
                continue;
            }

            if (
                !applySellSettlement(
                    player,
                    settlement.material(),
                    settlement.result()
                )
            ) {
                remaining.add(settlement);
                logDeferredSettlementStillPending(
                    player.getUniqueId(),
                    settlement
                );
            } else {
                logDeferredSettlementApplied(player.getUniqueId(), settlement);
            }
        }

        if (!remaining.isEmpty()) {
            deferredSettlements.put(
                player.getUniqueId(),
                List.copyOf(remaining)
            );
        }
        persistDeferredSettlements();
    }

    private boolean applyBuySettlement(
        Player player,
        Material material,
        MarketExecuteResult result
    ) {
        int delivered = inventoryService.addOrDrop(
            player,
            material,
            result.executedQuantity()
        );
        if (delivered < result.executedQuantity()) {
            sendMessage(
                player,
                message("messages.buy-overflow")
                    .replace(
                        "{quantity}",
                        Integer.toString(result.executedQuantity())
                    )
                    .replace(
                        "{total}",
                        result.totalPrice() + " " + result.currency()
                    )
                    .replace(
                        "{dropped}",
                        Integer.toString(result.executedQuantity() - delivered)
                    )
            );
            return true;
        }

        sendMessage(
            player,
            message("messages.buy-success")
                .replace(
                    "{quantity}",
                    Integer.toString(result.executedQuantity())
                )
                .replace(
                    "{total}",
                    result.totalPrice() + " " + result.currency()
                )
        );
        return true;
    }

    private boolean applySellSettlement(
        Player player,
        Material material,
        MarketExecuteResult result
    ) {
        int removed = inventoryService.remove(
            player,
            material,
            result.executedQuantity()
        );
        if (removed != result.executedQuantity()) {
            reportSellRemovalFailure(player, material, result, removed);
            return false;
        }

        sendMessage(
            player,
            message("messages.sell-success")
                .replace(
                    "{quantity}",
                    Integer.toString(result.executedQuantity())
                )
                .replace(
                    "{total}",
                    result.totalPrice() + " " + result.currency()
                )
        );
        return true;
    }

    private void reportSellRemovalFailure(
        Player player,
        Material material,
        MarketExecuteResult result,
        int removed
    ) {
        if (plugin != null) {
            plugin
                .getLogger()
                .severe(
                    sellRemovalFailureLogMessage(
                        player.getUniqueId(),
                        material,
                        result,
                        removed
                    )
                );
        }
        sendMessage(
            player,
            sellRemovalFailurePlayerMessage(material, result, removed)
        );
    }

    private void logDeferredSettlementApplied(
        UUID playerId,
        DeferredSettlement settlement
    ) {
        if (plugin == null) {
            return;
        }

        plugin
            .getLogger()
            .info(
                "Applied deferred market " +
                    settlement.side().name().toLowerCase() +
                    " settlement for player " +
                    playerId +
                    ": item=" +
                    settlement.material().name() +
                    ", executed=" +
                    settlement.result().executedQuantity() +
                    ", settled=" +
                    settlement.result().totalPrice() +
                    " " +
                    settlement.result().currency() +
                    ", snapshotVersion=" +
                    settlement.result().snapshotVersion()
            );
    }

    private void logDeferredSettlementStillPending(
        UUID playerId,
        DeferredSettlement settlement
    ) {
        if (plugin == null) {
            return;
        }

        plugin
            .getLogger()
            .severe(
                "Deferred market " +
                    settlement.side().name().toLowerCase() +
                    " settlement remains queued for player " +
                    playerId +
                    ": item=" +
                    settlement.material().name() +
                    ", executed=" +
                    settlement.result().executedQuantity() +
                    ", settled=" +
                    settlement.result().totalPrice() +
                    " " +
                    settlement.result().currency() +
                    ", snapshotVersion=" +
                    settlement.result().snapshotVersion()
            );
    }

    private DeferredSettlementStore createDeferredSettlementStore(
        Plugin plugin
    ) {
        if (plugin == null || plugin.getDataFolder() == null) {
            return null;
        }

        Logger logger =
            plugin.getLogger() != null
                ? plugin.getLogger()
                : Logger.getLogger(MarketGuiService.class.getName());
        return new DeferredSettlementStore(
            plugin
                .getDataFolder()
                .toPath()
                .resolve("deferred-settlements.json"),
            logger
        );
    }

    private Map<UUID, List<DeferredSettlement>> loadDeferredSettlements() {
        if (deferredSettlementStore == null) {
            return Map.of();
        }

        return new HashMap<>(deferredSettlementStore.load());
    }

    private void persistDeferredSettlements() {
        if (deferredSettlementStore == null) {
            return;
        }

        deferredSettlementStore.save(deferredSettlements);
    }

    private int rowsFor(int itemCount) {
        int itemRows = Math.max(1, (int) Math.ceil(itemCount / 9.0d));
        return Math.min(6, Math.max(3, itemRows + 1));
    }

    private int backSlot(int rows) {
        return rows * 9 - 5;
    }

    private ItemStack categoryIcon(
        MarketCategorySnapshot category,
        boolean readOnly
    ) {
        List<String> lore = new ArrayList<>(colorize(category.description()));
        lore.add("");
        lore.add(
            colorize(readOnly ? "&6Read-only browsing" : "&aLive market data")
        );
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
        lore.add(
            colorize(readOnly ? "&6Cached preview only" : "&aClick to trade")
        );
        return simpleItem(item.icon(), item.displayName(), lore);
    }

    private ItemStack tradePreview(
        MarketItemSnapshot item,
        MarketSession session,
        boolean readOnly
    ) {
        List<String> lore = new ArrayList<>(colorize(item.description()));
        lore.add("");
        lore.add(colorize("&7Buy estimate: &f" + item.buyEstimate()));
        lore.add(colorize("&7Sell estimate: &f" + item.sellEstimate()));
        lore.add(colorize("&7Variation: &f" + item.variationPercent()));
        lore.add(colorize("&7Stock: &f" + item.stockDisplay()));
        lore.add(colorize("&7Updated: &f" + item.lastUpdated()));
        lore.add(
            colorize(
                "&7Quote status: &f" +
                    quoteStatusLabel(
                        session.quoteStatus(),
                        session.quoteStatusMessage()
                    )
            )
        );
        if (session.buyQuotedTotal() != null) {
            lore.add(
                colorize("&7Quoted buy total: &f" + session.buyQuotedTotal())
            );
        }
        if (session.sellQuotedTotal() != null) {
            lore.add(
                colorize("&7Quoted sell total: &f" + session.sellQuotedTotal())
            );
        }
        lore.add("");
        lore.add(
            colorize(
                readOnly ? "&6Read-only market preview" : "&aLive market view"
            )
        );
        lore.add(
            colorize(
                readOnly
                    ? "&7Trading stays disabled while using cache."
                    : "&7Buy or sell to request a quote."
            )
        );
        return simpleItem(item.icon(), item.displayName(), lore);
    }

    private ItemStack quoteActionButton(
        String action,
        String quotedTotal,
        MarketQuoteStatus quoteStatus,
        boolean readOnly
    ) {
        List<String> lore = new ArrayList<>();
        lore.add(colorize("&7Click to request a quote and execute."));
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
            material = "Buy".equals(action)
                ? Material.EMERALD
                : Material.REDSTONE;
            name = "&a" + action + " Now";
        }

        return simpleItem(material, name, lore);
    }

    private ItemStack quantityButton(Material material, String name) {
        return quantityButton(material, name, 1);
    }

    private ItemStack quantityButton(
        Material material,
        String name,
        int amount
    ) {
        return quantityButton(material, name, amount, false);
    }

    private ItemStack quantityButton(
        Material material,
        String name,
        int amount,
        boolean enchanted
    ) {
        List<String> lore = List.of("&7Adjust trade quantity.");
        return simpleItem(material, name, lore, amount, enchanted);
    }

    private ItemStack quantityDisplay(int quantity, String statusMessage) {
        List<String> lore = new ArrayList<>();
        lore.add(colorize("&7Current trade quantity."));
        if (statusMessage != null && !statusMessage.isBlank()) {
            lore.add(colorize("&7Status: &f" + statusMessage));
        }
        return simpleItem(Material.PAPER, "&fQuantity: &e" + quantity, lore);
    }

    private String quoteStatusLabel(
        MarketQuoteStatus status,
        String fallbackMessage
    ) {
        return switch (status) {
            case DISABLED -> fallbackMessage != null
                ? fallbackMessage
                : "Read-only";
            case PENDING -> fallbackMessage != null
                ? fallbackMessage
                : "Refreshing";
            case AVAILABLE -> fallbackMessage != null
                ? fallbackMessage
                : "Ready";
            case UNAVAILABLE -> fallbackMessage != null
                ? fallbackMessage
                : "Unavailable";
        };
    }

    private String rejectionMessage(String code) {
        return config.getString(
            "messages.rejections." + code,
            "&cTrade request rejected."
        );
    }

    private ItemStack simpleItem(
        Material material,
        String name,
        List<String> lore
    ) {
        return simpleItem(material, name, lore, 1, false);
    }

    private ItemStack simpleItem(
        Material material,
        String name,
        List<String> lore,
        int amount,
        boolean enchanted
    ) {
        ItemStack itemStack = new ItemStack(material);
        itemStack.setAmount(amount);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }

        itemMeta.displayName(render(name));
        itemMeta.lore(render(lore));
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (enchanted) {
            itemMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private Integer tradeQuantityDeltaForSlot(int rawSlot) {
        return QUANTITY_CONTROLS
            .stream()
            .filter(control -> control.slot() == rawSlot)
            .map(QuantityControl::delta)
            .findFirst()
            .orElse(null);
    }

    private String title(String path) {
        return config.getString(path, "Market");
    }

    private String message(String path) {
        return config.getString(path, "&cMarket data is unavailable.");
    }

    private String colorize(String text) {
        return SECTION_SERIALIZER.serialize(
            AMPERSAND_SERIALIZER.deserialize(text)
        );
    }

    private List<String> colorize(List<String> lines) {
        return lines.stream().map(this::colorize).toList();
    }

    private void sendMessage(Player player, String text) {
        player.sendMessage(render(text));
    }

    private Component render(String text) {
        return AMPERSAND_SERIALIZER.deserialize(text);
    }

    private List<Component> render(List<String> lines) {
        return lines.stream().map(this::render).toList();
    }

    record DeferredSettlement(
        MarketQuoteSide side,
        Material material,
        MarketExecuteResult result
    ) {}

    private record QuantityControl(
        int slot,
        int delta,
        Material material,
        String name,
        int itemAmount,
        boolean enchanted
    ) {}
}
