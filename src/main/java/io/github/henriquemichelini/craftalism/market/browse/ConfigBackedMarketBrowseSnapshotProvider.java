package io.github.henriquemichelini.craftalism.market.browse;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConfigBackedMarketBrowseSnapshotProvider implements MarketBrowseSnapshotProvider {
    private final FileConfiguration config;

    public ConfigBackedMarketBrowseSnapshotProvider(FileConfiguration config) {
        this.config = config;
    }

    @Override
    public MarketBrowseSnapshot loadSnapshot() {
        ConfigurationSection marketSection = config.getConfigurationSection("market");
        if (marketSection == null) {
            throw new IllegalStateException("Missing market configuration section.");
        }

        boolean readOnly = marketSection.getBoolean("read-only", true);
        ConfigurationSection categoriesSection = marketSection.getConfigurationSection("categories");
        if (categoriesSection == null) {
            return new MarketBrowseSnapshot(List.of(), readOnly);
        }

        List<MarketCategorySnapshot> categories = new ArrayList<>();
        for (String categoryId : categoriesSection.getKeys(false)) {
            ConfigurationSection categorySection = categoriesSection.getConfigurationSection(categoryId);
            if (categorySection == null) {
                continue;
            }

            categories.add(new MarketCategorySnapshot(
                    categoryId,
                    categorySection.getString("title", titleCase(categoryId)),
                    parseMaterial(categorySection.getString("icon", "CHEST"), categoryId),
                    categorySection.getStringList("description"),
                    loadItems(categoryId, categorySection.getConfigurationSection("items"))
            ));
        }

        return new MarketBrowseSnapshot(categories, readOnly);
    }

    private List<MarketItemSnapshot> loadItems(String categoryId, ConfigurationSection itemsSection) {
        if (itemsSection == null) {
            return List.of();
        }

        List<MarketItemSnapshot> items = new ArrayList<>();
        for (String itemId : itemsSection.getKeys(false)) {
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
            if (itemSection == null) {
                continue;
            }

            items.add(new MarketItemSnapshot(
                    itemId,
                    itemSection.getString("display-name", titleCase(itemId)),
                    parseMaterial(itemSection.getString("material", "STONE"), categoryId + "." + itemId),
                    itemSection.getStringList("description"),
                    itemSection.getString("buy-estimate", "Unavailable"),
                    itemSection.getString("sell-estimate", "Unavailable"),
                    itemSection.getString("variation-percent", "n/a"),
                    itemSection.getString("stock-display", "Unknown"),
                    itemSection.getString("last-updated", "Unknown")
            ));
        }

        return items;
    }

    private Material parseMaterial(String materialName, String path) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            throw new IllegalStateException("Invalid material '" + materialName + "' configured at " + path + ".");
        }

        return material;
    }

    private String titleCase(String id) {
        String[] parts = id.split("[_-]");
        List<String> titleParts = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            String lower = part.toLowerCase(Locale.ROOT);
            titleParts.add(Character.toUpperCase(lower.charAt(0)) + lower.substring(1));
        }

        return String.join(" ", titleParts);
    }
}
