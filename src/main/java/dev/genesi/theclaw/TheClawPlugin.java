package dev.genesi.theclaw;

import dev.genesi.theclaw.command.ClawAdminCommand;
import dev.genesi.theclaw.command.ClawCommand;
import dev.genesi.theclaw.economy.EconomyService;
import dev.genesi.theclaw.listener.GameListener;
import dev.genesi.theclaw.manager.ArenaManager;
import dev.genesi.theclaw.manager.GameManager;
import dev.genesi.theclaw.manager.MessageService;
import dev.genesi.theclaw.manager.PointsService;
import dev.genesi.theclaw.util.ItemFactory;
import org.bukkit.plugin.java.JavaPlugin;

public class TheClawPlugin extends JavaPlugin {

    private ArenaManager arenaManager;
    private GameManager gameManager;
    private PointsService pointsService;
    private EconomyService economyService;
    private MessageService messageService;
    private ItemFactory itemFactory;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messageService = new MessageService(this);
        this.itemFactory = new ItemFactory(this);
        this.economyService = new EconomyService(this);
        this.pointsService = new PointsService(this);
        this.arenaManager = new ArenaManager(this);
        this.gameManager = new GameManager(this);

        arenaManager.load();
        pointsService.load();
        economyService.hook();

        ClawCommand playerCommand = new ClawCommand(this);
        ClawAdminCommand adminCommand = new ClawAdminCommand(this);
        getCommand("claw").setExecutor(playerCommand);
        getCommand("claw").setTabCompleter(playerCommand);
        getCommand("clawadmin").setExecutor(adminCommand);
        getCommand("clawadmin").setTabCompleter(adminCommand);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        getLogger().info("TheClaw enabled. Click a machine block to join.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
        if (arenaManager != null) {
            arenaManager.save();
        }
        if (pointsService != null) {
            pointsService.save();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        messageService.reload();
        itemFactory.reload();
        arenaManager.load();
        pointsService.load();
        economyService.hook();
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public PointsService getPointsService() {
        return pointsService;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public ItemFactory getItemFactory() {
        return itemFactory;
    }
}
