package com.sidaesekki.Say2Play.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sidaesekki.Say2Play.global.error.ErrorCode;
import com.sidaesekki.Say2Play.global.error.GameException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameSessionTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private GameSession session;
    private GameCharacter player;
    private GameCharacter enemy;

    @BeforeEach
    void setUp() {
        session = new GameSession(UUID.randomUUID(), SessionMode.SOLO, NOW);
        player = session.character(CharacterType.PLAYER);
        enemy = session.character(CharacterType.ENEMY);
    }

    @Test
    void 캐릭터는_문서의_시작_상태로_배치된다() {
        assertThat(player.getX()).isEqualTo(6);
        assertThat(player.getY()).isEqualTo(7);
        assertThat(player.getFacing()).isEqualTo(Facing.N);
        assertThat(enemy.getX()).isEqualTo(6);
        assertThat(enemy.getY()).isEqualTo(1);
        assertThat(enemy.getFacing()).isEqualTo(Facing.S);
        assertThat(player.getState()).isEqualTo(CharacterState.IDLE);
    }

    @Test
    void 앞으로_이동은_바라보는_방향을_따라_목표를_정한다() {
        run(player, ActionType.TURN_RIGHT);
        QueuedAction action = session.enqueue(player, ActionType.MOVE_FORWARD, NOW);
        assertThat(action.getStatus()).isEqualTo(ActionStatus.QUEUED);
        assertThat(action.getTargetX()).isNull();

        session.startNext(player, NOW);
        assertThat(action.getStatus()).isEqualTo(ActionStatus.RUNNING);
        assertThat(action.getTargetX()).isEqualTo(7);
        assertThat(action.getTargetY()).isEqualTo(7);
        assertThat(player.getState()).isEqualTo(CharacterState.MOVING);
        assertThat(player.getX()).isEqualTo(6);

        assertThat(session.finish(player, action.getId(), NOW)).isPresent();
        assertThat(player.getX()).isEqualTo(7);
        assertThat(player.getFacing()).isEqualTo(Facing.E);
        assertThat(player.getState()).isEqualTo(CharacterState.IDLE);
        assertThat(player.queueLength()).isZero();

        run(player, ActionType.MOVE_BACKWARD);
        assertThat(player.getX()).isEqualTo(6);
        assertThat(player.getY()).isEqualTo(7);
        assertThat(player.getFacing()).isEqualTo(Facing.E);
    }

    @Test
    void 맵_경계를_넘으면_경계_칸에_머문다() {
        run(player, ActionType.MOVE_BACKWARD);
        QueuedAction action = run(player, ActionType.MOVE_BACKWARD);

        assertThat(player.getY()).isEqualTo(8);
        assertThat(action.getTargetY()).isEqualTo(8);
        assertThat(action.isBlocked()).isFalse();
    }

    @Test
    void 다른_캐릭터가_있는_칸으로는_이동하지_못한다() {
        for (int i = 0; i < 5; i++) {
            run(player, ActionType.MOVE_FORWARD);
        }
        assertThat(player.getY()).isEqualTo(2);

        QueuedAction action = run(player, ActionType.MOVE_FORWARD);

        assertThat(action.isBlocked()).isTrue();
        assertThat(action.getTargetX()).isEqualTo(6);
        assertThat(action.getTargetY()).isEqualTo(2);
        assertThat(player.getY()).isEqualTo(2);
    }

    @Test
    void 다른_캐릭터가_이동_중인_목표_칸으로는_이동하지_못한다() {
        for (int i = 0; i < 4; i++) {
            run(player, ActionType.MOVE_FORWARD);
        }
        session.enqueue(enemy, ActionType.MOVE_FORWARD, NOW);
        session.startNext(enemy, NOW);

        session.enqueue(player, ActionType.MOVE_FORWARD, NOW);
        QueuedAction action = session.startNext(player, NOW).orElseThrow();

        assertThat(action.isBlocked()).isTrue();
        assertThat(action.getTargetY()).isEqualTo(3);
    }

    @Test
    void 회전은_방향만_바꾸고_구르기는_바라보는_방향으로_이동한다() {
        QueuedAction turn = run(player, ActionType.TURN_RIGHT);
        assertThat(turn.getTargetX()).isNull();
        assertThat(player.getFacing()).isEqualTo(Facing.E);

        run(player, ActionType.ROLL);
        assertThat(player.getX()).isEqualTo(7);
        assertThat(player.getY()).isEqualTo(7);

        run(player, ActionType.TURN_LEFT);
        run(player, ActionType.TURN_LEFT);
        assertThat(player.getFacing()).isEqualTo(Facing.W);
    }

    @Test
    void 실행_중인_동작이_있으면_다음_동작을_시작하지_않는다() {
        session.enqueue(player, ActionType.ATTACK, NOW);
        session.enqueue(player, ActionType.DEFEND, NOW);

        assertThat(session.startNext(player, NOW)).isPresent();
        assertThat(session.startNext(player, NOW)).isEmpty();
        assertThat(player.getQueue()).extracting(QueuedAction::getSeq).containsExactly(1L, 2L);
    }

    @Test
    void 큐는_실행_중인_동작을_포함해_100개까지_넣을_수_있다() {
        for (int i = 0; i < GameSession.MAX_QUEUE_LENGTH; i++) {
            session.enqueue(player, ActionType.ATTACK, NOW);
        }

        assertThatThrownBy(() -> session.enqueue(player, ActionType.ATTACK, NOW))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.QUEUE_FULL));
    }

    @Test
    void 초기화하면_큐를_비우고_시작_위치로_돌아가며_이전_완료_예약은_무시된다() {
        QueuedAction action = session.enqueue(player, ActionType.MOVE_BACKWARD, NOW);
        session.startNext(player, NOW);
        session.enqueue(player, ActionType.ATTACK, NOW);

        session.reset(NOW.plusSeconds(1));

        assertThat(player.queueLength()).isZero();
        assertThat(player.getState()).isEqualTo(CharacterState.IDLE);
        assertThat(session.finish(player, action.getId(), NOW)).isEmpty();
        assertThat(player.getX()).isEqualTo(6);
        assertThat(session.getLastActiveAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void 캐릭터는_HP_100으로_시작한다() {
        assertThat(player.getHp()).isEqualTo(GameCharacter.MAX_HP);
        assertThat(enemy.getHp()).isEqualTo(100);
    }

    @Test
    void 공격이_끝날_때_바로_앞_칸의_상대에게_적중하면_HP가_10_깎인다() {
        moveFaceToFace();

        GameSession.FinishResult result = finishRun(player, ActionType.ATTACK);

        GameSession.AttackHit hit = result.attackHit().orElseThrow();
        assertThat(hit.target()).isSameAs(enemy);
        assertThat(hit.damage()).isEqualTo(10);
        assertThat(hit.defended()).isFalse();
        assertThat(enemy.getHp()).isEqualTo(90);
        assertThat(player.getHp()).isEqualTo(100);
    }

    @Test
    void 앞_칸이_비어_있으면_빗나간다() {
        GameSession.FinishResult result = finishRun(player, ActionType.ATTACK);

        assertThat(result.attackHit()).isEmpty();
        assertThat(enemy.getHp()).isEqualTo(100);
    }

    @Test
    void 공격_도중_상대가_앞_칸에서_벗어나면_빗나간다() {
        moveFaceToFace();
        QueuedAction attack = session.enqueue(player, ActionType.ATTACK, NOW);
        session.startNext(player, NOW);
        run(enemy, ActionType.TURN_RIGHT);
        run(enemy, ActionType.ROLL);
        assertThat(enemy.getX()).isEqualTo(5);

        assertThat(session.finish(player, attack.getId(), NOW).orElseThrow().attackHit()).isEmpty();
        assertThat(enemy.getHp()).isEqualTo(100);
    }

    @Test
    void 공격자를_마주_보고_방어_중이면_피해가_없다() {
        moveFaceToFace();
        session.enqueue(enemy, ActionType.DEFEND, NOW);
        session.startNext(enemy, NOW);

        GameSession.AttackHit hit = finishRun(player, ActionType.ATTACK).attackHit().orElseThrow();

        assertThat(hit.defended()).isTrue();
        assertThat(hit.damage()).isZero();
        assertThat(enemy.getHp()).isEqualTo(100);
    }

    @Test
    void 옆을_보고_방어하면_막지_못한다() {
        moveFaceToFace();
        run(enemy, ActionType.TURN_RIGHT);
        session.enqueue(enemy, ActionType.DEFEND, NOW);
        session.startNext(enemy, NOW);

        GameSession.AttackHit hit = finishRun(player, ActionType.ATTACK).attackHit().orElseThrow();

        assertThat(hit.defended()).isFalse();
        assertThat(enemy.getHp()).isEqualTo(90);
    }

    @Test
    void HP가_0이_되면_쓰러지고_판이_끝나며_양쪽_모두_명령을_받지_않는다() {
        moveFaceToFace();
        for (int i = 0; i < 9; i++) {
            finishRun(player, ActionType.ATTACK);
        }
        QueuedAction running = session.enqueue(enemy, ActionType.TURN_LEFT, NOW);
        session.startNext(enemy, NOW);
        session.enqueue(enemy, ActionType.ATTACK, NOW);

        QueuedAction attack = session.enqueue(player, ActionType.ATTACK, NOW);
        session.enqueue(player, ActionType.DEFEND, NOW);
        GameSession.FinishResult result = finishRunAhead(attack);

        assertThat(result.roundOver()).isTrue();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ROUND_OVER);
        assertThat(enemy.getHp()).isZero();
        assertThat(enemy.getState()).isEqualTo(CharacterState.DEAD);
        assertThat(enemy.queueLength()).isZero();
        assertThat(player.queueLength()).isZero();
        assertThat(player.getState()).isEqualTo(CharacterState.IDLE);
        assertThat(session.finish(enemy, running.getId(), NOW)).isEmpty();
        assertError(() -> session.enqueue(enemy, ActionType.ATTACK, NOW), ErrorCode.ROUND_OVER);
        assertError(() -> session.enqueue(player, ActionType.ATTACK, NOW), ErrorCode.ROUND_OVER);
    }

    @Test
    void 판이_끝나면_예약된_자동_초기화로_새_판이_시작된다() {
        knockOutEnemy();
        long round = session.getRound();

        assertThat(session.resetRound(round, NOW)).isTrue();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.PLAYING);
        assertThat(session.getRound()).isEqualTo(round + 1);
        assertThat(enemy.getHp()).isEqualTo(100);
        assertThat(enemy.getState()).isEqualTo(CharacterState.IDLE);
        assertThat(player.getY()).isEqualTo(7);
    }

    @Test
    void 이미_지난_판의_자동_초기화는_무시된다() {
        knockOutEnemy();
        long round = session.getRound();
        session.reset(NOW);
        run(player, ActionType.MOVE_FORWARD);

        assertThat(session.resetRound(round, NOW)).isFalse();
        assertThat(player.getY()).isEqualTo(6);
    }

    @Test
    void 초기화하면_HP가_100으로_돌아오고_다시_명령할_수_있다() {
        moveFaceToFace();
        for (int i = 0; i < 10; i++) {
            finishRun(player, ActionType.ATTACK);
        }

        session.reset(NOW);

        assertThat(enemy.getHp()).isEqualTo(100);
        assertThat(enemy.getState()).isEqualTo(CharacterState.IDLE);
        assertThat(session.enqueue(enemy, ActionType.ATTACK, NOW)).isNotNull();
    }

    @Test
    void SOLO는_한_명이_두_캐릭터를_맡고_더_참가할_수_없다() {
        Participant me = session.join(NOW);

        assertThat(me.getControls()).containsExactly(CharacterType.PLAYER, CharacterType.ENEMY);
        assertThat(session.findParticipant(me.getToken())).contains(me);
        assertThat(session.findParticipant("wrong-token")).isEmpty();
        assertThat(session.findParticipant((String) null)).isEmpty();
        assertError(() -> session.join(NOW), ErrorCode.SESSION_FULL);
    }

    @Test
    void MULTI는_두_명이_모이면_시작하고_자기_캐릭터만_조종한다() {
        GameSession multi = new GameSession(UUID.randomUUID(), SessionMode.MULTI, NOW);
        Participant host = multi.join(NOW);
        GameCharacter hostCharacter = multi.character(CharacterType.PLAYER);

        assertThat(multi.getStatus()).isEqualTo(SessionStatus.WAITING);
        assertThat(host.getControls()).containsExactly(CharacterType.PLAYER);
        assertError(() -> multi.enqueue(hostCharacter, ActionType.ATTACK, NOW), ErrorCode.WAITING_FOR_OPPONENT);

        Participant guest = multi.join(NOW);

        assertThat(guest.getControls()).containsExactly(CharacterType.ENEMY);
        assertThat(multi.getStatus()).isEqualTo(SessionStatus.PLAYING);
        assertThat(host.getToken()).isNotEqualTo(guest.getToken());
        assertError(() -> multi.join(NOW), ErrorCode.SESSION_FULL);
        assertError(() -> multi.checkControls(guest, hostCharacter), ErrorCode.NOT_YOUR_CHARACTER);
        assertError(() -> multi.reset(NOW), ErrorCode.RESET_NOT_ALLOWED);
    }

    @Test
    void MULTI에서_한_명이_나가면_판을_버리고_새_상대를_기다린다() {
        GameSession multi = new GameSession(UUID.randomUUID(), SessionMode.MULTI, NOW);
        Participant host = multi.join(NOW);
        Participant guest = multi.join(NOW);
        GameCharacter hostCharacter = multi.character(CharacterType.PLAYER);
        multi.enqueue(hostCharacter, ActionType.MOVE_FORWARD, NOW);
        multi.startNext(hostCharacter, NOW);

        assertThat(multi.leave(host, NOW)).isFalse();

        assertThat(multi.getStatus()).isEqualTo(SessionStatus.WAITING);
        assertThat(hostCharacter.queueLength()).isZero();
        assertThat(multi.findParticipant(host.getToken())).isEmpty();

        Participant newcomer = multi.join(NOW);
        assertThat(newcomer.getControls()).containsExactly(CharacterType.PLAYER);
        assertThat(multi.getStatus()).isEqualTo(SessionStatus.PLAYING);

        assertThat(multi.leave(guest, NOW)).isFalse();
        assertThat(multi.leave(newcomer, NOW)).isTrue();
    }

    @Test
    void 연결이_모두_끊긴_뒤의_시간으로_자리를_비웠는지_판단한다() {
        Participant me = session.join(NOW);
        assertThat(me.isAbsentSince(NOW)).isTrue();

        assertThat(me.connect()).isTrue();
        assertThat(me.connect()).isFalse();
        assertThat(me.isAbsentSince(NOW.plusSeconds(60))).isFalse();

        assertThat(me.disconnect(NOW.plusSeconds(1))).isFalse();
        assertThat(me.disconnect(NOW.plusSeconds(2))).isTrue();
        assertThat(me.isConnected()).isFalse();
        assertThat(me.isAbsentSince(NOW.plusSeconds(1))).isFalse();
        assertThat(me.isAbsentSince(NOW.plusSeconds(2))).isTrue();
    }

    private void knockOutEnemy() {
        moveFaceToFace();
        for (int i = 0; i < 10; i++) {
            finishRun(player, ActionType.ATTACK);
        }
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ROUND_OVER);
    }

    private GameSession.FinishResult finishRunAhead(QueuedAction head) {
        session.startNext(player, NOW).orElseThrow();
        return session.finish(player, head.getId(), NOW).orElseThrow();
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GameException.class,
                e -> assertThat(e.errorCode()).isEqualTo(expected));
    }

    // 플레이어를 (6,2)로 옮겨 (6,1)의 적과 마주 보게 한다. 플레이어는 N, 적은 S를 본다.
    private void moveFaceToFace() {
        for (int i = 0; i < 5; i++) {
            run(player, ActionType.MOVE_FORWARD);
        }
        assertThat(player.getY()).isEqualTo(2);
    }

    private GameSession.FinishResult finishRun(GameCharacter character, ActionType type) {
        QueuedAction action = session.enqueue(character, type, NOW);
        session.startNext(character, NOW).orElseThrow();
        return session.finish(character, action.getId(), NOW).orElseThrow();
    }

    private QueuedAction run(GameCharacter character, ActionType type) {
        QueuedAction action = session.enqueue(character, type, NOW);
        session.startNext(character, NOW).orElseThrow();
        session.finish(character, action.getId(), NOW).orElseThrow();
        return action;
    }
}
