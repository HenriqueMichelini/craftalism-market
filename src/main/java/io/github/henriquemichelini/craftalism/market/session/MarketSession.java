package io.github.henriquemichelini.craftalism.market.session;

public record MarketSession(
        MarketScreen screen,
        String selectedCategoryId,
        String selectedItemId,
        boolean readOnly
) {
}
