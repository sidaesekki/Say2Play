package com.sidaesekki.Say2Play.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "명령 텍스트는 공백을 제외하고 1~500자로 입력해 주세요."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    CHARACTER_NOT_FOUND(HttpStatus.NOT_FOUND, "캐릭터를 찾을 수 없습니다."),
    SESSION_ENDED(HttpStatus.CONFLICT, "이미 종료된 세션입니다."),
    INVALID_PLAYER_TOKEN(HttpStatus.UNAUTHORIZED, "참가자 토큰이 없거나 올바르지 않습니다."),
    NOT_YOUR_CHARACTER(HttpStatus.FORBIDDEN, "내가 조종하는 캐릭터가 아닙니다."),
    STOMP_NOT_ALLOWED(HttpStatus.FORBIDDEN, "자기 세션의 이벤트 구독만 할 수 있습니다."),
    RESET_NOT_ALLOWED(HttpStatus.FORBIDDEN, "멀티플레이에서는 초기화할 수 없습니다. 판이 끝나면 자동으로 초기화됩니다."),
    SESSION_FULL(HttpStatus.CONFLICT, "이미 참가자가 가득 찬 세션입니다."),
    WAITING_FOR_OPPONENT(HttpStatus.CONFLICT, "상대가 참가할 때까지 기다려 주세요."),
    ROUND_OVER(HttpStatus.CONFLICT, "판이 끝났습니다. 잠시 뒤 자동으로 다시 시작합니다."),
    QUEUE_FULL(HttpStatus.TOO_MANY_REQUESTS, "대기 중인 동작이 너무 많습니다. 잠시 뒤 다시 입력해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    AI_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI 서버 호출에 실패했습니다."),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 서버가 제시간에 응답하지 않았습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
