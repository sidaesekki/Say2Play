package com.sidaesekki.Say2Play.game.service;

import com.sidaesekki.Say2Play.game.domain.GameCharacter;
import com.sidaesekki.Say2Play.game.domain.GameSession;
import com.sidaesekki.Say2Play.game.domain.QueuedAction;
import com.sidaesekki.Say2Play.game.dto.ActionResponse;
import com.sidaesekki.Say2Play.game.dto.CharacterResponse;
import com.sidaesekki.Say2Play.game.websocket.GameEvent;
import com.sidaesekki.Say2Play.game.websocket.GameEventPublisher;
import com.sidaesekki.Say2Play.game.websocket.GameEventType;
import java.time.Clock;
import java.util.UUID;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * 캐릭터 큐의 동작을 순서대로 실행한다.
 * 동작을 시작하면 ACTION_STARTED를 보내고, duration 뒤에 완료 처리를 예약한다.
 * 완료되면 ACTION_FINISHED를 보내고 다음 동작을 시작한다.
 */
@Component
public class ActionExecutor {

    private final SessionRepository sessionRepository;
    private final GameEventPublisher publisher;
    private final TaskScheduler taskScheduler;
    private final Clock clock;

    public ActionExecutor(SessionRepository sessionRepository, GameEventPublisher publisher,
                          TaskScheduler taskScheduler, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.publisher = publisher;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
    }

    /** 호출하는 쪽이 {@code synchronized (session)}을 잡고 있어야 한다. */
    public void startIfIdle(GameSession session, GameCharacter character) {
        session.startNext(character, clock.instant()).ifPresent(action -> {
            publisher.publish(session.getId(), GameEventType.ACTION_STARTED, startedEvent(character, action));
            UUID sessionId = session.getId();
            UUID characterId = character.getId();
            UUID actionId = action.getId();
            taskScheduler.schedule(() -> finish(sessionId, characterId, actionId), action.finishesAt());
        });
    }

    private void finish(UUID sessionId, UUID characterId, UUID actionId) {
        GameSession session = sessionRepository.find(sessionId).orElse(null);
        if (session == null) {
            return;
        }
        synchronized (session) {
            if (session.isEnded()) {
                return;
            }
            GameCharacter character = session.findCharacter(characterId).orElseThrow();
            session.finish(character, actionId, clock.instant()).ifPresent(result -> {
                publisher.publish(sessionId, GameEventType.ACTION_FINISHED, new GameEvent.ActionFinished(
                        actionId, CharacterResponse.from(character), character.queueLength()));
                result.attackHit().ifPresent(hit -> publisher.publish(sessionId, GameEventType.ATTACK_HIT,
                        new GameEvent.AttackHit(characterId, actionId, CharacterResponse.from(hit.target()),
                                hit.damage(), hit.defended())));
                startIfIdle(session, character);
            });
        }
    }

    private static GameEvent.ActionStarted startedEvent(GameCharacter character, QueuedAction action) {
        GameEvent.Position from = new GameEvent.Position(character.getX(), character.getY(), character.getFacing());
        GameEvent.Position to = new GameEvent.Position(
                action.getTargetX() != null ? action.getTargetX() : character.getX(),
                action.getTargetY() != null ? action.getTargetY() : character.getY(),
                action.getTargetFacing());
        return new GameEvent.ActionStarted(character.getId(), ActionResponse.from(action),
                action.getActionType().duration().toMillis(), from, to);
    }
}
