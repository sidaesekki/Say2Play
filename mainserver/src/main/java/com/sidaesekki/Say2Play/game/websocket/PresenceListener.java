package com.sidaesekki.Say2Play.game.websocket;

import com.sidaesekki.Say2Play.game.service.GameSessionService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** STOMP 연결과 끊김을 참가자의 접속 상태로 옮긴다. */
@Component
public class PresenceListener {

    private final GameSessionService gameSessionService;
    private final ConnectionRegistry connectionRegistry;

    public PresenceListener(GameSessionService gameSessionService, ConnectionRegistry connectionRegistry) {
        this.gameSessionService = gameSessionService;
        this.connectionRegistry = connectionRegistry;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        if (event.getUser() instanceof PlayerPrincipal principal) {
            String connectionId = (String) event.getMessage().getHeaders().get("simpSessionId");
            connectionRegistry.own(connectionId, principal);
            gameSessionService.connected(principal.sessionId(), principal.participantId());
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        if (event.getUser() instanceof PlayerPrincipal principal) {
            gameSessionService.disconnected(principal.sessionId(), principal.participantId());
        }
    }
}
