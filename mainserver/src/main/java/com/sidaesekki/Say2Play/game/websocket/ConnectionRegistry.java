package com.sidaesekki.Say2Play.game.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * 열려 있는 WebSocket 연결을 기억해 두었다가, 세션이 끝나거나 참가자가 나가면 서버 쪽에서 닫는다.
 * STOMP의 simpSessionId는 WebSocketSession의 id와 같다. 연결의 주인은 STOMP CONNECT가 끝난 뒤 {@link #own}으로 알려 준다.
 */
@Component
public class ConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectionRegistry.class);

    private final Map<String, WebSocketSession> connections = new ConcurrentHashMap<>();
    private final Map<String, PlayerPrincipal> owners = new ConcurrentHashMap<>();

    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                connections.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                connections.remove(session.getId());
                owners.remove(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }

    public void own(String connectionId, PlayerPrincipal principal) {
        if (connections.containsKey(connectionId)) {
            owners.put(connectionId, principal);
        }
    }

    public void closeSession(UUID sessionId) {
        close(principal -> principal.sessionId().equals(sessionId));
    }

    public void closeParticipant(UUID participantId) {
        close(principal -> principal.participantId().equals(participantId));
    }

    private void close(Predicate<PlayerPrincipal> target) {
        owners.forEach((connectionId, owner) -> {
            WebSocketSession connection = connections.get(connectionId);
            if (connection != null && target.test(owner)) {
                try {
                    connection.close(CloseStatus.NORMAL);
                } catch (IOException e) {
                    log.debug("WebSocket close failed: {}", connection.getId(), e);
                }
            }
        });
    }
}
