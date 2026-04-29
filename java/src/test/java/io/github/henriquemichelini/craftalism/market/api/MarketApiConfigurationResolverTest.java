package io.github.henriquemichelini.craftalism.market.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
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

    @Test
    void usesApiMarketRouteDefaults() {
        MarketApiConfiguration configuration = new MarketApiConfigurationResolver(
            new YamlConfiguration(),
            new MapMarketEnvironment(Map.of()),
            Logger.getLogger(MarketApiConfigurationResolverTest.class.getName())
        ).resolve();

        assertEquals("/api/market/snapshot", configuration.snapshotPath());
        assertEquals("/api/market/quotes", configuration.quotePath());
        assertEquals("/api/market/execute", configuration.executePath());
        assertEquals("api:write", configuration.scopes());
    }

    @Test
    void defaultsOAuthScopeToApiWrite() {
        MarketApiConfiguration configuration = new MarketApiConfigurationResolver(
            new YamlConfiguration(),
            new MapMarketEnvironment(Map.of()),
            Logger.getLogger(MarketApiConfigurationResolverTest.class.getName())
        ).resolve();

        assertEquals("api:write", configuration.scopes());
    }

    @Test
    void keepsExplicitOAuthScopeOverride() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("market-api.auth.scopes", "market:trade");

        MarketApiConfiguration configuration = new MarketApiConfigurationResolver(
            config,
            new MapMarketEnvironment(Map.of()),
            Logger.getLogger(MarketApiConfigurationResolverTest.class.getName())
        ).resolve();

        assertEquals("market:trade", configuration.scopes());
    }

    @Test
    void resolvesOAuthInputsFromEconomyCompatibleEnvironment() {
        MarketApiConfiguration configuration = new MarketApiConfigurationResolver(
            new YamlConfiguration(),
            new MapMarketEnvironment(
                Map.of(
                    "CRAFTALISM_API_URL",
                    "http://craftalism-api:8080/",
                    "AUTH_ISSUER_URI",
                    "http://craftalism-auth:9000/",
                    "AUTH_TOKEN_PATH",
                    "oauth2/token",
                    "MINECRAFT_CLIENT_ID",
                    "minecraft-server",
                    "MINECRAFT_CLIENT_SECRET",
                    "client-secret",
                    "MINECRAFT_CLIENT_SCOPES",
                    "api:write"
                )
            ),
            Logger.getLogger(MarketApiConfigurationResolverTest.class.getName())
        ).resolve();

        assertEquals("http://craftalism-api:8080", configuration.baseUrl());
        assertEquals(
            "http://craftalism-auth:9000",
            configuration.authIssuerUrl()
        );
        assertEquals("/oauth2/token", configuration.tokenPath());
        assertEquals("minecraft-server", configuration.clientId());
        assertEquals("client-secret", configuration.clientSecret());
        assertEquals("api:write", configuration.scopes());
    }

    @Test
    void resolvesApiKeyAsClientSecretFallback() {
        MarketApiConfiguration configuration = new MarketApiConfigurationResolver(
            new YamlConfiguration(),
            new MapMarketEnvironment(
                Map.of(
                    "AUTH_ISSUER_URI",
                    "http://craftalism-auth:9000",
                    "MINECRAFT_CLIENT_ID",
                    "minecraft-server",
                    "CRAFTALISM_API_KEY",
                    "api-key-secret"
                )
            ),
            Logger.getLogger(MarketApiConfigurationResolverTest.class.getName())
        ).resolve();

        assertEquals("api-key-secret", configuration.clientSecret());
    }

    @Test
    void resolvesOAuthProviderWhenCredentialsAndIssuerExist() {
        MarketApiConfiguration configuration = new MarketApiConfiguration(
            "http://craftalism-api:8080",
            "/api/market/snapshot",
            "/api/market/quotes",
            "/api/market/execute",
            "",
            "http://craftalism-auth:9000",
            "/oauth2/token",
            "minecraft-server",
            "client-secret",
            "api:write",
            Duration.ofSeconds(3),
            Duration.ofSeconds(5)
        );

        assertTrue(MarketBearerTokenProviderFactory.hasOAuthConfiguration(configuration));
        assertEquals(
            "http://craftalism-auth:9000/oauth2/token",
            MarketBearerTokenProviderFactory.resolveTokenUrl(configuration)
        );
    }

    @Test
    void doesNotUseOAuthWithoutIssuerOrAbsoluteTokenUrl() {
        MarketApiConfiguration configuration = new MarketApiConfiguration(
            "http://craftalism-api:8080",
            "/api/market/snapshot",
            "/api/market/quotes",
            "/api/market/execute",
            "static-token",
            "",
            "/oauth2/token",
            "minecraft-server",
            "client-secret",
            "",
            Duration.ofSeconds(3),
            Duration.ofSeconds(5)
        );

        assertFalse(MarketBearerTokenProviderFactory.hasOAuthConfiguration(configuration));
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
