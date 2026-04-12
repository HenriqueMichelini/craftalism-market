package io.github.henriquemichelini.craftalism.market.browse;

import java.util.List;
import java.util.Optional;

public record MarketBrowseSnapshot(List<MarketCategorySnapshot> categories, boolean readOnly) {
    public MarketBrowseSnapshot {
        categories = List.copyOf(categories);
    }

    public Optional<MarketCategorySnapshot> findCategory(String categoryId) {
        return categories.stream()
                .filter(category -> category.id().equals(categoryId))
                .findFirst();
    }

    public Optional<MarketItemSnapshot> findItem(String categoryId, String itemId) {
        return findCategory(categoryId)
                .flatMap(category -> category.findItem(itemId));
    }
}
