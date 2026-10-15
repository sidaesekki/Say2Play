package com.sidaesekki.Say2Play.game.domain;

import com.sidaesekki.Say2Play.global.error.ErrorCode;
import com.sidaesekki.Say2Play.global.error.GameException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * GAME_SESSION과 그 안의 캐릭터, 참가자. 서버 메모리에만 있다.
 * 여러 스레드(HTTP 요청, 동작 완료 스케줄러, 만료 정리)가 함께 쓰므로
 * 이 객체와 하위 캐릭터/큐를 읽거나 바꿀 때는 반드시 {@code synchronized (session)} 안에서 한다.
 */
public class GameSession {

    public static final int MAP_COLS = 13;
    public static final int MAP_ROWS = 9;
    public static final int MAX_QUEUE_LENGTH = 100;
    public static final int ATTACK_DAMAGE = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UUID id;
    private final SessionMode mode;
    private final Instant createdAt;
    private final List<GameCharacter> characters = new ArrayList<>();
    private final List<Participant> participants = new ArrayList<>();
    private SessionStatus status;
    // 판이 새로 시작될 때마다 1씩 오른다. 예약해 둔 자동 초기화가 이미 지난 판의 것인지 가릴 때 쓴다.
    private long round = 1;
    private Instant lastActiveAt;

    public GameSession(UUID id, SessionMode mode, Instant now) {
        this.id = id;
        this.mode = mode;
        this.createdAt = now;
        this.lastActiveAt = now;
        this.status = mode == SessionMode.SOLO ? SessionStatus.PLAYING : SessionStatus.WAITING;
        for (CharacterType type : CharacterType.values()) {
            characters.add(new GameCharacter(UUID.randomUUID(), type, now));
        }
    }

    /**
     * 빈 자리에 참가자를 넣는다. SOLO는 한 명이 두 캐릭터를, MULTI는 PLAYER, ENEMY 순서로 한 명씩 맡는다.
     * MULTI에서 두 명이 모이면 새 판을 시작한다.
     */
    public Participant join(Instant now) {
        Set<CharacterType> slot;
        if (mode == SessionMode.SOLO) {
            if (!participants.isEmpty()) {
                throw new GameException(ErrorCode.SESSION_FULL);
            }
            slot = EnumSet.allOf(CharacterType.class);
        } else {
            slot = EnumSet.of(Arrays.stream(CharacterType.values())
                    .filter(type -> participants.stream().noneMatch(participant -> participant.controls(type)))
                    .findFirst()
                    .orElseThrow(() -> new GameException(ErrorCode.SESSION_FULL)));
        }
        Participant participant = new Participant(UUID.randomUUID(), newToken(), slot, now);
        participants.add(participant);
        lastActiveAt = now;
        if (mode == SessionMode.MULTI && participants.size() == CharacterType.values().length) {
            startNewRound(now);
        }
        return participant;
    }

    /**
     * 참가자를 내보낸다. 남은 참가자가 없으면 true를 돌려주며, 호출하는 쪽이 세션을 종료한다.
     * MULTI에서 한 명이 남으면 진행 중이던 판을 버리고 새 상대를 기다린다.
     */
    public boolean leave(Participant participant, Instant now) {
        participants.remove(participant);
        if (participants.isEmpty()) {
            return true;
        }
        resetCharacters(now);
        status = SessionStatus.WAITING;
        return false;
    }

    public Optional<Participant> findParticipant(String token) {
        return participants.stream().filter(participant -> participant.matchesToken(token)).findFirst();
    }

    public Optional<Participant> findParticipant(UUID participantId) {
        return participants.stream().filter(participant -> participant.getId().equals(participantId)).findFirst();
    }

    public void checkControls(Participant participant, GameCharacter character) {
        if (!participant.controls(character.getType())) {
            throw new GameException(ErrorCode.NOT_YOUR_CHARACTER);
        }
    }

    public Optional<GameCharacter> findCharacter(UUID characterId) {
        return characters.stream().filter(character -> character.getId().equals(characterId)).findFirst();
    }

    public GameCharacter character(CharacterType type) {
        return characters.stream().filter(character -> character.getType() == type).findFirst().orElseThrow();
    }

    public void checkCanEnqueue(GameCharacter character) {
        if (status == SessionStatus.WAITING) {
            throw new GameException(ErrorCode.WAITING_FOR_OPPONENT);
        }
        if (status == SessionStatus.ROUND_OVER) {
            throw new GameException(ErrorCode.ROUND_OVER);
        }
        if (character.queueLength() >= MAX_QUEUE_LENGTH) {
            throw new GameException(ErrorCode.QUEUE_FULL);
        }
    }

