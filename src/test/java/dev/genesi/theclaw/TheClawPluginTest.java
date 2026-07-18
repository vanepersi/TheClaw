package dev.genesi.theclaw;

import dev.genesi.theclaw.model.Arena;
import dev.genesi.theclaw.model.GameSession;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
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
        plugin.getConfig().set("countdown-seconds", 0);
        plugin.getConfig().set("default-duration-seconds", 20);
        plugin.getConfig().set("operator-max-distance", 6.0);
        plugin.getConfig().set("off-pad-grace-seconds", 10);
        plugin.getConfig().set("sync-success-means-grab", false);
        plugin.getConfig().set("claw-visual.enabled", false);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginEnables() {
        assertTrue(plugin.isEnabled());
    }

    @Test
    void arenaReadyRequiresMachinePadClawDropBoundsPrizes() {
        Arena arena = plugin.getArenaManager().create("test");
        assertFalse(arena.isReady());
        arena.setMachineBlock(blockLoc(0, 64, 0));
        arena.setControlPad(blockLoc(2, 64, 0));
        arena.setClawSpawn(loc(5, 66, 5));
        arena.setDropChute(loc(8, 64, 5));
        arena.setBoundsA(loc(0, 64, 0));
        arena.setBoundsB(loc(10, 70, 10));
        assertFalse(arena.isReady());
        arena.addPrize(loc(4, 64, 4));
        assertTrue(arena.isReady());
    }

    @Test
    void firstClickWaitsForClawSecondStartsMatch() {
        Arena arena = readyArena("cabinet");
        Player p1 = server.addPlayer("Op");
        Player p2 = server.addPlayer("Claw");

        assertEquals("ok-waiting", plugin.getGameManager().clickMachine(p1, arena));
        GameSession waiting = plugin.getGameManager().getByArena("cabinet").orElseThrow();
        assertEquals(GameSession.State.WAITING, waiting.getState());
        assertTrue(waiting.isOperator(p1.getUniqueId()));

        assertEquals("ok-start", plugin.getGameManager().clickMachine(p2, arena));
        GameSession session = plugin.getGameManager().getByArena("cabinet").orElseThrow();
        assertTrue(session.isClaw(p2.getUniqueId()));
        assertTrue(session.getState() == GameSession.State.COUNTDOWN
                || session.getState() == GameSession.State.GUIDING);
    }

    @Test
    void machineBlockClickJoins() {
        Arena arena = readyArena("clickme");
        Player p1 = server.addPlayer("Clicker");
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.IRON_BLOCK);

        PlayerInteractEvent event = new PlayerInteractEvent(
                p1, Action.RIGHT_CLICK_BLOCK, null, block, null, EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertTrue(plugin.getGameManager().getByPlayer(p1.getUniqueId()).isPresent());
    }

    @Test
    void leaveWaitingClearsQueue() {
        Arena arena = readyArena("queue");
        Player p1 = server.addPlayer("Solo");
        plugin.getGameManager().clickMachine(p1, arena);
        plugin.getGameManager().leave(p1, true);
        assertTrue(plugin.getGameManager().getByPlayer(p1.getUniqueId()).isEmpty());
        assertTrue(plugin.getGameManager().getByArena("queue").isEmpty());
    }

    private Arena readyArena(String name) {
        Arena arena = plugin.getArenaManager().create(name);
        arena.setMachineBlock(blockLoc(0, 64, 0));
        arena.setControlPad(blockLoc(2, 64, 0));
        arena.setClawSpawn(loc(5, 66, 5));
        arena.setDropChute(loc(8, 64, 5));
        arena.setBoundsA(loc(0, 64, 0));
        arena.setBoundsB(loc(10, 70, 10));
        arena.addPrize(loc(4, 64, 4));
        arena.addPrize(loc(6, 64, 6));
        plugin.getArenaManager().save();
        world.getBlockAt(0, 64, 0).setType(Material.IRON_BLOCK);
        world.getBlockAt(2, 64, 0).setType(Material.GOLD_BLOCK);
        return arena;
    }

    private Location loc(double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    private Location blockLoc(int x, int y, int z) {
        return new Location(world, x, y, z);
    }
}
