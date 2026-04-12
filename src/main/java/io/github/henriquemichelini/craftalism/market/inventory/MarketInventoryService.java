package io.github.henriquemichelini.craftalism.market.inventory;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class MarketInventoryService {
    public int count(Player player, Material material) {
        return count(player.getInventory().getContents(), material);
    }

    public int remove(Player player, Material material, int quantity) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        int removed = remove(contents, material, quantity);
        for (int slot = 0; slot < contents.length && slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, contents[slot]);
        }

        return removed;
    }

    int count(ItemStack[] contents, Material material) {
        int total = 0;
        for (ItemStack itemStack : contents) {
            if (itemStack == null || itemStack.getType() != material) {
                continue;
            }

            total += itemStack.getAmount();
        }

        return total;
    }

    int remove(ItemStack[] contents, Material material, int quantity) {
        int remaining = quantity;

        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack itemStack = contents[slot];
            if (itemStack == null || itemStack.getType() != material) {
                continue;
            }

            int removeAmount = Math.min(itemStack.getAmount(), remaining);
            int newAmount = itemStack.getAmount() - removeAmount;
            remaining -= removeAmount;

            if (newAmount == 0) {
                contents[slot] = null;
            } else {
                itemStack.setAmount(newAmount);
                contents[slot] = itemStack;
            }
        }

        return quantity - remaining;
    }
}
