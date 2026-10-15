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
        session = new GameSession(UUID.randomUUID(), NOW);
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
    void 이동은_시작할_때_목표를_정하고_끝날_때_좌표를_확정한다() {
        QueuedAction action = session.enqueue(player, ActionType.MOVE_RIGHT, NOW);
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
        assertThat(player.getFacing()).isEqualTo(Facing.N);
        assertThat(player.getState()).isEqualTo(CharacterState.IDLE);
        assertThat(player.queueLength()).isZero();
    }

    @Test
    void 맵_경계를_넘으면_경계_칸에_머문다() {
        run(player, ActionType.MOVE_DOWN);
        QueuedAction action = run(player, ActionType.MOVE_DOWN);

        assertThat(player.getY()).isEqualTo(8);
        assertThat(action.getTargetY()).isEqualTo(8);
        assertThat(action.isBlocked()).isFalse();
    }

    @Test
    void 다른_캐릭터가_있는_칸으로는_이동하지_못한다() {
        for (int i = 0; i < 5; i++) {
            run(player, ActionType.MOVE_UP);
        }
        assertThat(player.getY()).isEqualTo(2);

        QueuedAction action = run(player, ActionType.MOVE_UP);

        assertThat(action.isBlocked()).isTrue();
        assertThat(action.getTargetX()).isEqualTo(6);
        assertThat(action.getTargetY()).isEqualTo(2);
        assertThat(player.getY()).isEqualTo(2);
    }

    @Test
    void 다른_캐릭터가_이동_중인_목표_칸으로는_이동하지_못한다() {
        for (int i = 0; i < 4; i++) {
            run(player, ActionType.MOVE_UP);
        }
        session.enqueue(enemy, ActionType.MOVE_DOWN, NOW);
        session.startNext(enemy, NOW);

        session.enqueue(player, ActionType.MOVE_UP, NOW);
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
        QueuedAction action = session.enqueue(player, ActionType.MOVE_LEFT, NOW);
        session.startNext(player, NOW);
        session.enqueue(player, ActionType.ATTACK, NOW);

        session.reset(NOW.plusSeconds(1));

        assertThat(player.queueLength()).isZero();
        assertThat(player.getState()).isEqualTo(CharacterState.IDLE);
        assertThat(session.finish(player, action.getId(), NOW)).isEmpty();
        assertThat(player.getX()).isEqualTo(6);
        assertThat(session.getLastActiveAt()).isEqualTo(NOW.plusSeconds(1));
    }

    private QueuedAction run(GameCharacter character, ActionType type) {
        QueuedAction action = session.enqueue(character, type, NOW);
        session.startNext(character, NOW).orElseThrow();
        session.finish(character, action.getId(), NOW).orElseThrow();
        return action;
    }
}
