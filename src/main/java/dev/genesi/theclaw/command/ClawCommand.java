package dev.genesi.theclaw.command;

import dev.genesi.theclaw.TheClawPlugin;
import dev.genesi.theclaw.model.Arena;
import dev.genesi.theclaw.model.GameSession;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class ClawCommand implements CommandExecutor, TabCompleter {

    private final TheClawPlugin plugin;

    public ClawCommand(TheClawPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("theclaw.use")) {
            plugin.getMessageService().send(player, "no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(player);
            case "leave" -> plugin.getGameManager().leave(player, true);
            case "arenas", "list" -> handleArenas(player);
            case "points" -> plugin.getMessageService().send(player, "points-self", java.util.Map.of(
                    "points", String.valueOf(plugin.getPointsService().getPoints(player))
            ));
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleArenas(Player player) {
        var arenas = plugin.getArenaManager().getArenas();
        if (arenas.isEmpty()) {
            plugin.getMessageService().sendRaw(player, "&eNo claw machines set up yet.");
            return;
        }
        for (Arena arena : arenas) {
            String status = !arena.isReady() ? "&csetup incomplete"
                    : plugin.getGameManager().getByArena(arena.getName())
                    .map(s -> s.getState() == GameSession.State.WAITING
                            ? "&eneeds claw player"
                            : "&6in game")
                    .orElse("&aopen — click the machine");
            plugin.getMessageService().sendRaw(player, "&8- &f" + arena.getName() + " &7| " + status);
        }
    }

    private void sendHelp(Player player) {
        plugin.getMessageService().sendRaw(player, "&dTheClaw:");
        plugin.getMessageService().sendRaw(player, "&eClick the claw machine &7to join");
        plugin.getMessageService().sendRaw(player, "&e/claw leave &7- leave queue/game");
        plugin.getMessageService().sendRaw(player, "&e/claw arenas &7- list machines");
        plugin.getMessageService().sendRaw(player, "&e/claw points &7- arcade points");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("leave", "arenas", "points", "help"), args[0]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
