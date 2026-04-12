package io.github.henriquemichelini.craftalism.market.api;

public record MarketExecuteResult(
        int executedQuantity,
        String totalPrice,
        String unitPrice,
        String currency,
        String snapshotVersion
) {
}
