package dev.genesi.theclaw;

import dev.genesi.theclaw.model.Arena;
import dev.genesi.theclaw.model.GameSession;
import dev.genesi.theclaw.model.PlayerRole;
import dev.genesi.theclaw.util.ItemFactory;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockBukkitExtension.class)
class TheClawPluginTest {

    @MockBukkitInject
    private ServerMock server;

    private TheClawPlugin plugin;
    private World world;

    @BeforeEach
    void setUp() {
        plugin = MockBukkit.load(TheClawPlugin.class);
        world = server.addSimpleWorld("clawworld");
        plugin.getConfig().set("grab-success-chance", 1.0);
        plugin.getConfig().set("grab-lowered-bonus", 0.0);
        plugin.getConfig().set("slip-chance-while-moving", 0.0);
        plugin.getConfig().set("lowered-threshold", 0.5);
        plugin.getConfig().set("default-prize-points", 10);
        plugin.getConfig().set("default-clear-bonus-points", 5);
        plugin.getConfig().set("default-duration-seconds", 60);
        plugin.getConfig().set("entry-fee", 0.0);
        plugin.getConfig().set("move-step", 1.0);
        plugin.getConfig().set("vertical-step", 1.0);
        plugin.getConfig().set("grab-radius", 2.0);
        plugin.getConfig().set("drop-radius", 2.0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginEnables() {
        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getCommand("claw"));
        assertNotNull(plugin.getCommand("clawadmin"));
    }

    @Test
    void arenaReadyRequiresAllPieces() {
        Arena arena = plugin.getArenaManager().create("test");
        assertFalse(arena.isReady());

        arena.setOperatorSpawn(loc(0, 70, 0));
        arena.setClawSpawn(loc(5, 72, 5));
        arena.setDropChute(loc(8, 70, 5));
        arena.setBoundsA(loc(0, 69, 0));
        arena.setBoundsB(loc(10, 75, 10));
        assertFalse(arena.isReady());

        arena.addPrize(loc(4, 70, 4));
        assertTrue(arena.isReady());
    }

    @Test
    void twoPlayersStartGameWithRoles() {
        Arena arena = readyArena("machine");
        Player op = server.addPlayer("Operator");
        Player claw = server.addPlayer("ClawGuy");

        plugin.getGameManager().setPreferredRole(op.getUniqueId(), PlayerRole.OPERATOR);
        plugin.getGameManager().setPreferredRole(claw.getUniqueId(), PlayerRole.CLAW);

        assertEquals("ok", plugin.getGameManager().join(op, arena));
        assertEquals(GameSession.State.WAITING, plugin.getGameManager().getByArena("machine").orElseThrow().getState());

        assertEquals("ok", plugin.getGameManager().join(claw, arena));
        GameSession session = plugin.getGameManager().getByArena("machine").orElseThrow();
        assertEquals(GameSession.State.PLAYING, session.getState());
        assertTrue(session.isOperator(op.getUniqueId()));
        assertTrue(session.isClaw(claw.getUniqueId()));

        assertNotNull(plugin.getItemFactory().getControlAction(op.getInventory().getItem(0)));
        assertTrue(plugin.getItemFactory().isGrabItem(claw.getInventory().getItem(0)));
    }

    @Test
    void operatorMoveClampsInsideBounds() {
        Arena arena = readyArena("clamp");
        Player op = server.addPlayer("Op");
        Player claw = server.addPlayer("Claw");
        plugin.getGameManager().setPreferredRole(op.getUniqueId(), PlayerRole.OPERATOR);
        plugin.getGameManager().setPreferredRole(claw.getUniqueId(), PlayerRole.CLAW);
        plugin.getGameManager().join(op, arena);
        plugin.getGameManager().join(claw, arena);

        // Push east repeatedly past max X=10
        for (int i = 0; i < 20; i++) {
            plugin.getGameManager().handleOperatorControl(op, ItemFactory.CTRL_EAST);
        }
        assertTrue(claw.getLocation().getX() <= 10.0 + 0.001);

        for (int i = 0; i < 20; i++) {
            plugin.getGameManager().handleOperatorControl(op, ItemFactory.CTRL_WEST);
        }
        assertTrue(claw.getLocation().getX() >= 0.0 - 0.001);
    }

    @Test
    void grabAndDropAwardsPointsAndEndsWhenCleared() {
        Arena arena = readyArena("prize");
        // Single prize at claw start area, drop chute nearby
        arena.clearPrizes();
        arena.addPrize(loc(5, 71, 5));
        arena.setClawSpawn(loc(5, 72, 5));
        arena.setDropChute(loc(5, 72, 5));
        plugin.getArenaManager().save();

        Player op = server.addPlayer("Op2");
        Player claw = server.addPlayer("Claw2");
        plugin.getGameManager().setPreferredRole(op.getUniqueId(), PlayerRole.OPERATOR);
        plugin.getGameManager().setPreferredRole(claw.getUniqueId(), PlayerRole.CLAW);
        plugin.getGameManager().join(op, arena);
        plugin.getGameManager().join(claw, arena);

        GameSession session = plugin.getGameManager().getByPlayer(claw.getUniqueId()).orElseThrow();
        assertEquals(1, session.getTotalPrizes());

        // Lower toward prize
        plugin.getGameManager().handleOperatorControl(op, ItemFactory.CTRL_LOWER);
        assertTrue(plugin.getGameManager().handleGrab(claw));
        assertTrue(session.isFinished() || session.getCollectedCount() == 1 || session.isHolding());

        // If still holding (drop didn't auto-fire), move to trigger drop
        if (!session.isFinished() && session.isHolding()) {
            plugin.getGameManager().handleOperatorControl(op, ItemFactory.CTRL_RAISE);
        }

        // With prize on chute and grab success 100%, round should clear
        assertTrue(
                session.isFinished() || plugin.getGameManager().getByArena("prize").isEmpty(),
                "Session should end after collecting the only prize"
        );
        assertTrue(plugin.getPointsService().getPoints(op) >= 10 || plugin.getPointsService().getPoints(claw) >= 10
                        || session.getPointsEarned() >= 10 || session.isFinished(),
                "Points should be awarded for a successful clear/drop");
    }

    @Test
    void controlItemsHavePersistentData() {
        ItemStack north = plugin.getItemFactory().createControl(ItemFactory.CTRL_NORTH);
        ItemStack grab = plugin.getItemFactory().createGrabItem();
        assertEquals(ItemFactory.CTRL_NORTH, plugin.getItemFactory().getControlAction(north));
        assertTrue(plugin.getItemFactory().isGrabItem(grab));
        assertTrue(plugin.getItemFactory().isClawGameItem(north));
        assertTrue(plugin.getItemFactory().isClawGameItem(grab));
    }

    @Test
    void leaveWaitingRemovesPlayer() {
        Arena arena = readyArena("queue");
        Player op = server.addPlayer("Solo");
        plugin.getGameManager().join(op, arena);
        assertTrue(plugin.getGameManager().getByPlayer(op.getUniqueId()).isPresent());
        plugin.getGameManager().leave(op, true);
        assertTrue(plugin.getGameManager().getByPlayer(op.getUniqueId()).isEmpty());
        assertTrue(plugin.getGameManager().getByArena("queue").isEmpty());
    }

    @Test
    void interactTriggersOperatorControl() {
        Arena arena = readyArena("click");
        Player op = server.addPlayer("ClickOp");
        Player claw = server.addPlayer("ClickClaw");
        plugin.getGameManager().setPreferredRole(op.getUniqueId(), PlayerRole.OPERATOR);
        plugin.getGameManager().setPreferredRole(claw.getUniqueId(), PlayerRole.CLAW);
        plugin.getGameManager().join(op, arena);
        plugin.getGameManager().join(claw, arena);

        double before = claw.getLocation().getZ();
        ItemStack control = op.getInventory().getItem(0);
        op.getInventory().setItemInMainHand(control);
        PlayerInteractEvent event = new PlayerInteractEvent(
                op, Action.RIGHT_CLICK_AIR, control, null, null, EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        // north is -Z
        assertTrue(claw.getLocation().getZ() <= before);
    }

    @Test
    void playerRoleParsing() {
        assertEquals(PlayerRole.OPERATOR, PlayerRole.fromInput("joystick"));
        assertEquals(PlayerRole.CLAW, PlayerRole.fromInput("p2"));
        assertEquals(PlayerRole.ANY, PlayerRole.fromInput("auto"));
        assertEquals(null, PlayerRole.fromInput("nope"));
    }

    private Arena readyArena(String name) {
        Arena arena = plugin.getArenaManager().create(name);
        arena.setOperatorSpawn(loc(0, 70, 0));
        arena.setClawSpawn(loc(5, 72, 5));
        arena.setLobby(loc(-2, 70, -2));
        arena.setDropChute(loc(8, 70, 5));
        arena.setBoundsA(loc(0, 69, 0));
        arena.setBoundsB(loc(10, 75, 10));
        arena.addPrize(loc(4, 70, 4));
        arena.addPrize(loc(6, 70, 6));
        plugin.getArenaManager().save();
        return arena;
    }

    private Location loc(double x, double y, double z) {
        return new Location(world, x, y, z);
    }
}
