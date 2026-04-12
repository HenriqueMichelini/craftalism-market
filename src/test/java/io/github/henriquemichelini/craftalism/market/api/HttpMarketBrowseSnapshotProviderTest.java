package io.github.henriquemichelini.craftalism.market.api;

import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshot;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpMarketBrowseSnapshotProviderTest {
    @Test
    void parsesSnapshotResponseIntoBrowseModels() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("market-display.categories.farming.description", java.util.List.of("&7Browse staple crops."));
        configuration.set("market-display.items.wheat.description", java.util.List.of("&7A common crop."));

        HttpMarketBrowseSnapshotProvider provider = new HttpMarketBrowseSnapshotProvider(
                (uri, timeout) -> """
                        {
                          "snapshotVersion": "opaque-version-token",
                          "generatedAt": "2026-04-12T18:30:00Z",
                          "categories": [
                            {
                              "categoryId": "farming",
                              "displayName": "Farming",
                              "items": [
                                {
                                  "itemId": "wheat",
                                  "displayName": "Wheat",
                                  "iconKey": "WHEAT",
                                  "buyUnitEstimate": 4.8,
                                  "sellUnitEstimate": 4.1,
                                  "currency": "coins",
                                  "currentStock": 1820,
                                  "variationPercent": 2.3,
                                  "blocked": false,
                                  "operating": true,
                                  "lastUpdatedAt": "2026-04-12T18:29:42Z"
                                }
                              ]
                            }
                          ]
                        }
                        """,
                URI.create("http://localhost:8080/market/snapshot"),
                Duration.ofSeconds(5),
                configuration
        );

        MarketBrowseSnapshot snapshot = provider.loadSnapshot();

        assertFalse(snapshot.readOnly());
        assertEquals(1, snapshot.categories().size());
        assertEquals(Material.WHEAT, snapshot.categories().getFirst().icon());
        assertEquals("4.8 coins", snapshot.categories().getFirst().items().getFirst().buyEstimate());
        assertEquals("Stock: 1820", snapshot.categories().getFirst().items().getFirst().stockDisplay());
    }

    @Test
    void rejectsMissingCategoriesArray() {
        HttpMarketBrowseSnapshotProvider provider = new HttpMarketBrowseSnapshotProvider(
                (uri, timeout) -> "{\"snapshotVersion\":\"x\"}",
                URI.create("http://localhost:8080/market/snapshot"),
                Duration.ofSeconds(5),
                new YamlConfiguration()
        );

        IllegalStateException error = assertThrows(IllegalStateException.class, provider::loadSnapshot);
        assertEquals("Missing array field 'categories' in market snapshot response.", error.getMessage());
    }
}
