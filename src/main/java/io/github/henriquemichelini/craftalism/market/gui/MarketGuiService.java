package io.github.henriquemichelini.craftalism.market.gui;

import io.github.henriquemichelini.craftalism.market.api.MarketQuoteClient;
import io.github.henriquemichelini.craftalism.market.api.MarketQuotePair;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteResult;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteSide;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotService;
import io.github.henriquemichelini.craftalism.market.browse.MarketCategorySnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketItemSnapshot;
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
    private final MarketSessionRegistry sessionRegistry;
    private final FileConfiguration config;
    private final Map<UUID, BukkitTask> pendingQuoteTasks = new ConcurrentHashMap<>();

    public MarketGuiService(
            Plugin plugin,
            MarketBrowseSnapshotService snapshotService,
            MarketQuoteClient quoteClient,
            MarketSessionRegistry sessionRegistry,
            FileConfiguration config
    ) {
        this.plugin = plugin;
        this.snapshotService = snapshotService;
        this.quoteClient = quoteClient;
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

        if (rawSlot == TRADE_BUY_SLOT || rawSlot == TRADE_SELL_SLOT) {
            player.sendMessage(colorize(message("messages.trade-disabled")));
            refreshTrade(player, categoryId, itemId);
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
        lore.add(colorize("&7Execution is not enabled yet."));
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
            material = Material.GOLD_NUGGET;
            name = "&e" + action + " Ready";
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
