package br.com.enderfy.enderskygrid.model;

import java.util.List;
import java.util.Map;

public record SkyGridConfig(
        List<String> worlds,
        WorldSettings overworld,
        WorldSettings nether,
        WorldSettings end,
        Map<String, LootTableDef> lootTables
) {}
