package dev.genesi.theclaw.util;

import dev.genesi.theclaw.TheClawPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ItemFactory {

    public static final String PDC_CONTROL = "claw_control";
    public static final String PDC_GRAB = "claw_grab";
    public static final String PDC_PRIZE = "claw_prize";

    public static final String CTRL_NORTH = "north";
    public static final String CTRL_SOUTH = "south";
    public static final String CTRL_WEST = "west";
    public static final String CTRL_EAST = "east";
    public static final String CTRL_LOWER = "lower";
    public static final String CTRL_RAISE = "raise";

    private final TheClawPlugin plugin;
    private NamespacedKey controlKey;
    private NamespacedKey grabKey;
    private NamespacedKey prizeKey;

    public ItemFactory(TheClawPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.controlKey = new NamespacedKey(plugin, PDC_CONTROL);
        this.grabKey = new NamespacedKey(plugin, PDC_GRAB);
        this.prizeKey = new NamespacedKey(plugin, PDC_PRIZE);
    }

    public NamespacedKey getControlKey() {
        return controlKey;
    }

    public NamespacedKey getGrabKey() {
        return grabKey;
    }

    public NamespacedKey getPrizeKey() {
        return prizeKey;
    }

    public ItemStack createPrizeItem() {
        ItemStack stack = createConfiguredItem("prize");
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(prizeKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public ItemStack createGrabItem() {
        ItemStack stack = createConfiguredItem("grab");
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(grabKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public ItemStack createControl(String action) {
        String section = switch (action) {
            case CTRL_NORTH -> "controls.move-north";
            case CTRL_SOUTH -> "controls.move-south";
            case CTRL_WEST -> "controls.move-west";
            case CTRL_EAST -> "controls.move-east";
            case CTRL_LOWER -> "controls.lower";
            case CTRL_RAISE -> "controls.raise";
            default -> "controls.move-north";
        };
        ItemStack stack = createConfiguredItem(section);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(controlKey, PersistentDataType.STRING, action);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isGrabItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(grabKey, PersistentDataType.BYTE);
    }

    public String getControlAction(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(controlKey, PersistentDataType.STRING);
    }

    public boolean isClawGameItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        return pdc.has(controlKey, PersistentDataType.STRING)
                || pdc.has(grabKey, PersistentDataType.BYTE);
    }

    private ItemStack createConfiguredItem(String sectionName) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(sectionName);
        if (section == null) {
            return new ItemStack(Material.STONE);
        }

        String itemsAdderId = section.getString("itemsadder-id", "");
        if (itemsAdderId != null && !itemsAdderId.isBlank()) {
            ItemStack ia = tryItemsAdder(itemsAdderId);
            if (ia != null) {
                applyDisplay(ia, section);
                return ia;
            }
        }

        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack stack = new ItemStack(material);
        applyDisplay(stack, section);
        applyModel(stack, section);
        return stack;
    }

    private void applyDisplay(ItemStack stack, ConfigurationSection section) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        String name = section.getString("display-name");
        if (name != null && !name.isBlank()) {
            meta.displayName(legacy(name));
        }
        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(this::legacy).toList());
        }
        stack.setItemMeta(meta);
    }

    private void applyModel(ItemStack stack, ConfigurationSection section) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }

        String itemModel = section.getString("item-model", "");
        if (itemModel != null && !itemModel.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(itemModel.toLowerCase(Locale.ROOT));
            if (key != null) {
                trySetItemModel(meta, key);
            }
        }

        int cmd = section.getInt("custom-model-data", 0);
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        stack.setItemMeta(meta);
    }

    private void trySetItemModel(ItemMeta meta, NamespacedKey key) {
        try {
            Method method = meta.getClass().getMethod("setItemModel", NamespacedKey.class);
            method.invoke(meta, key);
        } catch (ReflectiveOperationException ignored) {
            // Older API fallback — custom-model-data still applies when set.
        }
    }

    private ItemStack tryItemsAdder(String id) {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            return null;
        }
        try {
            Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method getInstance = customStack.getMethod("getInstance", String.class);
            Object stackWrapper = getInstance.invoke(null, id);
            if (stackWrapper == null) {
                return null;
            }
            Method getItemStack = stackWrapper.getClass().getMethod("getItemStack");
            Object result = getItemStack.invoke(stackWrapper);
            if (result instanceof ItemStack itemStack) {
                return itemStack.clone();
            }
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load ItemsAdder item '" + id + "'", e);
        }
        return null;
    }

    private Component legacy(String input) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(input == null ? "" : input);
    }
}
