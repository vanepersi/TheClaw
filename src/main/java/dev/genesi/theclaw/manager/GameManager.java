package dev.genesi.theclaw.manager;

import dev.genesi.theclaw.TheClawPlugin;
import dev.genesi.theclaw.model.Arena;
import dev.genesi.theclaw.model.GameSession;
import dev.genesi.theclaw.scoreboard.ScoreboardService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class GameManager {

    private final TheClawPlugin plugin;
    private final ScoreboardService scoreboards;
    private final Map<String, GameSession> byArena = new HashMap<>();
    private final Map<UUID, GameSession> byPlayer = new HashMap<>();
    private final Map<String, List<ItemDisplay>> previews = new HashMap<>();

    public GameManager(TheClawPlugin plugin) {
        this.plugin = plugin;
        this.scoreboards = new ScoreboardService(plugin);
    }

    public ScoreboardService getScoreboards() {
        return scoreboards;
    }

    public Optional<GameSession> getByPlayer(UUID uuid) {
        return Optional.ofNullable(byPlayer.get(uuid));
    }

    public Optional<GameSession> getByArena(String arenaName) {
        return Optional.ofNullable(byArena.get(arenaName.toLowerCase()));
    }

    public Optional<Arena> findArenaByMachine(Location clickedBlock) {
        if (clickedBlock == null || clickedBlock.getWorld() == null) {
            return Optional.empty();
        }
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            Location machine = arena.getMachineBlock();
            if (machine == null || machine.getWorld() == null) {
                continue;
            }
            if (machine.getWorld().equals(clickedBlock.getWorld())
                    && machine.getBlockX() == clickedBlock.getBlockX()
                    && machine.getBlockY() == clickedBlock.getBlockY()
                    && machine.getBlockZ() == clickedBlock.getBlockZ()) {
                return Optional.of(arena);
            }
        }
        return Optional.empty();
    }

    public String clickMachine(Player player, Arena arena) {
        if (byPlayer.containsKey(player.getUniqueId())) {
            return "already-playing";
        }
        if (!arena.isReady()) {
            return "arena-not-ready";
        }

        GameSession existing = byArena.get(arena.getName());
        if (existing != null && existing.getState() != GameSession.State.WAITING) {
            return "arena-busy";
        }
        if (existing != null && existing.isFull()) {
            return "arena-busy";
        }

        if (existing == null) {
            int duration = plugin.getArenaManager().resolveDuration(arena);
            int prizePoints = plugin.getArenaManager().resolvePrizePoints(arena);
            int clearBonus = plugin.getArenaManager().resolveClearBonus(arena);
            existing = new GameSession(arena.getName(), arena.getPrizes().size(), duration, prizePoints, clearBonus);
            byArena.put(arena.getName(), existing);
        }

        if (existing.getOperatorId() == null) {
            existing.setOperator(player);
            byPlayer.put(player.getUniqueId(), existing);
            plugin.getMessageService().sendPlain(player, "machine-needs-claw");
            scoreboards.show(player, existing, true);
            return "ok-waiting";
        }

        // Second clicker is always the claw.
        existing.setClaw(player);
        byPlayer.put(player.getUniqueId(), existing);
        plugin.getMessageService().send(player, "thanks-human-claw");
        beginMatch(existing, arena);
        return "ok-start";
    }

    private void beginMatch(GameSession session, Arena arena) {
        clearPreview(arena.getName());
        session.setState(GameSession.State.COUNTDOWN);

        Player operator = Bukkit.getPlayer(session.getOperatorId());
        Player claw = Bukkit.getPlayer(session.getClawId());
        if (operator == null || claw == null) {
            endSession(session, false, "partner-left", null);
            return;
        }

        spawnPrizes(session, arena.getPrizes());

        Location clawSpawn = arena.getClawSpawn();
        if (clawSpawn != null) {
            claw.teleport(clawSpawn);
        }
        // Face straight up so they cannot see the prize pit.
        float yaw = clawSpawn != null ? clawSpawn.getYaw() : claw.getLocation().getYaw();
        session.lockClawLook(yaw, -90f);

        animateClawScaleIn(session, claw);
        applyClawEffects(claw);
        keepClawFloating(claw);
        spawnClawVisual(session, claw);

        scoreboards.show(operator, session, true);
        scoreboards.show(claw, session, false);

        int countdown = plugin.getConfig().getInt("countdown-seconds", 3);
        runCountdown(session, arena, countdown);
    }

    private void runCountdown(GameSession session, Arena arena, int secondsLeft) {
        if (session.isFinished()) {
            return;
        }
        Player operator = Bukkit.getPlayer(session.getOperatorId());
        Player claw = Bukkit.getPlayer(session.getClawId());
        if (operator == null || claw == null) {
            endSession(session, false, "partner-left", null);
            return;
        }

        if (secondsLeft <= 0) {
            showTitle(operator, "&aGO!", "&7Guide the claw");
            showTitle(claw, "&aGO!", "&7Follow the signals");
            session.setState(GameSession.State.GUIDING);
            session.setTickTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(session), 1L, 1L));
            return;
        }

        showTitle(operator, "&e" + secondsLeft, "&7Get on the control pad");
        showTitle(claw, "&e" + secondsLeft, "&7Get ready...");
        Bukkit.getScheduler().runTaskLater(plugin, () -> runCountdown(session, arena, secondsLeft - 1), 20L);
    }

    private void tick(GameSession session) {
        if (session.isFinished()) {
            return;
        }
        Player operator = Bukkit.getPlayer(session.getOperatorId());
        Player claw = Bukkit.getPlayer(session.getClawId());
        if (operator == null || !operator.isOnline() || claw == null || !claw.isOnline()) {
            endSession(session, false, "partner-left", null);
            return;
        }

        Arena arena = plugin.getArenaManager().get(session.getArenaName()).orElse(null);
        if (arena == null) {
            endSession(session, false, null, null);
            return;
        }

        lockClawLook(session, claw);
        keepClawFloating(claw);
        keepClawInBounds(session, claw, arena);
        syncClawVisual(session, claw);
        if (session.isHolding()) {
            syncHeldPrize(session, claw);
            tryDeliverPrize(session, claw, arena);
        }

        if (session.getState() == GameSession.State.GUIDING) {
            tickGuiding(session, operator, claw, arena);
        } else if (session.getState() == GameSession.State.SYNC) {
            tickSync(session, operator, claw, arena);
            // Keep the number on screen longer by refreshing the title.
            if (session.getSyncNumber() != null && Bukkit.getCurrentTick() % 15 == 0) {
                refreshSyncTitle(session, operator, claw);
            }
        }

        if (Bukkit.getCurrentTick() % 20 == 0) {
            scoreboards.update(operator, session, true);
            scoreboards.update(claw, session, false);
        }
    }

    private void tickGuiding(GameSession session, Player operator, Player claw, Arena arena) {
        double maxDist = plugin.getConfig().getDouble("operator-max-distance", 6.0);
        Location center = arena.getMachineCenter();
        if (center != null && operator.getWorld().equals(center.getWorld())
                && operator.getLocation().distanceSquared(center) > maxDist * maxDist) {
            plugin.getMessageService().send(operator, "too-far");
            plugin.getMessageService().send(claw, "partner-too-far");
            endSession(session, false, "ended-too-far", null);
            return;
        }

        if (!isOnControlPad(operator, arena)) {
            if (session.getOffPadSeconds() < 0) {
                session.setOffPadSeconds(plugin.getConfig().getInt("off-pad-grace-seconds", 10));
                showTitle(operator, "&cHurry back!", "&7Stand on the control pad");
            } else if (Bukkit.getCurrentTick() % 20 == 0) {
                session.setOffPadSeconds(session.getOffPadSeconds() - 1);
                showTitle(operator, "&cHurry back!", "&e" + session.getOffPadSeconds() + "s");
                if (session.getOffPadSeconds() <= 0) {
                    endSession(session, false, "ended-left-pad", null);
                    return;
                }
            }
        } else {
            session.setOffPadSeconds(-1);
            pollOperatorInput(session, operator);
        }

        if (Bukkit.getCurrentTick() % 20 == 0) {
            session.decrementSecond();
            if (session.getRemainingSeconds() <= 0) {
                beginDrop(session, operator, claw, arena);
            }
        }
    }

    private void pollOperatorInput(GameSession session, Player operator) {
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("signal-cooldown-millis", 350L);
        if (now - session.getLastSignalMillis() < cooldown) {
            return;
        }

        GameSession.Signal signal = null;
        try {
            var input = operator.getCurrentInput();
            if (input.isForward()) {
                signal = GameSession.Signal.FORWARD;
            } else if (input.isBackward()) {
                signal = GameSession.Signal.BACK;
            } else if (input.isLeft()) {
                signal = GameSession.Signal.LEFT;
            } else if (input.isRight()) {
                signal = GameSession.Signal.RIGHT;
            }
        } catch (Throwable ignored) {
            // Input API unavailable in some environments.
        }

        if (signal != null) {
            sendSignal(session, signal);
        }
    }

    public void handleScrollSignal(Player operator, boolean forward, boolean sneak) {
        GameSession session = byPlayer.get(operator.getUniqueId());
        if (session == null || session.getState() != GameSession.State.GUIDING) {
            return;
        }
        if (!session.isOperator(operator.getUniqueId())) {
            return;
        }
        Arena arena = plugin.getArenaManager().get(session.getArenaName()).orElse(null);
        if (arena == null || !isOnControlPad(operator, arena)) {
            return;
        }

        GameSession.Signal signal;
        if (sneak) {
            signal = forward ? GameSession.Signal.RIGHT : GameSession.Signal.LEFT;
        } else {
            signal = forward ? GameSession.Signal.FORWARD : GameSession.Signal.BACK;
        }
        sendSignal(session, signal);
    }

    private void sendSignal(GameSession session, GameSession.Signal signal) {
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("signal-cooldown-millis", 350L);
        if (now - session.getLastSignalMillis() < cooldown) {
            return;
        }
        session.setLastSignal(signal, now);

        Player claw = Bukkit.getPlayer(session.getClawId());
        Player operator = Bukkit.getPlayer(session.getOperatorId());
        if (claw == null) {
            return;
        }

        String titleKey = switch (signal) {
            case FORWARD -> "signal-forward";
            case BACK -> "signal-back";
            case LEFT -> "signal-left";
            case RIGHT -> "signal-right";
        };
        String title = plugin.getConfig().getString("messages." + titleKey, signal.name());
        showTitle(claw, title, plugin.getConfig().getString("messages.signal-subtitle", "&7Move that way!"));
        claw.playSound(claw.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
        if (operator != null) {
            operator.playSound(operator.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.6f);
        }
    }

    private void beginDrop(GameSession session, Player operator, Player claw, Arena arena) {
        session.setState(GameSession.State.DROPPING);
        showTitle(operator, "&6CLAW DROP!", "&7Sync challenge incoming");
        showTitle(claw, "&6DROPPING!", "&7Get ready to sync");
        claw.getWorld().playSound(claw.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1.0f, 0.7f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.isFinished()) {
                return;
            }
            startSyncChallenge(session, operator, claw, arena);
        }, plugin.getConfig().getLong("drop-windup-ticks", 30L));
    }

    private void startSyncChallenge(GameSession session, Player operator, Player claw, Arena arena) {
        if (session.isFinished()) {
            return;
        }
        session.setState(GameSession.State.SYNC);
        int number = ThreadLocalRandom.current().nextInt(1, 10);
        int ticks = plugin.getConfig().getInt("sync-window-ticks", 60);
        session.beginSync(number, ticks);

        refreshSyncTitle(session, operator, claw);
        operator.playSound(operator.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        claw.playSound(claw.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
    }

    private void refreshSyncTitle(GameSession session, Player operator, Player claw) {
        Integer number = session.getSyncNumber();
        if (number == null) {
            return;
        }
        String main = plugin.getMessageService().apply(
                plugin.getConfig().getString("messages.sync-title", "&ePRESS &f{number}&e!"),
                Map.of("number", String.valueOf(number))
        );
        String sub = plugin.getMessageService().apply(
                plugin.getConfig().getString("messages.sync-subtitle", "&7Both players — press hotbar &f{number}"),
                Map.of("number", String.valueOf(number))
        );
        long stayMs = plugin.getConfig().getLong("sync-title-stay-millis", 3500L);
        showTitle(operator, main, sub, stayMs);
        showTitle(claw, main, sub, stayMs);
    }

    private void tickSync(GameSession session, Player operator, Player claw, Arena arena) {
        session.decrementSyncTick();
        if (session.bothSynced()) {
            resolveSync(session, operator, claw, arena, true);
            return;
        }
        if (session.getSyncTicksLeft() <= 0) {
            resolveSync(session, operator, claw, arena, false);
        }
    }

    public void handleHotbarPress(Player player, int newSlot) {
        GameSession session = byPlayer.get(player.getUniqueId());
        if (session == null || session.getState() != GameSession.State.SYNC) {
            return;
        }
        Integer needed = session.getSyncNumber();
        if (needed == null) {
            return;
        }
        // Hotbar slots are 0-8 → keys 1-9
        int pressed = newSlot + 1;
        if (pressed != needed) {
            return;
        }
        if (session.markSynced(player.getUniqueId())) {
            plugin.getMessageService().send(player, "sync-locked-in", Map.of("number", String.valueOf(needed)));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
        }
        if (session.bothSynced()) {
            Player operator = Bukkit.getPlayer(session.getOperatorId());
            Player claw = Bukkit.getPlayer(session.getClawId());
            Arena arena = plugin.getArenaManager().get(session.getArenaName()).orElse(null);
            if (operator != null && claw != null && arena != null) {
                resolveSync(session, operator, claw, arena, true);
            }
        }
    }

    private void resolveSync(GameSession session, Player operator, Player claw, Arena arena, boolean bothPressed) {
        if (session.getState() != GameSession.State.SYNC) {
            return;
        }
        session.clearSync();

        // Correct + fast sync = claw FAILS (you lose) — rigged like a real machine.
        boolean canGrab = !bothPressed;
        if (plugin.getConfig().getBoolean("sync-success-means-grab", false)) {
            canGrab = bothPressed;
        }

        if (!canGrab) {
            plugin.getMessageService().send(operator, "sync-lose");
            plugin.getMessageService().send(claw, "sync-lose");
            showTitle(operator, "&cSLIP!", "&7The claw failed...");
            showTitle(claw, "&cSLIP!", "&7Nothing stuck");
            claw.playSound(claw.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.6f);
            endSession(session, false, "round-over-miss", null);
            return;
        }

        // Sync passed — claw must shift-click a nearby prize to pick it up.
        plugin.getMessageService().send(operator, "sync-ok-grab");
        plugin.getMessageService().send(claw, "sync-ok-grab-claw");
        showTitle(operator, "&aNOW!", "&7Guide them onto a prize");
        showTitle(claw, "&aSHIFT + CLICK!", "&7Pick up a prize");
        session.setState(GameSession.State.GUIDING);
        session.setRemainingSeconds(plugin.getConfig().getInt("deliver-seconds", 25));
    }

    public boolean handleClawShiftClick(Player clawPlayer) {
        GameSession session = byPlayer.get(clawPlayer.getUniqueId());
        if (session == null || session.isFinished()) {
            return false;
        }
        if (!session.isClaw(clawPlayer.getUniqueId())) {
            return false;
        }
        if (session.getState() != GameSession.State.GUIDING) {
            return false;
        }
        if (session.isHolding()) {
            plugin.getMessageService().send(clawPlayer, "already-holding");
            return true;
        }

        double radius = plugin.getConfig().getDouble("grab-radius", 1.75);
        Optional<ItemDisplay> nearby = findNearbyPrize(session, clawPlayer.getLocation(), radius);
        if (nearby.isEmpty()) {
            plugin.getMessageService().send(clawPlayer, "grab-too-far");
            clawPlayer.playSound(clawPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
            return true;
        }

        ItemDisplay display = nearby.get();
        Integer index = display.getPersistentDataContainer().get(
                plugin.getItemFactory().getPrizeKey(),
                org.bukkit.persistence.PersistentDataType.INTEGER
        );
        if (index == null || session.isCollected(index)) {
            plugin.getMessageService().send(clawPlayer, "grab-too-far");
            return true;
        }

        session.setHeldPrizeIndex(index);
        syncHeldPrize(session, clawPlayer);
        Player operator = Bukkit.getPlayer(session.getOperatorId());
        plugin.getMessageService().send(clawPlayer, "prize-grabbed");
        if (operator != null) {
            plugin.getMessageService().send(operator, "prize-grabbed");
        }
        showTitle(clawPlayer, "&aHOLDING!", "&7Get to the drop opening");
        if (operator != null) {
            showTitle(operator, "&aHOLDING!", "&7Guide them to the drop");
        }
        clawPlayer.playSound(clawPlayer.getLocation(), Sound.BLOCK_METAL_HIT, 1.0f, 1.3f);
        return true;
    }

    private void tryDeliverPrize(GameSession session, Player claw, Arena arena) {
        if (!session.isHolding()) {
            return;
        }
        Integer index = session.getHeldPrizeIndex();
        if (index == null || index < 0 || index >= session.getPrizeDisplays().size()) {
            session.setHeldPrizeIndex(null);
            return;
        }
        ItemDisplay display = session.getPrizeDisplays().get(index);
        if (display == null || display.isDead()) {
            session.setHeldPrizeIndex(null);
            return;
        }

        Location chute = arena.getDropChute();
        if (chute == null || chute.getWorld() == null || !claw.getWorld().equals(chute.getWorld())) {
            return;
        }

        // Must be on the drop chute block itself — not just "nearby".
        Location feet = claw.getLocation();
        if (feet.getBlockX() != chute.getBlockX()
                || feet.getBlockZ() != chute.getBlockZ()
                || Math.abs(feet.getBlockY() - chute.getBlockY()) > 1) {
            return;
        }

        session.setHeldPrizeIndex(null);
        if (!session.markCollected(index)) {
            return;
        }
        detachDisplay(claw, display);
        display.remove();

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
        claw.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, chute.clone().add(0.5, 0.5, 0.5), 18, 0.25, 0.25, 0.25, 0.02);
        claw.playSound(claw.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);

        if (session.allCollected()) {
            endSession(session, true, "win-clear", null);
        } else {
            endSession(session, false, "round-over-win", null);
        }
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
            double refund = session.takeFeePaid(player.getUniqueId());
            if (refund > 0) {
                plugin.getEconomyService().deposit(player, refund);
            }
            session.clearPlayer(player.getUniqueId());
            byPlayer.remove(player.getUniqueId());
            scoreboards.clear(player);
            if (announce) {
                plugin.getMessageService().send(player, "left");
            }
            if (session.playerCount() == 0) {
                byArena.remove(session.getArenaName());
            }
            return;
        }

        if (announce) {
            plugin.getMessageService().send(player, "left");
        }
        endSession(session, false, "partner-left", player.getUniqueId());
    }

    public void forceStop(Arena arena) {
        GameSession session = byArena.get(arena.getName());
        if (session != null) {
            endSession(session, false, null, null);
        }
    }

    public void shutdown() {
        for (GameSession session : List.copyOf(byArena.values())) {
            endSession(session, false, null, null);
        }
        for (String arena : List.copyOf(previews.keySet())) {
            clearPreview(arena);
        }
        scoreboards.clearAll();
    }

    public void preview(Arena arena) {
        clearPreview(arena.getName());
        previews.put(arena.getName(), spawnDisplays(arena.getPrizes(), true));
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

    private void endSession(GameSession session, boolean cleared, String messageKey, UUID excludeMessage) {
        if (session.isFinished()) {
            return;
        }
        session.setFinished(true);
        if (session.getTickTask() != null) {
            session.getTickTask().cancel();
        }

        Player operator = session.getOperatorId() == null ? null : Bukkit.getPlayer(session.getOperatorId());
        Player claw = session.getClawId() == null ? null : Bukkit.getPlayer(session.getClawId());

        for (ItemDisplay display : session.getPrizeDisplays()) {
            if (display != null && !display.isDead()) {
                if (claw != null) {
                    detachDisplay(claw, display);
                }
                display.remove();
            }
        }
        ItemDisplay clawVisual = session.getClawVisual();
        if (clawVisual != null && !clawVisual.isDead()) {
            if (claw != null) {
                detachDisplay(claw, clawVisual);
            }
            clawVisual.remove();
        }
        session.setClawVisual(null);

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

        cleanupPlayer(operator, session, false);
        cleanupPlayer(claw, session, true);

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
            if (operator != null && !Objects.equals(operator.getUniqueId(), excludeMessage)) {
                plugin.getMessageService().send(operator, messageKey, placeholders);
            }
            if (claw != null && !Objects.equals(claw.getUniqueId(), excludeMessage)) {
                plugin.getMessageService().send(claw, messageKey, placeholders);
            }
        }
    }

    private void award(Player player, GameSession session, int points) {
        plugin.getPointsService().addPoints(player, points);
        if (plugin.getConfig().getBoolean("reward.deposit-to-vault", false)) {
            plugin.getEconomyService().deposit(player, points);
        }
    }

    private void cleanupPlayer(Player player, GameSession session, boolean wasClaw) {
        if (player == null) {
            return;
        }
        scoreboards.clear(player);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.DARKNESS);
        if (wasClaw) {
            restoreScale(player, session.getOriginalClawScale());
            player.setGravity(true);
        }
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setAllowFlight(player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR);
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
        }
        player.sendActionBar(net.kyori.adventure.text.Component.empty());
        player.clearTitle();
    }

    private void animateClawScaleIn(GameSession session, Player claw) {
        AttributeInstance scale = claw.getAttribute(Attribute.SCALE);
        if (scale == null) {
            return;
        }
        session.setOriginalClawScale(scale.getBaseValue());
        double target = plugin.getConfig().getDouble("claw-scale", 0.22);
        scale.setBaseValue(target);
        claw.getWorld().spawnParticle(Particle.CLOUD, claw.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
        claw.playSound(claw.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.2f);
    }

    private void restoreScale(Player player, double original) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(original <= 0 ? 1.0 : original);
        }
    }

    private void applyClawEffects(Player claw) {
        claw.setGameMode(GameMode.ADVENTURE);
        claw.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 1, false, false, true));
        claw.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        claw.setWalkSpeed((float) plugin.getConfig().getDouble("claw-walk-speed", 0.14));
        claw.setFlySpeed((float) plugin.getConfig().getDouble("claw-fly-speed", 0.06));
        keepClawFloating(claw);
    }

    private void keepClawFloating(Player claw) {
        if (claw == null) {
            return;
        }
        claw.setAllowFlight(true);
        claw.setFlying(true);
        claw.setGravity(false);
    }

    private void lockClawLook(GameSession session, Player claw) {
        Location loc = claw.getLocation();
        float yaw = session.getClawLockedYaw();
        float pitch = -90f; // permanently face straight up
        if (Math.abs(loc.getYaw() - yaw) < 0.05 && Math.abs(loc.getPitch() - pitch) < 0.05) {
            return;
        }
        loc.setYaw(yaw);
        loc.setPitch(pitch);
        claw.teleport(loc);
    }

    private void keepClawInBounds(GameSession session, Player claw, Arena arena) {
        BoundingBox bounds = arena.getBounds();
        if (bounds == null) {
            return;
        }
        Location loc = claw.getLocation();
        double x = clamp(loc.getX(), bounds.getMinX(), bounds.getMaxX());
        double y = clamp(loc.getY(), bounds.getMinY(), bounds.getMaxY());
        double z = clamp(loc.getZ(), bounds.getMinZ(), bounds.getMaxZ());
        if (x != loc.getX() || y != loc.getY() || z != loc.getZ()) {
            Location next = loc.clone();
            next.setX(x);
            next.setY(y);
            next.setZ(z);
            next.setYaw(session.getClawLockedYaw());
            next.setPitch(-90f);
            claw.teleport(next);
        }
    }

    private boolean isOnControlPad(Player player, Arena arena) {
        Location pad = arena.getControlPad();
        if (pad == null || pad.getWorld() == null || !player.getWorld().equals(pad.getWorld())) {
            return false;
        }
        Location feet = player.getLocation().clone().subtract(0, 0.1, 0);
        return feet.getBlockX() == pad.getBlockX()
                && feet.getBlockY() == pad.getBlockY()
                && feet.getBlockZ() == pad.getBlockZ();
    }

    private void showTitle(Player player, String main, String sub) {
        showTitle(player, main, sub, 1200L);
    }

    private void showTitle(Player player, String main, String sub, long stayMillis) {
        Title title = Title.title(
                LegacyComponentSerializer.legacyAmpersand().deserialize(main == null ? "" : main),
                LegacyComponentSerializer.legacyAmpersand().deserialize(sub == null ? "" : sub),
                Title.Times.times(
                        Duration.ofMillis(100),
                        Duration.ofMillis(Math.max(500L, stayMillis)),
                        Duration.ofMillis(250)
                )
        );
        player.showTitle(title);
    }

    private Optional<ItemDisplay> findNearbyPrize(GameSession session, Location location, double radius) {
        double radiusSq = radius * radius;
        ItemDisplay closest = null;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < session.getPrizeDisplays().size(); i++) {
            if (session.isCollected(i) || Objects.equals(session.getHeldPrizeIndex(), i)) {
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

    private void syncHeldPrize(GameSession session, Player claw) {
        Integer index = session.getHeldPrizeIndex();
        if (index == null || index < 0 || index >= session.getPrizeDisplays().size()) {
            return;
        }
        ItemDisplay display = session.getPrizeDisplays().get(index);
        if (display == null || display.isDead()) {
            return;
        }
        if (!claw.getPassengers().contains(display)) {
            attachDisplay(claw, display, 0.0, -0.45, 0.0);
        }
    }

    private void syncClawVisual(GameSession session, Player claw) {
        ItemDisplay visual = session.getClawVisual();
        if (visual == null || visual.isDead()) {
            return;
        }
        if (!claw.getPassengers().contains(visual)) {
            double ox = plugin.getConfig().getDouble("claw-visual.offset-x", 0.0);
            double oy = plugin.getConfig().getDouble("claw-visual.offset-y", 0.35);
            double oz = plugin.getConfig().getDouble("claw-visual.offset-z", 0.0);
            attachDisplay(claw, visual, ox, oy, oz);
        }
    }

    private void attachDisplay(Player carrier, ItemDisplay display, double ox, double oy, double oz) {
        if (display == null || display.isDead() || carrier == null) {
            return;
        }
        try {
            applyTranslation(display, ox, oy, oz);
            if (!carrier.addPassenger(display)) {
                display.teleport(carrier.getLocation().clone().add(ox, oy, oz));
            }
        } catch (Exception ex) {
            display.teleport(carrier.getLocation().clone().add(ox, oy, oz));
        }
    }

    private void detachDisplay(Player carrier, ItemDisplay display) {
        if (display == null || display.isDead()) {
            return;
        }
        try {
            if (carrier != null) {
                carrier.removePassenger(display);
            }
            if (display.isInsideVehicle()) {
                display.leaveVehicle();
            }
        } catch (Exception ignored) {
        }
        applyTranslation(display, 0, 0, 0);
    }

    private void applyTranslation(ItemDisplay display, double x, double y, double z) {
        try {
            Transformation current = display.getTransformation();
            display.setTransformation(new Transformation(
                    new Vector3f((float) x, (float) y, (float) z),
                    current.getLeftRotation(),
                    current.getScale(),
                    current.getRightRotation()
            ));
        } catch (Exception ignored) {
        }
    }

    private void spawnPrizes(GameSession session, List<Location> locations) {
        float scale = (float) plugin.getConfig().getDouble("prize.scale", 1.0);
        double yOffset = plugin.getConfig().getDouble("prize.y-offset", 0.0);
        ItemStack item = plugin.getItemFactory().createPrizeItem();
        String transformName = plugin.getConfig().getString("prize.display-transform", "FIXED");
        for (int i = 0; i < locations.size(); i++) {
            Location base = locations.get(i);
            if (base == null || base.getWorld() == null) {
                session.getPrizeDisplays().add(null);
                continue;
            }
            final int index = i;
            ItemDisplay display = base.getWorld().spawn(base.clone().add(0, yOffset, 0), ItemDisplay.class, entity ->
                    configureItemDisplay(entity, item, scale, transformName, index));
            session.getPrizeDisplays().add(display);
        }
    }

    private void spawnClawVisual(GameSession session, Player claw) {
        if (!plugin.getConfig().getBoolean("claw-visual.enabled", true) || claw.getWorld() == null) {
            return;
        }
        float scale = (float) plugin.getConfig().getDouble("claw-visual.scale", 1.35);
        String transformName = plugin.getConfig().getString("claw-visual.display-transform", "FIXED");
        ItemStack item = plugin.getItemFactory().createClawVisualItem();
        ItemDisplay visual = claw.getWorld().spawn(claw.getLocation(), ItemDisplay.class, entity ->
                configureItemDisplay(entity, item, scale, transformName, null));
        session.setClawVisual(visual);
        syncClawVisual(session, claw);
    }

    private List<ItemDisplay> spawnDisplays(List<Location> locations, boolean preview) {
        float scale = (float) plugin.getConfig().getDouble("prize.scale", 1.0);
        double yOffset = plugin.getConfig().getDouble("prize.y-offset", 0.0);
        ItemStack item = plugin.getItemFactory().createPrizeItem();
        String transformName = plugin.getConfig().getString("prize.display-transform", "FIXED");
        java.util.ArrayList<ItemDisplay> spawned = new java.util.ArrayList<>();
        for (int i = 0; i < locations.size(); i++) {
            Location base = locations.get(i);
            if (base == null || base.getWorld() == null) {
                continue;
            }
            final int index = i;
            ItemDisplay display = base.getWorld().spawn(base.clone().add(0, yOffset, 0), ItemDisplay.class, entity ->
                    configureItemDisplay(entity, item, scale, transformName, preview ? index : null));
            spawned.add(display);
        }
        return spawned;
    }

    private void configureItemDisplay(ItemDisplay entity, ItemStack item, float scale, String transformName, Integer index) {
        entity.setItemStack(item.clone());
        try {
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf(
                    transformName == null ? "FIXED" : transformName.trim().toUpperCase()
            ));
            entity.setTransformation(new Transformation(
                    new Vector3f(),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0f, 0f, 1f, 0f)
            ));
            entity.setShadowRadius(0f);
            entity.setTeleportDuration(1);
        } catch (Exception ignored) {
        }
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        if (index != null) {
            entity.getPersistentDataContainer().set(
                    plugin.getItemFactory().getPrizeKey(),
                    org.bukkit.persistence.PersistentDataType.INTEGER,
                    index
            );
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
