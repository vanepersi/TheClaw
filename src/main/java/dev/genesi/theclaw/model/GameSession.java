package dev.genesi.theclaw.model;

import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class GameSession {

    public enum State {
        WAITING,
        PLAYING,
        FINISHED
    }

    private final String arenaName;
    private UUID operatorId;
    private UUID clawId;
    private String operatorName;
    private String clawName;
    private State state = State.WAITING;
    private final List<ItemDisplay> prizeDisplays = new ArrayList<>();
    private final List<Boolean> collected;
    private int remainingSeconds;
    private int prizePoints;
    private int clearBonusPoints;
    private int collectedCount;
    private int pointsEarned;
    private Integer heldPrizeIndex;
    private double raisedY;
    private BukkitTask tickTask;
    private boolean finished;
    private final Map<UUID, Double> feesPaid = new HashMap<>();

    public GameSession(String arenaName, int prizeCount, int durationSeconds, int prizePoints, int clearBonusPoints) {
        this.arenaName = arenaName;
        this.remainingSeconds = durationSeconds;
        this.prizePoints = prizePoints;
        this.clearBonusPoints = clearBonusPoints;
        this.collected = new ArrayList<>(prizeCount);
        for (int i = 0; i < prizeCount; i++) {
            collected.add(false);
        }
    }

    public void recordFeePaid(UUID uuid, double amount) {
        if (uuid == null || amount <= 0) {
            return;
        }
        feesPaid.merge(uuid, amount, Double::sum);
    }

    public double takeFeePaid(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        Double amount = feesPaid.remove(uuid);
        return amount == null ? 0 : amount;
    }

    public String getArenaName() {
        return arenaName;
    }

    public UUID getOperatorId() {
        return operatorId;
    }

    public UUID getClawId() {
        return clawId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public String getClawName() {
        return clawName;
    }

    public void setOperator(Player player) {
        this.operatorId = player.getUniqueId();
        this.operatorName = player.getName();
    }

    public void setClaw(Player player) {
        this.clawId = player.getUniqueId();
        this.clawName = player.getName();
    }

    public boolean isPlayer(UUID uuid) {
        return Objects.equals(uuid, operatorId) || Objects.equals(uuid, clawId);
    }

    public boolean isOperator(UUID uuid) {
        return Objects.equals(uuid, operatorId);
    }

    public boolean isClaw(UUID uuid) {
        return Objects.equals(uuid, clawId);
    }

    public boolean isFull() {
        return operatorId != null && clawId != null;
    }

    public int playerCount() {
        int count = 0;
        if (operatorId != null) {
            count++;
        }
        if (clawId != null) {
            count++;
        }
        return count;
    }

    public void clearPlayer(UUID uuid) {
        if (Objects.equals(uuid, operatorId)) {
            operatorId = null;
            operatorName = null;
        }
        if (Objects.equals(uuid, clawId)) {
            clawId = null;
            clawName = null;
        }
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public List<ItemDisplay> getPrizeDisplays() {
        return prizeDisplays;
    }

    public boolean isCollected(int index) {
        return index >= 0 && index < collected.size() && collected.get(index);
    }

    public boolean markCollected(int index) {
        if (index < 0 || index >= collected.size() || collected.get(index)) {
            return false;
        }
        collected.set(index, true);
        collectedCount++;
        return true;
    }

    public int getTotalPrizes() {
        return collected.size();
    }

    public int getCollectedCount() {
        return collectedCount;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void decrementSecond() {
        remainingSeconds--;
    }

    public int getPrizePoints() {
        return prizePoints;
    }

    public int getClearBonusPoints() {
        return clearBonusPoints;
    }

    public int getPointsEarned() {
        return pointsEarned;
    }

    public void addPointsEarned(int amount) {
        pointsEarned += Math.max(0, amount);
    }

    public Integer getHeldPrizeIndex() {
        return heldPrizeIndex;
    }

    public void setHeldPrizeIndex(Integer heldPrizeIndex) {
        this.heldPrizeIndex = heldPrizeIndex;
    }

    public boolean isHolding() {
        return heldPrizeIndex != null;
    }

    public double getRaisedY() {
        return raisedY;
    }

    public void setRaisedY(double raisedY) {
        this.raisedY = raisedY;
    }

    public BukkitTask getTickTask() {
        return tickTask;
    }

    public void setTickTask(BukkitTask tickTask) {
        this.tickTask = tickTask;
    }

    public boolean isFinished() {
        return finished || state == State.FINISHED;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
        if (finished) {
            this.state = State.FINISHED;
        }
    }

    public boolean allCollected() {
        return collectedCount >= collected.size() && collected.size() > 0;
    }
}
