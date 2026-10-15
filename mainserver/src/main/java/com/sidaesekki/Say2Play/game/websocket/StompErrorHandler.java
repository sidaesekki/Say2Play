package com.sidaesekki.Say2Play.game.websocket;

import com.sidaesekki.Say2Play.global.error.GameException;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/** 거절 사유를 ERROR 프레임의 {@code message}와 {@code code} 헤더에 담는다. REST 에러 코드와 같다. */
@Component
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof GameException gameException) {
                StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
                accessor.setMessage(gameException.getMessage());
                accessor.setNativeHeader("code", gameException.errorCode().name());
                accessor.setLeaveMutable(true);
                return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
            }
        }
        return super.handleClientMessageProcessingError(clientMessage, ex);
    }
}
