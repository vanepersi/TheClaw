package dev.genesi.theclaw.manager;

import dev.genesi.theclaw.TheClawPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class MessageService {

    private final TheClawPlugin plugin;
    private String prefix;

    public MessageService(TheClawPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        prefix = plugin.getConfig().getString("messages.prefix", "&8[&dTheClaw&8] &r");
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String raw = plugin.getConfig().getString("messages." + key, key);
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + apply(raw, placeholders)));
    }

    public void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + message));
    }

    public String apply(String input, Map<String, String> placeholders) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    public void actionBar(Player player, String message) {
        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }
}
