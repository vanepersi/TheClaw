package dev.genesi.theclaw.manager;

import dev.genesi.theclaw.TheClawPlugin;
import dev.genesi.theclaw.model.Arena;
import dev.genesi.theclaw.model.GameSession;
import dev.genesi.theclaw.model.PlayerRole;
import dev.genesi.theclaw.util.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class GameManager {

    private final TheClawPlugin plugin;
    private final Map<String, GameSession> byArena = new HashMap<>();
    private final Map<UUID, GameSession> byPlayer = new HashMap<>();
    private final Map<UUID, PlayerRole> preferredRole = new HashMap<>();
    private final Map<String, List<ItemDisplay>> previews = new HashMap<>();
    private final java.util.Set<UUID> pluginMoving = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public GameManager(TheClawPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isPluginMoving(UUID uuid) {
        return pluginMoving.contains(uuid);
    }

    public Optional<GameSession> getByPlayer(UUID uuid) {
        return Optional.ofNullable(byPlayer.get(uuid));
    }

    public Optional<GameSession> getByArena(String arenaName) {
        return Optional.ofNullable(byArena.get(arenaName.toLowerCase()));
    }

    public void setPreferredRole(UUID uuid, PlayerRole role) {
        preferredRole.put(uuid, role);
    }

    public PlayerRole getPreferredRole(UUID uuid) {
        return preferredRole.getOrDefault(uuid, PlayerRole.ANY);
    }

    public String join(Player player, Arena arena) {
        if (byPlayer.containsKey(player.getUniqueId())) {
            return "already-playing";
        }
        if (!arena.isReady()) {
            return "arena-not-ready";
        }

        GameSession existing = byArena.get(arena.getName());
        if (existing != null && existing.getState() == GameSession.State.PLAYING) {
            return "arena-busy";
        }
        if (existing != null && existing.isFull()) {
            return "arena-busy";
        }

        double fee = plugin.getArenaManager().resolveEntryFee(arena);
        if (fee > 0) {
            if (!plugin.getEconomyService().isReady()) {
                return "economy-missing";
            }
            if (!plugin.getEconomyService().has(player, fee) && !player.hasPermission("theclaw.bypass.fee")) {
                plugin.getMessageService().send(player, "not-enough-money", Map.of(
                        "amount", plugin.getEconomyService().format(fee),
                        "balance", plugin.getEconomyService().format(plugin.getEconomyService().getBalance(player))
                ));
                return "handled";
            }
            if (!plugin.getEconomyService().charge(player, fee)) {
                plugin.getMessageService().send(player, "not-enough-money", Map.of(
                        "amount", plugin.getEconomyService().format(fee),
                        "balance", plugin.getEconomyService().format(plugin.getEconomyService().getBalance(player))
                ));
                return "handled";
            }
        }

        GameSession session = existing;
        if (session == null) {
            int duration = plugin.getArenaManager().resolveDuration(arena);
            int prizePoints = plugin.getArenaManager().resolvePrizePoints(arena);
            int clearBonus = plugin.getArenaManager().resolveClearBonus(arena);
            session = new GameSession(arena.getName(), arena.getPrizes().size(), duration, prizePoints, clearBonus);
            byArena.put(arena.getName(), session);
        }

        PlayerRole preferred = getPreferredRole(player.getUniqueId());
        boolean asOperator = assignRole(session, preferred);

        if (asOperator) {
            session.setOperator(player);
        } else {
            session.setClaw(player);
        }

        byPlayer.put(player.getUniqueId(), session);

        Location lobby = arena.getLobby();
        if (lobby != null) {
            player.teleport(lobby);
        }

        String roleName = asOperator ? PlayerRole.OPERATOR.display() : PlayerRole.CLAW.display();
        if (!session.isFull()) {
            plugin.getMessageService().send(player, "joined-waiting", Map.of(
                    "arena", arena.getName(),
                    "role", roleName
            ));
            plugin.getMessageService().send(player, "waiting-partner", Map.of("arena", arena.getName()));
            return "ok";
        }

        startGame(session, arena);
        return "ok";
    }

    private boolean assignRole(GameSession session, PlayerRole preferred) {
        boolean operatorOpen = session.getOperatorId() == null;
        boolean clawOpen = session.getClawId() == null;

        if (preferred == PlayerRole.OPERATOR && operatorOpen) {
            return true;
        }
        if (preferred == PlayerRole.CLAW && clawOpen) {
            return false;
        }
        if (operatorOpen && !clawOpen) {
            return true;
        }
        if (clawOpen && !operatorOpen) {
            return false;
        }
        // Both open — first player becomes operator by default unless they asked for claw
        if (preferred == PlayerRole.CLAW) {
            return false;
        }
        return true;
    }

    private void startGame(GameSession session, Arena arena) {
        clearPreview(arena.getName());
        session.setState(GameSession.State.PLAYING);

        Player operator = Bukkit.getPlayer(session.getOperatorId());
        Player claw = Bukkit.getPlayer(session.getClawId());
        if (operator == null || claw == null) {
            endSession(session, false, null);
            return;
        }

        Location operatorSpawn = arena.getOperatorSpawn();
        Location clawSpawn = arena.getClawSpawn();
        if (operatorSpawn != null) {
            operator.teleport(operatorSpawn);
        }
        if (clawSpawn != null) {
            claw.teleport(clawSpawn);
            session.setRaisedY(clawSpawn.getY());
        } else {
            session.setRaisedY(claw.getLocation().getY());
        }

        spawnPrizes(session, arena.getPrizes());
        giveOperatorControls(operator);
        giveClawGrab(claw);

        claw.setGameMode(GameMode.ADVENTURE);
        claw.setAllowFlight(true);
        claw.setFlying(true);
        claw.setFlySpeed(0.0f);
        claw.setWalkSpeed(0.0f);

        plugin.getMessageService().send(operator, "joined-start", Map.of(
                "arena", arena.getName(),
                "role", PlayerRole.OPERATOR.display()
        ));
        plugin.getMessageService().send(claw, "joined-start", Map.of(
                "arena", arena.getName(),
                "role", PlayerRole.CLAW.display()
        ));

        updateActionBars(session);
        session.setTickTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(session), 20L, 20L));
    }

    public void leave(Player player, boolean announce) {
        GameSession session = byPlayer.get(player.getUniqueId());
        if (session == null) {
            if (announce) {
                plugin.getMessageService().send(player, "not-playing");
            }
            return;
        }

        if (session.getState() == GameSession.State.WAITING) {
            session.clearPlayer(player.getUniqueId());
            byPlayer.remove(player.getUniqueId());
            if (announce) {
                plugin.getMessageService().send(player, "left");
            }
            if (session.playerCount() == 0) {
                byArena.remove(session.getArenaName());
            } else {
                UUID remaining = session.getOperatorId() != null ? session.getOperatorId() : session.getClawId();
                Player other = remaining == null ? null : Bukkit.getPlayer(remaining);
                if (other != null) {
                    plugin.getMessageService().send(other, "partner-left");
                }
            }
            return;
        }

        if (announce) {
            plugin.getMessageService().send(player, "left");
        }
        endSession(session, false, "partner-left");
    }

    public void forceStop(Arena arena) {
        GameSession session = byArena.get(arena.getName());
        if (session == null) {
            return;
        }
        endSession(session, false, null);
    }

    public boolean handleOperatorControl(Player operator, String action) {
        GameSession session = byPlayer.get(operator.getUniqueId());
        if (session == null || session.isFinished() || session.getState() != GameSession.State.PLAYING) {
            return false;
        }
        if (!session.isOperator(operator.getUniqueId())) {
            return false;
        }

        Player claw = Bukkit.getPlayer(session.getClawId());
        if (claw == null || !claw.isOnline()) {
            endSession(session, false, "partner-left");
            return true;
        }

        Arena arena = plugin.getArenaManager().get(session.getArenaName()).orElse(null);
        if (arena == null) {
            return true;
        }

        BoundingBox bounds = arena.getBounds();
        if (bounds == null) {
            return true;
        }

        double step = plugin.getConfig().getDouble("move-step", 0.45);
        double vStep = plugin.getConfig().getDouble("vertical-step", 0.35);
        Location current = claw.getLocation();
        Vector delta = switch (action) {
            case ItemFactory.CTRL_NORTH -> new Vector(0, 0, -step);
            case ItemFactory.CTRL_SOUTH -> new Vector(0, 0, step);
            case ItemFactory.CTRL_WEST -> new Vector(-step, 0, 0);
            case ItemFactory.CTRL_EAST -> new Vector(step, 0, 0);
            case ItemFactory.CTRL_LOWER -> new Vector(0, -vStep, 0);
            case ItemFactory.CTRL_RAISE -> new Vector(0, vStep, 0);
            default -> null;
        };
        if (delta == null) {
            return true;
        }

        Location next = current.clone().add(delta);
        next.setX(clamp(next.getX(), bounds.getMinX(), bounds.getMaxX()));
        next.setY(clamp(next.getY(), bounds.getMinY(), bounds.getMaxY()));
        next.setZ(clamp(next.getZ(), bounds.getMinZ(), bounds.getMaxZ()));
        next.setYaw(current.getYaw());
        next.setPitch(current.getPitch());

        pluginMoving.add(claw.getUniqueId());
        try {
            claw.teleport(next);
            claw.setFlying(true);
        } finally {
            pluginMoving.remove(claw.getUniqueId());
        }
        operator.playSound(operator.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);

        if (session.isHolding()) {
            maybeSlip(session, claw, arena);
            syncHeldPrize(session, claw);
            tryDrop(session, claw, arena);
        }

        updateActionBars(session);
        return true;
    }

    public boolean handleGrab(Player clawPlayer) {
        GameSession session = byPlayer.get(clawPlayer.getUniqueId());
        if (session == null || session.isFinished() || session.getState() != GameSession.State.PLAYING) {
            return false;
        }
        if (!session.isClaw(clawPlayer.getUniqueId())) {
            return false;
        }
        if (session.isHolding()) {
            plugin.getMessageService().send(clawPlayer, "already-holding");
            return true;
        }

        Arena arena = plugin.getArenaManager().get(session.getArenaName()).orElse(null);
        if (arena == null) {
            return true;
        }

        double radius = plugin.getConfig().getDouble("grab-radius", 1.15);
        Optional<ItemDisplay> nearby = findNearbyPrize(session, clawPlayer.getLocation(), radius);
        if (nearby.isEmpty()) {
            plugin.getMessageService().send(clawPlayer, "grab-too-far");
            clawPlayer.playSound(clawPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
            return true;
        }

        boolean lowered = isLowered(session, clawPlayer);
        if (!lowered && plugin.getConfig().getDouble("grab-lowered-bonus", 0.18) > 0) {
            // Soft hint — still allow attempt, but much harder
            plugin.getMessageService().send(clawPlayer, "grab-not-lowered");
        }

        double chance = plugin.getConfig().getDouble("grab-success-chance", 0.38);
        if (lowered) {
            chance += plugin.getConfig().getDouble("grab-lowered-bonus", 0.18);
        }
        chance = clamp(chance, 0.05, 0.95);

        ItemDisplay display = nearby.get();
        Integer index = display.getPersistentDataContainer().get(plugin.getItemFactory().getPrizeKey(), PersistentDataType.INTEGER);
        if (index == null || session.isCollected(index)) {
            plugin.getMessageService().send(clawPlayer, "grab-too-far");
            return true;
        }

        Random random = ThreadLocalRandom.current();
        if (random.nextDouble() > chance) {
            plugin.getMessageService().send(clawPlayer, "prize-missed");
            clawPlayer.playSound(clawPlayer.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.6f);
            clawPlayer.getWorld().spawnParticle(Particle.SMOKE, clawPlayer.getLocation().add(0, 1, 0), 8, 0.2, 0.2, 0.2, 0.01);
            notifyOperator(session, "prize-missed");
            return true;
        }

        session.setHeldPrizeIndex(index);
        syncHeldPrize(session, clawPlayer);
        clawPlayer.playSound(clawPlayer.getLocation(), Sound.BLOCK_METAL_HIT, 1.0f, 1.3f);
        clawPlayer.getWorld().spawnParticle(Particle.CRIT, clawPlayer.getLocation().add(0, 1, 0), 16, 0.25, 0.25, 0.25, 0.05);
        plugin.getMessageService().send(clawPlayer, "prize-grabbed");
        notifyOperator(session, "prize-grabbed");
        updateActionBars(session);
        return true;
    }

    private void maybeSlip(GameSession session, Player claw, Arena arena) {
        double slip = plugin.getConfig().getDouble("slip-chance-while-moving", 0.12);
        if (slip <= 0 || !session.isHolding()) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() > slip) {
            return;
        }
        Integer index = session.getHeldPrizeIndex();
        session.setHeldPrizeIndex(null);
        if (index != null && index >= 0 && index < session.getPrizeDisplays().size()) {
            ItemDisplay display = session.getPrizeDisplays().get(index);
            if (display != null && !display.isDead()) {
                Location dropAt = claw.getLocation().clone().add(0, -0.4, 0);
                BoundingBox bounds = arena.getBounds();
                if (bounds != null) {
                    dropAt.setY(Math.max(bounds.getMinY() + 0.2, dropAt.getY()));
                }
                display.teleport(dropAt);
            }
        }
        claw.playSound(claw.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 0.5f);
        plugin.getMessageService().send(claw, "prize-slipped");
        notifyOperator(session, "prize-slipped");
    }

    private void tryDrop(GameSession session, Player claw, Arena arena) {
        if (!session.isHolding()) {
            return;
        }
        Location chute = arena.getDropChute();
        if (chute == null || chute.getWorld() == null) {
            return;
        }
        double dropRadius = plugin.getConfig().getDouble("drop-radius", 1.4);
        if (claw.getLocation().distanceSquared(chute) > dropRadius * dropRadius) {
            return;
        }

        Integer index = session.getHeldPrizeIndex();
        session.setHeldPrizeIndex(null);
        if (index == null || !session.markCollected(index)) {
            return;
        }

        ItemDisplay display = index < session.getPrizeDisplays().size() ? session.getPrizeDisplays().get(index) : null;
        if (display != null && !display.isDead()) {
            display.remove();
        }

        int points = session.getPrizePoints();
        session.addPointsEarned(points);

        Player operator = Bukkit.getPlayer(session.getOperatorId());
        Map<String, String> placeholders = Map.of(
                "points", String.valueOf(points),
                "prizes", String.valueOf(session.getCollectedCount()),
                "total", String.valueOf(session.getTotalPrizes())
        );
        plugin.getMessageService().send(claw, "prize-dropped", placeholders);
        if (operator != null) {
            plugin.getMessageService().send(operator, "prize-dropped", placeholders);
        }
        claw.playSound(claw.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        claw.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, chute.clone().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.02);

        updateActionBars(session);

        if (session.allCollected()) {
            endSession(session, true, "win-clear");
        }
    }

    private void syncHeldPrize(GameSession session, Player claw) {
        Integer index = session.getHeldPrizeIndex();
        if (index == null) {
            return;
        }
        if (index < 0 || index >= session.getPrizeDisplays().size()) {
            return;
        }
        ItemDisplay display = session.getPrizeDisplays().get(index);
        if (display == null || display.isDead()) {
            return;
        }
        Location attach = claw.getLocation().clone().add(0, -0.55, 0);
        display.teleport(attach);
    }

    private boolean isLowered(GameSession session, Player claw) {
        double threshold = plugin.getConfig().getDouble("lowered-threshold", 1.25);
        return claw.getLocation().getY() <= session.getRaisedY() - threshold;
    }

    public Optional<ItemDisplay> findNearbyPrize(GameSession session, Location location, double radius) {
        double radiusSq = radius * radius;
        ItemDisplay closest = null;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < session.getPrizeDisplays().size(); i++) {
            if (session.isCollected(i) || (session.getHeldPrizeIndex() != null && session.getHeldPrizeIndex() == i)) {
                continue;
            }
            ItemDisplay display = session.getPrizeDisplays().get(i);
            if (display == null || display.isDead()) {
                continue;
            }
            double dist = display.getLocation().distanceSquared(location);
            if (dist <= radiusSq && dist < best) {
                best = dist;
                closest = display;
            }
        }
        return Optional.ofNullable(closest);
    }

    public void preview(Arena arena) {
        clearPreview(arena.getName());
        List<ItemDisplay> spawned = spawnDisplays(arena.getPrizes(), true);
        previews.put(arena.getName(), spawned);
    }

    public void clearPreview(String arenaName) {
        List<ItemDisplay> list = previews.remove(arenaName.toLowerCase());
        if (list == null) {
            return;
        }
        for (ItemDisplay display : list) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
    }

    public void shutdown() {
        for (GameSession session : List.copyOf(byArena.values())) {
            endSession(session, false, null);
        }
        for (String arena : List.copyOf(previews.keySet())) {
            clearPreview(arena);
        }
    }

    private void tick(GameSession session) {
        if (session.isFinished() || session.getState() != GameSession.State.PLAYING) {
            return;
        }
        Player operator = Bukkit.getPlayer(session.getOperatorId());
        Player claw = Bukkit.getPlayer(session.getClawId());
        if (operator == null || !operator.isOnline() || claw == null || !claw.isOnline()) {
            endSession(session, false, "partner-left");
            return;
        }

        if (session.isHolding()) {
            syncHeldPrize(session, claw);
            plugin.getArenaManager().get(session.getArenaName()).ifPresent(arena -> tryDrop(session, claw, arena));
        }

        session.decrementSecond();
        updateActionBars(session);

        if (session.getRemainingSeconds() <= 0) {
            endSession(session, false, "time-up");
        }
    }

    private void endSession(GameSession session, boolean cleared, String messageKey) {
        if (session.isFinished()) {
            return;
        }
        session.setFinished(true);

        if (session.getTickTask() != null) {
            session.getTickTask().cancel();
        }

        for (ItemDisplay display : session.getPrizeDisplays()) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }

        Player operator = session.getOperatorId() == null ? null : Bukkit.getPlayer(session.getOperatorId());
        Player claw = session.getClawId() == null ? null : Bukkit.getPlayer(session.getClawId());

        int points = session.getPointsEarned();
        if (cleared) {
            points += session.getClearBonusPoints();
            session.addPointsEarned(session.getClearBonusPoints());
        }

        if (points > 0) {
            if (operator != null) {
                award(operator, session, points);
            }
            if (claw != null) {
                award(claw, session, points);
            }
        }

        cleanupPlayer(operator, session);
        cleanupPlayer(claw, session);

        byArena.remove(session.getArenaName());
        if (session.getOperatorId() != null) {
            byPlayer.remove(session.getOperatorId());
        }
        if (session.getClawId() != null) {
            byPlayer.remove(session.getClawId());
        }

        Map<String, String> placeholders = Map.of(
                "points", String.valueOf(points),
                "prizes", String.valueOf(session.getCollectedCount()),
                "total", String.valueOf(session.getTotalPrizes()),
                "arena", session.getArenaName()
        );

        if (messageKey != null) {
            if ("left".equals(messageKey)) {
                // only announce leave to the leaver via leave(); partner gets partner-left
            } else if ("partner-left".equals(messageKey)) {
                if (operator != null) {
                    plugin.getMessageService().send(operator, messageKey, placeholders);
                }
                if (claw != null) {
                    plugin.getMessageService().send(claw, messageKey, placeholders);
                }
            } else {
                if (operator != null) {
                    plugin.getMessageService().send(operator, messageKey, placeholders);
                }
                if (claw != null) {
                    plugin.getMessageService().send(claw, messageKey, placeholders);
                }
            }
        }
    }

    private void award(Player player, GameSession session, int points) {
        plugin.getPointsService().addPoints(player, points);
        if (plugin.getConfig().getBoolean("reward.deposit-to-vault", false)) {
            plugin.getEconomyService().deposit(player, points);
        }
        runRewardCommands(player, session, points);
    }

    private void runRewardCommands(Player player, GameSession session, int points) {
        List<String> commands = plugin.getConfig().getStringList("reward.commands");
        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }
            String parsed = command
                    .replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("{points}", String.valueOf(points))
                    .replace("{arena}", session.getArenaName())
                    .replace("{prizes}", String.valueOf(session.getCollectedCount()));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    private void cleanupPlayer(Player player, GameSession session) {
        if (player == null) {
            return;
        }
        if (plugin.getConfig().getBoolean("clear-items-on-end", true)) {
            clearGameItems(player);
        }
        player.setFlySpeed(0.1f);
        player.setWalkSpeed(0.2f);
        player.setAllowFlight(player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR);
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
        }
        player.sendActionBar(net.kyori.adventure.text.Component.empty());

        if (plugin.getConfig().getBoolean("teleport-on-end", true)) {
            plugin.getArenaManager().get(session.getArenaName()).ifPresent(arena -> {
                Location lobby = arena.getLobby();
                if (lobby == null) {
                    lobby = arena.getOperatorSpawn();
                }
                if (lobby != null) {
                    player.teleport(lobby);
                }
            });
        }
    }

    private void giveOperatorControls(Player player) {
        clearGameItems(player);
        player.getInventory().setItem(0, plugin.getItemFactory().createControl(ItemFactory.CTRL_NORTH));
        player.getInventory().setItem(1, plugin.getItemFactory().createControl(ItemFactory.CTRL_SOUTH));
        player.getInventory().setItem(2, plugin.getItemFactory().createControl(ItemFactory.CTRL_WEST));
        player.getInventory().setItem(3, plugin.getItemFactory().createControl(ItemFactory.CTRL_EAST));
        player.getInventory().setItem(4, plugin.getItemFactory().createControl(ItemFactory.CTRL_LOWER));
        player.getInventory().setItem(5, plugin.getItemFactory().createControl(ItemFactory.CTRL_RAISE));
    }

    private void giveClawGrab(Player player) {
        clearGameItems(player);
        player.getInventory().setItem(0, plugin.getItemFactory().createGrabItem());
    }

    private void clearGameItems(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (plugin.getItemFactory().isClawGameItem(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private void spawnPrizes(GameSession session, List<Location> locations) {
        float scale = (float) plugin.getConfig().getDouble("prize.scale", 1.0);
        double yOffset = plugin.getConfig().getDouble("prize.y-offset", 0.0);
        ItemStack item = plugin.getItemFactory().createPrizeItem();

        for (int i = 0; i < locations.size(); i++) {
            Location base = locations.get(i);
            if (base == null || base.getWorld() == null) {
                session.getPrizeDisplays().add(null);
                continue;
            }
            Location spawnAt = base.clone().add(0, yOffset, 0);
            final int index = i;
            ItemDisplay display = spawnAt.getWorld().spawn(spawnAt, ItemDisplay.class, entity -> {
                entity.setItemStack(item.clone());
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(0f, 0f, 1f, 0f),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f(0f, 0f, 1f, 0f)
                ));
                entity.setPersistent(false);
                entity.setInvulnerable(true);
                entity.getPersistentDataContainer().set(plugin.getItemFactory().getPrizeKey(), PersistentDataType.INTEGER, index);
            });
            session.getPrizeDisplays().add(display);
        }
    }

    private List<ItemDisplay> spawnDisplays(List<Location> locations, boolean preview) {
        float scale = (float) plugin.getConfig().getDouble("prize.scale", 1.0);
        double yOffset = plugin.getConfig().getDouble("prize.y-offset", 0.0);
        ItemStack item = plugin.getItemFactory().createPrizeItem();
        java.util.ArrayList<ItemDisplay> spawned = new java.util.ArrayList<>();

        for (int i = 0; i < locations.size(); i++) {
            Location base = locations.get(i);
            if (base == null || base.getWorld() == null) {
                continue;
            }
            Location spawnAt = base.clone().add(0, yOffset, 0);
            final int index = i;
            ItemDisplay display = spawnAt.getWorld().spawn(spawnAt, ItemDisplay.class, entity -> {
                entity.setItemStack(item.clone());
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(0f, 0f, 1f, 0f),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f(0f, 0f, 1f, 0f)
                ));
                entity.setPersistent(false);
                entity.setInvulnerable(true);
                if (preview) {
                    entity.getPersistentDataContainer().set(plugin.getItemFactory().getPrizeKey(), PersistentDataType.INTEGER, index);
                }
            });
            spawned.add(display);
        }
        return spawned;
    }

    private void updateActionBars(GameSession session) {
        Player operator = session.getOperatorId() == null ? null : Bukkit.getPlayer(session.getOperatorId());
        Player claw = session.getClawId() == null ? null : Bukkit.getPlayer(session.getClawId());

        Map<String, String> base = Map.of(
                "remaining", String.valueOf(Math.max(0, session.getRemainingSeconds())),
                "prizes", String.valueOf(session.getCollectedCount()),
                "total", String.valueOf(session.getTotalPrizes()),
                "arena", session.getArenaName(),
                "holding", session.isHolding() ? "HOLDING PRIZE" : "ready to grab"
        );

        if (operator != null) {
            String template = plugin.getConfig().getString("action-bar-operator",
                    "&dTheClaw &8| &e{remaining}s &8| &f{prizes}/{total} &8| &aJoystick");
            plugin.getMessageService().actionBar(operator, plugin.getMessageService().apply(template, base));
        }
        if (claw != null) {
            String template = plugin.getConfig().getString("action-bar-claw",
                    "&dTheClaw &8| &e{remaining}s &8| &f{prizes}/{total} &8| &b{holding}");
            plugin.getMessageService().actionBar(claw, plugin.getMessageService().apply(template, base));
        }
    }

    private void notifyOperator(GameSession session, String key) {
        Player operator = session.getOperatorId() == null ? null : Bukkit.getPlayer(session.getOperatorId());
        if (operator != null) {
            plugin.getMessageService().send(operator, key);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
