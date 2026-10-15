package com.sidaesekki.Say2Play.ai;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("say2play.ai")
public record AiProperties(
        @DefaultValue("fake") Mode mode,
        @DefaultValue("http://localhost:8001") URI baseUrl,
        @DefaultValue("5s") Duration timeout) {

    public enum Mode {
        FAKE, HTTP
    }
}
