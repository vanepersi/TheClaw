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
        COUNTDOWN,
        GUIDING,
        DROPPING,
        SYNC,
        FINISHED
    }

    public enum Signal {
        FORWARD,
        BACK,
        LEFT,
        RIGHT
    }

    private final String arenaName;
    private UUID operatorId;
    private UUID clawId;
    private String operatorName;
    private String clawName;
    private State state = State.WAITING;
    private final List<ItemDisplay> prizeDisplays = new ArrayList<>();
    private ItemDisplay clawVisual;
    private final List<Boolean> collected;
    private int remainingSeconds;
    private int prizePoints;
    private int clearBonusPoints;
    private int collectedCount;
    private int pointsEarned;
    private Integer heldPrizeIndex;
    private float clawLockedYaw;
    private float clawLockedPitch;
    private double originalClawScale = 1.0;
    private int offPadSeconds = -1;
    private Integer syncNumber;
    private boolean operatorSynced;
    private boolean clawSynced;
    private int syncTicksLeft;
    private long lastSignalMillis;
    private Signal lastSignal;
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

    public ItemDisplay getClawVisual() {
        return clawVisual;
    }

    public void setClawVisual(ItemDisplay clawVisual) {
        this.clawVisual = clawVisual;
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

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
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

    public float getClawLockedYaw() {
        return clawLockedYaw;
    }

    public float getClawLockedPitch() {
        return clawLockedPitch;
    }

    public void lockClawLook(float yaw, float pitch) {
        this.clawLockedYaw = yaw;
        this.clawLockedPitch = pitch;
    }

    public double getOriginalClawScale() {
        return originalClawScale;
    }

    public void setOriginalClawScale(double originalClawScale) {
        this.originalClawScale = originalClawScale;
    }

    public int getOffPadSeconds() {
        return offPadSeconds;
    }

    public void setOffPadSeconds(int offPadSeconds) {
        this.offPadSeconds = offPadSeconds;
    }

    public Integer getSyncNumber() {
        return syncNumber;
    }

    public void beginSync(int number, int ticks) {
        this.syncNumber = number;
        this.syncTicksLeft = ticks;
        this.operatorSynced = false;
        this.clawSynced = false;
    }

    public void clearSync() {
        this.syncNumber = null;
        this.syncTicksLeft = 0;
        this.operatorSynced = false;
        this.clawSynced = false;
    }

    public boolean markSynced(UUID uuid) {
        if (syncNumber == null) {
            return false;
        }
        if (Objects.equals(uuid, operatorId)) {
            operatorSynced = true;
            return true;
        }
        if (Objects.equals(uuid, clawId)) {
            clawSynced = true;
            return true;
        }
        return false;
    }

    public boolean bothSynced() {
        return operatorSynced && clawSynced;
    }

    public int getSyncTicksLeft() {
        return syncTicksLeft;
    }

    public void decrementSyncTick() {
        syncTicksLeft--;
    }

    public long getLastSignalMillis() {
        return lastSignalMillis;
    }

    public Signal getLastSignal() {
        return lastSignal;
    }

    public void setLastSignal(Signal signal, long millis) {
        this.lastSignal = signal;
        this.lastSignalMillis = millis;
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
