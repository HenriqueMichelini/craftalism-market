package io.github.henriquemichelini.craftalism.market.gui;

import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotProvider;
import io.github.henriquemichelini.craftalism.market.browse.MarketCategorySnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketItemSnapshot;
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

import java.util.ArrayList;
import java.util.List;

public final class MarketGuiService {
    private static final int TRADE_BUY_SLOT = 11;
    private static final int TRADE_ITEM_SLOT = 13;
    private static final int TRADE_SELL_SLOT = 15;

    private final MarketBrowseSnapshotProvider snapshotProvider;
    private final MarketSessionRegistry sessionRegistry;
    private final FileConfiguration config;

    public MarketGuiService(
            MarketBrowseSnapshotProvider snapshotProvider,
            MarketSessionRegistry sessionRegistry,
            FileConfiguration config
    ) {
        this.snapshotProvider = snapshotProvider;
        this.sessionRegistry = sessionRegistry;
        this.config = config;
    }

    public void openMainMenu(Player player) {
        MarketBrowseSnapshot snapshot = snapshotProvider.loadSnapshot();
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
        sessionRegistry.put(player.getUniqueId(), new MarketSession(MarketScreen.CATEGORY_LIST, null, null, snapshot.readOnly()));
        player.sendMessage(colorize(message("messages.opened-read-only")));
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
        MarketBrowseSnapshot snapshot = snapshotProvider.loadSnapshot();
        if (rawSlot >= snapshot.categories().size()) {
            return;
        }

        openCategory(player, snapshot.categories().get(rawSlot).id());
    }

    private void handleCategoryItemsClick(Player player, String categoryId, int rawSlot) {
        MarketBrowseSnapshot snapshot = snapshotProvider.loadSnapshot();
        MarketCategorySnapshot category = snapshot.findCategory(categoryId).orElse(null);
        if (category == null) {
            player.closeInventory();
            player.sendMessage(colorize(message("messages.unavailable-no-cache")));
            return;
        }

        if (rawSlot == backSlot(rowsFor(category.items().size() + 1))) {
            openMainMenu(player);
            return;
        }

        if (rawSlot >= category.items().size()) {
            return;
        }

        openTrade(player, categoryId, category.items().get(rawSlot).id());
    }

    private void handleTradeClick(Player player, String categoryId, String itemId, int rawSlot) {
        if (rawSlot == backSlot(3)) {
            openCategory(player, categoryId);
            return;
        }

        if (rawSlot == TRADE_BUY_SLOT || rawSlot == TRADE_SELL_SLOT) {
            player.sendMessage(colorize(message("messages.trade-disabled")));
            openTrade(player, categoryId, itemId);
        }
    }

    public void openCategory(Player player, String categoryId) {
        MarketBrowseSnapshot snapshot = snapshotProvider.loadSnapshot();
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
        sessionRegistry.put(player.getUniqueId(), new MarketSession(MarketScreen.ITEM_LIST, categoryId, null, snapshot.readOnly()));
    }

    public void openTrade(Player player, String categoryId, String itemId) {
        MarketBrowseSnapshot snapshot = snapshotProvider.loadSnapshot();
        MarketCategorySnapshot category = snapshot.findCategory(categoryId).orElse(null);
        MarketItemSnapshot item = snapshot.findItem(categoryId, itemId).orElse(null);
        if (category == null || item == null) {
            player.sendMessage(colorize(message("messages.unavailable-no-cache")));
            return;
        }

        TradeViewHolder holder = new TradeViewHolder(categoryId, itemId);
        Inventory inventory = createInventory(holder, 3, colorize(item.displayName()));

        inventory.setItem(TRADE_BUY_SLOT, simpleItem(
                Material.BARRIER,
                "&cBuy Unavailable",
                List.of(
                        "&7Trading is disabled in this slice.",
                        "&7This screen is informational only."
                )
        ));
        inventory.setItem(TRADE_ITEM_SLOT, tradePreview(item, snapshot.readOnly()));
        inventory.setItem(TRADE_SELL_SLOT, simpleItem(
                Material.BARRIER,
                "&cSell Unavailable",
                List.of(
                        "&7Trading is disabled in this slice.",
                        "&7This screen is informational only."
                )
        ));
        inventory.setItem(backSlot(3), simpleItem(
                Material.ARROW,
                "&eBack",
                List.of("&7Return to " + category.title() + ".")
        ));

        holder.setInventory(inventory);
        player.openInventory(inventory);
        sessionRegistry.put(player.getUniqueId(), new MarketSession(MarketScreen.TRADE_VIEW, categoryId, itemId, snapshot.readOnly()));
    }

    private Inventory createInventory(MarketInventoryHolder holder, int rows, String title) {
        return Bukkit.createInventory(holder, rows * 9, colorize(title));
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

    private ItemStack tradePreview(MarketItemSnapshot item, boolean readOnly) {
        List<String> lore = new ArrayList<>(colorize(item.description()));
        lore.add("");
        lore.add(colorize("&7Buy estimate: &f" + item.buyEstimate()));
        lore.add(colorize("&7Sell estimate: &f" + item.sellEstimate()));
        lore.add(colorize("&7Variation: &f" + item.variationPercent()));
        lore.add(colorize("&7Stock: &f" + item.stockDisplay()));
        lore.add(colorize("&7Updated: &f" + item.lastUpdated()));
        lore.add("");
        lore.add(colorize(readOnly ? "&6Read-only market preview" : "&aLive market view"));
        lore.add(colorize("&7Reopen &f/market &7to refresh."));
        return simpleItem(item.icon(), item.displayName(), lore);
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
