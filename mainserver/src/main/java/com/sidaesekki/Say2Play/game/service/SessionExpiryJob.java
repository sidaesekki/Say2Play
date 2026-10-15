package com.sidaesekki.Say2Play.game.service;

import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionExpiryJob {

    private final GameSessionService gameSessionService;
    private final Clock clock;

    public SessionExpiryJob(GameSessionService gameSessionService, Clock clock) {
        this.gameSessionService = gameSessionService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expireIdleSessions() {
        gameSessionService.expireIdleSessions(clock.instant());
    }
}
