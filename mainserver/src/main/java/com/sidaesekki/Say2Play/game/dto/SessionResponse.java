package com.sidaesekki.Say2Play.game.dto;

import com.sidaesekki.Say2Play.game.domain.GameSession;
import com.sidaesekki.Say2Play.game.domain.SessionStatus;
import java.util.UUID;

public record SessionResponse(UUID id, SessionStatus status, int mapCols, int mapRows) {

    public static SessionResponse from(GameSession session) {
        return new SessionResponse(session.getId(), session.getStatus(), session.getMapCols(), session.getMapRows());
    }
}
