package io.github.henriquemichelini.craftalism.market.gui;

final class TradeViewHolder extends MarketInventoryHolder {
    private final String categoryId;
    private final String itemId;

    TradeViewHolder(String categoryId, String itemId) {
        this.categoryId = categoryId;
        this.itemId = itemId;
    }

    String categoryId() {
        return categoryId;
    }

    String itemId() {
        return itemId;
    }
}
