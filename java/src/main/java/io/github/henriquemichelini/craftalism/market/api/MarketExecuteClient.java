package io.github.henriquemichelini.craftalism.market.api;

import java.util.UUID;

public interface MarketExecuteClient {
    MarketExecuteResult executeTrade(
            UUID playerId,
            String itemId,
            MarketQuoteSide side,
            int quantity,
            String quoteToken,
            String snapshotVersion
    );
}
