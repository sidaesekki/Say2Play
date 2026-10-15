package com.sidaesekki.Say2Play.game.websocket;

import com.sidaesekki.Say2Play.game.dto.ActionResponse;
import com.sidaesekki.Say2Play.game.dto.CharacterResponse;
import com.sidaesekki.Say2Play.game.domain.Facing;
import java.util.List;
import java.util.UUID;

/** WebSocket 메시지 {@code {"type": ..., "data": ...}}와 이벤트별 data. */
public record GameEvent(GameEventType type, Object data) {

    public record Position(int x, int y, Facing facing) {
    }

    public record ActionStarted(UUID characterId, ActionResponse action, long durationMs, Position from, Position to) {
    }

    public record ActionFinished(UUID actionId, CharacterResponse character, int queueLength) {
    }

    public record SessionReset(List<CharacterResponse> characters) {
    }

    public record SessionEnded(EndReason reason) {
    }

    public enum EndReason {
        CLOSED, EXPIRED
    }
}
