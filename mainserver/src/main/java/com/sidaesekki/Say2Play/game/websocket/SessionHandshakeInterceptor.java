package com.sidaesekki.Say2Play.game.websocket;

import com.sidaesekki.Say2Play.game.service.SessionRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** 경로의 sessionId가 없거나 만료된 세션이면 연결 수립 단계에서 404로 거절한다. */
@Component
public class SessionHandshakeInterceptor implements HandshakeInterceptor {

    static final String SESSION_ID_ATTRIBUTE = "sessionId";

    private final SessionRepository sessionRepository;

    public SessionHandshakeInterceptor(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Optional<UUID> sessionId = parseSessionId(request.getURI().getPath())
                .filter(id -> sessionRepository.find(id).isPresent());
        if (sessionId.isEmpty()) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }
        attributes.put(SESSION_ID_ATTRIBUTE, sessionId.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private static Optional<UUID> parseSessionId(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1);
        try {
            return Optional.of(UUID.fromString(last));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
