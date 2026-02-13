package br.com.enderfy.enderskygrid.model;

public record SkyGridConfig(
        int spacing,
        int minY,
        int maxY,
        WorldSettings overworld,
        WorldSettings nether,
        WorldSettings end
) {}

