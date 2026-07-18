package dev.genesi.theclaw.scoreboard;

import dev.genesi.theclaw.TheClawPlugin;
import dev.genesi.theclaw.model.GameSession;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScoreboardService {

    private final TheClawPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public ScoreboardService(TheClawPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Player player, GameSession session, boolean operator) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective(
                "theclaw",
                Criteria.DUMMY,
                LegacyComponentSerializer.legacyAmpersand().deserialize(
                        plugin.getConfig().getString("scoreboard.title", "&d&lTheClaw")
                )
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        update(player, session, operator);
    }

    public void update(Player player, GameSession session, boolean operator) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        Objective objective = board.getObjective("theclaw");
        if (objective == null) {
            return;
        }
        for (String entry : List.copyOf(board.getEntries())) {
            board.resetScores(entry);
        }

        List<String> lines = operator
                ? plugin.getConfig().getStringList("scoreboard.operator")
                : plugin.getConfig().getStringList("scoreboard.claw");
        if (lines.isEmpty()) {
            lines = operator ? defaultOperator() : defaultClaw();
        }

        int score = lines.size();
        for (String raw : lines) {
            String line = plugin.getMessageService().apply(raw, Map.of(
                    "time", String.valueOf(Math.max(0, session.getRemainingSeconds())),
                    "prizes", String.valueOf(session.getCollectedCount()),
                    "total", String.valueOf(session.getTotalPrizes()),
                    "state", session.getState().name(),
                    "signal", session.getLastSignal() == null ? "-" : session.getLastSignal().name(),
                    "pad", session.getOffPadSeconds() < 0 ? "ON PAD" : "OFF " + session.getOffPadSeconds() + "s"
            ));
            String entry = colorUnique(line, score);
            objective.getScore(entry).setScore(score);
            score--;
        }
    }

    public void clear(Player player) {
        if (player == null) {
            return;
        }
        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void clearAll() {
        for (UUID uuid : List.copyOf(boards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                clear(player);
            }
        }
        boards.clear();
    }

    private String colorUnique(String line, int index) {
        String colored = LegacyComponentSerializer.legacySection().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(line)
        );
        if (colored.length() > 40) {
            colored = colored.substring(0, 40);
        }
        return colored + "§" + Integer.toHexString(index % 16);
    }

    private List<String> defaultOperator() {
        return List.of(
                "&7Role: &aOperator",
                "&7Stand on the &epad",
                "&7WASD / scroll = signal",
                "&7Guide the blind claw!",
                "&7Time: &e{time}s",
                "&7Pad: &f{pad}"
        );
    }

    private List<String> defaultClaw() {
        return List.of(
                "&7Role: &bHuman Claw",
                "&7You are &8BLIND",
                "&7Follow title signals",
                "&7Walk only — no looking",
                "&7Time: &e{time}s",
                "&7Last: &f{signal}"
        );
    }
}
