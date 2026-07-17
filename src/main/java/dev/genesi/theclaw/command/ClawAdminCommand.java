package dev.genesi.theclaw.command;

import dev.genesi.theclaw.TheClawPlugin;
import dev.genesi.theclaw.model.Arena;
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

public final class ClawAdminCommand implements CommandExecutor, TabCompleter {

    private final TheClawPlugin plugin;

    public ClawAdminCommand(TheClawPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("theclaw.admin")) {
            plugin.getMessageService().send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "create" -> handleCreate(sender, args);
            case "delete", "remove" -> handleDelete(sender, args);
            case "setoperator", "operator", "setjoystick" -> handleSetLocation(sender, args, "operator");
            case "setclaw", "claw" -> handleSetLocation(sender, args, "claw");
            case "setlobby", "lobby" -> handleSetLocation(sender, args, "lobby");
            case "setdrop", "drop", "setchute" -> handleSetLocation(sender, args, "drop");
            case "setboundsa", "boundsa", "posa" -> handleSetLocation(sender, args, "bounds-a");
            case "setboundsb", "boundsb", "posb" -> handleSetLocation(sender, args, "bounds-b");
            case "addprize", "prize" -> handleAddPrize(sender, args);
            case "removeprize" -> handleRemovePrize(sender, args);
            case "clearprizes" -> handleClearPrizes(sender, args);
            case "setduration", "duration" -> handleSetDuration(sender, args);
            case "setprizepoints", "prizepoints" -> handleSetPrizePoints(sender, args);
            case "setfee", "fee" -> handleSetFee(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "preview" -> handlePreview(sender, args, true);
            case "unpreview" -> handlePreview(sender, args, false);
            case "forcestop", "cancel" -> handleForceStop(sender, args);
            case "points" -> handlePoints(sender, args);
            case "redeem" -> handleRedeem(sender, args);
            case "reload" -> {
                plugin.reloadPlugin();
                plugin.getMessageService().send(sender, "reloaded");
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin create <name>");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (plugin.getArenaManager().exists(name)) {
            plugin.getMessageService().sendRaw(sender, "&cArena already exists.");
            return;
        }
        plugin.getArenaManager().create(name);
        plugin.getMessageService().send(sender, "arena-created", Map.of("arena", name));
        plugin.getMessageService().sendRaw(sender, "&7Next: setoperator, setclaw, setlobby, setdrop, setboundsa, setboundsb, addprize");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin delete <name>");
            return;
        }
        String name = args[1];
        if (plugin.getGameManager().getByArena(name).isPresent()) {
            plugin.getMessageService().sendRaw(sender, "&cStop the active game first: /clawadmin forcestop " + name);
            return;
        }
        if (!plugin.getArenaManager().delete(name)) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", name));
            return;
        }
        plugin.getMessageService().send(sender, "arena-deleted", Map.of("arena", name.toLowerCase(Locale.ROOT)));
    }

    private void handleSetLocation(CommandSender sender, String[] args, String type) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "players-only");
            return;
        }
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin " + args[0] + " <arena>");
            return;
        }
        Optional<Arena> optional = plugin.getArenaManager().get(args[1]);
        if (optional.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        Arena arena = optional.get();
        String messageKey = switch (type) {
            case "operator" -> {
                arena.setOperatorSpawn(player.getLocation());
                yield "operator-spawn-set";
            }
            case "claw" -> {
                arena.setClawSpawn(player.getLocation());
                yield "claw-spawn-set";
            }
            case "lobby" -> {
                arena.setLobby(player.getLocation());
                yield "lobby-set";
            }
            case "drop" -> {
                arena.setDropChute(player.getLocation());
                yield "drop-set";
            }
            case "bounds-a" -> {
                arena.setBoundsA(player.getLocation());
                yield "bounds-a-set";
            }
            case "bounds-b" -> {
                arena.setBoundsB(player.getLocation());
                yield "bounds-b-set";
            }
            default -> null;
        };
        if (messageKey == null) {
            return;
        }
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, messageKey, Map.of("arena", arena.getName()));
    }

    private void handleAddPrize(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "players-only");
            return;
        }
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin addprize <arena>");
            return;
        }
        Optional<Arena> optional = plugin.getArenaManager().get(args[1]);
        if (optional.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        Arena arena = optional.get();
        int id = arena.addPrize(player.getLocation());
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "prize-added", Map.of("arena", arena.getName(), "id", String.valueOf(id)));
    }

    private void handleRemovePrize(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin removeprize <arena> <id>");
            return;
        }
        Optional<Arena> optional = plugin.getArenaManager().get(args[1]);
        if (optional.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageService().sendRaw(sender, "&cInvalid prize id.");
            return;
        }
        Arena arena = optional.get();
        if (!arena.removePrize(id)) {
            plugin.getMessageService().sendRaw(sender, "&cPrize id out of range.");
            return;
        }
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "prize-removed", Map.of("arena", arena.getName(), "id", String.valueOf(id)));
    }

    private void handleClearPrizes(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin clearprizes <arena>");
            return;
        }
        Optional<Arena> optional = plugin.getArenaManager().get(args[1]);
        if (optional.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        optional.get().clearPrizes();
        plugin.getArenaManager().save();
        plugin.getMessageService().sendRaw(sender, "&aCleared all prize spawns for &e" + optional.get().getName());
    }

    private void handleSetDuration(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin setduration <arena> <seconds>");
            return;
        }
        Optional<Arena> optional = plugin.getArenaManager().get(args[1]);
        if (optional.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        int seconds;
        try {
            seconds = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageService().sendRaw(sender, "&cInvalid number.");
            return;
        }
        optional.get().setDurationOverride(Math.max(10, seconds));
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "duration-set", Map.of(
                "arena", optional.get().getName(),
                "seconds", String.valueOf(Math.max(10, seconds))
        ));
    }

    private void handleSetPrizePoints(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin setprizepoints <arena> <points>");
            return;
        }
        Optional<Arena> optional = plugin.getArenaManager().get(args[1]);
        if (optional.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        int points;
        try {
            points = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageService().sendRaw(sender, "&cInvalid number.");
            return;
        }
        optional.get().setPrizePointsOverride(Math.max(0, points));
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "prize-points-set", Map.of(
                "arena", optional.get().getName(),
                "points", String.valueOf(Math.max(0, points))
        ));
    }

    private void handleSetFee(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin setfee <arena> <amount>");
            return;
        }
        Optional<Arena> optional = plugin.getArenaManager().get(args[1]);
        if (optional.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        double fee;
        try {
            fee = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageService().sendRaw(sender, "&cInvalid amount.");
            return;
        }
        if (fee < 0) {
            plugin.getMessageService().sendRaw(sender, "&cFee cannot be negative.");
            return;
        }
        optional.get().setEntryFeeOverride(fee);
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "fee-set", Map.of(
                "arena", optional.get().getName(),
                "fee", plugin.getEconomyService().format(fee)
        ));
    }

    private void handleList(CommandSender sender) {
        var arenas = plugin.getArenaManager().getArenas();
        if (arenas.isEmpty()) {
            plugin.getMessageService().sendRaw(sender, "&eNo arenas.");
            return;
        }
        for (Arena arena : arenas) {
            plugin.getMessageService().sendRaw(sender, "&8- &f" + arena.getName()
                    + (arena.isReady() ? " &aready" : " &cincomplete")
                    + " &7prizes:" + arena.getPrizes().size());
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin info <arena>");
            return;
        }
        Optional<Arena> optional = plugin.getArenaManager().get(args[1]);
        if (optional.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        Arena arena = optional.get();
        plugin.getMessageService().sendRaw(sender, "&dArena &f" + arena.getName());
        plugin.getMessageService().sendRaw(sender, "&7Ready: " + (arena.isReady() ? "&ayes" : "&cno"));
        plugin.getMessageService().sendRaw(sender, "&7Operator: " + loc(arena.getOperatorSpawn()));
        plugin.getMessageService().sendRaw(sender, "&7Claw: " + loc(arena.getClawSpawn()));
        plugin.getMessageService().sendRaw(sender, "&7Lobby: " + loc(arena.getLobby()));
        plugin.getMessageService().sendRaw(sender, "&7Drop: " + loc(arena.getDropChute()));
        plugin.getMessageService().sendRaw(sender, "&7Bounds A: " + loc(arena.getBoundsA()));
        plugin.getMessageService().sendRaw(sender, "&7Bounds B: " + loc(arena.getBoundsB()));
        plugin.getMessageService().sendRaw(sender, "&7Prizes: &f" + arena.getPrizes().size());
    }

    private void handlePreview(CommandSender sender, String[] args, boolean enable) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin " + (enable ? "preview" : "unpreview") + " <arena>");
            return;
        }
        Optional<Arena> optional = plugin.getArenaManager().get(args[1]);
        if (optional.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        if (enable) {
            plugin.getGameManager().preview(optional.get());
            plugin.getMessageService().send(sender, "preview-spawned", Map.of("arena", optional.get().getName()));
        } else {
            plugin.getGameManager().clearPreview(optional.get().getName());
            plugin.getMessageService().send(sender, "preview-cleared", Map.of("arena", optional.get().getName()));
        }
    }

    private void handleForceStop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin forcestop <arena>");
            return;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(args[1]);
        if (arena.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        if (plugin.getGameManager().getByArena(arena.get().getName()).isEmpty()) {
            plugin.getMessageService().sendRaw(sender, "&cNo active game in that arena.");
            return;
        }
        plugin.getGameManager().forceStop(arena.get());
        plugin.getMessageService().send(sender, "force-stopped", Map.of("arena", arena.get().getName()));
    }

    private void handlePoints(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin points <player> <get|set|add|remove> [amount]");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("get")) {
            plugin.getMessageService().send(sender, "points-other", Map.of(
                    "player", args[1],
                    "points", String.valueOf(plugin.getPointsService().getPoints(target))
            ));
            return;
        }
        if (args.length < 4) {
            plugin.getMessageService().sendRaw(sender, "&cSpecify an amount.");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            plugin.getMessageService().sendRaw(sender, "&cInvalid amount.");
            return;
        }
        int balance = switch (action) {
            case "set" -> {
                plugin.getPointsService().setPoints(target, amount);
                yield plugin.getPointsService().getPoints(target);
            }
            case "add" -> plugin.getPointsService().addPoints(target, amount);
            case "remove" -> {
                plugin.getPointsService().removePoints(target, amount);
                yield plugin.getPointsService().getPoints(target);
            }
            default -> -1;
        };
        if (balance < 0) {
            plugin.getMessageService().sendRaw(sender, "&cUse get, set, add, or remove.");
            return;
        }
        String key = switch (action) {
            case "set" -> "points-set";
            case "add" -> "points-added";
            default -> "points-removed";
        };
        plugin.getMessageService().send(sender, key, Map.of(
                "player", args[1],
                "amount", String.valueOf(amount),
                "points", String.valueOf(balance)
        ));
    }

    private void handleRedeem(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin redeem <player> <amount>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageService().sendRaw(sender, "&cInvalid amount.");
            return;
        }
        if (!plugin.getPointsService().removePoints(target, amount)) {
            plugin.getMessageService().send(sender, "not-enough-points", Map.of(
                    "player", args[1],
                    "points", String.valueOf(plugin.getPointsService().getPoints(target))
            ));
            return;
        }
        plugin.getMessageService().send(sender, "redeem-success", Map.of(
                "player", args[1],
                "amount", String.valueOf(amount),
                "points", String.valueOf(plugin.getPointsService().getPoints(target))
        ));
    }

    private String loc(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return "&cunset";
        }
        return "&a" + location.getWorld().getName()
                + " " + String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ());
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageService().sendRaw(sender, "&dTheClaw admin:");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin create|delete <name>");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin setoperator|setclaw|setlobby|setdrop <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin setboundsa|setboundsb <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin addprize|removeprize|clearprizes <arena> [id]");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin setduration|setprizepoints|setfee <arena> <value>");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin preview|unpreview|forcestop <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin points <player> <get|set|add|remove> [amount]");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin redeem <player> <amount>");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin list|info <arena>|reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("theclaw.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(Arrays.asList(
                    "create", "delete", "setoperator", "setclaw", "setlobby", "setdrop",
                    "setboundsa", "setboundsb", "addprize", "removeprize", "clearprizes",
                    "setduration", "setprizepoints", "setfee", "list", "info",
                    "preview", "unpreview", "forcestop", "points", "redeem", "reload", "help"
            ), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (List.of("delete", "setoperator", "setclaw", "setlobby", "setdrop", "setboundsa", "setboundsb",
                    "addprize", "removeprize", "clearprizes", "setduration", "setprizepoints", "setfee",
                    "info", "preview", "unpreview", "forcestop", "operator", "claw", "lobby", "drop").contains(sub)) {
                return filter(arenaNames(), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("points")) {
            return filter(Arrays.asList("get", "set", "add", "remove"), args[2]);
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
