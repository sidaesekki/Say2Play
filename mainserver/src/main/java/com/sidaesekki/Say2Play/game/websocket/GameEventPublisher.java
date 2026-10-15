package com.sidaesekki.Say2Play.game.websocket;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/** 게임 세션의 토픽({@code /topic/sessions/{sessionId}})으로 이벤트를 브로드캐스트한다. */
@Component
public class GameEventPublisher {

    // 마지막 이벤트가 클라이언트에 전달될 시간을 준 뒤 연결을 닫는다.
    private static final Duration CLOSE_DELAY = Duration.ofSeconds(1);

    private final SimpMessagingTemplate messagingTemplate;
    private final ConnectionRegistry connectionRegistry;
    private final TaskScheduler taskScheduler;

    public GameEventPublisher(SimpMessagingTemplate messagingTemplate, ConnectionRegistry connectionRegistry,
                              @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
        this.messagingTemplate = messagingTemplate;
        this.connectionRegistry = connectionRegistry;
        this.taskScheduler = taskScheduler;
    }

    public void publish(UUID sessionId, GameEventType type, Object data) {
        messagingTemplate.convertAndSend(StompAuthInterceptor.TOPIC_PREFIX + sessionId, new GameEvent(type, data));
    }

    /** SESSION_ENDED를 보낸 뒤 그 세션의 연결을 모두 닫는다. */
    public void publishEndedAndClose(UUID sessionId, GameEvent.EndReason reason) {
        publish(sessionId, GameEventType.SESSION_ENDED, new GameEvent.SessionEnded(reason));
        taskScheduler.schedule(() -> connectionRegistry.closeSession(sessionId), Instant.now().plus(CLOSE_DELAY));
    }

    /** 나간 참가자의 연결을 닫는다. 토큰이 무효가 되었으므로 다시 연결할 수 없다. */
    public void closeParticipant(UUID participantId) {
        taskScheduler.schedule(() -> connectionRegistry.closeParticipant(participantId),
                Instant.now().plus(CLOSE_DELAY));
    }
}
