package com.sidaesekki.Say2Play.game.domain;

import com.sidaesekki.Say2Play.global.error.ErrorCode;
import com.sidaesekki.Say2Play.global.error.GameException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * GAME_SESSION과 그 안의 캐릭터들. 서버 메모리에만 있다.
 * 여러 스레드(HTTP 요청, 동작 완료 스케줄러, 만료 정리)가 함께 쓰므로
 * 이 객체와 하위 캐릭터/큐를 읽거나 바꿀 때는 반드시 {@code synchronized (session)} 안에서 한다.
 */
public class GameSession {

    public static final int MAP_COLS = 13;
    public static final int MAP_ROWS = 9;
    public static final int MAX_QUEUE_LENGTH = 100;

    private final UUID id;
    private final Instant createdAt;
    private final List<GameCharacter> characters = new ArrayList<>();
    private SessionStatus status = SessionStatus.PLAYING;
    private Instant lastActiveAt;

    public GameSession(UUID id, Instant now) {
        this.id = id;
        this.createdAt = now;
        this.lastActiveAt = now;
        for (CharacterType type : CharacterType.values()) {
            characters.add(new GameCharacter(UUID.randomUUID(), type, now));
        }
    }

    public Optional<GameCharacter> findCharacter(UUID characterId) {
        return characters.stream().filter(character -> character.getId().equals(characterId)).findFirst();
    }

    public GameCharacter character(CharacterType type) {
        return characters.stream().filter(character -> character.getType() == type).findFirst().orElseThrow();
    }

    public void checkQueueCapacity(GameCharacter character) {
        if (character.queueLength() >= MAX_QUEUE_LENGTH) {
            throw new GameException(ErrorCode.QUEUE_FULL);
        }
    }

    public QueuedAction enqueue(GameCharacter character, ActionType actionType, Instant now) {
        checkQueueCapacity(character);
        lastActiveAt = now;
        return character.enqueue(actionType, now);
    }

    /**
     * 캐릭터가 쉬고 있고 대기 중인 동작이 있으면 맨 앞 동작을 실행 상태로 바꾼다.
     * 이동 동작의 목표 칸과 충돌 여부는 이 시점에 정한다(erd.md "이동 규칙").
     */
    public Optional<QueuedAction> startNext(GameCharacter character, Instant now) {
        Optional<QueuedAction> head = character.head();
        if (head.isEmpty() || head.get().isRunning()) {
            return Optional.empty();
        }
        QueuedAction action = head.get();
        ActionType type = action.getActionType();
        Facing targetFacing = type.facingAfter(character.getFacing());
        Optional<Facing> direction = type.moveDirection(character.getFacing());
        if (direction.isEmpty()) {
            character.begin(action, null, null, targetFacing, false, now);
            return Optional.of(action);
        }
        int targetX = clamp(character.getX() + direction.get().dx(), MAP_COLS);
        int targetY = clamp(character.getY() + direction.get().dy(), MAP_ROWS);
        boolean blocked = isTakenByOther(character, targetX, targetY);
        if (blocked) {
            targetX = character.getX();
            targetY = character.getY();
        }
        character.begin(action, targetX, targetY, targetFacing, blocked, now);
        return Optional.of(action);
    }

    /**
     * 실행 중인 동작이 {@code actionId}일 때만 완료 처리한다.
     * 초기화 등으로 이미 사라진 동작의 완료 예약이 늦게 도착하면 empty를 돌려준다.
     */
    public Optional<QueuedAction> finish(GameCharacter character, UUID actionId, Instant now) {
        Optional<QueuedAction> running = character.runningAction()
                .filter(action -> action.getId().equals(actionId));
        running.ifPresent(action -> character.complete(action, now));
        return running;
    }

    public void reset(Instant now) {
        characters.forEach(character -> character.placeAtStart(now));
        lastActiveAt = now;
    }

    public void end() {
        status = SessionStatus.ENDED;
    }

    public boolean isEnded() {
        return status == SessionStatus.ENDED;
    }

    public boolean isIdleSince(Instant threshold) {
        return !lastActiveAt.isAfter(threshold);
    }

    private boolean isTakenByOther(GameCharacter mover, int x, int y) {
        return characters.stream()
                .filter(other -> other != mover)
                .anyMatch(other -> (other.getX() == x && other.getY() == y)
                        || other.runningAction()
                                .filter(action -> action.getTargetX() != null)
                                .filter(action -> action.getTargetX() == x && action.getTargetY() == y)
                                .isPresent());
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    public UUID getId() {
        return id;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public int getMapCols() {
        return MAP_COLS;
    }

    public int getMapRows() {
        return MAP_ROWS;
    }

    public List<GameCharacter> getCharacters() {
        return List.copyOf(characters);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }
}
