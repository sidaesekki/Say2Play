package com.sidaesekki.Say2Play.game.service;

import com.sidaesekki.Say2Play.ai.ActionResolver;
import com.sidaesekki.Say2Play.game.domain.ActionType;
import com.sidaesekki.Say2Play.game.domain.GameCharacter;
import com.sidaesekki.Say2Play.game.domain.GameSession;
import com.sidaesekki.Say2Play.game.domain.QueuedAction;
import com.sidaesekki.Say2Play.game.dto.ActionResponse;
import com.sidaesekki.Say2Play.game.dto.CharacterDetailResponse;
import com.sidaesekki.Say2Play.game.dto.CharacterResponse;
import com.sidaesekki.Say2Play.game.dto.CommandResponse;
import com.sidaesekki.Say2Play.game.dto.ResetResponse;
import com.sidaesekki.Say2Play.game.dto.SessionCreateResponse;
import com.sidaesekki.Say2Play.game.dto.SessionDetailResponse;
import com.sidaesekki.Say2Play.game.dto.SessionResponse;
import com.sidaesekki.Say2Play.game.websocket.GameEvent;
import com.sidaesekki.Say2Play.game.websocket.GameEventPublisher;
import com.sidaesekki.Say2Play.game.websocket.GameEventType;
import com.sidaesekki.Say2Play.global.error.ErrorCode;
import com.sidaesekki.Say2Play.global.error.GameException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GameSessionService {

    private static final int MAX_TEXT_LENGTH = 500;

    private final SessionRepository sessionRepository;
    private final ActionResolver actionResolver;
    private final ActionExecutor actionExecutor;
    private final GameEventPublisher publisher;
    private final SessionProperties sessionProperties;
    private final Clock clock;

    public GameSessionService(SessionRepository sessionRepository, ActionResolver actionResolver,
                              ActionExecutor actionExecutor, GameEventPublisher publisher,
                              SessionProperties sessionProperties, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.actionResolver = actionResolver;
        this.actionExecutor = actionExecutor;
        this.publisher = publisher;
        this.sessionProperties = sessionProperties;
        this.clock = clock;
    }

    public SessionCreateResponse create() {
        GameSession session = new GameSession(UUID.randomUUID(), clock.instant());
        sessionRepository.save(session);
        synchronized (session) {
            return new SessionCreateResponse(SessionResponse.from(session),
                    session.getCharacters().stream().map(CharacterResponse::from).toList());
        }
    }

    public SessionDetailResponse get(UUID sessionId) {
        GameSession session = findSession(sessionId);
        synchronized (session) {
            return new SessionDetailResponse(SessionResponse.from(session),
                    session.getCharacters().stream().map(CharacterDetailResponse::from).toList());
        }
    }

    /**
     * 텍스트를 AI로 Action 1개에 매칭해 캐릭터 큐에 넣는다.
     * AI 호출(최대 5초)은 세션 락 밖에서 하고, 큐에 넣기 직전에 상태를 다시 확인한다.
     */
    public CommandResponse command(UUID sessionId, UUID characterId, String text) {
        String trimmed = validateText(text);
        GameSession session = findSession(sessionId);
        GameCharacter character;
        synchronized (session) {
            checkPlaying(session);
            character = session.findCharacter(characterId)
                    .orElseThrow(() -> new GameException(ErrorCode.CHARACTER_NOT_FOUND));
            session.checkQueueCapacity(character);
        }

        ActionType actionType = actionResolver.resolve(trimmed);

        synchronized (session) {
            checkPlaying(session);
            QueuedAction action = session.enqueue(character, actionType, clock.instant());
            actionExecutor.startIfIdle(session, character);
            return new CommandResponse(character.getId(), ActionResponse.from(action), character.queueLength());
        }
    }

    public ResetResponse reset(UUID sessionId) {
        GameSession session = findSession(sessionId);
        synchronized (session) {
            checkPlaying(session);
            session.reset(clock.instant());
            publisher.publish(sessionId, GameEventType.SESSION_RESET, new GameEvent.SessionReset(
                    session.getCharacters().stream().map(CharacterResponse::from).toList()));
            return new ResetResponse(session.getCharacters().stream().map(CharacterDetailResponse::from).toList());
        }
    }

    public void delete(UUID sessionId) {
        end(findSession(sessionId), GameEvent.EndReason.CLOSED);
    }

    /** 마지막 입력 이후 idle-timeout이 지난 세션을 종료한다. */
    public void expireIdleSessions(Instant now) {
        Instant threshold = now.minus(sessionProperties.idleTimeout());
        for (GameSession session : sessionRepository.findAll()) {
            boolean idle;
            synchronized (session) {
                idle = session.isIdleSince(threshold);
            }
            if (idle) {
                end(session, GameEvent.EndReason.EXPIRED);
            }
        }
    }

    private void end(GameSession session, GameEvent.EndReason reason) {
        synchronized (session) {
            if (session.isEnded()) {
                return;
            }
            session.end();
            sessionRepository.remove(session.getId());
        }
        publisher.publishEndedAndClose(session.getId(), reason);
    }

    private GameSession findSession(UUID sessionId) {
        return sessionRepository.find(sessionId)
                .orElseThrow(() -> new GameException(ErrorCode.SESSION_NOT_FOUND));
    }

    // 세션은 종료와 동시에 메모리에서 지워지므로, 대부분 404가 되고 409는 종료와 겹친 요청에서만 나온다.
    private static void checkPlaying(GameSession session) {
        if (session.isEnded()) {
            throw new GameException(ErrorCode.SESSION_ENDED);
        }
    }

    private static String validateText(String text) {
        if (text == null) {
            throw new GameException(ErrorCode.VALIDATION_ERROR);
        }
        String trimmed = text.strip();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length < 1 || length > MAX_TEXT_LENGTH) {
            throw new GameException(ErrorCode.VALIDATION_ERROR);
        }
        return trimmed;
    }
}
