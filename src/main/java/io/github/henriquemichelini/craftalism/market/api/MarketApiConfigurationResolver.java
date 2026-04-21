package io.github.henriquemichelini.craftalism.market.api;

import java.time.Duration;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;

public final class MarketApiConfigurationResolver {

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:8080";
    private static final String DEFAULT_TOKEN_PATH = "/oauth2/token";

    private final FileConfiguration config;
    private final MarketEnvironment environment;
    private final Logger logger;

    public MarketApiConfigurationResolver(
        FileConfiguration config,
        MarketEnvironment environment,
        Logger logger
    ) {
        this.config = config;
        this.environment = environment;
        this.logger = logger;
    }

    public MarketApiConfiguration resolve() {
        return new MarketApiConfiguration(
            normalizeBaseUrl(
                firstNonBlank(
                    environment.get("CRAFTALISM_API_URL"),
                    config.getString("market-api.base-url", DEFAULT_BASE_URL)
                )
            ),
            normalizePath(
                config.getString(
                    "market-api.snapshot-path",
                    "/market/snapshot"
                ),
                "/market/snapshot"
            ),
            normalizePath(
                config.getString("market-api.quote-path", "/market/quote"),
                "/market/quote"
            ),
            normalizePath(
                config.getString("market-api.execute-path", "/market/execute"),
                "/market/execute"
            ),
            blankToEmpty(config.getString("market-api.auth-token", "")),
            normalizeOptionalBaseUrl(
                firstNonBlank(
                    environment.get("AUTH_ISSUER_URI"),
                    config.getString("market-api.auth.issuer-url", "")
                )
            ),
            normalizeTokenPath(
                firstNonBlank(
                    environment.get("AUTH_TOKEN_PATH"),
                    config.getString(
                        "market-api.auth.token-path",
                        DEFAULT_TOKEN_PATH
                    )
                )
            ),
            blankToEmpty(
                firstNonBlank(
                    environment.get("MINECRAFT_CLIENT_ID"),
                    config.getString("market-api.auth.client-id", "")
                )
            ),
            blankToEmpty(
                firstNonBlank(
                    environment.get("MINECRAFT_CLIENT_SECRET"),
                    environment.get("CRAFTALISM_API_KEY"),
                    config.getString("market-api.auth.client-secret", ""),
                    config.getString("market-api.auth.api-key", "")
                )
            ),
            blankToEmpty(
                firstNonBlank(
                    environment.get("MINECRAFT_CLIENT_SCOPES"),
                    config.getString("market-api.auth.scopes", "")
                )
            ),
            Duration.ofMillis(
                normalizeTimeout(
                    config.getLong("market-api.connect-timeout-ms", 3000L),
                    3000L,
                    "market-api.connect-timeout-ms"
                )
            ),
            Duration.ofMillis(
                normalizeTimeout(
                    config.getLong("market-api.request-timeout-ms", 5000L),
                    5000L,
                    "market-api.request-timeout-ms"
                )
            )
        );
    }

    private long normalizeTimeout(long value, long fallback, String key) {
        if (value > 0L) {
            return value;
        }

        logger.warning(
            "Invalid timeout for " +
                key +
                " (" +
                value +
                "), falling back to " +
                fallback +
                "ms."
        );
        return fallback;
    }

    private String normalizeBaseUrl(String value) {
        String trimmed = blankToEmpty(value);
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed.endsWith("/")
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
        }

        logger.warning(
            "Invalid market API base URL '" +
                value +
                "', falling back to " +
                DEFAULT_BASE_URL
        );
        return DEFAULT_BASE_URL;
    }

    private String normalizeOptionalBaseUrl(String value) {
        String trimmed = blankToEmpty(value);
        if (trimmed.isBlank()) {
            return "";
        }

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed.endsWith("/")
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
        }

        logger.warning("Invalid auth issuer URL '" + value + "', ignoring it.");
        return "";
    }

    private String normalizePath(String value, String fallback) {
        String trimmed = blankToEmpty(value);
        if (trimmed.isBlank()) {
            return fallback;
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private String normalizeTokenPath(String value) {
        String trimmed = blankToEmpty(value);
        if (trimmed.isBlank()) {
            return DEFAULT_TOKEN_PATH;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
