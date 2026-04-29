package io.github.henriquemichelini.craftalism.market.api;

import java.net.URI;
import java.util.logging.Logger;

public final class MarketBearerTokenProviderFactory {

    private MarketBearerTokenProviderFactory() {}

    public static MarketBearerTokenProvider create(
        MarketApiConfiguration configuration,
        MarketApiTransport transport,
        Logger logger
    ) {
        if (hasOAuthConfiguration(configuration)) {
            return new OAuth2ClientCredentialsTokenProvider(
                transport,
                URI.create(resolveTokenUrl(configuration)),
                configuration.requestTimeout(),
                configuration.clientId(),
                configuration.clientSecret(),
                configuration.scopes(),
                logger
            );
        }

        if (!configuration.authToken().isBlank()) {
            return new StaticMarketBearerTokenProvider(
                configuration.authToken()
            );
        }

        logger.warning(
            "Market API auth is not configured. Set OAuth client credentials or market-api.auth-token."
        );
        return new StaticMarketBearerTokenProvider("");
    }

    public static boolean hasOAuthConfiguration(
        MarketApiConfiguration configuration
    ) {
        return (
            !configuration.clientId().isBlank() &&
            !configuration.clientSecret().isBlank() &&
            (!configuration.authIssuerUrl().isBlank() ||
                isAbsoluteUrl(configuration.tokenPath()))
        );
    }

    static String resolveTokenUrl(MarketApiConfiguration configuration) {
        if (isAbsoluteUrl(configuration.tokenPath())) {
            return configuration.tokenPath();
        }

        String issuerUrl = configuration.authIssuerUrl();
        if (
            issuerUrl.endsWith("/") && configuration.tokenPath().startsWith("/")
        ) {
            return (
                issuerUrl.substring(0, issuerUrl.length() - 1) +
                configuration.tokenPath()
            );
        }
        if (
            !issuerUrl.endsWith("/") &&
            !configuration.tokenPath().startsWith("/")
        ) {
            return issuerUrl + "/" + configuration.tokenPath();
        }
        return issuerUrl + configuration.tokenPath();
    }

    private static boolean isAbsoluteUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }
}
