package br.com.enderfy.enderskygrid.generator;

import br.com.enderfy.enderskygrid.config.ConfigManager;
import br.com.enderfy.enderskygrid.model.*;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

public class SkyGridGenerator extends ChunkGenerator {

    @Override
    public void generateNoise(
            @NotNull WorldInfo worldInfo,
            @NotNull Random random,
            int chunkX,
            int chunkZ,
            @NotNull ChunkData chunkData
    ) {
        SkyGridConfig config = ConfigManager.get();
        if (config == null) return;

        if (!config.worlds().contains(worldInfo.getName())) return;

        WorldSettings settings = worldSettings(worldInfo, config);
        int spacing = Math.max(1, settings.spacing());

        final int baseX = chunkX << 4;
        final int baseZ = chunkZ << 4;

        final int minY = Math.max(settings.minY(), chunkData.getMinHeight());
        final int maxY = Math.min(settings.maxY(), chunkData.getMaxHeight() - 1);
        if (minY > maxY) return;

        final int xStart = alignToChunk(baseX, spacing);
        final int zStart = alignToChunk(baseZ, spacing);
        final int yStart = alignUp(minY, spacing);

        for (int x = xStart; x < 16; x += spacing) {
            final int wx = baseX + x;

            for (int z = zStart; z < 16; z += spacing) {
                final int wz = baseZ + z;

                for (int y = yStart; y <= maxY; y += spacing) {
                    Biome biome = chunkData.getBiome(x, y, z);
                    List<GridEntry> palette = paletteFor(biome, settings);
                    if (palette == null || palette.isEmpty()) continue;

                    Random r = seeded(worldInfo.getSeed(), wx, y, wz);
                    GridEntry picked = pickWeighted(palette, r);
                    if (picked == null) continue;

                    chunkData.setBlock(x, y, z, picked.material());
                }
            }
        }
    }

    private static WorldSettings worldSettings(WorldInfo worldInfo, SkyGridConfig cfg) {
        World.Environment environment = worldInfo.getEnvironment();
        return switch (environment) {
            case NETHER -> cfg.nether();
            case THE_END -> cfg.end();
            default -> cfg.overworld();
        };
    }

    private static List<GridEntry> paletteFor(Biome biome, WorldSettings settings) {
        List<GridEntry> list = settings.biomesMaterial().get(biome);
        if (list == null || list.isEmpty()) list = settings.biomesMaterial().get(settings.defaultBiome());
        return list;
    }

    private static GridEntry pickWeighted(List<GridEntry> entries, Random r) {
        double total = 0.0;
        for (GridEntry e : entries) total += Math.max(0.0, e.weight());
        if (total <= 0) return null;

        double roll = r.nextDouble() * total;
        double acc = 0.0;

        for (GridEntry e : entries) {
            double w = Math.max(0.0, e.weight());
            if (w <= 0) continue;

            acc += w;
            if (roll <= acc) return e;
        }
        return null;
    }

    private static int alignToChunk(int base, int spacing) {
        int mod = base % spacing;
        if (mod < 0) mod += spacing;
        return (mod == 0) ? 0 : (spacing - mod);
    }

    private static int alignUp(int value, int spacing) {
        int mod = value % spacing;
        if (mod < 0) mod += spacing;
        return (mod == 0) ? value : (value + (spacing - mod));
    }

    private static Random seeded(long seed, int x, int y, int z) {
        long s = seed;
        s ^= (x * 341873128712L);
        s ^= (z * 132897987541L);
        s ^= (y * 42317861L);
        return new Random(s);
    }
}
