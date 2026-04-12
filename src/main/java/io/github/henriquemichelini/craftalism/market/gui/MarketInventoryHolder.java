package io.github.henriquemichelini.craftalism.market.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

abstract class MarketInventoryHolder implements InventoryHolder {
    private Inventory inventory;

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
