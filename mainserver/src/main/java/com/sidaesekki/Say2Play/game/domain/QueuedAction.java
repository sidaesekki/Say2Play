package com.sidaesekki.Say2Play.game.domain;

import java.time.Instant;
import java.util.UUID;

/** ACTION_QUEUE 한 행. GameSession의 락 안에서만 바뀐다. */
public class QueuedAction {

    private final UUID id;
    private final long seq;
    private final ActionType actionType;
    private final Instant createdAt;
    private ActionStatus status = ActionStatus.QUEUED;
    private Integer targetX;
    private Integer targetY;
    private boolean blocked;
    private Instant startedAt;
    // 완료 시 확정할 방향. API에는 노출하지 않는다.
    private Facing targetFacing;

    QueuedAction(UUID id, long seq, ActionType actionType, Instant createdAt) {
        this.id = id;
        this.seq = seq;
        this.actionType = actionType;
        this.createdAt = createdAt;
    }

    void start(Integer targetX, Integer targetY, Facing targetFacing, boolean blocked, Instant now) {
        this.status = ActionStatus.RUNNING;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetFacing = targetFacing;
        this.blocked = blocked;
        this.startedAt = now;
    }

    public boolean isRunning() {
        return status == ActionStatus.RUNNING;
    }

    public Instant finishesAt() {
        return startedAt.plus(actionType.duration());
    }

    public UUID getId() {
        return id;
    }

    public long getSeq() {
        return seq;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public ActionStatus getStatus() {
        return status;
    }

    public Integer getTargetX() {
        return targetX;
    }

    public Integer getTargetY() {
        return targetY;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Facing getTargetFacing() {
        return targetFacing;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
