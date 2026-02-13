package br.com.enderfy.enderskygrid;

import br.com.enderfy.enderskygrid.config.ConfigManager;
import br.com.enderfy.enderskygrid.generator.SkyGridGenerator;
import br.com.enderfy.enderskygrid.generator.SkygridListener;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class EnderSkyGrid extends JavaPlugin {

    private static EnderSkyGrid INSTANCE;

    @Override
    public void onEnable() {
        INSTANCE = this;
        saveDefaultConfig();
        ConfigManager.load();

        getServer().getPluginManager().registerEvents(new SkygridListener(), this);
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        ConfigManager.load();
    }

    public static EnderSkyGrid get() {
        return INSTANCE;
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, String id) {
        return new SkyGridGenerator();
    }
}
