package br.com.enderfy.enderskygrid.model;

import java.util.List;

public record SkyGridConfig(
        int spacing,
        int minY,
        int maxY,
        List<String> worlds,
        WorldSettings overworld,
        WorldSettings nether,
        WorldSettings end
) {}

