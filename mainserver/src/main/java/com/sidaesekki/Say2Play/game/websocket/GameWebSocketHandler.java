package com.sidaesekki.Say2Play.game.websocket;

import com.sidaesekki.Say2Play.game.service.SessionRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** v1은 서버 → 클라이언트 단방향이라 클라이언트가 보낸 메시지는 무시한다. */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final GameEventPublisher publisher;
    private final SessionRepository sessionRepository;

    public GameWebSocketHandler(GameEventPublisher publisher, SessionRepository sessionRepository) {
        this.publisher = publisher;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession connection) throws Exception {
        UUID sessionId = sessionId(connection);
        publisher.register(sessionId, connection);
        // 핸드셰이크와 등록 사이에 세션이 종료됐으면 닫는다.
        if (sessionRepository.find(sessionId).isEmpty()) {
            publisher.unregister(sessionId, connection);
            connection.close(CloseStatus.NORMAL);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession connection, TextMessage message) {
    }

    @Override
    public void afterConnectionClosed(WebSocketSession connection, CloseStatus status) {
        publisher.unregister(sessionId(connection), connection);
    }

    private static UUID sessionId(WebSocketSession connection) {
        return (UUID) connection.getAttributes().get(SessionHandshakeInterceptor.SESSION_ID_ATTRIBUTE);
    }
}
