package com.sidaesekki.Say2Play.game.service;

import com.sidaesekki.Say2Play.ai.ActionResolver;
import com.sidaesekki.Say2Play.game.domain.ActionType;
import com.sidaesekki.Say2Play.game.domain.GameCharacter;
import com.sidaesekki.Say2Play.game.domain.GameSession;
import com.sidaesekki.Say2Play.game.domain.Participant;
import com.sidaesekki.Say2Play.game.domain.QueuedAction;
import com.sidaesekki.Say2Play.game.domain.SessionMode;
import com.sidaesekki.Say2Play.game.dto.ActionResponse;
import com.sidaesekki.Say2Play.game.dto.CharacterDetailResponse;
import com.sidaesekki.Say2Play.game.dto.CommandResponse;
import com.sidaesekki.Say2Play.game.dto.MeResponse;
import com.sidaesekki.Say2Play.game.dto.PlayerResponse;
import com.sidaesekki.Say2Play.game.dto.ResetResponse;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class GameSessionService {

    private static final int MAX_TEXT_LENGTH = 500;

    private final SessionRepository sessionRepository;
    private final ActionResolver actionResolver;
    private final ActionExecutor actionExecutor;
    private final GameEventPublisher publisher;
    private final TaskScheduler taskScheduler;
    private final SessionProperties sessionProperties;
    private final Clock clock;

    public GameSessionService(SessionRepository sessionRepository, ActionResolver actionResolver,
                              ActionExecutor actionExecutor, GameEventPublisher publisher,
                              @Qualifier("taskScheduler") TaskScheduler taskScheduler,
                              SessionProperties sessionProperties, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.actionResolver = actionResolver;
        this.actionExecutor = actionExecutor;
        this.publisher = publisher;
        this.taskScheduler = taskScheduler;
        this.sessionProperties = sessionProperties;
        this.clock = clock;
    }

    /** 세션을 만들고 만든 사람을 첫 참가자로 넣는다. MULTI면 상대가 초대 링크로 참가할 때까지 WAITING이다. */
    public SessionDetailResponse create(SessionMode mode) {
        if (mode == null) {
            throw new GameException(ErrorCode.VALIDATION_ERROR);
        }
        Instant now = clock.instant();
        GameSession session = new GameSession(UUID.randomUUID(), mode, now);
        synchronized (session) {
            Participant participant = session.join(now);
            sessionRepository.save(session);
            scheduleAbsenceCheck(session.getId(), participant.getId(), now);
            return detail(session, MeResponse.withToken(participant));
        }
    }

    /** 초대 링크로 MULTI 세션에 참가한다. 두 명이 모이면 새 판이 시작된다. */
    public SessionDetailResponse join(UUID sessionId) {
        GameSession session = findSession(sessionId);
        synchronized (session) {
            checkNotEnded(session);
            Instant now = clock.instant();
            Participant participant = session.join(now);
            publisher.publish(sessionId, GameEventType.PLAYER_JOINED, new GameEvent.PlayerJoined(
                    participant.getControls(), SessionResponse.from(session), ActionExecutor.characters(session)));
            scheduleAbsenceCheck(sessionId, participant.getId(), now);
            return detail(session, MeResponse.withToken(participant));
        }
    }

    public SessionDetailResponse get(UUID sessionId, String token) {
        GameSession session = findSession(sessionId);
        synchronized (session) {
            return detail(session, MeResponse.from(requireParticipant(session, token)));
        }
    }

    /**
     * 텍스트를 AI로 Action 1개에 매칭해 캐릭터 큐에 넣는다. 자기 캐릭터에만 보낼 수 있다.
     * AI 호출(최대 5초)은 세션 락 밖에서 하고, 큐에 넣기 직전에 상태를 다시 확인한다.
     */
    public CommandResponse command(UUID sessionId, UUID characterId, String token, String text) {
        String trimmed = validateText(text);
        GameSession session = findSession(sessionId);
        GameCharacter character;
        synchronized (session) {
            character = checkCanCommand(session, characterId, token);
        }

        ActionType actionType = actionResolver.resolve(trimmed);

        synchronized (session) {
            checkCanCommand(session, characterId, token);
            QueuedAction action = session.enqueue(character, actionType, clock.instant());
            actionExecutor.startIfIdle(session, character);
            return new CommandResponse(character.getId(), ActionResponse.from(action), character.queueLength());
        }
    }

    /** SOLO에서만 쓸 수 있는 수동 초기화. */
    public ResetResponse reset(UUID sessionId, String token) {
        GameSession session = findSession(sessionId);
        synchronized (session) {
            checkNotEnded(session);
            requireParticipant(session, token);
            session.reset(clock.instant());
            actionExecutor.publishReset(session);
            return new ResetResponse(session.getCharacters().stream().map(CharacterDetailResponse::from).toList());
        }
    }

    /** 게임 나가기. 남은 사람이 없으면 세션이 끝나고, MULTI에서 한 명이 남으면 새 상대를 기다린다. */
    public void leave(UUID sessionId, String token) {
        GameSession session = findSession(sessionId);
        Participant participant;
        synchronized (session) {
            participant = requireParticipant(session, token);
        }
        leave(session, participant, GameEvent.LeaveReason.LEFT);
    }

    public void connected(UUID sessionId, UUID participantId) {
        sessionRepository.find(sessionId).ifPresent(session -> {
            synchronized (session) {
                session.findParticipant(participantId).filter(Participant::connect).ifPresent(participant ->
                        publisher.publish(sessionId, GameEventType.PLAYER_CONNECTION,
                                new GameEvent.PlayerConnection(participant.getControls(), true)));
            }
        });
    }

    /** 마지막 연결이 끊기면 reconnect-grace 안에 돌아오는지 지켜본다. */
    public void disconnected(UUID sessionId, UUID participantId) {
        sessionRepository.find(sessionId).ifPresent(session -> {
            synchronized (session) {
                Instant now = clock.instant();
                session.findParticipant(participantId).filter(participant -> participant.disconnect(now))
                        .ifPresent(participant -> {
                            publisher.publish(sessionId, GameEventType.PLAYER_CONNECTION,
                                    new GameEvent.PlayerConnection(participant.getControls(), false));
                            scheduleAbsenceCheck(sessionId, participantId, now);
                        });
            }
        });
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

    // 참가하거나 연결이 끊긴 뒤 grace 동안 한 번도 연결되지 않으면 나간 것으로 본다.
    private void scheduleAbsenceCheck(UUID sessionId, UUID participantId, Instant from) {
        taskScheduler.schedule(() -> checkAbsence(sessionId, participantId),
                from.plus(sessionProperties.reconnectGrace()));
    }

    private void checkAbsence(UUID sessionId, UUID participantId) {
        sessionRepository.find(sessionId).ifPresent(session -> {
            Participant absent;
            synchronized (session) {
                Instant threshold = clock.instant().minus(sessionProperties.reconnectGrace());
                absent = session.findParticipant(participantId)
                        .filter(participant -> participant.isAbsentSince(threshold))
                        .orElse(null);
            }
            if (absent != null) {
                leave(session, absent, GameEvent.LeaveReason.DISCONNECTED);
            }
        });
    }

    private void leave(GameSession session, Participant participant, GameEvent.LeaveReason reason) {
        boolean empty;
        synchronized (session) {
            if (session.isEnded() || session.findParticipant(participant.getId()).isEmpty()) {
                return;
            }
            empty = session.leave(participant, clock.instant());
            if (!empty) {
                publisher.publish(session.getId(), GameEventType.PLAYER_LEFT, new GameEvent.PlayerLeft(
                        participant.getControls(), reason, SessionResponse.from(session),
                        ActionExecutor.characters(session)));
            }
        }
        publisher.closeParticipant(participant.getId());
        if (empty) {
            end(session, GameEvent.EndReason.CLOSED);
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

    private GameCharacter checkCanCommand(GameSession session, UUID characterId, String token) {
        checkNotEnded(session);
        Participant participant = requireParticipant(session, token);
        GameCharacter character = session.findCharacter(characterId)
                .orElseThrow(() -> new GameException(ErrorCode.CHARACTER_NOT_FOUND));
        session.checkControls(participant, character);
        session.checkCanEnqueue(character);
        return character;
    }

    private static SessionDetailResponse detail(GameSession session, MeResponse me) {
        return new SessionDetailResponse(SessionResponse.from(session),
                session.getCharacters().stream().map(CharacterDetailResponse::from).toList(),
                session.getParticipants().stream().map(PlayerResponse::from).toList(),
                me);
    }

    private GameSession findSession(UUID sessionId) {
        return sessionRepository.find(sessionId)
                .orElseThrow(() -> new GameException(ErrorCode.SESSION_NOT_FOUND));
    }

    private static Participant requireParticipant(GameSession session, String token) {
        return session.findParticipant(token)
                .orElseThrow(() -> new GameException(ErrorCode.INVALID_PLAYER_TOKEN));
    }

    // 세션은 종료와 동시에 메모리에서 지워지므로, 대부분 404가 되고 409는 종료와 겹친 요청에서만 나온다.
    private static void checkNotEnded(GameSession session) {
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
