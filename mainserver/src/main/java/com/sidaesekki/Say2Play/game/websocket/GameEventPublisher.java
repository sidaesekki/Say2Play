package com.sidaesekki.Say2Play.game.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.json.JsonMapper;

/** 게임 세션별로 연결된 WebSocket에 이벤트를 브로드캐스트한다. */
@Component
public class GameEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(GameEventPublisher.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final JsonMapper jsonMapper;
    private final Map<UUID, Set<WebSocketSession>> connections = new ConcurrentHashMap<>();

    public GameEventPublisher(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    void register(UUID sessionId, WebSocketSession connection) {
        // 여러 스레드에서 보내도 안전하도록 감싼다.
        connections.computeIfAbsent(sessionId, id -> ConcurrentHashMap.newKeySet())
                .add(new ConcurrentWebSocketSessionDecorator(connection, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT));
    }

    void unregister(UUID sessionId, WebSocketSession connection) {
        connections.computeIfPresent(sessionId, (id, set) -> {
            set.removeIf(decorated -> decorated.getId().equals(connection.getId()));
            return set.isEmpty() ? null : set;
        });
    }

    public void publish(UUID sessionId, GameEventType type, Object data) {
        Set<WebSocketSession> targets = connections.get(sessionId);
        if (targets == null || targets.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(jsonMapper.writeValueAsString(new GameEvent(type, data)));
        for (WebSocketSession target : targets) {
            send(target, message);
        }
    }

    /** SESSION_ENDED를 보낸 뒤 그 세션의 연결을 모두 닫는다. */
    public void publishEndedAndClose(UUID sessionId, GameEvent.EndReason reason) {
        publish(sessionId, GameEventType.SESSION_ENDED, new GameEvent.SessionEnded(reason));
        Set<WebSocketSession> targets = connections.remove(sessionId);
        if (targets == null) {
            return;
        }
        for (WebSocketSession target : targets) {
            close(target);
        }
    }

    private void send(WebSocketSession target, TextMessage message) {
        if (!target.isOpen()) {
            return;
        }
        try {
            target.sendMessage(message);
        } catch (IOException | RuntimeException e) {
            log.debug("WebSocket send failed: {}", target.getId(), e);
            close(target);
        }
    }

    private void close(WebSocketSession target) {
        try {
            target.close(CloseStatus.NORMAL);
        } catch (IOException e) {
            log.debug("WebSocket close failed: {}", target.getId(), e);
        }
    }
}
