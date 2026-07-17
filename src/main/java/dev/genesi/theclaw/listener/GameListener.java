package dev.genesi.theclaw.listener;

import dev.genesi.theclaw.TheClawPlugin;
import dev.genesi.theclaw.model.GameSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class GameListener implements Listener {

    private final TheClawPlugin plugin;

    public GameListener(TheClawPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Optional<GameSession> sessionOpt = plugin.getGameManager().getByPlayer(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }
        GameSession session = sessionOpt.get();
        if (session.getState() != GameSession.State.PLAYING || session.isFinished()) {
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        String control = plugin.getItemFactory().getControlAction(hand);
        if (control != null && session.isOperator(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.getGameManager().handleOperatorControl(player, control);
            return;
        }

        if (plugin.getItemFactory().isGrabItem(hand) && session.isClaw(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.getGameManager().handleGrab(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onClawMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Optional<GameSession> sessionOpt = plugin.getGameManager().getByPlayer(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }
        GameSession session = sessionOpt.get();
        if (session.getState() != GameSession.State.PLAYING || !session.isClaw(player.getUniqueId())) {
            return;
        }
        if (event.getTo() == null) {
            return;
        }
        if (plugin.getGameManager().isPluginMoving(player.getUniqueId())) {
            return;
        }
        // Claw player is moved only by the operator — lock self-movement, allow look.
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom().clone().setDirection(event.getTo().getDirection()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (plugin.getGameManager().getByPlayer(player.getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getItemFactory().isClawGameItem(event.getItemDrop().getItemStack())
                && plugin.getGameManager().getByPlayer(event.getPlayer().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (plugin.getGameManager().getByPlayer(event.getPlayer().getUniqueId()).isEmpty()) {
            return;
        }
        if (plugin.getItemFactory().isClawGameItem(event.getMainHandItem())
                || plugin.getItemFactory().isClawGameItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGameManager().leave(event.getPlayer(), false);
    }
}
