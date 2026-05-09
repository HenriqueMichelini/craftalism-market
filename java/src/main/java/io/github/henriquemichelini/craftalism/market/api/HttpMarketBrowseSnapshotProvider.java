package io.github.henriquemichelini.craftalism.market.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotProvider;
import io.github.henriquemichelini.craftalism.market.browse.MarketCategorySnapshot;
import io.github.henriquemichelini.craftalism.market.browse.MarketItemSnapshot;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public final class HttpMarketBrowseSnapshotProvider
    implements MarketBrowseSnapshotProvider
{

    private final MarketApiTransport transport;
    private final URI snapshotUri;
    private final Duration requestTimeout;
    private final FileConfiguration config;
    private final MarketBearerTokenProvider bearerTokenProvider;

    public HttpMarketBrowseSnapshotProvider(
        MarketApiTransport transport,
        URI snapshotUri,
        Duration requestTimeout,
        FileConfiguration config,
        MarketBearerTokenProvider bearerTokenProvider
    ) {
        this.transport = transport;
        this.snapshotUri = snapshotUri;
        this.requestTimeout = requestTimeout;
        this.config = config;
        this.bearerTokenProvider = bearerTokenProvider;
    }

    @Override
    public MarketBrowseSnapshot loadSnapshot() {
        try {
            String responseBody = transport.get(
                snapshotUri,
                requestTimeout,
                bearerTokenProvider.getBearerToken()
            );
            return parseSnapshot(responseBody);
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Failed to fetch market snapshot from the API.",
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Interrupted while fetching market snapshot from the API.",
                exception
            );
        }
    }

    MarketBrowseSnapshot parseSnapshot(String responseBody) {
        JsonObject root = JsonParser.parseString(
            responseBody
        ).getAsJsonObject();
        JsonArray categoriesJson = requiredArray(root, "categories");

        List<MarketCategorySnapshot> categories = new ArrayList<>();
        for (JsonElement categoryElement : categoriesJson) {
            JsonObject categoryJson = categoryElement.getAsJsonObject();
            String categoryId = requiredString(categoryJson, "categoryId");
            String displayName = requiredString(categoryJson, "displayName");
            List<MarketItemSnapshot> items = parseItems(
                categoryId,
                requiredArray(categoryJson, "items")
            );

            categories.add(
                new MarketCategorySnapshot(
                    categoryId,
                    displayName,
                    categoryIcon(categoryId, items),
                    descriptionList(
                        "market-display.categories." +
                            categoryId +
                            ".description"
                    ),
                    items
                )
            );
        }

        return new MarketBrowseSnapshot(
            optionalString(root, "snapshotVersion", ""),
            categories,
            false
        );
    }

    private List<MarketItemSnapshot> parseItems(
        String categoryId,
        JsonArray itemsJson
    ) {
        List<MarketItemSnapshot> items = new ArrayList<>();
        for (JsonElement itemElement : itemsJson) {
            JsonObject itemJson = itemElement.getAsJsonObject();
            String itemId = requiredString(itemJson, "itemId");
            String displayName = requiredString(itemJson, "displayName");
            String currency = optionalString(itemJson, "currency", "coins");
            boolean blocked = booleanValue(itemJson, "blocked", false);
            boolean operating = booleanValue(itemJson, "operating", true);

            items.add(
                new MarketItemSnapshot(
                    itemId,
                    displayName,
                    itemIcon(itemJson, itemId),
                    descriptionList(
                        "market-display.items." + itemId + ".description"
                    ),
                    appendCurrency(
                        displayMoneyValue(itemJson.get("buyUnitEstimate")),
                        currency
                    ),
                    appendCurrency(
                        displayMoneyValue(itemJson.get("sellUnitEstimate")),
                        currency
                    ),
                    formatVariation(
                        displayValue(itemJson.get("variationPercent"))
                    ),
                    stockDisplay(itemJson, blocked, operating),
                    blocked,
                    operating,
                    optionalString(itemJson, "lastUpdatedAt", "Unknown")
                )
            );
        }

        return items;
    }

    private Material categoryIcon(
        String categoryId,
        List<MarketItemSnapshot> items
    ) {
        String override = config.getString(
            "market-display.categories." + categoryId + ".icon"
        );
        if (override != null && !override.isBlank()) {
            Material configured = materialOrNull(override);
            if (configured != null) {
                return configured;
            }
        }

        if (!items.isEmpty()) {
            return items.getFirst().icon();
        }

        return Material.CHEST;
    }

    private List<String> descriptionList(String path) {
        return config.getStringList(path);
    }

    private Material itemIcon(JsonObject itemJson, String itemId) {
        Material material = materialOrNull(
            optionalString(itemJson, "iconKey", "")
        );
        if (material != null) {
            return material;
        }

        material = materialOrNull(itemId);
        if (material != null) {
            return material;
        }

        return Material.CHEST;
    }

    private Material materialOrNull(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return null;
        }

        Material direct = Material.matchMaterial(materialName);
        if (direct != null) {
            return direct;
        }

        String normalized = materialName.trim();
        int namespaceIndex = normalized.indexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex < normalized.length() - 1) {
            normalized = normalized.substring(namespaceIndex + 1);
        }
        normalized = normalized
            .toUpperCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');
        return Material.matchMaterial(normalized);
    }

    private JsonArray requiredArray(JsonObject source, String field) {
        if (!source.has(field) || !source.get(field).isJsonArray()) {
            throw new IllegalStateException(
                "Missing array field '" +
                    field +
                    "' in market snapshot response."
            );
        }

        return source.getAsJsonArray(field);
    }

    private String requiredString(JsonObject source, String field) {
        if (!source.has(field) || source.get(field).isJsonNull()) {
            throw new IllegalStateException(
                "Missing field '" + field + "' in market snapshot response."
            );
        }

        return source.get(field).getAsString();
    }

    private String optionalString(
        JsonObject source,
        String field,
        String fallback
    ) {
        if (!source.has(field) || source.get(field).isJsonNull()) {
            return fallback;
        }

        return source.get(field).getAsString();
    }

    private boolean booleanValue(
        JsonObject source,
        String field,
        boolean fallback
    ) {
        if (!source.has(field) || source.get(field).isJsonNull()) {
            return fallback;
        }

        return source.get(field).getAsBoolean();
    }

    private String displayValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "Unavailable";
        }

        if (element.getAsJsonPrimitive().isNumber()) {
            return element
                .getAsBigDecimal()
                .stripTrailingZeros()
                .toPlainString();
        }

        return element.getAsString();
    }

    private String displayMoneyValue(JsonElement element) {
        return MoneyValueFormatter.normalize(element);
    }

    private String appendCurrency(String value, String currency) {
        if ("Unavailable".equals(value)) {
            return value;
        }

        return value + " " + currency;
    }

    private String formatVariation(String variation) {
        if ("Unavailable".equals(variation) || variation.endsWith("%")) {
            return variation;
        }

        return variation + "%";
    }

    private String stockDisplay(
        JsonObject itemJson,
        boolean blocked,
        boolean operating
    ) {
        String stock = displayValue(itemJson.get("currentStock"));

        if (blocked) {
            return "Blocked";
        }

        if (!operating) {
            return "Unavailable";
        }

        return stock;
    }
}
