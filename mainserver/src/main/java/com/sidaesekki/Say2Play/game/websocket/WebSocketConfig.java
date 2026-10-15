package com.sidaesekki.Say2Play.game.websocket;

import com.sidaesekki.Say2Play.global.config.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler handler;
    private final SessionHandshakeInterceptor handshakeInterceptor;
    private final CorsProperties corsProperties;

    public WebSocketConfig(GameWebSocketHandler handler, SessionHandshakeInterceptor handshakeInterceptor,
                           CorsProperties corsProperties) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/sessions/*")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }
}
