package io.github.henriquemichelini.craftalism.market.browse;

import org.bukkit.Material;

import java.util.List;

public record MarketItemSnapshot(
        String id,
        String displayName,
        Material icon,
        List<String> description,
        String buyEstimate,
        String sellEstimate,
        String variationPercent,
        String stockDisplay,
        String lastUpdated
) {
    public MarketItemSnapshot {
        description = List.copyOf(description);
    }
}
