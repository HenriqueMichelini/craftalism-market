package io.github.henriquemichelini.craftalism.market.api;

public interface MarketQuoteClient {
    MarketQuoteResult requestQuote(String itemId, MarketQuoteSide side, int quantity, String snapshotVersion);
}
