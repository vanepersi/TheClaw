package dev.genesi.theclaw.manager;

import dev.genesi.theclaw.TheClawPlugin;
import dev.genesi.theclaw.model.Arena;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class ArenaManager {

    private final TheClawPlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private File file;
    private FileConfiguration config;

    public ArenaManager(TheClawPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        arenas.clear();
        file = new File(plugin.getDataFolder(), "arenas.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create arenas.yml", e);
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("arenas");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                arenas.put(key.toLowerCase(), Arena.deserialize(key, section.getConfigurationSection(key)));
            }
        }
    }

    public void save() {
        if (config == null || file == null) {
            return;
        }
        config.set("arenas", null);
        for (Arena arena : arenas.values()) {
            config.createSection("arenas." + arena.getName(), arena.serialize());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save arenas.yml", e);
        }
    }

    public Optional<Arena> get(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(arenas.get(name.toLowerCase()));
    }

    public Collection<Arena> getArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public boolean exists(String name) {
        return arenas.containsKey(name.toLowerCase());
    }

    public Arena create(String name) {
        Arena arena = new Arena(name);
        arenas.put(arena.getName(), arena);
        save();
        return arena;
    }

    public boolean delete(String name) {
        Arena removed = arenas.remove(name.toLowerCase());
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public int resolveDuration(Arena arena) {
        if (arena.getDurationOverride() != null) {
            return arena.getDurationOverride();
        }
        return plugin.getConfig().getInt("default-duration-seconds", 90);
    }

    public int resolvePrizePoints(Arena arena) {
        if (arena.getPrizePointsOverride() != null) {
            return arena.getPrizePointsOverride();
        }
        return plugin.getConfig().getInt("default-prize-points", 15);
    }

    public int resolveClearBonus(Arena arena) {
        if (arena.getClearBonusOverride() != null) {
            return arena.getClearBonusOverride();
        }
        return plugin.getConfig().getInt("default-clear-bonus-points", 25);
    }

    public double resolveEntryFee(Arena arena) {
        if (arena.getEntryFeeOverride() != null) {
            return arena.getEntryFeeOverride();
        }
        return plugin.getConfig().getDouble("entry-fee", 0.0);
    }
}
