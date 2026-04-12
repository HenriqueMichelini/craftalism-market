package io.github.henriquemichelini.craftalism.market.api;

public interface MarketExecuteClient {
    MarketExecuteResult executeTrade(
            String itemId,
            MarketQuoteSide side,
            int quantity,
            String quoteToken,
            String snapshotVersion
    );
}
