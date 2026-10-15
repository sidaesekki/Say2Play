package com.sidaesekki.Say2Play.game.websocket;

import com.sidaesekki.Say2Play.game.domain.GameSession;
import com.sidaesekki.Say2Play.game.domain.Participant;
import com.sidaesekki.Say2Play.game.service.SessionRepository;
import com.sidaesekki.Say2Play.global.error.ErrorCode;
import com.sidaesekki.Say2Play.global.error.GameException;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * CONNECT: {@code X-Session-Id}, {@code X-Player-Token} 헤더로 참가자를 확인한다.
 * SUBSCRIBE: 자기 세션의 토픽만 구독할 수 있다.
 * SEND: v1은 서버 → 클라이언트 단방향이라 받지 않는다(명령은 REST).
 * 거절하면 클라이언트는 ERROR 프레임을 받고 연결이 끊긴다.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    public static final String SESSION_ID_HEADER = "X-Session-Id";
    public static final String PLAYER_TOKEN_HEADER = "X-Player-Token";
    public static final String TOPIC_PREFIX = "/topic/sessions/";

    private final SessionRepository sessionRepository;

    public StompAuthInterceptor(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader(SESSION_ID_HEADER),
                    accessor.getFirstNativeHeader(PLAYER_TOKEN_HEADER)));
        } else if (command == StompCommand.SUBSCRIBE) {
            if (!(accessor.getUser() instanceof PlayerPrincipal principal)
                    || !(TOPIC_PREFIX + principal.sessionId()).equals(accessor.getDestination())) {
                throw new GameException(ErrorCode.STOMP_NOT_ALLOWED);
            }
        } else if (command == StompCommand.SEND) {
            throw new GameException(ErrorCode.STOMP_NOT_ALLOWED);
        }
        return message;
    }

    private PlayerPrincipal authenticate(String sessionIdHeader, String token) {
        UUID sessionId;
        try {
            sessionId = UUID.fromString(sessionIdHeader);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new GameException(ErrorCode.SESSION_NOT_FOUND);
        }
        GameSession session = sessionRepository.find(sessionId)
                .orElseThrow(() -> new GameException(ErrorCode.SESSION_NOT_FOUND));
        synchronized (session) {
            Participant participant = session.findParticipant(token)
                    .orElseThrow(() -> new GameException(ErrorCode.INVALID_PLAYER_TOKEN));
            return new PlayerPrincipal(sessionId, participant.getId());
        }
    }
}
