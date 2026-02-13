package br.com.enderfy.enderskygrid.model;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

public record WorldSettings(
        double chestChance,
        LootTable chestLoot,
        double spawnerChance,
        List<EntityType> spawnerMobs,
        List<Material> materials
) {}


