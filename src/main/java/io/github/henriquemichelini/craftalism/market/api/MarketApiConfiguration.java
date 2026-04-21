package io.github.henriquemichelini.craftalism.market.api;

import java.time.Duration;

public record MarketApiConfiguration(
        String baseUrl,
        String snapshotPath,
        String quotePath,
        String executePath,
        String authToken,
        String authIssuerUrl,
        String tokenPath,
        String clientId,
        String clientSecret,
        String scopes,
        Duration connectTimeout,
        Duration requestTimeout
) {
}
