package br.com.enderfy.enderskygrid.config;

import br.com.enderfy.enderskygrid.EnderSkyGrid;
import br.com.enderfy.enderskygrid.model.LootEntry;
import br.com.enderfy.enderskygrid.model.LootTable;
import br.com.enderfy.enderskygrid.model.SkyGridConfig;
import br.com.enderfy.enderskygrid.model.WorldSettings;
import br.com.enderfy.enderskygrid.utils.TextUtils;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ConfigManager {

    private static SkyGridConfig skygridConfig;

    public static SkyGridConfig get() {
        return skygridConfig;
    }

    public static void load() {
        FileConfiguration config = EnderSkyGrid.get().getConfig();

        int spacing = config.getInt("skygrid.spacing", 4);
        int minY = config.getInt("skygrid.min-y", -64);
        int maxY = config.getInt("skygrid.max-y", 100);

        WorldSettings overworld = loadWorld(config, "skygrid.overworld");
        WorldSettings nether = loadWorld(config, "skygrid.nether");
        WorldSettings end = loadWorld(config, "skygrid.end");

        skygridConfig = new SkyGridConfig(spacing, minY, maxY, overworld, nether, end);
    }

    private static WorldSettings loadWorld(FileConfiguration c, String path) {
        double chestChance = c.getDouble(path + ".chest.chance", 0.002);
        double spawnerChance = c.getDouble(path + ".spawner.chance", 0.0002);

        LootTable loot = loadLootTable(c, path + ".chest.loot"); // NOVO

        List<EntityType> mobs = loadMobsSafe(c.getStringList(path + ".spawner.mobs"));
        List<Material> materials = loadMaterialsSafe(c.getStringList(path + ".materials"));

        return new WorldSettings(chestChance, loot, spawnerChance, mobs, materials);
    }

    private static LootTable loadLootTable(FileConfiguration c, String path) {
        int minRolls = clamp(c.getInt(path + ".rolls.min", 1), 0, 64);
        int maxRolls = clamp(c.getInt(path + ".rolls.max", minRolls), minRolls, 64);

        List<Map<?, ?>> raw = c.getMapList(path + ".entries");
        if (raw.isEmpty()) return new LootTable(0, 0, List.of());

        List<LootEntry> entries = new ArrayList<>();

        for (Map<?, ?> m : raw) {
            Object matObj = m.get("material");
            String rawMat = (matObj == null) ? "DIRT" : String.valueOf(matObj);

            Material material = Material.matchMaterial(rawMat);
            if (material == null || material.isAir() || !material.isItem()) {
                EnderSkyGrid.get().getLogger().warning("[EnderSkyGrid] Invalid loot material: " + rawMat + " at " + path);
                continue;
            }

            int weight = clamp(toInt(m.get("weight"), 1), 0, 1_000_000);

            int minAmount = 1;
            int maxAmount = 1;
            Object amountObj = m.get("amount");
            if (amountObj instanceof Map<?, ?> a) {
                minAmount = clamp(toInt(a.get("min"), 1), 1, 64);
                maxAmount = clamp(toInt(a.get("max"), minAmount), minAmount, 64);
            } else if (amountObj != null) {
                int fixed = clamp(toInt(amountObj, 1), 1, 64);
                minAmount = fixed;
                maxAmount = fixed;
            }

            ItemStack item = new ItemStack(material, 1);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                Object name = m.get("name");
                if (name != null) meta.displayName(TextUtils.of(String.valueOf(name)).build());

                Object lore = m.get("lore");
                if (lore instanceof List<?> loreList) {
                    List<String> lines = loreList.stream().map(String::valueOf).toList();
                    meta.lore(TextUtils.parseAll(lines));
                }

                Object enchants = m.get("enchants");
                if (enchants instanceof Map<?, ?> enchMap) {
                    for (Map.Entry<?, ?> e : enchMap.entrySet()) {
                        String rawEnchantment = String.valueOf(e.getKey()).toUpperCase(Locale.ROOT);
                        int level = toInt(e.getValue(), 1);

                        Enchantment enchantment = Enchantment.getByName(rawEnchantment);
                        if (enchantment != null) meta.addEnchant(enchantment, level, true);
                    }
                }

                item.setItemMeta(meta);
            }

            entries.add(new LootEntry(item, weight, minAmount, maxAmount));
        }

        return new LootTable(minRolls, maxRolls, entries);
    }

    private static List<EntityType> loadMobsSafe(List<String> rawMobs) {
        List<EntityType> mobs = new ArrayList<>();
        if (rawMobs == null) return mobs;

        for (String rawMob : rawMobs) {
            if (rawMob == null) continue;
            try {
                mobs.add(EntityType.valueOf(rawMob.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                EnderSkyGrid.get().getLogger().warning("[EnderSkyGrid] Invalid mob type: " + rawMob);
            }
        }
        return List.copyOf(mobs);
    }

    private static List<Material> loadMaterialsSafe(List<String> rawMaterials) {
        List<Material> materials = new ArrayList<>();
        if (rawMaterials == null) return materials;

        for (String rawMaterial : rawMaterials) {
            if (rawMaterial == null) continue;

            Material mat = Material.matchMaterial(rawMaterial);
            if (mat == null || !mat.isBlock() || mat.isAir()) {
                EnderSkyGrid.get().getLogger().warning("[EnderSkyGrid] Invalid block material: " + rawMaterial);
                continue;
            }
            materials.add(mat);
        }
        return List.copyOf(materials);
    }

    private static int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); }
        catch (Exception ignored) { return def; }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
