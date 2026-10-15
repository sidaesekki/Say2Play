package com.sidaesekki.Say2Play.game.websocket;

import com.sidaesekki.Say2Play.global.config.CorsProperties;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import tools.jackson.databind.json.JsonMapper;

/**
 * STOMP over WebSocket. 클라이언트는 {@code /ws}로 연결하고 {@code /topic/sessions/{sessionId}}를 구독한다.
 * 하트비트로 끊긴 연결을 감지해 재접속 유예(reconnect-grace) 처리를 시작한다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class StompConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_MS = 10_000;

    private final CorsProperties corsProperties;
    private final StompAuthInterceptor authInterceptor;
    private final StompErrorHandler errorHandler;
    private final ConnectionRegistry connectionRegistry;
    private final TaskScheduler taskScheduler;
    private final JsonMapper jsonMapper;

    public StompConfig(CorsProperties corsProperties, StompAuthInterceptor authInterceptor,
                       StompErrorHandler errorHandler, ConnectionRegistry connectionRegistry, @Qualifier("taskScheduler") TaskScheduler taskScheduler,
                       JsonMapper jsonMapper) {
        this.corsProperties = corsProperties;
        this.authInterceptor = authInterceptor;
        this.errorHandler = errorHandler;
        this.connectionRegistry = connectionRegistry;
        this.taskScheduler = taskScheduler;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
        registry.setErrorHandler(errorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(taskScheduler);
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(connectionRegistry::decorate);
    }

    // REST 응답과 같은 JSON 형식(ISO-8601 시간 등)을 쓰도록 Boot의 JsonMapper로 직렬화한다.
    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        messageConverters.add(new JacksonJsonMessageConverter(jsonMapper));
        return false;
    }
}
