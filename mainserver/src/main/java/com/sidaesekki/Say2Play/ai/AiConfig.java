package com.sidaesekki.Say2Play.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ActionResolver actionResolver(AiProperties properties) {
        return switch (properties.mode()) {
            case FAKE -> new FakeActionResolver();
            case HTTP -> new HttpActionResolver(properties.baseUrl(), properties.timeout());
        };
    }
}
