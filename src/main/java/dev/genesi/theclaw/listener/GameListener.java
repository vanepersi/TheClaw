package dev.genesi.theclaw.listener;

import dev.genesi.theclaw.TheClawPlugin;
import dev.genesi.theclaw.model.Arena;
import dev.genesi.theclaw.model.GameSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.Optional;

public final class GameListener implements Listener {

    private final TheClawPlugin plugin;

    public GameListener(TheClawPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMachineClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }

        Optional<Arena> arena = plugin.getGameManager().findArenaByMachine(event.getClickedBlock().getLocation());
        if (arena.isEmpty()) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        String result = plugin.getGameManager().clickMachine(player, arena.get());
        switch (result) {
            case "already-playing" -> plugin.getMessageService().send(player, "already-playing");
            case "arena-not-ready" -> plugin.getMessageService().send(player, "arena-not-ready", Map.of("arena", arena.get().getName()));
            case "arena-busy" -> plugin.getMessageService().send(player, "arena-busy", Map.of("arena", arena.get().getName()));
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Optional<GameSession> sessionOpt = plugin.getGameManager().getByPlayer(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }
        GameSession session = sessionOpt.get();
        if (session.getState() == GameSession.State.WAITING || session.isFinished()) {
            return;
        }

        // Freeze claw head look — position movement allowed.
        if (session.isClaw(player.getUniqueId()) && event.getTo() != null) {
            Location to = event.getTo().clone();
            to.setYaw(session.getClawLockedYaw());
            to.setPitch(session.getClawLockedPitch());
            event.setTo(to);
        }

        // Operator on pad: cancel walking away via WASD so input can be used as signals.
        if (session.isOperator(player.getUniqueId())
                && session.getState() == GameSession.State.GUIDING
                && event.getTo() != null) {
            plugin.getArenaManager().get(session.getArenaName()).ifPresent(arena -> {
                Location pad = arena.getControlPad();
                if (pad == null) {
                    return;
                }
                Location from = event.getFrom();
                boolean onPad = from.getBlockX() == pad.getBlockX()
                        && from.getBlockY() == pad.getBlockY()
                        && from.getBlockZ() == pad.getBlockZ();
                if (!onPad) {
                    return;
                }
                if (from.getX() != event.getTo().getX()
                        || from.getY() != event.getTo().getY()
                        || from.getZ() != event.getTo().getZ()) {
                    Location stay = from.clone();
                    stay.setYaw(event.getTo().getYaw());
                    stay.setPitch(event.getTo().getPitch());
                    event.setTo(stay);
                }
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onScrollHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Optional<GameSession> sessionOpt = plugin.getGameManager().getByPlayer(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }
        GameSession session = sessionOpt.get();

        if (session.getState() == GameSession.State.SYNC) {
            plugin.getGameManager().handleHotbarPress(player, event.getNewSlot());
            return;
        }

        if (session.getState() != GameSession.State.GUIDING || !session.isOperator(player.getUniqueId())) {
            return;
        }

        int prev = event.getPreviousSlot();
        int next = event.getNewSlot();
        // Scroll "forward" vs "back" approximated by slot direction
        boolean forward = (next - prev + 9) % 9 <= 4 && next != prev;
        if ((prev == 0 && next == 8) || (prev > next && !(prev == 8 && next == 0))) {
            forward = false;
        }
        if ((prev == 8 && next == 0) || (next > prev && !(prev == 0 && next == 8))) {
            forward = true;
        }
        plugin.getGameManager().handleScrollSignal(player, forward, player.isSneaking());
        // Keep their selected slot stable so scrolling is only a signal
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        // Sneak modifies scroll meaning; no extra work needed here.
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.getGameManager().getByPlayer(player.getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGameManager().leave(event.getPlayer(), false);
    }
}
