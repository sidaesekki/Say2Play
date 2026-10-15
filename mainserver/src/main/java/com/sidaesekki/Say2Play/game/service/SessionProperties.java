package com.sidaesekki.Say2Play.game.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("say2play.session")
public record SessionProperties(@DefaultValue("30m") Duration idleTimeout) {
}
