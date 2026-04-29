package io.github.henriquemichelini.craftalism.market.browse;

import org.bukkit.Material;

import java.util.List;
import java.util.Optional;

public record MarketCategorySnapshot(
        String id,
        String title,
        Material icon,
        List<String> description,
        List<MarketItemSnapshot> items
) {
    public MarketCategorySnapshot {
        description = List.copyOf(description);
        items = List.copyOf(items);
    }

    public Optional<MarketItemSnapshot> findItem(String itemId) {
        return items.stream()
                .filter(item -> item.id().equals(itemId))
                .findFirst();
    }
}
