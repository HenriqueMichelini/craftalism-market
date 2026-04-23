package io.github.henriquemichelini.craftalism.market.gui;

import org.bukkit.Material;

record QuantityControl(
    int slot,
    int delta,
    Material material,
    String name,
    int itemAmount,
    boolean enchanted
) {}
