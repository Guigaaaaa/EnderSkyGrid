package br.com.enderfy.enderskygrid.model;

import org.bukkit.inventory.ItemStack;

public record LootEntry(
        ItemStack template,
        int weight,
        int minAmount,
        int maxAmount
) {}
