package br.com.enderfy.enderskygrid.model;

import org.bukkit.block.Biome;

import java.util.List;
import java.util.Map;

public record WorldSettings(
        int spacing,
        int minY,
        int maxY,
        Biome defaultBiome,
        Map<Biome, List<GridEntry>> biomesMaterial
) {}
