package io.github.henriquemichelini.craftalism.market.api;

import java.util.UUID;

public interface MarketQuoteClient {
    MarketQuoteResult requestQuote(UUID playerId, String itemId, MarketQuoteSide side, int quantity, String snapshotVersion);
}
