package io.github.henriquemichelini.craftalism.market.api;

public record MarketQuoteResult(
        MarketQuoteSide side,
        int quantity,
        String totalPrice,
        String unitPrice,
        String currency,
        String quoteToken,
        String snapshotVersion
) {
}
