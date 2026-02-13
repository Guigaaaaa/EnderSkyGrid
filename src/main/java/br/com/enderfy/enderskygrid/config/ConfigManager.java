package br.com.enderfy.enderskygrid.config;

import br.com.enderfy.enderskygrid.EnderSkyGrid;
import br.com.enderfy.enderskygrid.model.*;
import br.com.enderfy.enderskygrid.utils.TextUtils;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
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

        List<String> worlds = config.getStringList("skygrid.enabled-worlds");

        WorldSettings overworld = loadWorld(config, "skygrid.overworld", Biome.PLAINS);
        WorldSettings nether = loadWorld(config, "skygrid.nether", Biome.NETHER_WASTES);
        WorldSettings end = loadWorld(config, "skygrid.end", Biome.THE_END);

        Map<String, LootTableDef> lootTables = loadLootTables(config, "skygrid.loottables");

        skygridConfig = new SkyGridConfig(worlds, overworld, nether, end, lootTables);
    }

    private static WorldSettings loadWorld(FileConfiguration config, String path, Biome fallbackDefaultBiome) {
        int spacing = clamp(config.getInt(path + ".spacing", 4), 1, 128);
        int minY = config.getInt(path + ".min-y", -64);
        int maxY = config.getInt(path + ".max-y", 100);

        Biome defaultBiome = tryGetBiome(config.getString(path + ".default-biome", fallbackDefaultBiome.name()));
        if (defaultBiome == null) defaultBiome = fallbackDefaultBiome;

        Map<Biome, List<GridEntry>> byBiome = new HashMap<>();

        ConfigurationSection section = config.getConfigurationSection(path + ".biomes-material");
        if (section != null) {
            for (String rawBiome : section.getKeys(false)) {
                Biome biome = tryGetBiome(rawBiome);
                if (biome == null) {
                    EnderSkyGrid.get().getLogger().warning("[EnderSkyGrid] Invalid biome key: " + rawBiome + " at " + path + ".biomes-material");
                    continue;
                }

                List<Map<?, ?>> list = config.getMapList(path + ".biomes-material." + rawBiome);
                List<GridEntry> entries = loadGridEntriesSafe(list, path + ".biomes-material." + rawBiome);
                if (!entries.isEmpty()) byBiome.put(biome, entries);
            }
        }

        if (byBiome.isEmpty()) {
            EnderSkyGrid.get().getLogger().warning("[EnderSkyGrid] Biomes keys empty at " + path + ".biomes-material");
        }

        return new WorldSettings(spacing, minY, maxY, defaultBiome, byBiome);
    }

    private static List<GridEntry> loadGridEntriesSafe(List<Map<?, ?>> raw, String path) {
        List<GridEntry> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;

        for (Map<?, ?> mat : raw) {
            Object matObj = mat.get("material");
            String rawMat = (matObj == null) ? "DIRT" : String.valueOf(matObj);
            Material material = Material.matchMaterial(rawMat);

            if (material == null || material.isAir() || !material.isBlock()) {
                EnderSkyGrid.get().getLogger().warning("[EnderSkyGrid] Invalid block material: " + rawMat + " at " + path);
                continue;
            }

            double weight = toDouble(mat.get("weight"), -1);
            if (weight <= 0) continue;

            List<String> lootTables = toStringList(mat.get("loot-tables"));
            Map<EntityType, Integer> mobs = parseMobWeights(toStringList(mat.get("mobs")), path);

            out.add(new GridEntry(material, weight, lootTables, mobs));
        }

        return List.copyOf(out);
    }

    private static Map<String, LootTableDef> loadLootTables(FileConfiguration config, String path) {
        Map<String, LootTableDef> out = new HashMap<>();

        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return Map.of();

        for (String key : section.getKeys(false)) {
            String p = path + "." + key;

            int tableWeight = clamp(config.getInt(p + ".weight", 1), 0, 1_000_000);
            LootTable loot = loadLootTable(config, p);

            out.put(key, new LootTableDef(tableWeight, loot));
        }

        return Map.copyOf(out);
    }

    private static LootTable loadLootTable(FileConfiguration config, String path) {
        int minRolls = clamp(config.getInt(path + ".rolls.min", 1), 0, 64);
        int maxRolls = clamp(config.getInt(path + ".rolls.max", minRolls), minRolls, 64);

        List<Map<?, ?>> raw = config.getMapList(path + ".entries");
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
                if (enchants instanceof Map<?, ?> enchantMap) {
                    for (Map.Entry<?, ?> e : enchantMap.entrySet()) {
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

    private static Map<EntityType, Integer> parseMobWeights(List<String> raw, String path) {
        Map<EntityType, Integer> mobs = new HashMap<>();
        if (raw == null) return mobs;

        for (String rawMob : raw) {
            if (rawMob == null || rawMob.isBlank()) continue;

            String[] parts = rawMob.split(":", 2);
            String rawType = parts[0].trim();

            EntityType type;
            try {
                type = EntityType.valueOf(rawType.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                EnderSkyGrid.get().getLogger().warning("[EnderSkyGrid] Invalid mob type: " + rawType + " at " + path);
                continue;
            }

            int weight = 1;
            if (parts.length == 2) weight = clamp(toInt(parts[1].trim(), 1), 0, 1_000_000);
            if (weight <= 0) continue;

            mobs.put(type, weight);
        }

        return Map.copyOf(mobs);
    }

    private static List<String> toStringList(Object object) {
        if (object == null) return List.of();
        if (object instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        return List.of(String.valueOf(object));
    }

    private static int toInt(Object object, int def) {
        if (object instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(object)); }
        catch (Exception ignored) { return def; }
    }

    private static double toDouble(Object object, double def) {
        if (object instanceof Number number) return number.doubleValue();
        try { return Double.parseDouble(String.valueOf(object)); }
        catch (Exception ignored) { return def; }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static Biome tryGetBiome(String biomeName) {
        if (biomeName == null) return null;
        try {
            return Biome.valueOf(biomeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