    public QueuedAction enqueue(GameCharacter character, ActionType actionType, Instant now) {
        checkCanEnqueue(character);
        lastActiveAt = now;
        return character.enqueue(actionType, now);
    }

    /**
     * 캐릭터가 쉬고 있고 대기 중인 동작이 있으면 맨 앞 동작을 실행 상태로 바꾼다.
     * 이동 동작의 목표 칸과 충돌 여부는 이 시점에 정한다(erd.md "이동 규칙").
     */
    public Optional<QueuedAction> startNext(GameCharacter character, Instant now) {
        Optional<QueuedAction> head = character.head();
        if (status != SessionStatus.PLAYING || head.isEmpty() || head.get().isRunning()) {
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
     * 공격이 끝나면 이 시점에 적중을 판정한다(erd.md "공격 판정 규칙").
     * 적중으로 상대가 쓰러지면 판이 끝난다(ROUND_OVER).
     */
    public Optional<FinishResult> finish(GameCharacter character, UUID actionId, Instant now) {
        Optional<QueuedAction> running = character.runningAction()
                .filter(action -> action.getId().equals(actionId));
        if (running.isEmpty()) {
            return Optional.empty();
        }
        QueuedAction action = running.get();
        character.complete(action, now);
        AttackHit hit = action.getActionType() == ActionType.ATTACK ? resolveAttack(character, now) : null;
        boolean roundOver = hit != null && hit.target().isDead();
        if (roundOver) {
            status = SessionStatus.ROUND_OVER;
            characters.forEach(each -> each.clearQueue(now));
        }
        return Optional.of(new FinishResult(action, hit, roundOver));
    }

    // 공격자 바로 앞 칸에 살아 있는 상대가 있으면 적중. 상대가 공격자를 마주 보고 방어 중이면 피해 0.
    private AttackHit resolveAttack(GameCharacter attacker, Instant now) {
        int x = attacker.getX() + attacker.getFacing().dx();
        int y = attacker.getY() + attacker.getFacing().dy();
        return characters.stream()
                .filter(target -> target != attacker && !target.isDead())
                .filter(target -> target.getX() == x && target.getY() == y)
                .findFirst()
                .map(target -> {
                    boolean defended = target.runningAction()
                            .filter(action -> action.getActionType() == ActionType.DEFEND)
                            .isPresent()
                            && target.getFacing() == attacker.getFacing().opposite();
                    int damage = defended ? 0 : ATTACK_DAMAGE;
                    if (damage > 0) {
                        target.takeDamage(damage, now);
                    }
                    return new AttackHit(target, damage, defended);
                })
                .orElse(null);
    }

    /** SOLO의 수동 초기화. MULTI는 판이 끝날 때 자동으로만 초기화된다. */
    public void reset(Instant now) {
        if (mode == SessionMode.MULTI) {
            throw new GameException(ErrorCode.RESET_NOT_ALLOWED);
        }
        startNewRound(now);
        lastActiveAt = now;
    }

    /** 판이 끝난 뒤 예약된 자동 초기화. 그사이 이미 새 판이 시작됐으면 false. */
    public boolean resetRound(long expectedRound, Instant now) {
        if (round != expectedRound || status != SessionStatus.ROUND_OVER) {
            return false;
        }
        startNewRound(now);
        return true;
    }

    private void startNewRound(Instant now) {
        resetCharacters(now);
        status = SessionStatus.PLAYING;
    }

    private void resetCharacters(Instant now) {
        round++;
        characters.forEach(character -> character.placeAtStart(now));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

    public SessionMode getMode() {
        return mode;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public long getRound() {
        return round;
    }

    public List<Participant> getParticipants() {
        return List.copyOf(participants);
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

    /** 완료된 동작과, 공격이 적중했으면 그 결과. {@code roundOver}면 이 공격으로 판이 끝났다. */
    public record FinishResult(QueuedAction action, AttackHit hit, boolean roundOver) {

        public Optional<AttackHit> attackHit() {
            return Optional.ofNullable(hit);
        }
    }

    /** {@code defended}면 상대가 막아서 {@code damage}가 0이다. */
    public record AttackHit(GameCharacter target, int damage, boolean defended) {
    }
}
