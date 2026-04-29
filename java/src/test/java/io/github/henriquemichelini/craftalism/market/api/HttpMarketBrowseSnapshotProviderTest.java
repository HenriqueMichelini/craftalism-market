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
        java.util.concurrent.atomic.AtomicReference<String> authHeader = new java.util.concurrent.atomic.AtomicReference<>();

        HttpMarketBrowseSnapshotProvider provider = new HttpMarketBrowseSnapshotProvider(
                new MarketApiTransport() {
                    @Override
                    public String get(java.net.URI uri, java.time.Duration timeout, String bearerToken) {
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
                        """;
                    }

                    @Override
                    public String postJson(java.net.URI uri, String body, java.time.Duration timeout, String bearerToken) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String postForm(java.net.URI uri, String body, java.time.Duration timeout, String authorizationHeader) {
                        throw new UnsupportedOperationException();
                    }
                },
                URI.create("http://localhost:8080/market/snapshot"),
                Duration.ofSeconds(5),
                configuration,
                () -> "secret-token"
        );

        MarketBrowseSnapshot snapshot = provider.loadSnapshot();

        assertFalse(snapshot.readOnly());
        assertEquals("opaque-version-token", snapshot.snapshotVersion());
        assertEquals(1, snapshot.categories().size());
        assertEquals(Material.WHEAT, snapshot.categories().getFirst().icon());
        assertEquals("4.8 coins", snapshot.categories().getFirst().items().getFirst().buyEstimate());
        assertEquals("Stock: 1820", snapshot.categories().getFirst().items().getFirst().stockDisplay());
        assertEquals("secret-token", authHeader.get());
    }

    @Test
    void preservesIntegerSnapshotMoneyDisplayUnits() {
        HttpMarketBrowseSnapshotProvider provider = new HttpMarketBrowseSnapshotProvider(
                new MarketApiTransport() {
                    @Override
                    public String get(java.net.URI uri, java.time.Duration timeout, String bearerToken) {
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
                    public String postJson(java.net.URI uri, String body, java.time.Duration timeout, String bearerToken) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String postForm(java.net.URI uri, String body, java.time.Duration timeout, String authorizationHeader) {
                        throw new UnsupportedOperationException();
                    }
                },
                URI.create("http://localhost:8080/market/snapshot"),
                Duration.ofSeconds(5),
                new YamlConfiguration(),
                () -> "secret-token"
        );

        MarketBrowseSnapshot snapshot = provider.loadSnapshot();

        assertEquals("48000 coins", snapshot.categories().getFirst().items().getFirst().buyEstimate());
        assertEquals("41000 coins", snapshot.categories().getFirst().items().getFirst().sellEstimate());
    }

    @Test
    void rejectsMissingCategoriesArray() {
        HttpMarketBrowseSnapshotProvider provider = new HttpMarketBrowseSnapshotProvider(
                new MarketApiTransport() {
                    @Override
                    public String get(java.net.URI uri, java.time.Duration timeout, String bearerToken) {
                        return "{\"snapshotVersion\":\"x\"}";
                    }

                    @Override
                    public String postJson(java.net.URI uri, String body, java.time.Duration timeout, String bearerToken) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String postForm(java.net.URI uri, String body, java.time.Duration timeout, String authorizationHeader) {
                        throw new UnsupportedOperationException();
                    }
                },
                URI.create("http://localhost:8080/market/snapshot"),
                Duration.ofSeconds(5),
                new YamlConfiguration(),
                () -> "secret-token"
        );

        IllegalStateException error = assertThrows(IllegalStateException.class, provider::loadSnapshot);
        assertEquals("Missing array field 'categories' in market snapshot response.", error.getMessage());
    }
}
