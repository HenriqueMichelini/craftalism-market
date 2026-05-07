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
        boolean blocked,
        boolean operating,
        String lastUpdated
) {
    public MarketItemSnapshot(
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
        this(
            id,
            displayName,
            icon,
            description,
            buyEstimate,
            sellEstimate,
            variationPercent,
            stockDisplay,
            false,
            true,
            lastUpdated
        );
    }

    public MarketItemSnapshot {
        description = List.copyOf(description);
    }

    public boolean tradeAvailable() {
        return !blocked && operating;
    }
}
