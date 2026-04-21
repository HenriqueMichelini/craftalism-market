package io.github.henriquemichelini.craftalism.market.api;

public final class SystemMarketEnvironment implements MarketEnvironment {
    @Override
    public String get(String key) {
        return System.getenv(key);
    }
}
