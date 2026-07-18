package dev.genesi.theclaw.command;

import dev.genesi.theclaw.TheClawPlugin;
import dev.genesi.theclaw.model.Arena;
import org.bukkit.Location;
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
            case "setmachine", "machine" -> handleSetLocation(sender, args, "machine");
            case "setpad", "pad", "setcontrolpad" -> handleSetLocation(sender, args, "pad");
            case "setclaw", "claw" -> handleSetLocation(sender, args, "claw");
            case "setdrop", "drop", "setchute" -> handleSetLocation(sender, args, "drop");
            case "setboundsa", "boundsa", "posa" -> handleSetLocation(sender, args, "bounds-a");
            case "setboundsb", "boundsb", "posb" -> handleSetLocation(sender, args, "bounds-b");
            case "addprize", "prize" -> handleAddPrize(sender, args);
            case "removeprize" -> handleRemovePrize(sender, args);
            case "clearprizes" -> handleClearPrizes(sender, args);
            case "setduration", "duration" -> handleSetInt(sender, args, "duration");
            case "setprizepoints", "prizepoints" -> handleSetInt(sender, args, "points");
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "preview" -> handlePreview(sender, args, true);
            case "unpreview" -> handlePreview(sender, args, false);
            case "forcestop", "cancel" -> handleForceStop(sender, args);
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
        plugin.getMessageService().sendRaw(sender, "&7Next: setmachine, setpad, setclaw, setdrop, setboundsa, setboundsb, addprize");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin delete <name>");
            return;
        }
        if (plugin.getGameManager().getByArena(args[1]).isPresent()) {
            plugin.getMessageService().sendRaw(sender, "&cStop the game first: /clawadmin forcestop " + args[1]);
            return;
        }
        if (!plugin.getArenaManager().delete(args[1])) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        plugin.getMessageService().send(sender, "arena-deleted", Map.of("arena", args[1].toLowerCase(Locale.ROOT)));
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
        String key = switch (type) {
            case "machine" -> {
                arena.setMachineBlock(player.getLocation().getBlock().getLocation());
                yield "machine-set";
            }
            case "pad" -> {
                Location under = player.getLocation().clone().subtract(0, 0.2, 0).getBlock().getLocation();
                arena.setControlPad(under);
                yield "pad-set";
            }
            case "claw" -> {
                arena.setClawSpawn(player.getLocation());
                yield "claw-spawn-set";
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
        if (key == null) {
            return;
        }
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, key, Map.of("arena", arena.getName()));
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
        int id = optional.get().addPrize(player.getLocation());
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "prize-added", Map.of("arena", optional.get().getName(), "id", String.valueOf(id)));
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
            plugin.getMessageService().sendRaw(sender, "&cInvalid id.");
            return;
        }
        if (!optional.get().removePrize(id)) {
            plugin.getMessageService().sendRaw(sender, "&cPrize id out of range.");
            return;
        }
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "prize-removed", Map.of("arena", optional.get().getName(), "id", String.valueOf(id)));
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
        plugin.getMessageService().sendRaw(sender, "&aCleared prizes for &e" + optional.get().getName());
    }

    private void handleSetInt(CommandSender sender, String[] args, String type) {
        if (args.length < 3) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin " + args[0] + " <arena> <value>");
            return;
        }
        Optional<Arena> optional = plugin.getArenaManager().get(args[1]);
        if (optional.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        int value;
        try {
            value = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageService().sendRaw(sender, "&cInvalid number.");
            return;
        }
        if (type.equals("duration")) {
            optional.get().setDurationOverride(Math.max(5, value));
            plugin.getMessageService().send(sender, "duration-set", Map.of(
                    "arena", optional.get().getName(),
                    "seconds", String.valueOf(Math.max(5, value))
            ));
        } else {
            optional.get().setPrizePointsOverride(Math.max(0, value));
            plugin.getMessageService().send(sender, "prize-points-set", Map.of(
                    "arena", optional.get().getName(),
                    "points", String.valueOf(Math.max(0, value))
            ));
        }
        plugin.getArenaManager().save();
    }

    private void handleList(CommandSender sender) {
        if (plugin.getArenaManager().getArenas().isEmpty()) {
            plugin.getMessageService().sendRaw(sender, "&eNo arenas.");
            return;
        }
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            plugin.getMessageService().sendRaw(sender, "&8- &f" + arena.getName()
                    + (arena.isReady() ? " &aready" : " &cincomplete"));
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
        plugin.getMessageService().sendRaw(sender, "&7Machine: " + loc(arena.getMachineBlock()));
        plugin.getMessageService().sendRaw(sender, "&7Pad: " + loc(arena.getControlPad()));
        plugin.getMessageService().sendRaw(sender, "&7Claw: " + loc(arena.getClawSpawn()));
        plugin.getMessageService().sendRaw(sender, "&7Drop: " + loc(arena.getDropChute()));
        plugin.getMessageService().sendRaw(sender, "&7Bounds A/B: " + loc(arena.getBoundsA()) + " &7/ " + loc(arena.getBoundsB()));
        plugin.getMessageService().sendRaw(sender, "&7Prizes: &f" + arena.getPrizes().size());
    }

    private void handlePreview(CommandSender sender, String[] args, boolean enable) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /clawadmin preview|unpreview <arena>");
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
            plugin.getMessageService().sendRaw(sender, "&cNo active game.");
            return;
        }
        plugin.getGameManager().forceStop(arena.get());
        plugin.getMessageService().send(sender, "force-stopped", Map.of("arena", arena.get().getName()));
    }

    private String loc(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return "&cunset";
        }
        return "&a" + location.getWorld().getName() + " "
                + String.format("%d,%d,%d", location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageService().sendRaw(sender, "&dTheClaw admin:");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin create|delete <name>");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin setmachine <arena> &7- clickable join block");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin setpad <arena> &7- stand here to signal");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin setclaw|setdrop <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin setboundsa|setboundsb <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin addprize <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/clawadmin preview|forcestop|info|reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("theclaw.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(Arrays.asList(
                    "create", "delete", "setmachine", "setpad", "setclaw", "setdrop",
                    "setboundsa", "setboundsb", "addprize", "removeprize", "clearprizes",
                    "setduration", "setprizepoints", "list", "info", "preview", "unpreview",
                    "forcestop", "reload", "help"
            ), args[0]);
        }
        if (args.length == 2) {
            return filter(arenaNames(), args[1]);
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
