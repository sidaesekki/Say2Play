package com.sidaesekki.Say2Play.game.websocket;

import com.sidaesekki.Say2Play.game.domain.CharacterType;
import com.sidaesekki.Say2Play.game.domain.Facing;
import com.sidaesekki.Say2Play.game.dto.ActionResponse;
import com.sidaesekki.Say2Play.game.dto.CharacterResponse;
import com.sidaesekki.Say2Play.game.dto.SessionResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** STOMP 메시지 {@code {"type": ..., "data": ...}}와 이벤트별 data. */
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

    /** 한쪽이 쓰러져 판이 끝났을 때. {@code resetAt}에 두 캐릭터가 시작 상태로 돌아간다. */
    public record RoundOver(UUID winnerId, UUID loserId, Instant resetAt, long delayMs) {
    }

    /** 새 판이 시작될 때(수동 초기화, 자동 초기화). */
    public record SessionReset(SessionResponse session, List<CharacterResponse> characters) {
    }

    /** 상대가 참가해 새 판이 시작될 때. */
    public record PlayerJoined(List<CharacterType> controls, SessionResponse session,
                               List<CharacterResponse> characters) {
    }

    /** 참가자가 나가 판이 중단되고 상대를 다시 기다릴 때. */
    public record PlayerLeft(List<CharacterType> controls, LeaveReason reason, SessionResponse session,
                             List<CharacterResponse> characters) {
    }

    public record PlayerConnection(List<CharacterType> controls, boolean connected) {
    }

    public record SessionEnded(EndReason reason) {
    }

    public enum LeaveReason {
        LEFT, DISCONNECTED
    }

    public enum EndReason {
        CLOSED, EXPIRED
    }
}
