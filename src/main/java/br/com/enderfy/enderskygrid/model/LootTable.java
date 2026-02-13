package br.com.enderfy.enderskygrid.model;

import org.bukkit.inventory.ItemStack;

import java.util.*;

public class LootTable {
    private final int minRolls;
    private final int maxRolls;
    private final List<LootEntry> entries;
    private final int totalWeight;

    public LootTable(int minRolls, int maxRolls, List<LootEntry> entries) {
        this.minRolls = Math.max(0, minRolls);
        this.maxRolls = Math.max(this.minRolls, maxRolls);
        this.entries = List.copyOf(entries);

        int sum = 0;
        for (LootEntry e : this.entries) sum += Math.max(0, e.weight());
        this.totalWeight = sum;
    }

    public List<ItemStack> roll(Random r) {
        if (entries.isEmpty() || totalWeight <= 0 || maxRolls <= 0) return List.of();

        int rolls = (minRolls == maxRolls)
                ? minRolls
                : (minRolls + r.nextInt((maxRolls - minRolls) + 1));

        List<ItemStack> out = new ArrayList<>(rolls);

        for (int i = 0; i < rolls; i++) {
            LootEntry picked = pickWeighted(r);
            if (picked == null) continue;

            int minA = Math.max(1, picked.minAmount());
            int maxA = Math.max(minA, picked.maxAmount());
            int amount = (minA == maxA) ? minA : (minA + r.nextInt((maxA - minA) + 1));

            ItemStack item = picked.template().clone();
            item.setAmount(Math.min(64, amount));
            out.add(item);
        }
        return out;
    }

    private LootEntry pickWeighted(Random r) {
        int roll = r.nextInt(totalWeight) + 1;
        int acc = 0;

        for (LootEntry e : entries) {
            int w = Math.max(0, e.weight());
            if (w == 0) continue;

            acc += w;
            if (roll <= acc) return e;
        }
        return null;
    }
}
