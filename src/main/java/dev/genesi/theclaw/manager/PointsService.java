package dev.genesi.theclaw.manager;

import dev.genesi.theclaw.TheClawPlugin;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

public final class PointsService {

    private final TheClawPlugin plugin;
    private File file;
    private FileConfiguration config;

    public PointsService(TheClawPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "points.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create points.yml", e);
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        if (config == null || file == null) {
            return;
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save points.yml", e);
        }
    }

    public int getPoints(OfflinePlayer player) {
        return config.getInt(player.getUniqueId().toString(), 0);
    }

    public int getPoints(UUID uuid) {
        return config.getInt(uuid.toString(), 0);
    }

    public void setPoints(OfflinePlayer player, int points) {
        config.set(player.getUniqueId().toString(), Math.max(0, points));
        save();
    }

    public int addPoints(OfflinePlayer player, int amount) {
        int next = Math.max(0, getPoints(player) + amount);
        setPoints(player, next);
        return next;
    }

    public boolean removePoints(OfflinePlayer player, int amount) {
        if (amount < 0) {
            return false;
        }
        int current = getPoints(player);
        if (current < amount) {
            return false;
        }
        setPoints(player, current - amount);
        return true;
    }
}
