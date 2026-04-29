package io.github.henriquemichelini.craftalism.market.api;

public final class StaticMarketBearerTokenProvider implements MarketBearerTokenProvider {
    private final String bearerToken;

    public StaticMarketBearerTokenProvider(String bearerToken) {
        this.bearerToken = bearerToken == null ? "" : bearerToken;
    }

    @Override
    public String getBearerToken() {
        return bearerToken;
    }
}
