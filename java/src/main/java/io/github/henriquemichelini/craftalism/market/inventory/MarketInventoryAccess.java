package io.github.henriquemichelini.craftalism.market.inventory;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public interface MarketInventoryAccess {
    int count(Player player, Material material);

    int remove(Player player, Material material, int quantity);

    int addOrDrop(Player player, Material material, int quantity);
}
