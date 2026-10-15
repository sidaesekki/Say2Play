package com.sidaesekki.Say2Play.game.service;

import com.sidaesekki.Say2Play.game.domain.GameSession;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 진행 중인 게임 세션을 메모리에 보관한다. 서버가 재시작되면 모두 사라진다. */
@Component
public class SessionRepository {

    private final Map<UUID, GameSession> sessions = new ConcurrentHashMap<>();

    public void save(GameSession session) {
        sessions.put(session.getId(), session);
    }

    public Optional<GameSession> find(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public Collection<GameSession> findAll() {
        return List.copyOf(sessions.values());
    }

    public void remove(UUID sessionId) {
        sessions.remove(sessionId);
    }
}
