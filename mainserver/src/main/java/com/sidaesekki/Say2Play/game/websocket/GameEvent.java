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

    /** 공격이 끝날 때 앞 칸의 상대에게 적중했을 때. {@code target}은 피해가 반영된 상대 상태다. */
    public record AttackHit(UUID attackerId, UUID actionId, CharacterResponse target, int damage, boolean defended) {
    }

    public record SessionReset(List<CharacterResponse> characters) {
    }

    public record SessionEnded(EndReason reason) {
    }

    public enum EndReason {
        CLOSED, EXPIRED
    }
}
