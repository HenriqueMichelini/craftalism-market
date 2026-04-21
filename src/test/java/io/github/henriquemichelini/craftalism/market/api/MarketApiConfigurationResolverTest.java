package io.github.henriquemichelini.craftalism.market.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MarketApiConfigurationResolverTest {

    @Test
    void resolvesMarketApiBaseUrlFromEnvironment() {
        MarketApiConfiguration configuration = new MarketApiConfigurationResolver(
            new YamlConfiguration(),
            new MapMarketEnvironment(
                Map.of("MARKET_API_BASE_URL", "http://craftalism-api:8080/")
            ),
            Logger.getLogger(MarketApiConfigurationResolverTest.class.getName())
        ).resolve();

        assertEquals("http://craftalism-api:8080", configuration.baseUrl());
    }

    @Test
    void prefersMarketSpecificBaseUrlOverCraftalismApiUrl() {
        MarketApiConfiguration configuration = new MarketApiConfigurationResolver(
            new YamlConfiguration(),
            new MapMarketEnvironment(
                Map.of(
                    "MARKET_API_BASE_URL",
                    "http://market-api:8080",
                    "CRAFTALISM_API_URL",
                    "http://craftalism-api:8080"
                )
            ),
            Logger.getLogger(MarketApiConfigurationResolverTest.class.getName())
        ).resolve();

        assertEquals("http://market-api:8080", configuration.baseUrl());
    }

    private record MapMarketEnvironment(Map<String, String> values)
        implements MarketEnvironment
    {
        @Override
        public String get(String key) {
            return values.get(key);
        }
    }
}
