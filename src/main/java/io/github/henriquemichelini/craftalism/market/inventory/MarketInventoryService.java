package io.github.henriquemichelini.craftalism.market.inventory;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

public final class MarketInventoryService implements MarketInventoryAccess {
    public int count(Player player, Material material) {
        return count(player.getInventory().getStorageContents(), material);
    }

    public int remove(Player player, Material material, int quantity) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        int removed = remove(contents, material, quantity);
        inventory.setStorageContents(contents);

        return removed;
    }

    public int addOrDrop(Player player, Material material, int quantity) {
        ItemStack stack = new ItemStack(material, quantity);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        int delivered = quantity;

        for (ItemStack overflowStack : overflow.values()) {
            delivered -= overflowStack.getAmount();
            player.getWorld().dropItemNaturally(player.getLocation(), overflowStack);
        }

        return delivered;
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
