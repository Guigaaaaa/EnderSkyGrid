package br.com.enderfy.enderskygrid.generator;

import br.com.enderfy.enderskygrid.config.ConfigManager;
import br.com.enderfy.enderskygrid.model.SkyGridConfig;
import org.bukkit.Material;
import org.bukkit.World;
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

        final int spacing = Math.max(1, config.spacing());

        final int baseX = chunkX << 4;
        final int baseZ = chunkZ << 4;

        final int minY = Math.max(config.minY(), chunkData.getMinHeight());
        final int maxY = Math.min(config.maxY(), chunkData.getMaxHeight() - 1);
        if (minY > maxY) return;

        final List<Material> palette = paletteFor(worldInfo, config);
        if (palette == null || palette.isEmpty()) return;

        final double chestChance = worldSettings(worldInfo, config).chestChance();
        final double spawnerChance = worldSettings(worldInfo, config).spawnerChance();

        final int xStart = alignToChunk(baseX, spacing);
        final int zStart = alignToChunk(baseZ, spacing);

        final int yStart = alignUp(minY, spacing);

        for (int x = xStart; x < 16; x += spacing) {
            final int wx = baseX + x;

            for (int z = zStart; z < 16; z += spacing) {
                final int wz = baseZ + z;

                for (int y = yStart; y <= maxY; y += spacing) {
                    Random r = seeded(worldInfo.getSeed(), wx, y, wz);
                    double roll = r.nextDouble();

                    if (roll < spawnerChance) {
                        chunkData.setBlock(x, y, z, Material.SPAWNER);
                    } else if (roll < spawnerChance + chestChance) {
                        chunkData.setBlock(x, y, z, Material.CHEST);
                    } else {
                        chunkData.setBlock(x, y, z, palette.get(r.nextInt(palette.size())));
                    }
                }
            }
        }
    }

    private static br.com.enderfy.enderskygrid.model.WorldSettings worldSettings(WorldInfo worldInfo, SkyGridConfig cfg) {
        World.Environment env = worldInfo.getEnvironment();
        return switch (env) {
            case NETHER -> cfg.nether();
            case THE_END -> cfg.end();
            default -> cfg.overworld();
        };
    }

    private static List<Material> paletteFor(WorldInfo worldInfo, SkyGridConfig cfg) {
        return worldSettings(worldInfo, cfg).materials();
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

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }
}
