package com.sidaesekki.Say2Play.game.domain;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** CHARACTER 한 행과 그 캐릭터의 ACTION_QUEUE. GameSession의 락 안에서만 바뀐다. */
public class GameCharacter {

    public static final int MAX_HP = 100;

    private final UUID id;
    private final CharacterType type;
    private final Instant createdAt;
    private final Deque<QueuedAction> queue = new ArrayDeque<>();
    private long nextSeq = 1;
    private int x;
    private int y;
    private Facing facing;
    private CharacterState state;
    private int hp;
    private Instant updatedAt;

    GameCharacter(UUID id, CharacterType type, Instant now) {
        this.id = id;
        this.type = type;
        this.createdAt = now;
        placeAtStart(now);
    }

    void placeAtStart(Instant now) {
        queue.clear();
        x = type.startX();
        y = type.startY();
        facing = type.startFacing();
        state = CharacterState.IDLE;
        hp = MAX_HP;
        updatedAt = now;
    }

    QueuedAction enqueue(ActionType actionType, Instant now) {
        QueuedAction action = new QueuedAction(UUID.randomUUID(), nextSeq++, actionType, now);
        queue.addLast(action);
        return action;
    }

    void begin(QueuedAction action, Integer targetX, Integer targetY, Facing targetFacing, boolean blocked, Instant now) {
        action.start(targetX, targetY, targetFacing, blocked, now);
        state = action.getActionType().state();
        updatedAt = now;
    }

    void complete(QueuedAction action, Instant now) {
        queue.removeFirst();
        if (action.getTargetX() != null) {
            x = action.getTargetX();
            y = action.getTargetY();
        }
        facing = action.getTargetFacing();
        state = CharacterState.IDLE;
        updatedAt = now;
    }

    /** HP는 0 아래로 내려가지 않는다. 0이 되면 쓰러지고 남은 동작은 모두 취소된다. */
    void takeDamage(int damage, Instant now) {
        hp = Math.max(0, hp - damage);
        if (hp == 0) {
            queue.clear();
            state = CharacterState.DEAD;
        }
        updatedAt = now;
    }

    public boolean isDead() {
        return state == CharacterState.DEAD;
    }

    Optional<QueuedAction> head() {
        return Optional.ofNullable(queue.peekFirst());
    }

    public Optional<QueuedAction> runningAction() {
        return head().filter(QueuedAction::isRunning);
    }

    /** seq 오름차순. 실행 중인 동작이 있으면 첫 번째다. */
    public List<QueuedAction> getQueue() {
        return List.copyOf(queue);
    }

    public int queueLength() {
        return queue.size();
    }

    public UUID getId() {
        return id;
    }

    public CharacterType getType() {
        return type;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Facing getFacing() {
        return facing;
    }

    public CharacterState getState() {
        return state;
    }

    public int getHp() {
        return hp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
