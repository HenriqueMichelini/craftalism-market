package io.github.henriquemichelini.craftalism.market.gui;

import io.github.henriquemichelini.craftalism.market.browse.MarketCategorySnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketItemSnapshot;
import io.github.henriquemichelini.craftalism.market.session.MarketQuoteStatus;
import io.github.henriquemichelini.craftalism.market.session.MarketSession;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class MarketGuiRenderer {

    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER =
        LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION_SERIALIZER =
        LegacyComponentSerializer.legacySection();

    private final FileConfiguration config;

    MarketGuiRenderer(FileConfiguration config) {
        this.config = config;
    }

    ItemStack categoryIcon(
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

    ItemStack itemIcon(MarketItemSnapshot item, boolean readOnly) {
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

    ItemStack tradePreview(
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

    ItemStack quoteActionButton(
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
                ? Material.SLIME_BLOCK
                : Material.HONEY_BLOCK;
            name = "&a" + action + " Now";
        }

        return simpleItem(material, name, lore);
    }

    ItemStack quantityButton(
        Material material,
        String name,
        int amount,
        boolean enchanted
    ) {
        return simpleItem(
            material,
            name,
            List.of("&7Adjust trade quantity."),
            amount,
            enchanted
        );
    }

    ItemStack simpleItem(
        Material material,
        String name,
        List<String> lore
    ) {
        return simpleItem(material, name, lore, 1, false);
    }

    String title(String path) {
        return config.getString(path, "Market");
    }

    String message(String path) {
        return config.getString(path, "&cMarket data is unavailable.");
    }

    String rejectionMessage(String code) {
        return config.getString(
            "messages.rejections." + code,
            "&cTrade request rejected."
        );
    }

    String colorize(String text) {
        return SECTION_SERIALIZER.serialize(
            AMPERSAND_SERIALIZER.deserialize(text)
        );
    }

    void sendMessage(Player player, String text) {
        player.sendMessage(render(text));
    }

    Component render(String text) {
        return AMPERSAND_SERIALIZER.deserialize(text);
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
        itemMeta.lore(lore.stream().map(this::render).toList());
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (enchanted) {
            itemMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        itemStack.setItemMeta(itemMeta);
        return itemStack;
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

    private List<String> colorize(List<String> lines) {
        return lines.stream().map(this::colorize).toList();
    }
}
