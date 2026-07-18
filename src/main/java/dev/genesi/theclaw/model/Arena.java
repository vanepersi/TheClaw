package dev.genesi.theclaw.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Arena {

    private final String name;
    private Location machineBlock;
    private Location controlPad;
    private Location clawSpawn;
    private Location dropChute;
    private Location boundsA;
    private Location boundsB;
    private final List<Location> prizes = new ArrayList<>();
    private Integer durationOverride;
    private Integer prizePointsOverride;
    private Integer clearBonusOverride;
    private Double entryFeeOverride;

    public Arena(String name) {
        this.name = name.toLowerCase();
    }

    public String getName() {
        return name;
    }

    public Location getMachineBlock() {
        return cloneLocation(machineBlock);
    }

    public void setMachineBlock(Location machineBlock) {
        this.machineBlock = cloneLocation(machineBlock);
    }

    public Location getControlPad() {
        return cloneLocation(controlPad);
    }

    public void setControlPad(Location controlPad) {
        this.controlPad = cloneLocation(controlPad);
    }

    public Location getClawSpawn() {
        return cloneLocation(clawSpawn);
    }

    public void setClawSpawn(Location clawSpawn) {
        this.clawSpawn = cloneLocation(clawSpawn);
    }

    public Location getDropChute() {
        return cloneLocation(dropChute);
    }

    public void setDropChute(Location dropChute) {
        this.dropChute = cloneLocation(dropChute);
    }

    public Location getBoundsA() {
        return cloneLocation(boundsA);
    }

    public void setBoundsA(Location boundsA) {
        this.boundsA = cloneLocation(boundsA);
    }

    public Location getBoundsB() {
        return cloneLocation(boundsB);
    }

    public void setBoundsB(Location boundsB) {
        this.boundsB = cloneLocation(boundsB);
    }

    public BoundingBox getBounds() {
        if (boundsA == null || boundsB == null || boundsA.getWorld() == null || boundsB.getWorld() == null) {
            return null;
        }
        if (!boundsA.getWorld().equals(boundsB.getWorld())) {
            return null;
        }
        return BoundingBox.of(boundsA, boundsB);
    }

    public Location getMachineCenter() {
        if (machineBlock != null) {
            return cloneLocation(machineBlock).add(0.5, 0.0, 0.5);
        }
        if (controlPad != null) {
            return cloneLocation(controlPad).add(0.5, 0.0, 0.5);
        }
        return getClawSpawn();
    }

    public List<Location> getPrizes() {
        List<Location> copy = new ArrayList<>(prizes.size());
        for (Location prize : prizes) {
            copy.add(cloneLocation(prize));
        }
        return copy;
    }

    public int addPrize(Location location) {
        prizes.add(cloneLocation(location));
        return prizes.size() - 1;
    }

    public boolean removePrize(int index) {
        if (index < 0 || index >= prizes.size()) {
            return false;
        }
        prizes.remove(index);
        return true;
    }

    public void clearPrizes() {
        prizes.clear();
    }

    public Integer getDurationOverride() {
        return durationOverride;
    }

    public void setDurationOverride(Integer durationOverride) {
        this.durationOverride = durationOverride;
    }

    public Integer getPrizePointsOverride() {
        return prizePointsOverride;
    }

    public void setPrizePointsOverride(Integer prizePointsOverride) {
        this.prizePointsOverride = prizePointsOverride;
    }

    public Integer getClearBonusOverride() {
        return clearBonusOverride;
    }

    public void setClearBonusOverride(Integer clearBonusOverride) {
        this.clearBonusOverride = clearBonusOverride;
    }

    public Double getEntryFeeOverride() {
        return entryFeeOverride;
    }

    public void setEntryFeeOverride(Double entryFeeOverride) {
        this.entryFeeOverride = entryFeeOverride;
    }

    public boolean isReady() {
        return machineBlock != null
                && controlPad != null
                && clawSpawn != null
                && dropChute != null
                && getBounds() != null
                && !prizes.isEmpty();
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("machine-block", serializeLocation(machineBlock));
        map.put("control-pad", serializeLocation(controlPad));
        map.put("claw-spawn", serializeLocation(clawSpawn));
        map.put("drop-chute", serializeLocation(dropChute));
        map.put("bounds-a", serializeLocation(boundsA));
        map.put("bounds-b", serializeLocation(boundsB));
        List<Map<String, Object>> prizeMaps = new ArrayList<>();
        for (Location prize : prizes) {
            prizeMaps.add(serializeLocation(prize));
        }
        map.put("prizes", prizeMaps);
        if (durationOverride != null) {
            map.put("duration-seconds", durationOverride);
        }
        if (prizePointsOverride != null) {
            map.put("prize-points", prizePointsOverride);
        }
        if (clearBonusOverride != null) {
            map.put("clear-bonus-points", clearBonusOverride);
        }
        if (entryFeeOverride != null) {
            map.put("entry-fee", entryFeeOverride);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Arena deserialize(String name, ConfigurationSection section) {
        Arena arena = new Arena(name);
        if (section == null) {
            return arena;
        }
        arena.machineBlock = firstLocation(section, "machine-block", "operator-spawn");
        arena.controlPad = firstLocation(section, "control-pad", "operator-spawn");
        arena.clawSpawn = deserializeLocation(section.getConfigurationSection("claw-spawn"));
        arena.dropChute = deserializeLocation(section.getConfigurationSection("drop-chute"));
        arena.boundsA = deserializeLocation(section.getConfigurationSection("bounds-a"));
        arena.boundsB = deserializeLocation(section.getConfigurationSection("bounds-b"));
        List<?> rawPrizes = section.getList("prizes");
        if (rawPrizes != null) {
            for (Object entry : rawPrizes) {
                if (entry instanceof Map<?, ?> map) {
                    arena.prizes.add(deserializeLocationMap((Map<String, Object>) map));
                } else if (entry instanceof ConfigurationSection nested) {
                    arena.prizes.add(deserializeLocation(nested));
                }
            }
        }
        if (section.contains("duration-seconds")) {
            arena.durationOverride = section.getInt("duration-seconds");
        }
        if (section.contains("prize-points")) {
            arena.prizePointsOverride = section.getInt("prize-points");
        }
        if (section.contains("clear-bonus-points")) {
            arena.clearBonusOverride = section.getInt("clear-bonus-points");
        }
        if (section.contains("entry-fee")) {
            arena.entryFeeOverride = section.getDouble("entry-fee");
        }
        return arena;
    }

    private static Location firstLocation(ConfigurationSection section, String primary, String fallback) {
        Location location = deserializeLocation(section.getConfigurationSection(primary));
        if (location != null) {
            return location;
        }
        return deserializeLocation(section.getConfigurationSection(fallback));
    }

    private static Map<String, Object> serializeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", location.getYaw());
        map.put("pitch", location.getPitch());
        return map;
    }

    private static Location deserializeLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return deserializeLocationMap(section.getValues(false));
    }

    private static Location deserializeLocationMap(Map<String, Object> map) {
        if (map == null || !map.containsKey("world")) {
            return null;
        }
        Object worldObj = map.get("world");
        if (!(worldObj instanceof String worldName)) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                toDouble(map.get("x")),
                toDouble(map.get("y")),
                toDouble(map.get("z")),
                (float) toDouble(map.get("yaw")),
                (float) toDouble(map.get("pitch"))
        );
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    private static Location cloneLocation(Location location) {
        return location == null ? null : location.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Arena arena)) {
            return false;
        }
        return Objects.equals(name, arena.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
