package dev.genesi.theclaw.command;

import dev.genesi.theclaw.TheClawPlugin;
import dev.genesi.theclaw.model.Arena;
import dev.genesi.theclaw.model.GameSession;
import dev.genesi.theclaw.model.PlayerRole;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
            case "join" -> handleJoin(player, args);
            case "leave" -> plugin.getGameManager().leave(player, true);
            case "role" -> handleRole(player, args);
            case "arenas", "list" -> handleArenas(player);
            case "info" -> handleInfo(player, args);
            case "points", "balance", "bal" -> handlePoints(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(player, "&cUsage: /claw join <arena>");
            return;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(args[1]);
        if (arena.isEmpty()) {
            plugin.getMessageService().send(player, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        if (args.length >= 3) {
            PlayerRole role = PlayerRole.fromInput(args[2]);
            if (role == null) {
                plugin.getMessageService().send(player, "role-invalid");
                return;
            }
            plugin.getGameManager().setPreferredRole(player.getUniqueId(), role);
        }

        String result = plugin.getGameManager().join(player, arena.get());
        switch (result) {
            case "already-playing" -> plugin.getMessageService().send(player, "already-playing");
            case "arena-not-ready" -> plugin.getMessageService().send(player, "arena-not-ready", Map.of("arena", arena.get().getName()));
            case "arena-busy" -> plugin.getMessageService().send(player, "arena-busy", Map.of("arena", arena.get().getName()));
            case "economy-missing" -> plugin.getMessageService().send(player, "economy-missing");
            default -> {
            }
        }
    }

    private void handleRole(Player player, String[] args) {
        if (args.length < 2) {
            PlayerRole current = plugin.getGameManager().getPreferredRole(player.getUniqueId());
            plugin.getMessageService().sendRaw(player, "&7Preferred role: &e" + current.display());
            plugin.getMessageService().sendRaw(player, "&cUsage: /claw role <operator|claw|any>");
            return;
        }
        PlayerRole role = PlayerRole.fromInput(args[1]);
        if (role == null) {
            plugin.getMessageService().send(player, "role-invalid");
            return;
        }
        plugin.getGameManager().setPreferredRole(player.getUniqueId(), role);
        plugin.getMessageService().send(player, "role-set", Map.of("role", role.display()));
    }

    private void handleArenas(Player player) {
        var arenas = plugin.getArenaManager().getArenas();
        if (arenas.isEmpty()) {
            plugin.getMessageService().sendRaw(player, "&eNo arenas configured yet.");
            return;
        }
        plugin.getMessageService().sendRaw(player, "&dArenas:");
        for (Arena arena : arenas) {
            Optional<GameSession> session = plugin.getGameManager().getByArena(arena.getName());
            String status;
            if (!arena.isReady()) {
                status = "&csetup incomplete";
            } else if (session.isEmpty()) {
                status = "&aopen";
            } else if (session.get().getState() == GameSession.State.WAITING) {
                status = "&ewaiting &7(" + session.get().playerCount() + "/2)";
            } else {
                status = "&6in game";
            }
            double fee = plugin.getArenaManager().resolveEntryFee(arena);
            plugin.getMessageService().sendRaw(player,
                    "&8- &f" + arena.getName() + " &7fee:&a" + plugin.getEconomyService().format(fee)
                            + " &7| " + status);
        }
    }

    private void handleInfo(Player player, String[] args) {
        Arena arena;
        if (args.length >= 2) {
            arena = plugin.getArenaManager().get(args[1]).orElse(null);
            if (arena == null) {
                plugin.getMessageService().send(player, "arena-not-found", Map.of("arena", args[1]));
                return;
            }
        } else {
            Optional<GameSession> session = plugin.getGameManager().getByPlayer(player.getUniqueId());
            if (session.isEmpty()) {
                plugin.getMessageService().sendRaw(player, "&cUsage: /claw info <arena>");
                return;
            }
            arena = plugin.getArenaManager().get(session.get().getArenaName()).orElse(null);
            if (arena == null) {
                return;
            }
        }

        plugin.getMessageService().sendRaw(player, "&dArena &f" + arena.getName());
        plugin.getMessageService().sendRaw(player, "&7Ready: " + (arena.isReady() ? "&ayes" : "&cno"));
        plugin.getMessageService().sendRaw(player, "&7Prizes: &f" + arena.getPrizes().size());
        plugin.getMessageService().sendRaw(player, "&7Duration: &e" + plugin.getArenaManager().resolveDuration(arena) + "s");
        plugin.getMessageService().sendRaw(player, "&7Prize points: &e" + plugin.getArenaManager().resolvePrizePoints(arena));
        plugin.getMessageService().sendRaw(player, "&7Entry fee: &a" + plugin.getEconomyService().format(plugin.getArenaManager().resolveEntryFee(arena)));

        plugin.getGameManager().getByArena(arena.getName()).ifPresent(session -> {
            plugin.getMessageService().sendRaw(player, "&7State: &e" + session.getState());
            plugin.getMessageService().sendRaw(player, "&7Operator: &f" + nullSafe(session.getOperatorName()));
            plugin.getMessageService().sendRaw(player, "&7Claw: &f" + nullSafe(session.getClawName()));
        });
    }

    private void handlePoints(Player player, String[] args) {
        if (!player.hasPermission("theclaw.points")) {
            plugin.getMessageService().send(player, "no-permission");
            return;
        }
        if (args.length >= 2 && player.hasPermission("theclaw.admin")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            plugin.getMessageService().send(player, "points-other", Map.of(
                    "player", args[1],
                    "points", String.valueOf(plugin.getPointsService().getPoints(target))
            ));
            return;
        }
        plugin.getMessageService().send(player, "points-self", Map.of(
                "points", String.valueOf(plugin.getPointsService().getPoints(player))
        ));
    }

    private void sendHelp(Player player) {
        plugin.getMessageService().sendRaw(player, "&dTheClaw commands:");
        plugin.getMessageService().sendRaw(player, "&e/claw join <arena> [role] &7- Queue for a machine");
        plugin.getMessageService().sendRaw(player, "&e/claw leave &7- Leave queue or game");
        plugin.getMessageService().sendRaw(player, "&e/claw role <operator|claw|any> &7- Prefer a role");
        plugin.getMessageService().sendRaw(player, "&e/claw arenas &7- List arenas");
        plugin.getMessageService().sendRaw(player, "&e/claw info [arena] &7- Arena / match info");
        plugin.getMessageService().sendRaw(player, "&e/claw points &7- Your arcade points");
        plugin.getMessageService().sendRaw(player, "&7Operator moves the claw with hotbar items. Claw right-clicks Grab!");
    }

    private String nullSafe(String value) {
        return value == null ? "—" : value;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("join", "leave", "role", "arenas", "info", "points", "help"), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("join") || sub.equals("info")) {
                return filter(arenaNames(), args[1]);
            }
            if (sub.equals("role")) {
                return filter(Arrays.asList("operator", "claw", "any"), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("join")) {
            return filter(Arrays.asList("operator", "claw", "any"), args[2]);
        }
        return List.of();
    }

    private List<String> arenaNames() {
        return plugin.getArenaManager().getArenas().stream().map(Arena::getName).collect(Collectors.toList());
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
