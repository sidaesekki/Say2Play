package com.sidaesekki.Say2Play.game.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param idleTimeout 마지막 입력 이후 세션이 만료되기까지의 시간
 * @param reconnectGrace 참가자의 연결이 모두 끊긴 뒤 나간 것으로 처리하기까지의 시간
 * @param roundResetDelay 한쪽이 쓰러진 뒤 자동 초기화까지의 시간
 */
@ConfigurationProperties("say2play.session")
public record SessionProperties(
        @DefaultValue("30m") Duration idleTimeout,
        @DefaultValue("10s") Duration reconnectGrace,
        @DefaultValue("3s") Duration roundResetDelay) {
}
