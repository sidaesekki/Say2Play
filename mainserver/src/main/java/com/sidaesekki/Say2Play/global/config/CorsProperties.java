package com.sidaesekki.Say2Play.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("say2play.cors")
public record CorsProperties(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
}
