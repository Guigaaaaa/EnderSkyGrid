package br.com.enderfy.enderskygrid.model;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Map;

public record GridEntry(
        Material material,
        double weight,
        List<String> lootTables,
        Map<EntityType, Integer> mobWeights
) {
    public boolean isChest() {
        return material == Material.CHEST;
    }

    public boolean isSpawner() {
        return material == Material.SPAWNER;
    }
}
