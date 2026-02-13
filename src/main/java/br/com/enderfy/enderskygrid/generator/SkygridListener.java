package br.com.enderfy.enderskygrid.generator;

import br.com.enderfy.enderskygrid.config.ConfigManager;
import br.com.enderfy.enderskygrid.model.SkyGridConfig;
import br.com.enderfy.enderskygrid.model.WorldSettings;
import br.com.enderfy.enderskygrid.EnderSkyGrid;
import org.bukkit.*;
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

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;

        SkyGridConfig config = ConfigManager.get();
        if (config == null) return;

        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();
        WorldSettings settings = getSettings(world, config);

        if (!config.worlds().contains(world.getName())) return;

        int spacing = Math.max(1, config.spacing());

        int minY = Math.max(config.minY(), world.getMinHeight());
        int maxY = Math.min(config.maxY(), world.getMaxHeight() - 1);
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
                        markChestPending(block, world.getSeed(), wx, y, wz);
                    } else if (block.getType() == Material.SPAWNER) {
                        configureSpawnerNow(block, settings, world.getSeed(), wx, y, wz);
                    }
                }
            }
        }
    }

    private void markChestPending(Block block, long worldSeed, int x, int y, int z) {
        if (!(block.getState() instanceof Chest chest)) return;

        PersistentDataContainer pdc = chest.getPersistentDataContainer();
        pdc.set(chestPendingKey, PersistentDataType.BYTE, (byte) 1);

        pdc.set(chestSeedKey, PersistentDataType.LONG, mix(worldSeed, x, y, z));

        chest.update(true, false);
    }

    private void configureSpawnerNow(Block block, WorldSettings settings, long seed, int x, int y, int z) {
        if (!(block.getState() instanceof CreatureSpawner spawner)) return;

        Random r = new Random(mix(seed, x, y, z));

        List<EntityType> mobs = settings.spawnerMobs();
        if (mobs == null || mobs.isEmpty()) return;

        spawner.setSpawnedType(mobs.get(r.nextInt(mobs.size())));
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

        SkyGridConfig config = ConfigManager.get();
        if (config == null) return;

        World world = chest.getWorld();
        WorldSettings settings = getSettings(world, config);

        long seed = Optional.ofNullable(pdc.get(chestSeedKey, PersistentDataType.LONG))
                .orElse(mix(world.getSeed(), chest.getX(), chest.getY(), chest.getZ()));

        pdc.remove(chestPendingKey);
        pdc.remove(chestSeedKey);
        chest.update();

        var loot = settings.chestLoot();
        if (loot == null) return;

        Random r = new Random(seed);

        Inventory inv = chest.getBlockInventory();
        inv.clear();

        List<ItemStack> drops = loot.roll(r);
        if (drops.isEmpty()) return;

        List<Integer> slots = new ArrayList<>(inv.getSize());
        for (int i = 0; i < inv.getSize(); i++) slots.add(i);
        Collections.shuffle(slots, r);

        int idx = 0;
        for (ItemStack it : drops) {
            if (idx >= slots.size()) break;
            inv.setItem(slots.get(idx++), it);
        }
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
