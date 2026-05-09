package io.github.henriquemichelini.craftalism.market.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshot;
import java.net.URI;
import java.time.Duration;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class HttpMarketBrowseSnapshotProviderTest {

    @Test
    void parsesSnapshotResponseIntoBrowseModels() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set(
            "market-display.categories.farming.description",
            java.util.List.of("&7Browse staple crops.")
        );
        configuration.set(
            "market-display.items.wheat.description",
            java.util.List.of("&7A common crop.")
        );
        java.util.concurrent.atomic.AtomicReference<String> authHeader =
            new java.util.concurrent.atomic.AtomicReference<>();

        HttpMarketBrowseSnapshotProvider provider =
            new HttpMarketBrowseSnapshotProvider(new MarketApiTransport() {
                @Override
                public String get(
                    java.net.URI uri,
                    java.time.Duration timeout,
                    String bearerToken
                ) {
                    authHeader.set(bearerToken);
                    return """
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
                              "buyUnitEstimate": 48000,
                              "sellUnitEstimate": 41000,
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
                    """;
                }

                @Override
                public String postJson(
                    java.net.URI uri,
                    String body,
                    java.time.Duration timeout,
                    String bearerToken
                ) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String postForm(
                    java.net.URI uri,
                    String body,
                    java.time.Duration timeout,
                    String authorizationHeader
                ) {
                    throw new UnsupportedOperationException();
                }
            }, URI.create("http://localhost:8080/market/snapshot"), Duration.ofSeconds(5), configuration, () -> "secret-token");

        MarketBrowseSnapshot snapshot = provider.loadSnapshot();

        assertFalse(snapshot.readOnly());
        assertEquals("opaque-version-token", snapshot.snapshotVersion());
        assertEquals(1, snapshot.categories().size());
        assertEquals(Material.WHEAT, snapshot.categories().getFirst().icon());
        assertEquals(
            "4.8 coins",
            snapshot.categories().getFirst().items().getFirst().buyEstimate()
        );
        assertEquals(
            "1820",
            snapshot.categories().getFirst().items().getFirst().stockDisplay()
        );
        assertFalse(snapshot.categories().getFirst().items().getFirst().blocked());
        assertTrue(snapshot.categories().getFirst().items().getFirst().operating());
        assertEquals("secret-token", authHeader.get());
    }

    @Test
    void parsesOperatingPressureLadderSnapshotWithoutCurrentStockAsDisplayOnlyUnavailable() {
        HttpMarketBrowseSnapshotProvider provider =
            new HttpMarketBrowseSnapshotProvider(new MarketApiTransport() {
                @Override
                public String get(
                    java.net.URI uri,
                    java.time.Duration timeout,
                    String bearerToken
                ) {
                    return """
                    {
                      "snapshotVersion": "pressure-ladder",
                      "categories": [
                        {
                          "categoryId": "farming",
                          "displayName": "Farming",
                          "items": [
                            {
                              "itemId": "wheat",
                              "displayName": "Wheat",
                              "iconKey": "WHEAT",
                              "buyUnitEstimate": 48000,
                              "sellUnitEstimate": 41000,
                              "variationPercent": 2.3,
                              "blocked": false,
                              "operating": true
                            }
                          ]
                        }
                      ]
                    }
                    """;
                }

                @Override
                public String postJson(
                    java.net.URI uri,
                    String body,
                    java.time.Duration timeout,
                    String bearerToken
                ) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String postForm(
                    java.net.URI uri,
                    String body,
                    java.time.Duration timeout,
                    String authorizationHeader
                ) {
                    throw new UnsupportedOperationException();
                }
            }, URI.create("http://localhost:8080/market/snapshot"), Duration.ofSeconds(5), new YamlConfiguration(), () -> "secret-token");

        MarketBrowseSnapshot snapshot = provider.loadSnapshot();

        assertEquals(
            "Unavailable",
            snapshot.categories().getFirst().items().getFirst().stockDisplay()
        );
        assertFalse(snapshot.categories().getFirst().items().getFirst().blocked());
        assertTrue(snapshot.categories().getFirst().items().getFirst().operating());
        assertTrue(snapshot.categories().getFirst().items().getFirst().tradeAvailable());
    }

    @Test
    void convertsIntegerSnapshotMoneyFromEconomyRawUnits() {
        HttpMarketBrowseSnapshotProvider provider =
            new HttpMarketBrowseSnapshotProvider(new MarketApiTransport() {
                @Override
                public String get(
                    java.net.URI uri,
                    java.time.Duration timeout,
                    String bearerToken
                ) {
                    return """
                    {
                      "snapshotVersion": "fixed-point",
                      "categories": [
                        {
                          "categoryId": "farming",
                          "displayName": "Farming",
                          "items": [
                            {
                              "itemId": "wheat",
                              "displayName": "Wheat",
                              "iconKey": "WHEAT",
                              "buyUnitEstimate": 48000,
                              "sellUnitEstimate": 41000,
                              "currentStock": 1820,
                              "variationPercent": 2.3,
                              "blocked": false,
                              "operating": true
                            }
                          ]
                        }
                      ]
                    }
                    """;
                }

                @Override
                public String postJson(
                    java.net.URI uri,
                    String body,
                    java.time.Duration timeout,
                    String bearerToken
                ) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String postForm(
                    java.net.URI uri,
                    String body,
                    java.time.Duration timeout,
                    String authorizationHeader
                ) {
                    throw new UnsupportedOperationException();
                }
            }, URI.create("http://localhost:8080/market/snapshot"), Duration.ofSeconds(5), new YamlConfiguration(), () -> "secret-token");

        MarketBrowseSnapshot snapshot = provider.loadSnapshot();

        assertEquals(
            "4.8 coins",
            snapshot.categories().getFirst().items().getFirst().buyEstimate()
        );
        assertEquals(
            "4.1 coins",
            snapshot.categories().getFirst().items().getFirst().sellEstimate()
        );
    }

    @Test
    void parsesExpandedBackendDefaultCatalogWithoutStaticItemMetadata() {
        HttpMarketBrowseSnapshotProvider provider = providerReturning("""
            {
              "snapshotVersion": "expanded-catalog",
              "categories": [
                {
                  "categoryId": "farming",
                  "displayName": "Farming",
                  "items": [
                    { "itemId": "carrot", "displayName": "Carrot", "iconKey": "CARROT", "buyUnitEstimate": 10000, "sellUnitEstimate": 8000, "variationPercent": -1.4, "blocked": false, "operating": true },
                    { "itemId": "potato", "displayName": "Potato", "iconKey": "POTATO", "buyUnitEstimate": 12000, "sellUnitEstimate": 9600, "variationPercent": 0.8, "blocked": false, "operating": true },
                    { "itemId": "sugar_cane", "displayName": "Sugar Cane", "iconKey": "SUGAR_CANE", "buyUnitEstimate": 18000, "sellUnitEstimate": 14400, "variationPercent": 1.7, "blocked": false, "operating": true },
                    { "itemId": "wheat", "displayName": "Wheat", "iconKey": "WHEAT", "buyUnitEstimate": 50000, "sellUnitEstimate": 40000, "variationPercent": 2.3, "blocked": false, "operating": true }
                  ]
                },
                {
                  "categoryId": "forestry",
                  "displayName": "Forestry",
                  "items": [
                    { "itemId": "oak_log", "displayName": "Oak Log", "iconKey": "OAK_LOG", "buyUnitEstimate": 25000, "sellUnitEstimate": 20000, "variationPercent": -0.2, "blocked": false, "operating": true },
                    { "itemId": "spruce_log", "displayName": "Spruce Log", "iconKey": "SPRUCE_LOG", "buyUnitEstimate": 28000, "sellUnitEstimate": 22400, "variationPercent": 0.4, "blocked": false, "operating": true }
                  ]
                },
                {
                  "categoryId": "mining",
                  "displayName": "Mining",
                  "items": [
                    { "itemId": "coal", "displayName": "Coal", "iconKey": "COAL", "buyUnitEstimate": 35000, "sellUnitEstimate": 28000, "variationPercent": 0.9, "blocked": false, "operating": true },
                    { "itemId": "cobblestone", "displayName": "Cobblestone", "iconKey": "COBBLESTONE", "buyUnitEstimate": 4000, "sellUnitEstimate": 3200, "variationPercent": -0.8, "blocked": false, "operating": true },
                    { "itemId": "diamond", "displayName": "Diamond", "iconKey": "DIAMOND", "buyUnitEstimate": 900000, "sellUnitEstimate": 720000, "variationPercent": 4.5, "blocked": false, "operating": true },
                    { "itemId": "gold_ingot", "displayName": "Gold Ingot", "iconKey": "GOLD_INGOT", "buyUnitEstimate": 220000, "sellUnitEstimate": 176000, "variationPercent": 2.8, "blocked": false, "operating": true },
                    { "itemId": "iron_ingot", "displayName": "Iron Ingot", "iconKey": "IRON_INGOT", "buyUnitEstimate": 140000, "sellUnitEstimate": 112000, "variationPercent": 1.1, "blocked": false, "operating": true }
                  ]
                }
              ]
            }
            """);

        MarketBrowseSnapshot snapshot = provider.loadSnapshot();

        assertEquals(3, snapshot.categories().size());
        assertEquals(11, snapshot.categories().stream().mapToInt(category -> category.items().size()).sum());
        assertTrue(snapshot.findItem("farming", "potato").isPresent());
        assertTrue(snapshot.findItem("farming", "sugar_cane").isPresent());
        assertTrue(snapshot.findItem("forestry", "oak_log").isPresent());
        assertTrue(snapshot.findItem("forestry", "spruce_log").isPresent());
        assertTrue(snapshot.findItem("mining", "cobblestone").isPresent());
        assertTrue(snapshot.findItem("mining", "coal").isPresent());
        assertTrue(snapshot.findItem("mining", "gold_ingot").isPresent());
        assertTrue(snapshot.findItem("mining", "diamond").isPresent());
        assertEquals(Material.OAK_LOG, snapshot.findCategory("forestry").orElseThrow().icon());
    }

    @Test
    void keepsSnapshotItemVisibleWhenIconKeyIsMissingOrNeedsNormalization() {
        HttpMarketBrowseSnapshotProvider provider = providerReturning("""
            {
              "snapshotVersion": "normalized-icons",
              "categories": [
                {
                  "categoryId": "farming",
                  "displayName": "Farming",
                  "items": [
                    {
                      "itemId": "sugar_cane",
                      "displayName": "Sugar Cane",
                      "iconKey": "minecraft:sugar-cane",
                      "buyUnitEstimate": 18000,
                      "sellUnitEstimate": 14400,
                      "variationPercent": 1.7,
                      "blocked": false,
                      "operating": true
                    },
                    {
                      "itemId": "potato",
                      "displayName": "Potato",
                      "buyUnitEstimate": 12000,
                      "sellUnitEstimate": 9600,
                      "variationPercent": 0.8,
                      "blocked": false,
                      "operating": true
                    },
                    {
                      "itemId": "custom_backend_item",
                      "displayName": "Custom Backend Item",
                      "iconKey": "NOT_A_BUKKIT_MATERIAL",
                      "buyUnitEstimate": 12000,
                      "sellUnitEstimate": 9600,
                      "variationPercent": 0.8,
                      "blocked": false,
                      "operating": true
                    }
                  ]
                }
              ]
            }
            """);

        MarketBrowseSnapshot snapshot = provider.loadSnapshot();

        assertEquals(Material.SUGAR_CANE, snapshot.findItem("farming", "sugar_cane").orElseThrow().icon());
        assertEquals(Material.POTATO, snapshot.findItem("farming", "potato").orElseThrow().icon());
        assertEquals(Material.CHEST, snapshot.findItem("farming", "custom_backend_item").orElseThrow().icon());
    }

    @Test
    void rejectsMissingCategoriesArray() {
        HttpMarketBrowseSnapshotProvider provider =
            new HttpMarketBrowseSnapshotProvider(new MarketApiTransport() {
                @Override
                public String get(
                    java.net.URI uri,
                    java.time.Duration timeout,
                    String bearerToken
                ) {
                    return "{\"snapshotVersion\":\"x\"}";
                }

                @Override
                public String postJson(
                    java.net.URI uri,
                    String body,
                    java.time.Duration timeout,
                    String bearerToken
                ) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String postForm(
                    java.net.URI uri,
                    String body,
                    java.time.Duration timeout,
                    String authorizationHeader
                ) {
                    throw new UnsupportedOperationException();
                }
            }, URI.create("http://localhost:8080/market/snapshot"), Duration.ofSeconds(5), new YamlConfiguration(), () -> "secret-token");

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            provider::loadSnapshot
        );
        assertEquals(
            "Missing array field 'categories' in market snapshot response.",
            error.getMessage()
        );
    }

    private HttpMarketBrowseSnapshotProvider providerReturning(String responseBody) {
        return new HttpMarketBrowseSnapshotProvider(new MarketApiTransport() {
            @Override
            public String get(
                java.net.URI uri,
                java.time.Duration timeout,
                String bearerToken
            ) {
                return responseBody;
            }

            @Override
            public String postJson(
                java.net.URI uri,
                String body,
                java.time.Duration timeout,
                String bearerToken
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String postForm(
                java.net.URI uri,
                String body,
                java.time.Duration timeout,
                String authorizationHeader
            ) {
                throw new UnsupportedOperationException();
            }
        }, URI.create("http://localhost:8080/market/snapshot"), Duration.ofSeconds(5), new YamlConfiguration(), () -> "secret-token");
    }
}
