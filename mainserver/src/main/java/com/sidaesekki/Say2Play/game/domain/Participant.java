package com.sidaesekki.Say2Play.game.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 세션에 참가한 사람. 토큰으로 식별하며, GameSession의 락 안에서만 바뀐다. */
public class Participant {

    private final UUID id;
    private final String token;
    private final Set<CharacterType> controls;
    private final Instant joinedAt;
    private int connections;
    // 연결이 하나도 없게 된 시점. 연결되어 있으면 null.
    private Instant disconnectedSince;

    Participant(UUID id, String token, Set<CharacterType> controls, Instant now) {
        this.id = id;
        this.token = token;
        this.controls = EnumSet.copyOf(controls);
        this.joinedAt = now;
        this.disconnectedSince = now;
    }

    public boolean controls(CharacterType type) {
        return controls.contains(type);
    }

    boolean matchesToken(String candidate) {
        return candidate != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }

    /** 연결이 없다가 생겼으면 true. */
    public boolean connect() {
        connections++;
        boolean reconnected = disconnectedSince != null;
        disconnectedSince = null;
        return reconnected;
    }

    /** 마지막 연결이 끊겼으면 true. */
    public boolean disconnect(Instant now) {
        if (connections == 0) {
            return false;
        }
        connections--;
        if (connections > 0) {
            return false;
        }
        disconnectedSince = now;
        return true;
    }

    public boolean isConnected() {
        return disconnectedSince == null;
    }

    /** {@code threshold} 이전부터 계속 연결이 없었으면 true. */
    public boolean isAbsentSince(Instant threshold) {
        return disconnectedSince != null && !disconnectedSince.isAfter(threshold);
    }

    public UUID getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    /** CharacterType 순서(PLAYER, ENEMY)대로. */
    public List<CharacterType> getControls() {
        return List.copyOf(controls);
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
