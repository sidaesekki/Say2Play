package com.sidaesekki.Say2Play.game.websocket;

import java.security.Principal;
import java.util.UUID;

/** STOMP 연결의 주인. CONNECT 때 토큰을 확인해 정한다. */
public record PlayerPrincipal(UUID sessionId, UUID participantId) implements Principal {

    @Override
    public String getName() {
        return participantId.toString();
    }
}
