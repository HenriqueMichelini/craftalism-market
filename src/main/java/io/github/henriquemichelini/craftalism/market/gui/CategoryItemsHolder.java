package io.github.henriquemichelini.craftalism.market.gui;

final class CategoryItemsHolder extends MarketInventoryHolder {
    private final String categoryId;

    CategoryItemsHolder(String categoryId) {
        this.categoryId = categoryId;
    }

    String categoryId() {
        return categoryId;
    }
}
