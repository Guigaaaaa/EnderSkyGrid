package br.com.enderfy.enderskygrid.generator;

import br.com.enderfy.enderskygrid.EnderSkyGrid;
import br.com.enderfy.enderskygrid.config.ConfigManager;
import br.com.enderfy.enderskygrid.model.*;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class SkygridListener implements Listener {

    private final NamespacedKey chestPendingKey = new NamespacedKey(EnderSkyGrid.get(), "skygrid_chest_pending");
    private final NamespacedKey chestSeedKey = new NamespacedKey(EnderSkyGrid.get(), "skygrid_chest_seed");
    private final NamespacedKey chestLootPoolKey = new NamespacedKey(EnderSkyGrid.get(), "skygrid_chest_loot_pool");

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;

        SkyGridConfig config = ConfigManager.get();
        if (config == null) return;

        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();

        if (!config.worlds().contains(world.getName())) return;

        WorldSettings settings = getSettings(world, config);

        int spacing = Math.max(1, settings.spacing());

        int minY = Math.max(settings.minY(), world.getMinHeight());
        int maxY = Math.min(settings.maxY(), world.getMaxHeight() - 1);
        if (minY > maxY) return;

        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        int xStart = alignToChunk(baseX, spacing);
        int zStart = alignToChunk(baseZ, spacing);
        int yStart = alignUp(minY, spacing);

        for (int x = xStart; x < 16; x += spacing) {
            int wx = baseX + x;

            for (int z = zStart; z < 16; z += spacing) {
                int wz = baseZ + z;

                for (int y = yStart; y <= maxY; y += spacing) {
                    Block block = world.getBlockAt(wx, y, wz);

                    if (block.getType() == Material.CHEST) {
                        Biome biome = world.getBiome(wx, y, wz);
                        List<GridEntry> palette = paletteFor(biome, settings);

                        List<String> pool = resolveChestLootPool(palette, config, world.getEnvironment());
                        markChestPending(block, world.getSeed(), wx, y, wz, pool);
                    } else if (block.getType() == Material.SPAWNER) {
                        Biome biome = world.getBiome(wx, y, wz);
                        List<GridEntry> palette = paletteFor(biome, settings);

                        Map<EntityType, Integer> mobs = resolveSpawnerMobs(palette);
                        configureSpawnerNow(block, world.getSeed(), wx, y, wz, mobs);
                    }
                }
            }
        }
    }

    private void markChestPending(Block block, long worldSeed, int x, int y, int z, List<String> lootPool) {
        if (!(block.getState() instanceof Chest chest)) return;

        PersistentDataContainer pdc = chest.getPersistentDataContainer();
        pdc.set(chestPendingKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(chestSeedKey, PersistentDataType.LONG, mix(worldSeed, x, y, z));

        if (lootPool != null && !lootPool.isEmpty()) {
            pdc.set(chestLootPoolKey, PersistentDataType.STRING, String.join(",", lootPool));
        }

        chest.update(true, false);
    }

    private void configureSpawnerNow(Block block, long seed, int x, int y, int z, Map<EntityType, Integer> mobWeights) {
        if (!(block.getState() instanceof CreatureSpawner spawner)) return;

        if (mobWeights == null || mobWeights.isEmpty()) return;

        Random random = new Random(mix(seed, x, y, z));
        EntityType picked = pickWeightedMob(mobWeights, random);
        if (picked == null) return;

        spawner.setSpawnedType(picked);
        spawner.update(true, false);
    }

    @EventHandler
    public void onRightClickChest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;
        if (!(block.getState() instanceof Chest chest)) return;

        PersistentDataContainer pdc = chest.getPersistentDataContainer();
        Byte pending = pdc.get(chestPendingKey, PersistentDataType.BYTE);
        if (pending == null || pending != (byte) 1) return;

        SkyGridConfig cfg = ConfigManager.get();
        if (cfg == null) return;

        long seed = Optional.ofNullable(pdc.get(chestSeedKey, PersistentDataType.LONG))
                .orElse(mix(chest.getWorld().getSeed(), chest.getX(), chest.getY(), chest.getZ()));

        String poolRaw = pdc.get(chestLootPoolKey, PersistentDataType.STRING);
        List<String> pool = (poolRaw == null || poolRaw.isBlank())
                ? defaultPoolFor(chest.getWorld().getEnvironment())
                : Arrays.stream(poolRaw.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();

        pdc.remove(chestPendingKey);
        pdc.remove(chestSeedKey);
        pdc.remove(chestLootPoolKey);
        chest.update();

        LootTable table = pickLootTableFromPool(cfg, pool, new Random(seed));
        if (table == null) return;

        Inventory inv = chest.getBlockInventory();
        inv.clear();

        Random random = new Random(seed);

        List<ItemStack> drops = table.roll(random);
        if (drops.isEmpty()) return;

        List<Integer> slots = new ArrayList<>(inv.getSize());
        for (int i = 0; i < inv.getSize(); i++) slots.add(i);
        Collections.shuffle(slots, random);

        int idx = 0;
        for (ItemStack it : drops) {
            if (idx >= slots.size()) break;
            inv.setItem(slots.get(idx++), it);
        }
    }

    private LootTable pickLootTableFromPool(SkyGridConfig cfg, List<String> pool, Random r) {
        Map<String, LootTableDef> all = cfg.lootTables();
        if (all == null || all.isEmpty()) return null;

        List<LootTableDef> defs = new ArrayList<>();
        for (String name : pool) {
            LootTableDef def = all.get(name);
            if (def != null && def.weight() > 0 && def.table() != null) defs.add(def);
        }

        if (defs.isEmpty()) {
            for (String name : pool) {
                LootTableDef def = all.get(name);
                if (def != null && def.table() != null) return def.table();
            }
            LootTableDef def = all.get("overworld");
            return (def == null) ? null : def.table();
        }

        int total = 0;
        for (LootTableDef d : defs) total += Math.max(0, d.weight());
        if (total <= 0) return defs.getFirst().table();

        int roll = r.nextInt(total) + 1;
        int acc = 0;

        for (LootTableDef d : defs) {
            int w = Math.max(0, d.weight());
            if (w == 0) continue;

            acc += w;
            if (roll <= acc) return d.table();
        }

        return defs.getFirst().table();
    }

    private List<GridEntry> paletteFor(Biome biome, WorldSettings settings) {
        List<GridEntry> list = settings.biomesMaterial().get(biome);
        if (list == null || list.isEmpty()) list = settings.biomesMaterial().get(settings.defaultBiome());
        return list;
    }

    private List<String> resolveChestLootPool(List<GridEntry> palette, SkyGridConfig cfg, World.Environment env) {
        if (palette != null) {
            for (GridEntry e : palette) {
                if (e != null && e.isChest()) {
                    List<String> tables = e.lootTables();
                    if (tables != null && !tables.isEmpty()) return tables;
                }
            }
        }
        return defaultPoolFor(env);
    }

    private Map<EntityType, Integer> resolveSpawnerMobs(List<GridEntry> palette) {
        if (palette == null) return Map.of();

        for (GridEntry e : palette) {
            if (e != null && e.isSpawner()) {
                Map<EntityType, Integer> mobs = e.mobWeights();
                if (mobs != null && !mobs.isEmpty()) return mobs;
            }
        }
        return Map.of();
    }

    private List<String> defaultPoolFor(World.Environment env) {
        return switch (env) {
            case NETHER -> List.of("nether");
            case THE_END -> List.of("end");
            default -> List.of("overworld");
        };
    }

    private EntityType pickWeightedMob(Map<EntityType, Integer> mobWeights, Random r) {
        int total = 0;
        for (int w : mobWeights.values()) total += Math.max(0, w);
        if (total <= 0) return null;

        int roll = r.nextInt(total) + 1;
        int acc = 0;

        for (Map.Entry<EntityType, Integer> e : mobWeights.entrySet()) {
            int w = Math.max(0, e.getValue());
            if (w == 0) continue;

            acc += w;
            if (roll <= acc) return e.getKey();
        }
        return null;
    }

    private WorldSettings getSettings(World world, SkyGridConfig cfg) {
        return switch (world.getEnvironment()) {
            case NETHER -> cfg.nether();
            case THE_END -> cfg.end();
            default -> cfg.overworld();
        };
    }

    private static long mix(long seed, int x, int y, int z) {
        long s = seed;
        s ^= (x * 341873128712L);
        s ^= (z * 132897987541L);
        s ^= (y * 42317861L);
        return s;
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
}